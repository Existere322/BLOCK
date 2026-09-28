package app.shijie.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
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
fun AppRoot(
    vm: ShijieViewModel,
    openStats: Boolean = false,
    onStatsOpened: () -> Unit = {},
) {
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
        null -> {
            CafeSystemBars(darkIconsOnStatus = true, darkIconsOnNavigation = true)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CafeAccent)
            }
        }
        false -> {
            CafeSystemBars(darkIconsOnStatus = false, darkIconsOnNavigation = false)
            OnboardingScreen(vm)
        }
        true -> MainScaffold(vm, openStats, onStatsOpened)
    }
}

@Composable
private fun MainScaffold(
    vm: ShijieViewModel,
    openStats: Boolean,
    onStatsOpened: () -> Unit,
) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = route == TODAY || route == GROUPS || route == STATS || route == SETTINGS
    val darkStatus = route == null || route == TODAY
    CafeSystemBars(darkIconsOnStatus = !darkStatus, darkIconsOnNavigation = true)
    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(openStats) {
        if (!openStats) return@LaunchedEffect
        navigate(nav, STATS)
        onStatsOpened()
    }
    Scaffold(
        containerColor = CafePage,
        contentColor = CafeInk,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            NavHost(
                navController = nav,
                startDestination = TODAY,
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .background(CafePage),
                enterTransition = { slideIn(pageDirection()) },
                exitTransition = { slideOut(pageDirection()) },
                popEnterTransition = { slideIn(pageDirection()) },
                popExitTransition = { slideOut(pageDirection()) },
            ) {
                composable(TODAY) { TodayScreen(vm) }
                composable(GROUPS) { GroupsScreen(vm, onOpen = { nav.navigate("group/$it") }) }
                composable(
                    route = "group/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { back ->
                    val groupId = back.arguments?.getLong("id") ?: 0L
                    ChildPage(titleFor("group/{id}", groupId), onBack = { nav.popBackStack() }) {
                        GroupEditorScreen(
                            id = groupId,
                            vm = vm,
                            onDone = { nav.popBackStack() },
                            onMessage = vm::message,
                        )
                    }
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
                composable("workdays") {
                    ChildPage("法定工作日", onBack = { nav.popBackStack() }) { WorkdayScreen(vm) }
                }
                composable("coloros") {
                    ChildPage("后台引导", onBack = { nav.popBackStack() }) { ColorOsScreen(vm) }
                }
                composable("privacy") {
                    ChildPage("隐私说明", onBack = { nav.popBackStack() }) { PrivacyScreen() }
                }
            }
            if (showBar) {
                CafeTabBar(route, Modifier.align(Alignment.BottomCenter)) { navigate(nav, it) }
            }
        }
    }
}

@Composable
private fun ChildPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(CafePage)) {
        SubpageTopBar(title, onBack)
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
    }
}

private fun titleFor(route: String?, groupId: Long?): String = when (route) {
    "workdays" -> "法定工作日"
    "coloros" -> "后台引导"
    "privacy" -> "隐私说明"
    "group/{id}" -> if (groupId == 0L) "新建分组" else "编辑分组"
    else -> "BLOCK"
}

@Composable
private fun SubpageTopBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(CafePage)
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CafeIconButton(CafeIcons.Back, "返回", onBack)
        Text(
            title,
            modifier = Modifier.weight(1f),
            color = CafeInk,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(44.dp))
    }
}

/** Icon column plus the tab row's vertical padding. Kept in step with [CafeTabBar]. */
internal val TabBarBodyHeight = 67.dp

@Composable
internal fun Modifier.aboveTabBar(): Modifier = navigationBarsPadding().padding(bottom = TabBarBodyHeight)

@Composable
private fun CafeTabBar(route: String?, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .background(CafeWhite)
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabIcon("今日", TODAY, route, CafeIcons.Home, CafeIcons.HomeFilled) { onSelect(TODAY) }
        TabIcon("分组", GROUPS, route, CafeIcons.Groups, CafeIcons.Groups) { onSelect(GROUPS) }
        TabIcon("统计", STATS, route, CafeIcons.Stats, CafeIcons.Stats) { onSelect(STATS) }
        TabIcon("设置", SETTINGS, route, CafeIcons.Settings, CafeIcons.Settings) { onSelect(SETTINGS) }
    }
}

@Composable
private fun TabIcon(
    label: String,
    route: String,
    current: String?,
    icon: ImageVector,
    selectedIcon: ImageVector,
    onClick: () -> Unit,
) {
    val selected = current == route
    Column(
        Modifier.noRippleClickable(onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = if (selected) selectedIcon else icon,
            contentDescription = label,
            tint = if (selected) CafeAccent else CafeInk,
            modifier = Modifier.size(24.dp),
        )
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(width = 10.dp, height = 5.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (selected) CafeAccent else CafeWhite),
        )
    }
}

private fun navigate(nav: androidx.navigation.NavHostController, route: String) {
    nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private val pageSlide = tween<IntOffset>(durationMillis = 300, easing = FastOutSlowInEasing)

private fun tabIndex(route: String?): Int = when (route) {
    TODAY -> 0
    GROUPS -> 1
    STATS -> 2
    SETTINGS -> 3
    else -> -1
}

private fun isChild(route: String?): Boolean {
    return route == "group/{id}" || route == "workdays" || route == "coloros" || route == "privacy"
}

private fun androidx.compose.animation.AnimatedContentTransitionScope<androidx.navigation.NavBackStackEntry>.pageDirection(): Int {
    val from = initialState.destination.route
    val to = targetState.destination.route
    val fromTab = tabIndex(from)
    val toTab = tabIndex(to)
    if (fromTab >= 0 && toTab >= 0 && fromTab != toTab) return if (toTab > fromTab) 1 else -1
    if (isChild(to) && !isChild(from)) return 1
    if (isChild(from) && !isChild(to)) return -1
    return 1
}

private fun slideIn(direction: Int) = slideInHorizontally(pageSlide) { full -> full * direction }

private fun slideOut(direction: Int) = slideOutHorizontally(pageSlide) { full -> -full * direction }
