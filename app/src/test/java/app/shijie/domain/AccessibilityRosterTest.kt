package app.shijie.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityRosterTest {
    private val pkg = "app.shijie"
    private val cls = "app.shijie.guard.ShijieAccessibilityService"
    private val flat = "$pkg/$cls"
    private val short = "$pkg/.guard.ShijieAccessibilityService"

    @Test
    fun recognizesFlatAndShortFormsWithoutMatchingOtherServices() {
        assertFalse(AccessibilityRoster.listed(null, pkg, cls))
        assertFalse(AccessibilityRoster.listed("", pkg, cls))
        assertFalse(AccessibilityRoster.listed("com.other/.Service", pkg, cls))
        assertTrue(AccessibilityRoster.listed(flat, pkg, cls))
        assertTrue(AccessibilityRoster.listed("com.other/.Service:$short", pkg, cls))
        assertTrue(AccessibilityRoster.listed("$flat:", pkg, cls))
    }

    @Test
    fun ensureAppendsThisServiceAndKeepsTheRest() {
        assertEquals(flat, AccessibilityRoster.ensure(null, flat))
        assertEquals(flat, AccessibilityRoster.ensure(short, flat))
        assertEquals(
            "com.other/.Talkback:$flat",
            AccessibilityRoster.ensure("com.other/.Talkback:$short", flat),
        )
        assertEquals(
            "com.other/.Talkback:$flat",
            AccessibilityRoster.ensure("com.other/.Talkback:$flat", flat),
        )
    }

    @Test
    fun removeDropsOnlyThisService() {
        assertEquals("", AccessibilityRoster.remove(flat, pkg, cls))
        assertEquals("com.other/.Talkback", AccessibilityRoster.remove("com.other/.Talkback:$short", pkg, cls))
        assertEquals("com.other/.Talkback", AccessibilityRoster.remove("com.other/.Talkback", pkg, cls))
    }
}
