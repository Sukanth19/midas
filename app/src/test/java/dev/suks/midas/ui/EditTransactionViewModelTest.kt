package dev.suks.midas.ui

import android.content.Context
import android.os.Looper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import dev.suks.midas.AppContainer
import dev.suks.midas.data.MidasDatabase
import dev.suks.midas.data.SettingsRepository
import dev.suks.midas.data.Transaction
import dev.suks.midas.domain.CaptureStatus
import dev.suks.midas.domain.ParsedPayment
import dev.suks.midas.domain.PaymentMethod
import dev.suks.midas.domain.TxSource
import dev.suks.midas.domain.TxType
import dev.suks.midas.ui.transactions.EditTransactionViewModel
import dev.suks.midas.ui.transactions.EditTxState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class EditTransactionViewModelTest {
    @get:Rule val tmp = TemporaryFolder()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 11, 11)
    private val c by lazy {
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + SupervisorJob())) { tmp.root.resolve("s.preferences_pb") }
        AppContainer(
            context, database = MidasDatabase.build(context, inMemory = true),
            clock = Clock.fixed(today.atTime(10, 0).atZone(zone).toInstant(), zone),
            settings = SettingsRepository(store),
        )
    }

    @After fun tearDown() = c.close()

    private fun EditTransactionViewModel.await(done: (EditTxState) -> Boolean): EditTxState {
        repeat(200) {
            shadowOf(Looper.getMainLooper()).idle()
            if (done(state.value)) return state.value
            Thread.sleep(10)
        }
        error("timed out; last state ${state.value}")
    }

    @Test fun `shared text fills in amount, payee and their usual category`() {
        val food = runBlocking { c.categories.getAll().first { it.name == "Food & Dining" } }
        runBlocking { c.transactions.save(Transaction(amountMinor = 1, type = TxType.EXPENSE, categoryId = food.id, title = "Swiggy", date = today)) }
        val vm = EditTransactionViewModel(c, 0, false, sharedText = "Payment successful ₹450 to Swiggy")
        val s = vm.await { !it.loading }
        assertEquals("450", s.amountText)
        assertEquals("Swiggy", s.title)
        assertEquals(food.id, s.categoryId)
        assertTrue(s.fromShare)
    }

    @Test fun `unreadable shared text lands in the note`() {
        val s = EditTransactionViewModel(c, 0, false, sharedText = "dinner with friends").await { !it.loading }
        assertEquals("", s.amountText)
        assertEquals("dinner with friends", s.note)
    }

    @Test fun `saving a reviewed capture marks it added`() {
        val id = runBlocking {
            c.captures.capture(ParsedPayment(12_000, TxType.EXPENSE, "Rapido", PaymentMethod.UPI, null), "SMS", "", today.atTime(9, 0).atZone(zone).toInstant().toEpochMilli())!!
        }
        val vm = EditTransactionViewModel(c, 0, false, captureId = id)
        val s = vm.await { !it.loading }
        assertEquals("120", s.amountText)
        assertEquals("SMS", s.fromCapture)
        assertEquals(today, s.date)
        vm.setCategory(runBlocking { c.categories.getAll().first { it.name == "Transport" }.id })
        vm.save()
        vm.await { it.done }
        val capture = runBlocking { c.captures.get(id)!! }
        assertEquals(CaptureStatus.ADDED, capture.status)
        val tx = runBlocking { c.transactions.get(capture.transactionId!!)!! }
        assertEquals(TxSource.CAPTURED, tx.source)
        assertEquals("Rapido", tx.title)
    }
}
