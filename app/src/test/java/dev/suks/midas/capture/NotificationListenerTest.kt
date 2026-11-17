package dev.suks.midas.capture

import android.app.Notification
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import dev.suks.midas.domain.PaymentParser
import dev.suks.midas.domain.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotificationListenerTest {
    private fun notification(title: String?, text: String?): Notification =
        NotificationCompat.Builder(ApplicationProvider.getApplicationContext(), "x")
            .setContentTitle(title).setContentText(text).build()

    @Test fun `title and text are read together`() {
        val text = PaymentNotificationListener.textOf(notification("₹200 paid to Rahul", "Paid via HDFC Bank 1234"))!!
        assertEquals("₹200 paid to Rahul. Paid via HDFC Bank 1234", text)
        val p = PaymentParser.parse(text)!!
        assertEquals(20_000L, p.amountMinor)
        assertEquals(TxType.EXPENSE, p.type)
        assertEquals("Rahul", p.merchant)
    }

    @Test fun `empty notifications are skipped`() {
        assertNull(PaymentNotificationListener.textOf(notification(null, null)))
    }

    @Test fun `known payment apps`() {
        assertEquals("Google Pay", PaymentNotificationListener.PAYMENT_APPS["com.google.android.apps.nbu.paisa.user"])
        assertNull(PaymentNotificationListener.PAYMENT_APPS["com.whatsapp"])
    }
}
