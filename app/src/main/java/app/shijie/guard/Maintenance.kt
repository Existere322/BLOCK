package app.shijie.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.shijie.ShijieApp
import app.shijie.domain.Retention
import app.shijie.domain.UsageDay
import app.shijie.system.HealthNotifier
import app.shijie.widget.refreshUsageChartWidgets
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action !in BOOT_ACTIONS) return
        val appContext = context.applicationContext
        GuardRecovery.nudge(appContext)
        val pending = goAsync()
        val app = appContext as? ShijieApp
        if (app == null) {
            pending.finish()
            return
        }
        app.graph.scope.launch {
            try {
                app.graph.overrides.cancelOtherBoots(app.graph.clock.bootId(), app.graph.clock.now())
                Maintenance.ensure(app)
                GuardRecovery.nudge(app)
            } catch (error: Exception) {
                Log.w("Shijie", "boot recovery failed: ${error.message}")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private val BOOT_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
        )
    }
}

object Maintenance {
    fun ensure(context: Context) {
        val manager = WorkManager.getInstance(context)
        val daily = PeriodicWorkRequestBuilder<MaintenanceWorker>(24, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build(),
            )
            .setInitialDelay(12, TimeUnit.HOURS)
            .build()
        manager.enqueueUniquePeriodicWork(
            "shijie-daily",
            ExistingPeriodicWorkPolicy.UPDATE,
            daily,
        )

        val health = PeriodicWorkRequestBuilder<HealthWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build(),
            )
            .build()
        manager.enqueueUniquePeriodicWork(
            "shijie-health",
            ExistingPeriodicWorkPolicy.UPDATE,
            health,
        )
        manager.enqueueUniqueWork(
            "shijie-health-now",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<HealthWorker>().build(),
        )
    }
}

class HealthWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        HealthNotifier.sync(applicationContext)
        GuardRecovery.nudge(applicationContext)
        val graph = (applicationContext as? ShijieApp)?.graph
        if (graph != null && graph.guardConnected.value) {
            graph.engine.recheckForeground()
        }
        refreshUsageChartWidgets(applicationContext)
        return Result.success()
    }
}

class MaintenanceWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = (applicationContext as ShijieApp).graph
        val today = UsageDay.localDate(graph.clock.now(), graph.clock.zone())
        graph.usage.reconcile(today.minusDays((Retention.DAYS - 1).toLong()), today)
        graph.overrides.deleteOlderThan(graph.clock.now().minus(Duration.ofDays(Retention.DAYS.toLong())))
        HealthNotifier.sync(applicationContext)
        return Result.success()
    }
}
