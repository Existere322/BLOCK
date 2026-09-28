package app.shijie

import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.shijie.data.AppGraph
import app.shijie.guard.GuardRecovery
import app.shijie.ui.AppRoot
import app.shijie.ui.ShijieTheme
import app.shijie.ui.ShijieViewModel
import app.shijie.widget.EXTRA_OPEN_STATS
import kotlinx.coroutines.flow.MutableStateFlow

class ShijieApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        graph.start()
    }
}

class MainActivity : ComponentActivity() {
    private val openStatsRequests = MutableStateFlow(false)

    override fun onStart() {
        super.onStart()
        GuardRecovery.nudge(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openStatsRequests.value = intent.wantsStats()
        enableEdgeToEdge()
        setContent {
            val openStats by openStatsRequests.collectAsStateWithLifecycle()
            ShijieTheme {
                val model: ShijieViewModel = viewModel(factory = ShijieViewModel.factory(application))
                AppRoot(
                    vm = model,
                    openStats = openStats,
                    onStatsOpened = {
                        openStatsRequests.value = false
                        intent.removeExtra(EXTRA_OPEN_STATS)
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.wantsStats()) openStatsRequests.value = true
    }

    private fun Intent.wantsStats(): Boolean = getBooleanExtra(EXTRA_OPEN_STATS, false)
}
