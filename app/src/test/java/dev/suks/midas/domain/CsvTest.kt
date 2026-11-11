package dev.suks.midas.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CsvTest {

    @Test fun `escapes commas quotes and newlines`() {
        assertEquals("plain", Csv.escape("plain"))
        assertEquals("\"a,b\"", Csv.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", Csv.escape("say \"hi\""))
        assertEquals("\"two\nlines\"", Csv.escape("two\nlines"))
    }

    @Test fun `parse handles quoted fields, CRLF and empty cells`() {
        val rows = Csv.parse("a,b,c\r\n\"x,1\",,\"he said \"\"no\"\"\"\r\n\"multi\nline\",2,3\n")
        assertEquals(listOf("a", "b", "c"), rows[0])
        assertEquals(listOf("x,1", "", "he said \"no\""), rows[1])
        assertEquals(listOf("multi\nline", "2", "3"), rows[2])
        assertEquals(3, rows.size)
    }

    @Test fun `write then parse round trips`() {
        val data = listOf(listOf("date", "note"), listOf("2026-11-11", "Dinner, \"fancy\"\nplace"))
        assertEquals(data, Csv.parse(Csv.write(data)))
    }

    @Test fun `transactions export and import round trip`() {
        val items = listOf(
            CsvTransaction(LocalDate.of(2026, 11, 1), TxType.INCOME, 9_500_000, "Salary", "November salary", "", PaymentMethod.BANK),
            CsvTransaction(LocalDate.of(2026, 11, 2), TxType.EXPENSE, 125_050, "Food & Dining", "Dinner, with friends", "split 3 ways", PaymentMethod.UPI),
        )
        val text = TransactionCsv.export(items, "INR")
        assertEquals("date,type,amount,category,title,note,payment_method", text.lines().first())
        val back = TransactionCsv.import(text, "INR")
        assertEquals(items, back.items)
        assertEquals(0, back.skippedRows)
    }

    @Test fun `import is lenient about bank style files`() {
        val text = """
            Date,Amount,Category,Title
            2026-11-03,-450.00,Transport,Uber
            not-a-date,10,Food,Bad row
            2026-11-04,0,Food,Zero row
            2026-11-05,1200,,Coffee beans
        """.trimIndent()
        val r = TransactionCsv.import("﻿" + text, "INR")
        assertEquals(2, r.items.size)
        assertEquals(2, r.skippedRows)
        assertEquals(45_000L, r.items[0].amountMinor)
        assertEquals(TxType.EXPENSE, r.items[0].type)
        assertEquals("Other", r.items[1].category)
        assertEquals(PaymentMethod.OTHER, r.items[1].paymentMethod)
    }

    @Test fun `import without required columns skips everything`() {
        val r = TransactionCsv.import("foo,bar\n1,2\n3,4\n", "INR")
        assertEquals(0, r.items.size)
        assertEquals(2, r.skippedRows)
    }
}
