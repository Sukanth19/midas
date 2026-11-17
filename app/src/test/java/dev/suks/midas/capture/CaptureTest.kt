package dev.suks.midas.capture

import dev.suks.midas.domain.ParsedPayment
import dev.suks.midas.domain.PaymentMethod
import dev.suks.midas.domain.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureTest {
    @Test fun `only bank sender ids are read`() {
        assertTrue(SmsReceiver.isBusinessSender("VM-HDFCBK"))
        assertTrue(SmsReceiver.isBusinessSender("AX-SBIUPI-S"))
        assertFalse(SmsReceiver.isBusinessSender("+919876543210"))
        assertFalse(SmsReceiver.isBusinessSender(""))
    }

    @Test fun `notification titles`() {
        val p = ParsedPayment(45_000, TxType.EXPENSE, "Swiggy", PaymentMethod.UPI, null)
        assertEquals("₹450 to Swiggy", PaymentCapture.title(p))
        assertEquals("₹450 paid", PaymentCapture.title(p.copy(merchant = null)))
        assertEquals("₹450 from Priya", PaymentCapture.title(p.copy(type = TxType.INCOME, merchant = "Priya")))
    }
}
