package dev.suks.midas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** The launcher and notification icons inflate and draw the mark where expected. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "xxhdpi")
class IconTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun render(id: Int, size: Int): Bitmap {
        val d = ContextCompat.getDrawable(context, id)!!
        return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also {
            d.setBounds(0, 0, size, size)
            d.draw(Canvas(it))
            // Set MIDAS_ICON_OUT to a folder to look at the rendered icons.
            System.getenv("MIDAS_ICON_OUT")?.let { dir ->
                File(dir).mkdirs()
                File(dir, "${context.resources.getResourceEntryName(id)}.png").outputStream().use { o -> it.compress(Bitmap.CompressFormat.PNG, 100, o) }
            }
        }
    }

    /** Brightness 0..255 of a pixel, given in 108-unit icon coordinates. */
    private fun Bitmap.lum(x: Int, y: Int): Int {
        val p = getPixel(x * width / 108, y * height / 108)
        return (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
    }

    @Test fun `launcher icon is an adaptive icon with background, foreground and monochrome layers`() {
        val icon = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
        assertTrue(icon is AdaptiveIconDrawable)
        icon as AdaptiveIconDrawable
        assertTrue(icon.background != null && icon.foreground != null && icon.monochrome != null)
    }

    @Test fun `dark mark with a white glint sits on a gold background`() {
        val bg = render(R.drawable.ic_launcher_background, 432)
        val fg = render(R.drawable.ic_launcher_foreground, 432)
        assertTrue("background should be bright gold", bg.lum(54, 54) > 150)
        // The M's left leg is dark ink; the glint is white.
        assertTrue("M stroke should be drawn", Color.alpha(fg.getPixel(34 * 4, 60 * 4)) == 255 && fg.lum(34, 60) < 50)
        assertTrue("glint should be bright", fg.lum(72, 38) > 220)
        // Outside the mark the foreground is see-through, so the background shows.
        assertTrue("corners stay transparent", Color.alpha(fg.getPixel(8, 8)) == 0)
    }

    @Test fun `monochrome and notification icons draw the same mark`() {
        val mono = render(R.drawable.ic_launcher_monochrome, 432)
        assertTrue(Color.alpha(mono.getPixel(34 * 4, 60 * 4)) == 255)
        val stat = render(R.drawable.ic_stat_midas, 96)
        var opaque = 0
        for (x in 0 until 96) for (y in 0 until 96) if (Color.alpha(stat.getPixel(x, y)) > 128) opaque++
        assertTrue("notification icon should draw something", opaque > 96 * 96 / 10)
        assertTrue("notification icon keeps a margin", Color.alpha(stat.getPixel(1, 1)) == 0 && Color.alpha(stat.getPixel(94, 94)) == 0)
    }
}
