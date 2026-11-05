package dev.modifyx.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Builds a Material 3 scheme from a single seed colour. The maths is plain Kotlin (no
 * android.graphics) so the palette can be unit-tested, and every colour is derived from
 * the seed rather than hand-picked, which keeps user themes coherent.
 */
object Palette {

    fun argb(hue: Float, saturation: Float, value: Float): Long {
        val h = ((hue % 360f) + 360f) % 360f
        val s = saturation.coerceIn(0f, 1f)
        val v = value.coerceIn(0f, 1f)
        val c = v * s
        val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
        val m = v - c
        val rgb = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun channel(component: Float): Long =
            ((component + m) * 255f + 0.5f).toInt().coerceIn(0, 255).toLong()
        return (0xFFL shl 24) or (channel(rgb.first) shl 16) or (channel(rgb.second) shl 8) or channel(rgb.third)
    }

    fun toHsv(color: Long): FloatArray {
        val r = ((color shr 16) and 0xFF).toFloat() / 255f
        val g = ((color shr 8) and 0xFF).toFloat() / 255f
        val b = (color and 0xFF).toFloat() / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        val hue = when {
            delta == 0f -> 0f
            max == r -> 60f * (((g - b) / delta) % 6f)
            max == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }
        val saturation = if (max == 0f) 0f else delta / max
        return floatArrayOf(((hue % 360f) + 360f) % 360f, saturation, max)
    }

    /** WCAG relative luminance, used to pick readable text on a generated colour. */
    fun luminance(color: Long): Double {
        fun channel(shift: Int): Double {
            val raw = ((color shr shift) and 0xFF).toDouble() / 255.0
            return if (raw <= 0.03928) raw / 12.92 else Math.pow((raw + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    fun onColor(background: Long): Long = if (luminance(background) > 0.45) 0xFF000000 else 0xFFFFFFFF
}

fun schemeFromSeed(seed: Long, dark: Boolean, amoled: Boolean = false): ColorScheme {
    val hsv = Palette.toHsv(seed)
    val hue = hsv[0]
    val saturation = hsv[1].coerceIn(0.25f, 0.95f)

    val primary = Palette.argb(hue, saturation, if (dark) 0.78f else 0.36f)
    val onPrimary = Palette.onColor(primary)
    val primaryContainer = Palette.argb(hue, (saturation * 0.5f).coerceIn(0.12f, 0.55f), if (dark) 0.24f else 0.90f)
    val onPrimaryContainer = Palette.onColor(primaryContainer)

    val secondary = Palette.argb(hue + 26f, saturation * 0.55f, if (dark) 0.72f else 0.38f)
    val onSecondary = Palette.onColor(secondary)
    val secondaryContainer = Palette.argb(hue + 26f, saturation * 0.3f, if (dark) 0.22f else 0.90f)
    val onSecondaryContainer = Palette.onColor(secondaryContainer)

    val tertiary = Palette.argb(hue - 48f, saturation * 0.7f, if (dark) 0.74f else 0.40f)
    val onTertiary = Palette.onColor(tertiary)
    val tertiaryContainer = Palette.argb(hue - 48f, saturation * 0.35f, if (dark) 0.24f else 0.90f)
    val onTertiaryContainer = Palette.onColor(tertiaryContainer)

    val surface = if (dark) (if (amoled) 0xFF000000 else Palette.argb(hue, 0.06f, 0.07f)) else Palette.argb(hue, 0.04f, 0.99f)
    val onSurface = Palette.onColor(surface)
    val surfaceVariant = if (dark) Palette.argb(hue, 0.10f, 0.22f) else Palette.argb(hue, 0.08f, 0.90f)
    val onSurfaceVariant = Palette.onColor(surfaceVariant)

    val outline = Palette.argb(hue, 0.08f, if (dark) 0.58f else 0.50f)
    val error = if (dark) 0xFFFFB4AB else 0xFFBA1A1A
    val onError = Palette.onColor(error)
    val errorContainer = if (dark) 0xFF93000A else 0xFFFFDAD6
    val onErrorContainer = Palette.onColor(errorContainer)

    return if (dark) {
        darkColorScheme(
            primary = Color(primary),
            onPrimary = Color(onPrimary),
            primaryContainer = Color(primaryContainer),
            onPrimaryContainer = Color(onPrimaryContainer),
            secondary = Color(secondary),
            onSecondary = Color(onSecondary),
            secondaryContainer = Color(secondaryContainer),
            onSecondaryContainer = Color(onSecondaryContainer),
            tertiary = Color(tertiary),
            onTertiary = Color(onTertiary),
            tertiaryContainer = Color(tertiaryContainer),
            onTertiaryContainer = Color(onTertiaryContainer),
            background = Color(surface),
            onBackground = Color(onSurface),
            surface = Color(surface),
            onSurface = Color(onSurface),
            surfaceVariant = Color(surfaceVariant),
            onSurfaceVariant = Color(onSurfaceVariant),
            outline = Color(outline),
            error = Color(error),
            onError = Color(onError),
            errorContainer = Color(errorContainer),
            onErrorContainer = Color(onErrorContainer),
        )
    } else {
        lightColorScheme(
            primary = Color(primary),
            onPrimary = Color(onPrimary),
            primaryContainer = Color(primaryContainer),
            onPrimaryContainer = Color(onPrimaryContainer),
            secondary = Color(secondary),
            onSecondary = Color(onSecondary),
            secondaryContainer = Color(secondaryContainer),
            onSecondaryContainer = Color(onSecondaryContainer),
            tertiary = Color(tertiary),
            onTertiary = Color(onTertiary),
            tertiaryContainer = Color(tertiaryContainer),
            onTertiaryContainer = Color(onTertiaryContainer),
            background = Color(surface),
            onBackground = Color(onSurface),
            surface = Color(surface),
            onSurface = Color(onSurface),
            surfaceVariant = Color(surfaceVariant),
            onSurfaceVariant = Color(onSurfaceVariant),
            outline = Color(outline),
            error = Color(error),
            onError = Color(onError),
            errorContainer = Color(errorContainer),
            onErrorContainer = Color(onErrorContainer),
        )
    }
}
