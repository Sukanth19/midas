package dev.suks.midas.workspaces

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import dev.suks.midas.AppContainer
import dev.suks.midas.WorkspaceManager
import dev.suks.midas.data.Bill
import dev.suks.midas.data.GroceryItem
import dev.suks.midas.data.MidasDatabase
import dev.suks.midas.data.SettingsRepository
import dev.suks.midas.data.Transaction
import dev.suks.midas.data.Workspace
import dev.suks.midas.data.WorkspaceRegistry
import dev.suks.midas.domain.ThemeMode
import dev.suks.midas.domain.TxType
import dev.suks.midas.widget.WidgetData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class WorkspaceManagerTest {
    @get:Rule val tmp = TemporaryFolder()
    private val today = LocalDate.of(2026, 11, 11)
    private val clock = Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val stores = mutableMapOf<String, DataStore<Preferences>>()
    private val created = mutableListOf<AppContainer>()
    private val deleted = mutableListOf<String>()

    private fun store(name: String) = stores.getOrPut(name) {
        PreferenceDataStoreFactory.create(scope = storeScope) { tmp.root.resolve("$name.preferences_pb") }
    }

    private fun manager(scope: CoroutineScope): WorkspaceManager {
        val prefs = context.getSharedPreferences("workspaces-manager-test", Context.MODE_PRIVATE)
        return WorkspaceManager(
            registry = WorkspaceRegistry(prefs),
            scope = scope,
            newContainer = { id ->
                AppContainer(
                    context,
                    database = MidasDatabase.build(context, inMemory = true),
                    clock = clock,
                    workspaceId = id,
                    settings = SettingsRepository(store(Workspace.settingsName(Workspace.DEFAULT_ID)), store(Workspace.settingsName(id))),
                ).also { created += it }
            },
            deleteData = { deleted += it },
        )
    }

    @Before fun setUp() {
        context.getSharedPreferences("workspaces-manager-test", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun tearDown() {
        created.forEach { runCatching { it.close() } }
        storeScope.cancel()
    }

    private fun expense(title: String, minor: Long) =
        Transaction(amountMinor = minor, type = TxType.EXPENSE, categoryId = null, title = title, date = today)

    @Test fun `containers are opened once per workspace and reused`() = runTest {
        val m = manager(this)
        val first = m.current
        assertSame(first, m.current)
        assertEquals(Workspace.DEFAULT_ID, first.workspaceId)
        val ws = m.create("Roommate")
        assertNotSame(first, m.current)
        assertSame(m.containerFor(ws.id), m.current)
        assertEquals(2, created.size)
    }

    @Test fun `each workspace has its own data`() = runTest {
        val m = manager(this)
        val mine = m.current
        mine.transactions.save(expense("My lunch", 25_000))
        mine.grocery.save(GroceryItem(name = "Milk"))
        mine.bills.save(Bill(name = "My phone", amountMinor = 59_900, nextDue = today))

        m.create("Roommate")
        val theirs = m.current
        assertTrue(theirs.transactions.getAll().isEmpty())
        assertTrue(theirs.grocery.getItems().isEmpty())
        assertTrue(theirs.bills.getActive().isEmpty())
        // A brand new workspace still gets the default categories to pick from.
        assertTrue(theirs.categories.getAll().any { it.name == "Groceries" })

        theirs.transactions.save(expense("Their rent", 1_200_000))
        assertEquals(listOf("My lunch"), mine.transactions.getAll().map { it.title })
        assertEquals(listOf("Their rent"), theirs.transactions.getAll().map { it.title })
    }

    @Test fun `budget and currency are per workspace, theme is shared`() = runTest {
        val m = manager(this)
        val mine = m.current
        mine.settings.setCurrency("USD")
        mine.settings.setMonthlyBudget(500_000)
        m.create("Friend")
        val friend = m.current
        // A new workspace starts with the currency you were using, but no budget.
        assertEquals("USD", friend.settings.current().currency)
        assertEquals(0L, friend.settings.current().monthlyBudgetMinor)

        friend.settings.setCurrency("EUR")
        friend.settings.setMonthlyBudget(100_000)
        friend.settings.setTheme(ThemeMode.DARK)

        val s = mine.settings.current()
        assertEquals("USD", s.currency)
        assertEquals(500_000L, s.monthlyBudgetMinor)
        assertEquals(ThemeMode.DARK, s.themeMode)
    }

    @Test fun `create can leave the current workspace open`() = runTest {
        val m = manager(this)
        val ws = m.create("Later", switchTo = false)
        assertEquals(Workspace.DEFAULT_ID, m.registry.activeId.value)
        assertEquals(listOf(Workspace.DEFAULT_ID, ws.id), m.all().map { it.first.id })
    }

    @Test fun `widgets follow the open workspace`() = runTest {
        val m = manager(this)
        m.current.transactions.save(expense("Mine", 30_000))
        val ws = m.create("Roommate")
        m.current.transactions.save(expense("Theirs", 70_000))
        assertEquals(70_000L, WidgetData.load(m.current).budget.spentMinor)
        m.select(Workspace.DEFAULT_ID)
        assertEquals(30_000L, WidgetData.load(m.current).budget.spentMinor)
        m.select(ws.id)
        assertEquals(70_000L, WidgetData.load(m.current).budget.spentMinor)
    }

    @Test fun `delete removes the workspace, its files, and closes its database later`() = runTest {
        val m = manager(this)
        val ws = m.create("Trip")
        val trip = m.current
        trip.transactions.save(expense("Hotel", 400_000))

        assertTrue(m.delete(ws.id))
        assertEquals(Workspace.DEFAULT_ID, m.registry.activeId.value)
        assertEquals(listOf(ws.id), deleted)
        assertEquals(listOf(Workspace.DEFAULT_ID), m.all().map { it.first.id })

        // Screens of the old workspace may still be winding down, so the database stays open briefly.
        runCurrent()
        assertTrue(trip.database.isOpen)
        advanceTimeBy(10_001)
        assertFalse(trip.database.isOpen)
    }

    @Test fun `the default workspace can't be deleted`() = runTest {
        val m = manager(this)
        assertFalse(m.delete(Workspace.DEFAULT_ID))
        assertFalse(m.delete("unknown"))
        assertTrue(deleted.isEmpty())
    }

    @Test fun `rename goes through to the registry`() = runTest {
        val m = manager(this)
        val ws = m.create("Roomate")
        assertTrue(m.rename(ws.id, "Roommate"))
        assertEquals("Roommate", m.registry.get(ws.id)!!.name)
    }

}
