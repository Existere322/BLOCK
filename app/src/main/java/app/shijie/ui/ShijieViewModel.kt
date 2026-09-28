package app.shijie.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.shijie.ShijieApp
import app.shijie.data.AppGraph
import app.shijie.data.LaunchableRow
import app.shijie.data.RankedApp
import app.shijie.data.StoredGroup
import app.shijie.domain.BlockWindow
import app.shijie.domain.DayPolicy
import app.shijie.domain.RestrictionGroup
import app.shijie.domain.TransitionPlanner
import app.shijie.domain.UsageDay
import app.shijie.domain.WorkdayOverride
import app.shijie.domain.WorkdayStatus
import app.shijie.domain.GroupLock
import app.shijie.domain.ChartColumn
import app.shijie.domain.ChartSlice
import app.shijie.domain.UsageCategories
import app.shijie.domain.AppTheme
import app.shijie.domain.formatDuration
import app.shijie.domain.formatDurationMinutes
import app.shijie.system.Permissions
import app.shijie.widget.refreshUsageChartWidgets
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class StatsRange { TODAY, WEEK, MONTH }

data class GroupDraft(
    val id: Long = 0,
    val name: String = "",
    val colorArgb: Int = GroupPalette.first(),
    val iconKey: String = "book",
    val enabled: Boolean = true,
    val useRange: Boolean = false,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val dayPolicy: DayPolicy = DayPolicy.EVERY_DAY,
    val weekdays: Set<DayOfWeek> = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
    ),
    val windows: List<BlockWindow> = emptyList(),
    val quotaMinutes: String = "60",
    val apps: Set<String> = emptySet(),
)

data class GroupToday(
    val name: String,
    val iconKey: String,
    val colorArgb: Int,
    val enabled: Boolean,
    val activeToday: Boolean,
    val usageLabel: String,
    val windowLabel: String,
)

data class TodayUi(
    val accessibility: Boolean,
    val guardConnected: Boolean,
    val usageAccess: Boolean,
    val notifications: Boolean,
    val totalLabel: String,
    val uncovered: Boolean,
    val workday: WorkdayStatus?,
    val groups: List<GroupToday>,
    val lastEvent: Long?,
)

data class StatsUi(
    val range: StatsRange = StatsRange.TODAY,
    val columns: List<ChartColumn> = emptyList(),
    val legend: List<ChartSlice> = emptyList(),
    val ranking: List<RankedApp> = emptyList(),
)

class ShijieViewModel(private val graph: AppGraph) : ViewModel() {
    private val _onboarding = MutableStateFlow<Boolean?>(null)
    val onboardingDone: StateFlow<Boolean?> = _onboarding

    private val _today = MutableStateFlow<TodayUi?>(null)
    val today: StateFlow<TodayUi?> = _today

    val groups: StateFlow<List<StoredGroup>> = graph.groups.observe().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    private val _stats = MutableStateFlow(StatsUi())
    val stats: StateFlow<StatsUi> = _stats

    private val _workdays = MutableStateFlow<List<WorkdayOverride>>(emptyList())
    val workdays: StateFlow<List<WorkdayOverride>> = _workdays

    private val _lockLabel = MutableStateFlow<String?>(null)
    val lockLabel: StateFlow<String?> = _lockLabel

    private val _theme = MutableStateFlow(AppTheme.CURRENT)
    val theme: StateFlow<AppTheme> = _theme

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()

    private var range = StatsRange.TODAY

    init {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { graph.meta.theme() }
            _theme.value = saved
            graph.theme.value = saved
            selectPalette(saved)
            _onboarding.value = withContext(Dispatchers.IO) { graph.meta.isOnboardingDone() }
            refresh()
        }
    }

    fun setTheme(theme: AppTheme) {
        _theme.value = theme
        graph.theme.value = theme
        selectPalette(theme)
        viewModelScope.launch {
            withContext(Dispatchers.IO) { graph.meta.setTheme(theme) }
            refreshUsageChartWidgets(graph.app)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            app.shijie.system.HealthNotifier.sync(graph.app)
            loadToday()
            loadStats()
            refreshLock()
            _workdays.value = withContext(Dispatchers.IO) { graph.workdays.list() }
            refreshUsageChartWidgets(graph.app)
        }
    }

    fun engageLock(until: Instant) {
        viewModelScope.launch {
            val now = graph.clock.now()
            if (!until.isAfter(now)) {
                message("上锁结束时间要晚于现在")
                return@launch
            }
            withContext(Dispatchers.IO) { graph.meta.engageLock(until) }
            refreshLock()
            message("已上锁。到点之前，时界里没有解除按钮。")
        }
    }

    suspend fun systemUsed(packages: Set<String>): Long = withContext(Dispatchers.IO) {
        graph.usage.systemForegroundToday(packages, graph.engine.currentSession())
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { graph.meta.setOnboardingDone() }
            _onboarding.value = true
            refresh()
        }
    }

    fun setRange(value: StatsRange) {
        range = value
        viewModelScope.launch { loadStats() }
    }

    suspend fun loadDraft(id: Long): GroupDraft = withContext(Dispatchers.IO) {
        if (id == 0L) return@withContext GroupDraft()
        val stored = graph.groups.get(id) ?: return@withContext GroupDraft()
        val group = stored.group
        GroupDraft(
            id = group.id,
            name = group.name,
            colorArgb = group.colorArgb,
            iconKey = group.iconKey,
            enabled = group.enabled,
            useRange = group.startDate != null || group.endDate != null,
            startDate = group.startDate,
            endDate = group.endDate,
            dayPolicy = group.dayPolicy,
            weekdays = group.customWeekdays.ifEmpty {
                setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
            },
            windows = group.blockWindows,
            quotaMinutes = group.dailyQuota.toMinutes().toString(),
            apps = stored.packages.toSet(),
        )
    }

    suspend fun launchable(groupId: Long): List<LaunchableRow> = graph.groups.launchable(groupId)

    fun label(packageName: String): String = graph.groups.label(packageName)

    fun save(draft: GroupDraft, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            val error = withContext(Dispatchers.IO) {
                val minutes = draft.quotaMinutes.toLongOrNull()
                when {
                    draft.name.isBlank() -> "请填写分组名称"
                    minutes == null || minutes < 0L -> "请填写每日额度（分钟）"
                    draft.useRange && (draft.startDate == null || draft.endDate == null) -> "请选择起止日期"
                    draft.useRange && draft.startDate!! > draft.endDate!! -> "结束日期不能早于开始日期"
                    draft.windows.any { !it.isValid() } -> "有一条禁用时段无效"
                    else -> try {
                        val proposed = draft.toGroup(minutes)
                        if (graph.meta.isLocked(graph.clock.now()) && draft.id != 0L) {
                            val existing = graph.groups.get(draft.id)
                                ?: return@withContext "分组已不存在"
                            GroupLock.rejection(
                                existing.group,
                                proposed,
                                existing.packages.toSet(),
                                draft.apps,
                            )?.let { return@withContext it }
                        }
                        graph.groups.save(proposed, draft.apps)
                        null
                    } catch (error: IllegalArgumentException) {
                        error.message ?: "无法保存"
                    }
                }
            }
            if (error == null) refresh()
            onDone(error)
        }
    }

    fun deleteGroup(id: Long) {
        viewModelScope.launch {
            if (withContext(Dispatchers.IO) { graph.meta.isLocked(graph.clock.now()) }) {
                message("上锁期间不能删除分组")
                return@launch
            }
            withContext(Dispatchers.IO) { graph.groups.delete(id) }
            refresh()
        }
    }

    fun setWorkday(date: LocalDate, workday: Boolean) {
        viewModelScope.launch {
            if (withContext(Dispatchers.IO) { graph.meta.isLocked(graph.clock.now()) }) {
                message("上锁期间不能修改工作日")
                return@launch
            }
            withContext(Dispatchers.IO) { graph.workdays.set(date, workday) }
            refresh()
        }
    }

    fun clearWorkday(date: LocalDate) {
        viewModelScope.launch {
            if (withContext(Dispatchers.IO) { graph.meta.isLocked(graph.clock.now()) }) {
                message("上锁期间不能修改工作日")
                return@launch
            }
            withContext(Dispatchers.IO) { graph.workdays.clear(date) }
            refresh()
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            if (withContext(Dispatchers.IO) { graph.meta.isLocked(graph.clock.now()) }) {
                message("上锁期间不能删除本地数据")
                return@launch
            }
            withContext(Dispatchers.IO) { graph.db.clearAllTables() }
            _theme.value = AppTheme.CURRENT
            graph.theme.value = AppTheme.CURRENT
            selectPalette(AppTheme.CURRENT)
            _onboarding.value = false
            refresh()
        }
    }

    private suspend fun refreshLock() {
        val until = withContext(Dispatchers.IO) { graph.meta.lockInstant() }
        val now = graph.clock.now()
        _lockLabel.value = if (until != null && now.isBefore(until)) {
            val zoned = until.atZone(graph.clock.zone())
            "已上锁到 ${zoned.format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))}。到点之前不能修改已有分组的时间限制和使用时长，也不能关闭、删除或调整应用名单。名称和图标可以改，也可以新建分组。时界里没有解除按钮。"
        } else {
            null
        }
    }

    fun runTest(): Boolean = graph.engine.runTest()

    fun message(text: String) {
        _messages.tryEmit(text)
    }

    private suspend fun loadToday() {
        val ui = withContext(Dispatchers.IO) {
            val now = graph.clock.now()
            val zone = graph.clock.zone()
            val today = UsageDay.localDate(now, zone)
            val calendar = graph.workdays.calendar()
            val status = calendar.status(today)
            val open = graph.engine.currentSession()
            val total = graph.usage.todayTotal(open)
            val cards = graph.groups.snapshot().map { stored ->
                val group = stored.group
                val active = app.shijie.domain.GroupSchedule.isActive(group, today, calendar)
                val releases = graph.overrides.overlapping(group.id, today, zone)
                val used = graph.usage.quotaUsed(stored.packages.toSet(), group, releases, open)
                val remaining = (group.dailyQuota.toMillis() - used).coerceAtLeast(0L)
                GroupToday(
                    name = group.name,
                    iconKey = group.iconKey,
                    colorArgb = group.colorArgb,
                    enabled = group.enabled,
                    activeToday = active,
                    usageLabel = if (!Permissions.usageGranted(graph.app)) {
                        "还不能读取系统使用时间"
                    } else if (active) {
                        "系统已用 ${formatDurationMinutes(used)} · 额度剩余 ${formatDuration(remaining)}"
                    } else {
                        "今日不限制 · 系统已用 ${formatDurationMinutes(used)}"
                    },
                    windowLabel = TransitionPlanner.windowStatusText(now, zone, group, calendar),
                )
            }
            TodayUi(
                accessibility = Permissions.accessibilityEnabled(graph.app),
                guardConnected = graph.guardConnected.value,
                usageAccess = Permissions.usageGranted(graph.app),
                notifications = Permissions.notificationsEnabled(graph.app),
                totalLabel = formatDurationMinutes(total),
                uncovered = status.uncoveredYear,
                workday = status,
                groups = cards,
                lastEvent = graph.lastForegroundEvent.value,
            )
        }
        _today.value = ui
    }

    private suspend fun loadStats() {
        val ui = withContext(Dispatchers.IO) {
            val now = graph.clock.now()
            val zone = graph.clock.zone()
            val today = UsageDay.localDate(now, zone)
            val open = graph.engine.currentSession()
            val stored = graph.groups.snapshot()
            val membership = HashMap<String, MutableList<Pair<String, Int>>>()
            stored.forEach { group ->
                group.packages.forEach { pkg ->
                    val list = membership.getOrPut(pkg) { mutableListOf() }
                    if (list.none { it.first == group.group.name }) {
                        list += group.group.name to group.group.colorArgb
                    }
                }
            }
            val order = stored.map { it.group.name to it.group.colorArgb }
            when (range) {
                StatsRange.TODAY -> {
                    val hours = graph.usage.hourlyByPackage(open)
                    val labels = List(hours.size) { hour -> "%02d".format(hour) }
                    val (columns, legend) = UsageCategories.stack(labels, hours, membership, order)
                    StatsUi(
                        range = range,
                        columns = columns,
                        legend = legend,
                        ranking = graph.usage.ranking(today, today, open),
                    )
                }
                StatsRange.WEEK -> statsFor(today.minusDays(6), today, open, membership, order)
                StatsRange.MONTH -> statsFor(today.minusDays(29), today, open, membership, order)
            }
        }
        _stats.value = ui
    }

    private suspend fun statsFor(
        from: LocalDate,
        to: LocalDate,
        open: app.shijie.domain.OpenSession?,
        membership: Map<String, List<Pair<String, Int>>>,
        order: List<Pair<String, Int>>,
    ): StatsUi {
        val days = graph.usage.dailyByPackage(from, to, open)
        val labels = mutableListOf<String>()
        var day = from
        while (!day.isAfter(to)) {
            labels += "${day.monthValue}/${day.dayOfMonth}"
            day = day.plusDays(1)
        }
        val (columns, legend) = UsageCategories.stack(labels, days, membership, order)
        return StatsUi(
            range = range,
            columns = columns,
            legend = legend,
            ranking = graph.usage.ranking(from, to, open),
        )
    }

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return ShijieViewModel((app as ShijieApp).graph) as T
                }
            }
        }
    }
}

private fun GroupDraft.toGroup(minutes: Long): RestrictionGroup {
    return RestrictionGroup(
        id = id,
        name = name.trim(),
        colorArgb = colorArgb,
        iconKey = iconKey,
        enabled = enabled,
        startDate = if (useRange) startDate else null,
        endDate = if (useRange) endDate else null,
        dayPolicy = dayPolicy,
        customWeekdays = weekdays,
        blockWindows = windows,
        dailyQuota = Duration.ofMinutes(minutes),
    )
}
