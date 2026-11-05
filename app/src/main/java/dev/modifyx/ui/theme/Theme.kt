package dev.modifyx.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import dev.modifyx.data.CustomTheme
import dev.modifyx.data.ThemeId
import dev.modifyx.data.ThemeMode

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF6BE3C6),
    onPrimary = Color(0xFF00382E),
    primaryContainer = Color(0xFF005143),
    onPrimaryContainer = Color(0xFF89FFDF),
    secondary = Color(0xFFB4CCCA),
    onSecondary = Color(0xFF1F3534),
    secondaryContainer = Color(0xFF354B4A),
    onSecondaryContainer = Color(0xFFD0E8E6),
    tertiary = Color(0xFFA9C7FF),
    onTertiary = Color(0xFF002F65),
    background = Color(0xFF0B0F14),
    onBackground = Color(0xFFE1E3E1),
    surface = Color(0xFF0B0F14),
    onSurface = Color(0xFFE1E3E1),
    surfaceVariant = Color(0xFF3F4947),
    onSurfaceVariant = Color(0xFFBEC9C6),
    outline = Color(0xFF889391),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF00695C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9FF2DC),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF4A6360),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E4),
    onSecondaryContainer = Color(0xFF051F1D),
    tertiary = Color(0xFF3F5F90),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFBFDFA),
    onBackground = Color(0xFF191C1B),
    surface = Color(0xFFFBFDFA),
    onSurface = Color(0xFF191C1B),
    surfaceVariant = Color(0xFFDAE5E2),
    onSurfaceVariant = Color(0xFF3F4947),
    outline = Color(0xFF6F7977),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = 0.1.sp),
)

private fun amoledScheme(base: ColorScheme): ColorScheme = base.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    onBackground = Color(0xFFE6E6E6),
    onSurface = Color(0xFFE6E6E6),
)

@Composable
fun ModifyXTheme(
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    themeId: String,
    customThemes: List<CustomTheme>,
    amoled: Boolean,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val custom = customThemes.firstOrNull { ThemeId.custom(it.id) == themeId }

    val scheme = when {
        custom != null -> schemeFromSeed(custom.seed, dark, amoled)
        themeId == ThemeId.DYNAMIC && dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> if (amoled) amoledScheme(DarkScheme) else DarkScheme
        else -> LightScheme
    }

    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}
