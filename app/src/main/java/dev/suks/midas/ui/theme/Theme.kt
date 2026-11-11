package dev.suks.midas.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.suks.midas.domain.ThemeMode

// "Midas gold" scheme: Material 3 tonal palette generated around a warm gold seed.
val LightColors = lightColorScheme(
    primary = Color(0xFF745B00), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE08A), onPrimaryContainer = Color(0xFF241A00),
    secondary = Color(0xFF695D3F), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF2E1BB), onSecondaryContainer = Color(0xFF231B04),
    tertiary = Color(0xFF47664A), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC8ECC8), onTertiaryContainer = Color(0xFF04210B),
    error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFFF8F0), onBackground = Color(0xFF1F1B13),
    surface = Color(0xFFFFF8F0), onSurface = Color(0xFF1F1B13),
    surfaceVariant = Color(0xFFECE1CF), onSurfaceVariant = Color(0xFF4C4639),
    outline = Color(0xFF7E7667), outlineVariant = Color(0xFFCFC5B4),
    inverseSurface = Color(0xFF343027), inverseOnSurface = Color(0xFFF8F0E2), inversePrimary = Color(0xFFE6C449),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFAF2E5),
    surfaceContainer = Color(0xFFF4EDDF), surfaceContainerHigh = Color(0xFFEEE7D9),
    surfaceContainerHighest = Color(0xFFE9E1D4),
    surfaceDim = Color(0xFFE1D9CC), surfaceBright = Color(0xFFFFF8F0),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFFE6C449), onPrimary = Color(0xFF3D2E00),
    primaryContainer = Color(0xFF584400), onPrimaryContainer = Color(0xFFFFE08A),
    secondary = Color(0xFFD5C5A1), onSecondary = Color(0xFF393015),
    secondaryContainer = Color(0xFF50462A), onSecondaryContainer = Color(0xFFF2E1BB),
    tertiary = Color(0xFFACCFAD), onTertiary = Color(0xFF19361F),
    tertiaryContainer = Color(0xFF304D34), onTertiaryContainer = Color(0xFFC8ECC8),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF17130B), onBackground = Color(0xFFEBE1D4),
    surface = Color(0xFF17130B), onSurface = Color(0xFFEBE1D4),
    surfaceVariant = Color(0xFF4C4639), onSurfaceVariant = Color(0xFFCFC5B4),
    outline = Color(0xFF989080), outlineVariant = Color(0xFF4C4639),
    inverseSurface = Color(0xFFEBE1D4), inverseOnSurface = Color(0xFF343027), inversePrimary = Color(0xFF745B00),
    surfaceContainerLowest = Color(0xFF110E07), surfaceContainerLow = Color(0xFF1F1B13),
    surfaceContainer = Color(0xFF231F17), surfaceContainerHigh = Color(0xFF2E2A21),
    surfaceContainerHighest = Color(0xFF39342B),
    surfaceDim = Color(0xFF17130B), surfaceBright = Color(0xFF3E382F),
)

/** Colours Material 3 has no slot for: money in, warnings. */
@Immutable
data class MoneyColors(val positive: Color, val positiveContainer: Color, val warning: Color, val warningContainer: Color)

private val LightMoney = MoneyColors(Color(0xFF1B6D2F), Color(0xFFC8ECC8), Color(0xFF8F4E00), Color(0xFFFFDCBE))
private val DarkMoney = MoneyColors(Color(0xFF8DD99A), Color(0xFF1F4D29), Color(0xFFFFB870), Color(0xFF5A3100))

val LocalMoneyColors = staticCompositionLocalOf { LightMoney }

/** Tabular figures so columns of amounts line up and numbers don't jiggle when they change. */
private const val TNUM = "tnum"

private val base = Typography()
val MidasTypography = Typography(
    displayLarge = base.displayLarge.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = TNUM),
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = TNUM, letterSpacing = (-0.5).sp),
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = TNUM, letterSpacing = (-0.25).sp),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = TNUM),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = TNUM),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = TNUM),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = TNUM),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontFeatureSettings = TNUM),
    bodyMedium = base.bodyMedium.copy(fontFeatureSettings = TNUM),
)

val MidasShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun MidasTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = isDark(themeMode)
    val context = LocalContext.current
    val colors: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(LocalMoneyColors provides if (dark) DarkMoney else LightMoney) {
        MaterialTheme(colorScheme = colors, typography = MidasTypography, shapes = MidasShapes, content = content)
    }
}

val MaterialTheme.money: MoneyColors
    @Composable get() = LocalMoneyColors.current

/** Big hero amount style used on the dashboard. */
val HeroAmount = TextStyle(fontSize = 44.sp, lineHeight = 50.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TNUM, letterSpacing = (-1).sp)
