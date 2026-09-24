package app.shijie.domain

import android.content.pm.ApplicationInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageVisibilityTest {
    @Test
    fun userAppsAreCountedAndSystemAppsAreNot() {
        assertTrue(UsageVisibility.includePackage(0))
        assertTrue(UsageVisibility.includePackage(null))
        assertFalse(UsageVisibility.includePackage(ApplicationInfo.FLAG_SYSTEM))
        assertFalse(
            UsageVisibility.includePackage(
                ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP,
            ),
        )
    }
}
