package dev.suks.midas.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.test.core.app.ApplicationProvider
import dev.suks.midas.AppContainer
import dev.suks.midas.R
import dev.suks.midas.data.Bill
import dev.suks.midas.data.GroceryItem
import dev.suks.midas.data.MidasDatabase
import dev.suks.midas.data.Transaction
import dev.suks.midas.domain.TxType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class WidgetDataTest {
    private val today = LocalDate.of(2026, 11, 11)
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = MidasDatabase.build(context, inMemory = true)
    private val container = AppContainer(context, db, Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC))

    @After fun tearDown() = db.close()

    @Test fun `widget data reflects what is in the database`() = runTest {
        container.settings.setMonthlyBudget(1_000_000)
        container.transactions.save(Transaction(amountMinor = 300_000, type = TxType.EXPENSE, categoryId = null, title = "Shoes", date = today))
        container.transactions.save(Transaction(amountMinor = 50_000, type = TxType.EXPENSE, categoryId = null, title = "Cab", date = today.minusDays(2)))
        container.transactions.save(Transaction(amountMinor = 99_999, type = TxType.EXPENSE, categoryId = null, title = "Old", date = today.minusMonths(1)))
        container.grocery.save(GroceryItem(name = "Milk", quantity = 2.0, unitPriceMinor = 6_000, checked = true))
        container.grocery.save(GroceryItem(name = "Bread", unitPriceMinor = 5_000))
        repeat(5) { container.bills.save(Bill(name = "Bill $it", amountMinor = 1_000, nextDue = today.plusDays(it.toLong()))) }

        val d = WidgetData.load(container)
        assertEquals(350_000L, d.budget.spentMinor)
        assertEquals(650_000L, d.budget.remainingMinor)
        assertEquals("November", d.monthLabel)
        assertEquals(listOf(0L, 0L, 0L, 0L, 50_000L, 0L, 300_000L), d.last7Days)
        assertEquals(listOf("Bread", "Milk"), d.grocery.map { it.name }) // unchecked first
        assertEquals(17_000L, d.groceryTotals.estimatedMinor)
        assertEquals(4, d.bills.size)
        assertEquals("Bill 0", d.bills.first().name)
    }

    @Test fun `sample data looks like a real month`() {
        val s = WidgetData.sample(today)
        assertTrue(s.budget.fraction in 0.5f..0.9f)
        assertEquals(7, s.last7Days.size)
        assertTrue(s.grocery.isNotEmpty() && s.bills.size == 4)
        assertTrue(s.grocery.indexOfFirst { it.checked } > s.grocery.indexOfLast { !it.checked })
    }
}

/**
 * Inflates every widget-picker preview layout through RemoteViews, exactly as a launcher does,
 * which fails if a layout uses a view class widgets don't allow.
 *
 * Run with -Dmidas.renderPreviews=true to also render them (light and dark) into the
 * previewImage PNGs that older Android versions show.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetPreviewTest {

    private data class Preview(val layout: Int, val name: String, val widthDp: Int, val heightDp: Int)

    private val previews = listOf(
        Preview(R.layout.widget_preview_spending, "spending", 320, 150),
        Preview(R.layout.widget_preview_grocery, "grocery", 320, 270),
        Preview(R.layout.widget_preview_bills, "bills", 320, 205),
        Preview(R.layout.widget_preview_quickadd, "quickadd", 84, 84),
    )

    private fun render(p: Preview): Bitmap {
        val context: Context = ApplicationProvider.getApplicationContext()
        val parent = FrameLayout(context)
        val view = RemoteViews(context.packageName, p.layout).apply(context, parent)
        val density = context.resources.displayMetrics.density
        val w = (p.widthDp * density).toInt()
        val h = (p.heightDp * density).toInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, w, h)
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }

    private fun renderAll(folder: String) {
        val write = System.getProperty("midas.renderPreviews") == "true"
        val out = File(System.getProperty("midas.projectDir") ?: ".", "src/main/res/$folder")
        previews.forEach { p ->
            val bmp = render(p)
            // Something was actually drawn: the rounded background leaves corners transparent but fills the middle.
            assertTrue("${p.name} drew nothing", bmp.getPixel(bmp.width / 2, bmp.height / 2) ushr 24 != 0)
            assertEquals("${p.name} corner should be transparent", 0, bmp.getPixel(0, 0) ushr 24)
            if (write) {
                out.mkdirs()
                File(out, "widget_preview_${p.name}.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }

    @Test @Config(qualifiers = "xxhdpi")
    fun `light previews inflate as remote views and render`() = renderAll("drawable-nodpi")

    @Test @Config(qualifiers = "night-xxhdpi")
    fun `dark previews inflate as remote views and render`() {
        assertTrue(RuntimeEnvironment.getQualifiers().contains("night"))
        renderAll("drawable-night-nodpi")
    }
}
