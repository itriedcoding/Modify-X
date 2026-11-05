package dev.modifyx

import dev.modifyx.core.ProcessRunner
import dev.modifyx.data.BloatCatalog
import dev.modifyx.data.DebloatAction
import dev.modifyx.data.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogAndActionTest {

    @Test
    fun `catalog is large enough to be useful`() {
        assertTrue("catalog should cover many known packages", BloatCatalog.entries.size > 150)
    }

    @Test
    fun `every entry has a plausible package name`() {
        val pattern = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+$")
        BloatCatalog.entries.forEach { entry ->
            assertTrue("bad package name: ${entry.pkg}", pattern.matches(entry.pkg))
        }
    }

    @Test
    fun `looking up a package is stable and case sensitive`() {
        val known = BloatCatalog.entries.first()
        assertEquals(known, BloatCatalog.find(known.pkg))
        assertNull(BloatCatalog.find("dev.example.not.a.real.package"))
    }

    @Test
    fun `protected packages are never rated safe`() {
        BloatCatalog.protectedPackages.forEach { pkg ->
            val entry = BloatCatalog.find(pkg)
            if (entry != null) {
                assertFalse("$pkg is protected but rated SAFE", entry.risk == RiskLevel.SAFE)
            }
        }
    }

    @Test
    fun `every group has at least one entry`() {
        dev.modifyx.data.CatalogGroup.entries.forEach { group ->
            assertTrue("empty group ${group.name}", BloatCatalog.inGroup(group).isNotEmpty())
        }
    }

    @Test
    fun `reversible actions invert to each other`() {
        assertEquals(DebloatAction.RESTORE_FOR_USER, DebloatAction.UNINSTALL_FOR_USER.inverse())
        assertEquals(DebloatAction.UNINSTALL_FOR_USER, DebloatAction.RESTORE_FOR_USER.inverse())
        assertEquals(DebloatAction.ENABLE, DebloatAction.DISABLE.inverse())
        assertEquals(DebloatAction.DISABLE, DebloatAction.ENABLE.inverse())
        assertNull(DebloatAction.CLEAR_DATA.inverse())
        assertNull(DebloatAction.FORCE_STOP.inverse())
        assertNull(DebloatAction.UNINSTALL_FULL.inverse())
    }

    @Test
    fun `shell wrapper keeps PATH literal and carries an exit marker`() {
        val wrapped = ProcessRunner.wrap("pm list packages --user 0")
        assertTrue(wrapped.contains("\$PATH"))
        assertTrue(wrapped.contains("__MX_EXIT__:"))
        assertTrue(wrapped.contains("pm list packages --user 0"))
        assertTrue(wrapped.contains("2>&1"))
    }
}
