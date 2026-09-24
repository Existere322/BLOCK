package app.shijie.domain

import android.content.pm.ApplicationInfo

/** Usage totals count focused foreground time of user apps, not system packages. */
object UsageVisibility {
    fun includePackage(applicationFlags: Int?): Boolean {
        if (applicationFlags == null) return true
        return applicationFlags and ApplicationInfo.FLAG_SYSTEM == 0
    }
}
