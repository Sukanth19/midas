package dev.suks.midas.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentParserTest {
    private fun p(text: String) = PaymentParser.parse(text)

    @Test fun `bank debit sms`() {
        val hdfc = p("Sent Rs.450.00 From HDFC Bank A/C *1234 To SWIGGY On 12/11/26 Ref 431234567890 Not You? Call 18002586161")!!
        assertEquals(45_000L, hdfc.amountMinor)
        assertEquals(TxType.EXPENSE, hdfc.type)
        assertEquals("Swiggy", hdfc.merchant)
        assertEquals("1234", hdfc.account)

        val sbi = p("Dear UPI user A/C X5678 debited by 250.0 on date 12Nov26 trf to ZOMATO LTD Refno 431212345678. If not u? call 1800111109. -SBI")!!
        assertEquals(25_000L, sbi.amountMinor)
        assertEquals("Zomato Ltd", sbi.merchant)
        assertEquals(PaymentMethod.UPI, sbi.method)
        assertEquals("5678", sbi.account)

        val card = p("INR 1,299.00 spent on ICICI Bank Card XX9012 on 12-Nov-26 at AMAZON PAY INDIA. Avl Lmt: INR 45,000.00")!!
        assertEquals(129_900L, card.amountMinor)
        assertEquals("Amazon Pay India", card.merchant)
        assertEquals(PaymentMethod.CARD, card.method)

        val vpa = p("Rs 120 debited from a/c **4321 on 12-11-26 to VPA blinkit.payu@hdfcbank (UPI Ref No 431299887766)")!!
        assertEquals("Blinkit Payu", vpa.merchant)
    }

    @Test fun `bank credit sms`() {
        val c = p("Your A/c XX1234 is credited with Rs.5,000.00 on 12-11-26 by VPA rahul.k@okaxis (UPI Ref No 4312). -Axis Bank")!!
        assertEquals(500_000L, c.amountMinor)
        assertEquals(TxType.INCOME, c.type)
        assertEquals("Rahul K", c.merchant)

        val salary = p("INR 85,000.00 credited to your A/c XX1234 on 01-11-26. Info: NEFT-ACME CORP SALARY. Avl bal INR 1,02,000.00")!!
        assertEquals(8_500_000L, salary.amountMinor)
        assertEquals(TxType.INCOME, salary.type)
        assertEquals(PaymentMethod.BANK, salary.method)
    }

    @Test fun `payment app notifications`() {
        val paid = p("₹200 paid to Rahul Kumar")!!
        assertEquals(20_000L, paid.amountMinor)
        assertEquals(TxType.EXPENSE, paid.type)
        assertEquals("Rahul Kumar", paid.merchant)

        val youPaid = p("You paid ₹1,250.50 to Swiggy")!!
        assertEquals(125_050L, youPaid.amountMinor)
        assertEquals("Swiggy", youPaid.merchant)

        val got = p("Priya sent you ₹500")!!
        assertEquals(TxType.INCOME, got.type)
        assertEquals("Priya", got.merchant)

        val received = p("Received ₹300 from Amit")!!
        assertEquals(TxType.INCOME, received.type)
        assertEquals("Amit", received.merchant)
    }

    @Test fun `ignores things that aren't payments`() {
        assertNull(p("123456 is your OTP for a transaction of Rs.450 at SWIGGY. Do not share it with anyone."))
        assertNull(p("Rahul has requested ₹500 from you on Google Pay"))
        assertNull(p("Your credit card bill of Rs.12,000 is due on 15-11-26. Minimum amount due Rs.600"))
        assertNull(p("Rs.500 will be debited from your account on 15-11 for Netflix mandate"))
        assertNull(p("Get flat Rs.100 cashback! Pay your electricity bill now. Offer valid till Sunday"))
        assertNull(p("Payment of Rs.450 to SWIGGY failed. Money if debited will be refunded"))
        assertNull(p("Rs.2,000 withdrawn at ATM HDFC MG Road from A/c XX1234"))
        assertNull(p("Your order has been shipped and will arrive tomorrow"))
        assertNull(p("Prepaid recharge successful"))
    }

    @Test fun `tidies merchant names`() {
        assertEquals("Swiggy", PaymentParser.prettyName("SWIGGY"))
        assertEquals("Swiggy Instamart", PaymentParser.prettyName("swiggy.instamart@icici"))
        assertEquals("McDonald's", PaymentParser.prettyName("McDonald's"))
        assertEquals("9876543210@ybl", PaymentParser.prettyName("9876543210@ybl"))
    }

    @Test fun `lenient mode for shared text`() {
        assertNull(p("Payment successful ₹450 to Swiggy"))
        val shared = PaymentParser.parse("Payment successful ₹450 to Swiggy", lenient = true)!!
        assertEquals(45_000L, shared.amountMinor)
        assertEquals(TxType.EXPENSE, shared.type)
        assertEquals("Swiggy", shared.merchant)
        assertNull(PaymentParser.parse("no amount here", lenient = true))
    }
}
