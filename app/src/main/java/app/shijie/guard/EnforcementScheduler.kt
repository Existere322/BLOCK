package app.shijie.guard

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import app.shijie.ShijieApp

/**
 * Exact alarm backup for Handler-based enforcement.
 * Survives Doze better than [android.os.Handler.postDelayed], so quota/window
 * boundaries still re-evaluate while the accessibility service is alive.
 */
object EnforcementScheduler {
    private const val ACTION = "app.shijie.action.ENFORCE"
    private const val EXTRA_PACKAGE = "package"
    private const val REQUEST = 42
    private const val TAG = "Shijie"

    fun schedule(context: Context, delayMs: Long, packageName: String) {
        if (delayMs <= 0L || packageName.isBlank()) return
        val app = context.applicationContext
        val alarm = app.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(app, EnforcementReceiver::class.java)
            .setAction(ACTION)
            .putExtra(EXTRA_PACKAGE, packageName)
        val pending = PendingIntent.getBroadcast(
            app,
            REQUEST,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val trigger = SystemClock.elapsedRealtime() + delayMs.coerceAtMost(24L * 60L * 60L * 1000L)
        try {
            if (Build.VERSION.SDK_INT >= 31 && !alarm.canScheduleExactAlarms()) {
                alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pending)
            } else {
                alarm.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pending)
            }
        } catch (error: SecurityException) {
            Log.w(TAG, "exact alarm denied: ${error.message}")
            try {
                alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pending)
            } catch (fallback: Exception) {
                Log.w(TAG, "alarm schedule failed: ${fallback.message}")
            }
        } catch (error: Exception) {
            Log.w(TAG, "alarm schedule failed: ${error.message}")
        }
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        val alarm = app.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(app, EnforcementReceiver::class.java).setAction(ACTION)
        val pending = PendingIntent.getBroadcast(
            app,
            REQUEST,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarm.cancel(pending)
        pending.cancel()
    }
}

class EnforcementReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != "app.shijie.action.ENFORCE") return
        val packageName = intent.getStringExtra("package") ?: return
        val app = context.applicationContext as? ShijieApp ?: return
        Log.i("Shijie", "enforcement alarm for $packageName")
        app.graph.engine.onEnforcementAlarm(packageName)
    }
}
