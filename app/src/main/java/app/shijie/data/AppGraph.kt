package app.shijie.data

import android.app.Application
import app.shijie.guard.GuardEngine
import app.shijie.guard.Maintenance
import app.shijie.system.HealthNotifier
import app.shijie.system.SystemAppClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow

class AppGraph(val app: Application) {
    val clock = SystemAppClock(app)
    val db = ShijieDatabase.get(app)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val groups = GroupRepository(app, db)
    val usage = UsageRepository(app, db, clock)
    val workdays = WorkdayRepository(db)
    val overrides = OverrideRepository(db)
    val meta = MetaStore(db)
    val lastForegroundEvent = MutableStateFlow<Long?>(null)
    val engine = GuardEngine(this)

    fun start() {
        HealthNotifier.ensureChannel(app)
        Maintenance.ensure(app)
        scope.launch {
            val stored = meta.get(MetaStore.LAST_EVENT)?.toLongOrNull()
            if (stored != null) lastForegroundEvent.value = stored
            overrides.cancelOtherBoots(clock.bootId(), clock.now())
        }
    }
}
