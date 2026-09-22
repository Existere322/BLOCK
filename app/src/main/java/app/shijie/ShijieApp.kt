package app.shijie

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import app.shijie.data.AppGraph
import app.shijie.ui.AppRoot
import app.shijie.ui.ShijieTheme
import app.shijie.ui.ShijieViewModel

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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShijieTheme {
                val model: ShijieViewModel = viewModel(factory = ShijieViewModel.factory(application))
                AppRoot(model)
            }
        }
    }
}
