package dev.suks.midas.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.LocalDate

/**
 * Builds a database exactly as version 1 shipped it (from the exported schema), fills it with data,
 * then opens it with the current Room setup. Room checks every table against the new schema on open,
 * so this fails if a migration is missing or wrong.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "migration-test.db"

    @After fun tearDown() { context.deleteDatabase(name) }

    private fun createFromSchema(version: Int, fill: (SQLiteDatabase) -> Unit) {
        val dir = System.getProperty("midas.projectDir")?.let { File(it) } ?: File(".")
        val schema = File(dir, "schemas/dev.suks.midas.data.MidasDatabase/$version.json").let { if (it.exists()) it else File("app/${it.path}") }
        val db = Json.parseToJsonElement(schema.readText()).jsonObject["database"]!!.jsonObject
        val sql = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile?.mkdirs() }, null)
        db["entities"]!!.jsonArray.map { it.jsonObject }.forEach { e: JsonObject ->
            val table = e["tableName"]!!.jsonPrimitive.content
            sql.execSQL(e["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            e["indices"]?.jsonArray?.forEach { i -> sql.execSQL(i.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
        }
        db["setupQueries"]!!.jsonArray.forEach { sql.execSQL(it.jsonPrimitive.content) }
        sql.version = version
        fill(sql)
        sql.close()
    }

    @Test fun `version 1 data survives the upgrade`() = runTest {
        val day = LocalDate.of(2026, 11, 11).toEpochDay()
        createFromSchema(1) { db ->
            db.execSQL("INSERT INTO categories (id, name, icon, color, type, monthlyBudgetMinor, sortOrder) VALUES (1, 'Food', 'restaurant', 0, 'EXPENSE', 0, 0)")
            db.execSQL("INSERT INTO transactions (amountMinor, type, categoryId, title, note, date, paymentMethod, source, billId, createdAt) VALUES (4500, 'EXPENSE', 1, 'Lunch', '', $day, 'UPI', 'MANUAL', NULL, 0)")
            db.execSQL("INSERT INTO bills (name, amountMinor, categoryId, icon, frequency, interval, nextDue, anchorDay, remindDaysBefore, paymentMethod, active, lastPaidOn) VALUES ('Rent', 1800000, NULL, 'home', 'MONTHLY', 1, $day, 11, 2, 'BANK', 1, NULL)")
            db.execSQL("INSERT INTO grocery_items (name, quantity, unit, unitPriceMinor, section, checked, staple, sortOrder) VALUES ('Milk', 2.0, 'l', 6000, 'Dairy & Eggs', 1, 1, 0)")
        }
        val room = MidasDatabase.build(context, name = name)
        try {
            val lunch = room.transactions().getAll().single()
            assertEquals("Lunch", lunch.title)
            assertEquals(false, lunch.shared)
            assertEquals(null, lunch.paidById)
            assertEquals("Rent", room.bills().getActive().single().name)
            assertEquals("", room.bills().getActive().single().upiId)
            val grocery = GroceryRepository(room) {}
            grocery.finishWeek(LocalDate.ofEpochDay(day), null, null)
            assertEquals(6000L, room.grocery().latestPrice("milk")!!.unitPriceMinor)
        } finally {
            room.close()
        }
    }
}
