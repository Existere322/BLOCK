package app.shijie.system

import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.shijie.MainActivity
import app.shijie.R
import app.shijie.guard.ShijieAccessibilityService
import java.time.Instant
import java.time.ZoneId

interface AppClock {
    fun now(): Instant
    fun zone(): ZoneId
    fun bootId(): String
}

class SystemAppClock(private val context: Context) : AppClock {
    override fun now(): Instant = Instant.ofEpochMilli(System.currentTimeMillis())

    override fun zone(): ZoneId = ZoneId.systemDefault()

    override fun bootId(): String {
        val count = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        if (count >= 0) return "boot:$count"
        return try {
            java.io.File("/proc/sys/kernel/random/boot_id").readText().trim().ifBlank { "boot:unknown" }
        } catch (_: Exception) {
            "boot:unknown"
        }
    }
}

object Permissions {
    fun usageGranted(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun accessibilityEnabled(context: Context): Boolean {
        val manager = context.getSystemService(android.view.accessibility.AccessibilityManager::class.java) ?: return false
        val expected = android.content.ComponentName(context, ShijieAccessibilityService::class.java).flattenToString()
        return manager.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { info ->
                info.id == expected || info.resolveInfo?.serviceInfo?.let { service ->
                    android.content.ComponentName(service.packageName, service.name).flattenToString() == expected
                } == true
            }
    }

    fun notificationsEnabled(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}

object DeviceProfile {
    fun isOplus(): Boolean {
        val value = listOf(Build.MANUFACTURER, Build.BRAND, Build.DEVICE).joinToString(" ").lowercase()
        return listOf("oppo", "oneplus", "realme", "oplus").any { value.contains(it) }
    }

    fun isAndroid16OrNewer(): Boolean = Build.VERSION.SDK_INT >= 36

    fun summary(): String {
        val rom = romVersion()
        val base = "${Build.MANUFACTURER} / Android ${Build.VERSION.RELEASE}"
        return if (rom.isBlank()) base else "$base / $rom"
    }

    private fun romVersion(): String {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val get = clazz.getMethod("get", String::class.java, String::class.java)
            listOf("ro.build.version.oplusrom", "ro.build.version.opporom").firstNotNullOfOrNull { key ->
                (get.invoke(null, key, "") as String).takeIf { it.isNotBlank() }
            }.orEmpty()
        } catch (_: Exception) {
            ""
        }
    }
}

enum class GuideAction {
    ACCESSIBILITY,
    USAGE,
    BATTERY,
    AUTOSTART,
    APP_DETAILS,
    NOTIFICATIONS,
}

data class GuideStep(
    val title: String,
    val detail: String,
    val action: GuideAction,
)

object ColorOsGuide {
    fun steps(): List<GuideStep> = listOf(
        GuideStep(
            "无障碍服务",
            "设置 → 辅助功能 → 已下载的服务 → 时界 → 打开。只用于识别前台应用包名。",
            GuideAction.ACCESSIBILITY,
        ),
        GuideStep(
            "使用情况访问",
            "设置 → 应用 → 特殊应用权限 → 使用情况访问 → 时界 → 允许。用于统计和校正时长。",
            GuideAction.USAGE,
        ),
        GuideStep(
            "后台耗电",
            "设置 → 电池 → 应用耗电管理 → 时界 → 允许完全后台行为。不要选“禁止后台运行”。",
            GuideAction.BATTERY,
        ),
        GuideStep(
            "自启动",
            "设置 → 应用 → 自启动管理 → 打开时界。系统回收权限后，重启才能自动恢复保护。",
            GuideAction.AUTOSTART,
        ),
        GuideStep(
            "最近任务锁定",
            "打开最近任务，找到时界卡片，向下滑或点菜单后选择锁定。清理最近任务时不要划掉它。",
            GuideAction.APP_DETAILS,
        ),
    )
}

object SettingsNavigator {
    fun open(context: Context, action: GuideAction): Boolean {
        val intents = when (action) {
            GuideAction.ACCESSIBILITY -> listOf(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            GuideAction.USAGE -> listOf(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            GuideAction.NOTIFICATIONS -> listOf(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                details(context),
            )
            GuideAction.APP_DETAILS -> listOf(details(context))
            GuideAction.BATTERY -> listOf(
                component("com.oplus.battery", "com.oplus.battery.BatteryMainActivity"),
                component("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity"),
                component("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerConsumptionActivity"),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS),
                details(context),
            )
            GuideAction.AUTOSTART -> listOf(
                component("com.oplus.safecenter", "com.oplus.safecenter.startupapp.StartupAppListActivity"),
                component("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                component("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                component("com.coloros.safecenter", "com.coloros.privacypermissionsentry.PermissionTopActivity"),
                details(context),
            )
        }
        return startFirst(context, intents)
    }

    fun openSettingsRoot(context: Context): Boolean {
        return startFirst(context, listOf(Intent(Settings.ACTION_SETTINGS)))
    }

    private fun details(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:${context.packageName}"))
    }

    private fun component(packageName: String, className: String): Intent {
        return Intent().setComponent(android.content.ComponentName(packageName, className))
    }

    private fun startFirst(context: Context, intents: List<Intent>): Boolean {
        val manager = context.packageManager
        for (raw in intents) {
            val intent = Intent(raw).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val resolved = try {
                intent.resolveActivity(manager) != null
            } catch (_: Exception) {
                false
            }
            if (!resolved) continue
            try {
                context.startActivity(intent)
                return true
            } catch (_: Exception) {
                continue
            }
        }
        return false
    }
}

object HealthNotifier {
    const val CHANNEL_ID = "shijie-health"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "权限提醒", NotificationManager.IMPORTANCE_LOW).apply {
            setSound(null, null)
            enableVibration(false)
            description = "无障碍或使用情况访问被关闭时提醒一次"
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun sync(context: Context) {
        val manager = NotificationManagerCompat.from(context)
        val missing = mutableListOf<String>()
        if (!Permissions.accessibilityEnabled(context)) missing += "无障碍服务"
        if (!Permissions.usageGranted(context)) missing += "使用情况访问"
        if (missing.isEmpty() || !manager.areNotificationsEnabled()) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("时界的保护已暂停")
            .setContentText("请重新打开${missing.joinToString("、")}")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        if (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            manager.notify(NOTIFICATION_ID, notification)
        }
    }

    private const val NOTIFICATION_ID = 1001
}
