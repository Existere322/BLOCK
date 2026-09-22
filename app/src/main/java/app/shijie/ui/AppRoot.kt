package app.shijie.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

private const val TODAY = "today"
private const val GROUPS = "groups"
private const val STATS = "stats"
private const val SETTINGS = "settings"

@Composable
fun AppRoot(vm: ShijieViewModel) {
    val done by vm.onboardingDone.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    when (done) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        false -> OnboardingScreen(vm)
        true -> MainScaffold(vm)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(vm: ShijieViewModel) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = route == TODAY || route == GROUPS || route == STATS || route == SETTINGS
    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (!showBar) {
                TopAppBar(
                    title = { Text("时界") },
                    navigationIcon = {
                        IconButton(onClick = { nav.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    BarItem("今日", TODAY, route, Icons.Filled.Home) { navigate(nav, TODAY) }
                    BarItem("分组", GROUPS, route, Icons.Filled.Star) { navigate(nav, GROUPS) }
                    BarItem("统计", STATS, route, Icons.Filled.DateRange) { navigate(nav, STATS) }
                    BarItem("设置", SETTINGS, route, Icons.Filled.Settings) { navigate(nav, SETTINGS) }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = TODAY,
            modifier = Modifier.padding(padding),
        ) {
            composable(TODAY) { TodayScreen(vm) }
            composable(GROUPS) { GroupsScreen(vm, onOpen = { nav.navigate("group/$it") }) }
            composable(
                route = "group/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                GroupEditorScreen(
                    id = entry.arguments?.getLong("id") ?: 0L,
                    vm = vm,
                    onDone = { nav.popBackStack() },
                    onMessage = vm::message,
                )
            }
            composable(STATS) { StatsScreen(vm) }
            composable(SETTINGS) {
                SettingsScreen(
                    vm = vm,
                    onWorkdays = { nav.navigate("workdays") },
                    onGuide = { nav.navigate("coloros") },
                    onPrivacy = { nav.navigate("privacy") },
                )
            }
            composable("workdays") { WorkdayScreen(vm) }
            composable("coloros") { ColorOsScreen(vm) }
            composable("privacy") { PrivacyScreen() }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BarItem(
    label: String,
    route: String,
    current: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = current == route,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
    )
}

private fun navigate(nav: androidx.navigation.NavHostController, route: String) {
    nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
