package app.shijie.guard

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import app.shijie.domain.AccessibilityRoster

/**
 * ColorOS often leaves the accessibility service enabled in settings after a reboot
 * but does not bind it again. Opening the app used to do nothing about that, so
 * block windows stopped intercepting until the user toggled the service by hand.
 */
object GuardRecovery {
    private const val PREFS = "shijie_guard"
    private const val ARMED = "accessibility_armed"
    private const val TAG = "Shijie"

    fun markArmed(context: Context) {
        prefs(context).edit().putBoolean(ARMED, true).apply()
    }

    fun enabledInSettings(context: Context): Boolean {
        val app = context.applicationContext
        val component = ComponentName(app, ShijieAccessibilityService::class.java)
        return AccessibilityRoster.listed(readEnabled(app), app.packageName, component.className)
    }

    fun nudge(context: Context) {
        val app = context.applicationContext
        if (ShijieAccessibilityService.instance != null) return
        val component = ComponentName(app, ShijieAccessibilityService::class.java)
        var raw = readEnabled(app)
        var listed = AccessibilityRoster.listed(raw, app.packageName, component.className)
        val shouldRestore = !listed && armed(app)
        if ((listed || shouldRestore) && canWriteSecure(app)) {
            try {
                forceRebind(app, raw, component.flattenToString(), app.packageName, component.className)
                listed = true
            } catch (error: Exception) {
                Log.w(TAG, "accessibility restore failed: ${error.message}")
            }
        }
        if (!listed) return
        requestRebind(component)
    }

    /**
     * Writing the secure setting is what makes the system bind the service again.
     * Removing and putting it back fires the observer even when the final list is unchanged.
     */
    private fun forceRebind(
        context: Context,
        raw: String?,
        flatName: String,
        packageName: String,
        className: String,
    ) {
        val without = AccessibilityRoster.remove(raw, packageName, className)
        val withService = AccessibilityRoster.ensure(raw, flatName)
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, without)
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, withService)
        Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        Log.i(TAG, "rewrote accessibility service entry")
    }

    private fun requestRebind(component: ComponentName) {
        try {
            val method = AccessibilityService::class.java.getDeclaredMethod("requestRebind", ComponentName::class.java)
            method.isAccessible = true
            method.invoke(null, component)
            Log.i(TAG, "requested accessibility rebind")
        } catch (error: Exception) {
            Log.w(TAG, "accessibility rebind unavailable: ${error.message}")
        }
    }

    private fun readEnabled(context: Context): String? {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        } catch (_: Exception) {
            null
        }
    }

    private fun armed(context: Context): Boolean = prefs(context).getBoolean(ARMED, false)

    private fun canWriteSecure(context: Context): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
