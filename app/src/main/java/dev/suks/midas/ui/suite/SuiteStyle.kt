package dev.suks.midas.ui.suite

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.suks.midas.R

// Shared look of pupdr, Midas and Velto, copied from Velto's theme. Keep in step with Velto.

// Plus Jakarta Sans (SIL OFL, see third_party/), one variable font file for every weight
@OptIn(ExperimentalTextApi::class)
private fun jakarta(weight: Int) = Font(
    R.font.plus_jakarta_sans,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight))
)

private val Jakarta = FontFamily(jakarta(400), jakarta(500), jakarta(600), jakarta(700), jakarta(800))

/** The shared font, for one-off text styles outside the type scale. */
val SuiteFontFamily: FontFamily get() = Jakarta

val SuiteTypography = Typography(
    displayLarge = TextStyle(fontFamily = Jakarta, fontSize = 64.sp, fontWeight = FontWeight.W300, letterSpacing = (-2).sp),
    displaySmall = TextStyle(fontFamily = Jakarta, fontSize = 34.sp, fontWeight = FontWeight.W700, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = Jakarta, fontSize = 26.sp, fontWeight = FontWeight.W700, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontFamily = Jakarta, fontSize = 22.sp, fontWeight = FontWeight.W700),
    titleLarge = TextStyle(fontFamily = Jakarta, fontSize = 20.sp, fontWeight = FontWeight.W700),
    titleMedium = TextStyle(fontFamily = Jakarta, fontSize = 16.sp, fontWeight = FontWeight.W600),
    titleSmall = TextStyle(fontFamily = Jakarta, fontSize = 14.sp, fontWeight = FontWeight.W600),
    bodyLarge = TextStyle(fontFamily = Jakarta, fontSize = 16.sp, fontWeight = FontWeight.W400),
    bodyMedium = TextStyle(fontFamily = Jakarta, fontSize = 14.sp, fontWeight = FontWeight.W400),
    bodySmall = TextStyle(fontFamily = Jakarta, fontSize = 12.sp, fontWeight = FontWeight.W400),
    labelLarge = TextStyle(fontFamily = Jakarta, fontSize = 14.sp, fontWeight = FontWeight.W600),
    labelMedium = TextStyle(fontFamily = Jakarta, fontSize = 12.sp, fontWeight = FontWeight.W600),
    labelSmall = TextStyle(fontFamily = Jakarta, fontSize = 11.sp, fontWeight = FontWeight.W600, letterSpacing = 0.5.sp)
)

// Expressive corners: noticeably rounder than the baseline so cards and sheets feel soft
val SuiteShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

/** Spacing tokens so every screen uses the same rhythm. */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
}
