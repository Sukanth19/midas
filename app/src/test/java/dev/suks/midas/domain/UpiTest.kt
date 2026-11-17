package dev.suks.midas.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpiTest {
    @Test fun `validates upi ids`() {
        assertTrue(Upi.isValidId("landlord@okhdfcbank"))
        assertTrue(Upi.isValidId(" 9876543210@ybl "))
        assertTrue(Upi.isValidId("a.b-c_d@paytm"))
        assertFalse(Upi.isValidId("landlord"))
        assertFalse(Upi.isValidId("@ybl"))
        assertFalse(Upi.isValidId("name@"))
        assertFalse(Upi.isValidId("two words@ybl"))
    }

    @Test fun `builds a pay link with encoded fields`() {
        assertEquals(
            "upi://pay?pa=landlord@okhdfcbank&pn=Rent%20%26%20maintenance&am=18000.50&cu=INR&tn=Midas%3A%20Rent",
            Upi.payUri("landlord@okhdfcbank", "Rent & maintenance", 1_800_050, "Midas: Rent"),
        )
        assertEquals("upi://pay?pa=x@ybl&am=1.00&cu=INR", Upi.payUri("x@ybl", "", 100, ""))
    }

    @Test fun `reads app replies`() {
        assertEquals(Upi.Result.SUCCESS, Upi.parseResponse("txnId=AX1&responseCode=00&Status=SUCCESS&txnRef=9"))
        assertEquals(Upi.Result.SUCCESS, Upi.parseResponse("status=success"))
        assertEquals(Upi.Result.FAILED, Upi.parseResponse("Status=FAILURE&responseCode=ZD"))
        assertEquals(Upi.Result.UNKNOWN, Upi.parseResponse(null))
        assertEquals(Upi.Result.UNKNOWN, Upi.parseResponse("txnId=AX1"))
    }
}
