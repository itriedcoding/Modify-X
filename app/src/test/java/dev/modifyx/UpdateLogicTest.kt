package dev.modifyx

import dev.modifyx.core.UpdateChecker
import dev.modifyx.data.CustomTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateLogicTest {

    @Test
    fun versionsCompareNumerically() {
        assertTrue(UpdateChecker.isNewer("1.2.0", "1.1.0"))
        assertTrue(UpdateChecker.isNewer("1.10.0", "1.9.9"))
        assertTrue(UpdateChecker.isNewer("2.0.0", "1.99.99"))
        assertFalse(UpdateChecker.isNewer("1.1.0", "1.1.0"))
        assertFalse(UpdateChecker.isNewer("1.0.9", "1.1.0"))
        assertEquals(0, UpdateChecker.compareVersions("v1.2.0", "1.2.0"))
        assertTrue(UpdateChecker.isNewer("v1.2.1", "v1.2.0"))
        assertFalse(UpdateChecker.isNewer("1.2", "1.2.0"))
        assertTrue(UpdateChecker.isNewer("1.2.1", "1.2"))
    }

    @Test
    fun customThemesSurviveSerialization() {
        val themes = listOf(
            CustomTheme.create("Ocean", 0xFF00695C),
            CustomTheme.create("Ember", 0xFFB3261E),
        )
        val restored = CustomTheme.fromJson(CustomTheme.toJson(themes))
        assertEquals(themes, restored)
        assertEquals(themes[0].name, restored[0].name)
        assertEquals(themes[1].seed, restored[1].seed)
    }

    @Test
    fun brokenThemeJsonFallsBackToEmpty() {
        assertEquals(emptyList<CustomTheme>(), CustomTheme.fromJson("not json"))
        assertEquals(emptyList<CustomTheme>(), CustomTheme.fromJson(null))
        assertEquals(emptyList<CustomTheme>(), CustomTheme.fromJson("[]"))
    }
}
