package dev.modifyx

import dev.modifyx.ui.theme.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePaletteTest {

    @Test
    fun argbRoundTripsThroughHsv() {
        val colors = listOf(
            Palette.argb(0f, 0f, 0f),
            Palette.argb(0f, 1f, 1f),
            Palette.argb(160f, 0.7f, 0.5f),
            Palette.argb(359f, 0.4f, 0.9f),
        )
        colors.forEach { color ->
            val hsv = Palette.toHsv(color)
            val rebuilt = Palette.argb(hsv[0], hsv[1], hsv[2])
            assertEquals(color, rebuilt)
        }
    }

    @Test
    fun hueWrapsAndSaturationClamps() {
        assertEquals(Palette.argb(10f, 0.5f, 0.5f), Palette.argb(370f, 0.5f, 0.5f))
        assertEquals(Palette.argb(0f, 1f, 1f), Palette.argb(0f, 4f, 1f))
        assertEquals(Palette.argb(0f, 0f, 0f), Palette.argb(0f, -3f, -1f))
    }

    @Test
    fun textOnGeneratedColorsStaysReadable() {
        val dark = Palette.argb(200f, 0.9f, 0.2f)
        val light = Palette.argb(200f, 0.2f, 0.95f)
        assertEquals(0xFFFFFFFFL, Palette.onColor(dark))
        assertEquals(0xFF000000L, Palette.onColor(light))
        assertTrue(Palette.luminance(light) > Palette.luminance(dark))
    }
}
