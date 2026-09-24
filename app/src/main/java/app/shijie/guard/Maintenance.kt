package app.shijie.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        val pending = goAsync()
        val app = context.applicationContext as ShijieApp
        app.graph.scope.launch {
            try {
                app.graph.overrides.cancelOtherBoots(app.graph.clock.bootId(), app.graph.clock.now())
                Maintenance.ensure(app)
            } finally {
                pending.finish()
            }
        }
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
        val graph = (applicationContext as? ShijieApp)?.graph
        if (graph != null && graph.guardConnected.value) {
            graph.engine.recheckForeground()
        }
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
