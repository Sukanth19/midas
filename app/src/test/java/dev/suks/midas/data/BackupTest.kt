package dev.suks.midas.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import dev.suks.midas.AppContainer
import dev.suks.midas.WorkspaceManager
import dev.suks.midas.domain.TxType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

import kotlinx.coroutines.flow.first
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows

@RunWith(RobolectricTestRunner::class)
class BackupTest {
    @get:Rule val tmp = TemporaryFolder()
    private val today = LocalDate.of(2026, 11, 11)
    private val clock = Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val stores = mutableMapOf<String, DataStore<Preferences>>()
    private val created = mutableListOf<AppContainer>()

    private fun store(name: String) = stores.getOrPut(name) {
        PreferenceDataStoreFactory.create(scope = storeScope) { tmp.root.resolve("$name.preferences_pb") }
    }

    private fun manager(scope: CoroutineScope): WorkspaceManager {
        val prefs = context.getSharedPreferences("backup-test", Context.MODE_PRIVATE)
        return WorkspaceManager(
            registry = WorkspaceRegistry(prefs),
            scope = scope,
            newContainer = { id ->
                AppContainer(
                    context, database = MidasDatabase.build(context, inMemory = true), clock = clock, workspaceId = id,
                    settings = SettingsRepository(store(Workspace.settingsName(Workspace.DEFAULT_ID)), store(Workspace.settingsName(id))),
                ).also { created += it }
            },
            deleteData = {},
        )
    }

    @Before fun setUp() { context.getSharedPreferences("backup-test", Context.MODE_PRIVATE).edit().clear().commit() }

    @After fun tearDown() {
        created.forEach { runCatching { it.close() } }
        storeScope.cancel()
    }

    @Test fun `a backup restores every workspace exactly`() = runTest {
        val m = manager(this)
        val backups = BackupManager(m, "1.1.0")
        val home = m.current
        val food = home.categories.getAll().first { it.name == "Food & Dining" }
        home.transactions.save(Transaction(amountMinor = 45_000, type = TxType.EXPENSE, categoryId = food.id, title = "Dinner", date = today))
        home.bills.save(Bill(name = "Rent", amountMinor = 1_800_000, nextDue = today, upiId = "landlord@ybl"))
        home.settings.setMyName("Ani")
        val trip = m.create("Goa trip", switchTo = false)
        val t = m.containerFor(trip.id)
        t.settings.setCurrency("USD")
        val rahul = t.split.addPerson("Rahul")!!
        t.transactions.save(Transaction(amountMinor = 9_000, type = TxType.EXPENSE, categoryId = null, title = "Taxi", date = today, shared = true, paidById = rahul))
        t.split.settle(rahul, null, 3_000, today)

        val text = backups.export(now = 1L)

        // Things change after the backup...
        home.transactions.save(Transaction(amountMinor = 1, type = TxType.EXPENSE, categoryId = null, title = "Later", date = today))
        m.delete(trip.id)
        val other = m.create("Other")

        backups.restore(backups.read(text))

        assertEquals(listOf(Workspace.DEFAULT_ID, trip.id), m.registry.workspaces.value.map { it.id })
        assertEquals(Workspace.DEFAULT_ID, m.registry.activeId.value)
        assertNull(m.registry.get(other.id))
        val restoredHome = m.containerFor(Workspace.DEFAULT_ID)
        val dinner = restoredHome.transactions.getAll().single()
        assertEquals("Dinner", dinner.title)
        assertEquals(food.id, dinner.categoryId)
        assertEquals("landlord@ybl", restoredHome.bills.getActive().single().upiId)
        assertEquals("Ani", restoredHome.settings.current().myName)
        val restoredTrip = m.containerFor(trip.id)
        assertEquals("USD", restoredTrip.settings.current().currency)
        assertEquals(listOf("Rahul"), restoredTrip.split.getPeople().map { it.name })
        assertEquals(rahul, restoredTrip.transactions.getAll().single().paidById)
        assertEquals(3_000L, restoredTrip.split.settlements.first().single().amountMinor)
    }

    @Test fun `bad files are rejected without changing anything`() {
        val backups = BackupManager(manager(CoroutineScope(Dispatchers.Unconfined)), "1.1.0")
        assertThrows(IllegalArgumentException::class.java) { backups.read("{not json") }
        assertThrows(IllegalArgumentException::class.java) { backups.read("""{"format":99,"createdAt":1,"appVersion":"9","activeWorkspaceId":"default","workspaces":[]}""") }
    }

    @Test fun `old automatic backups are pruned, other files never`() {
        val names = (1..9).map { BackupManager.fileName(today.minusDays(it.toLong())) } + listOf("notes.txt", "midas-2026.csv")
        assertEquals(listOf(BackupManager.fileName(today.minusDays(8)), BackupManager.fileName(today.minusDays(9))), BackupManager.toPrune(names))
        assertEquals("midas-backup-2026-11-11.json", BackupManager.fileName(today))
    }
}
