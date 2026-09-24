package app.shijie.guard

import android.util.Log
import app.shijie.data.AppGraph
import app.shijie.domain.BlockDecision
import app.shijie.domain.BlockReason
import app.shijie.domain.EmergencyRelease
import app.shijie.domain.GroupSchedule
import app.shijie.domain.GroupUsage
import app.shijie.domain.OpenSession
import app.shijie.domain.RestrictionGroup
import app.shijie.domain.RestrictionUnion
import app.shijie.domain.RuleEvaluator
import app.shijie.domain.TemporaryOverride
import app.shijie.domain.TransitionPlanner
import app.shijie.domain.UsageDay
import app.shijie.domain.WindowMerger
import app.shijie.domain.formatWindow
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BlockOverlayModel(
    val packageName: String,
    val appLabel: String,
    val groupId: Long,
    val groupName: String,
    val reason: BlockReason,
    val detail: String,
    val releaseAvailable: Boolean,
    val test: Boolean,
)

interface GuardHost {
    fun goHome()
    fun showBlock(model: BlockOverlayModel)
    fun hideBlock()
    fun schedule(delayMs: Long, action: () -> Unit)
    fun cancelSchedule()
    fun screenInteractive(): Boolean
}

class GuardEngine(private val graph: AppGraph) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val generation = AtomicLong()
    private var host: GuardHost? = null
    private var sticky = false
    @Volatile private var session: OpenSession? = null
    private var screenOn = true

    fun attach(host: GuardHost) {
        this.host = host
        screenOn = host.screenInteractive()
    }

    fun detach(host: GuardHost) {
        if (this.host === host) {
            host.cancelSchedule()
            this.host = null
        }
    }

    fun currentSession(): OpenSession? = session

    fun noteEvent() {
        val stamp = System.currentTimeMillis()
        graph.lastForegroundEvent.value = stamp
        graph.scope.launch {
            graph.meta.put(app.shijie.data.MetaStore.LAST_EVENT, stamp.toString())
        }
    }

    fun onConnected() {
        screenOn = host?.screenInteractive() ?: true
        scope.launch {
            withContext(Dispatchers.IO) {
                graph.overrides.cancelOtherBoots(graph.clock.bootId(), graph.clock.now())
            }
            val resumed = withContext(Dispatchers.IO) { graph.usage.lastResumedPackage() }
            if (resumed != null) onForeground(resumed, immediate = true)
            withContext(Dispatchers.IO) {
                if (app.shijie.system.Permissions.usageGranted(graph.app)) {
                    val today = UsageDay.localDate(graph.clock.now(), graph.clock.zone())
                    graph.usage.reconcile(today.minusDays(6), today)
                }
            }
        }
    }

    fun onScreenOff() {
        screenOn = false
        host?.cancelSchedule()
        EnforcementScheduler.cancel(graph.app)
        session = null
        scope.launch {
            withContext(Dispatchers.IO) { graph.usage.flushRecent() }
        }
    }

    fun onScreenOn() {
        screenOn = true
        scope.launch {
            val resumed = withContext(Dispatchers.IO) {
                graph.usage.flushRecent()
                graph.usage.lastResumedPackage()
            } ?: return@launch
            onForeground(resumed, immediate = true)
        }
    }

    fun onForeground(packageName: String, immediate: Boolean = false) {
        val ticket = generation.incrementAndGet()
        scope.launch {
            if (!immediate) delay(80)
            if (ticket != generation.get()) return@launch
            val plan = withContext(Dispatchers.IO) { plan(packageName) }
            if (ticket != generation.get()) return@launch
            apply(plan)
        }
    }

    /** AlarmManager / health-worker entry: re-evaluate a specific or last-known package. */
    fun onEnforcementAlarm(packageName: String) {
        if (!screenOn) {
            screenOn = host?.screenInteractive() ?: true
        }
        if (!screenOn) return
        val target = packageName.ifBlank { session?.packageName }.orEmpty()
        if (target.isBlank()) {
            recheckForeground()
            return
        }
        onForeground(target, immediate = true)
    }

    fun recheckForeground() {
        if (!screenOn) return
        scope.launch {
            val resumed = session?.packageName
                ?: withContext(Dispatchers.IO) { graph.usage.lastResumedPackage() }
                ?: return@launch
            onForeground(resumed, immediate = true)
        }
    }

    fun runTest(): Boolean {
        val current = host ?: return false
        sticky = true
        current.goHome()
        current.showBlock(
            BlockOverlayModel(
                packageName = TEST_PACKAGE,
                appLabel = "拦截测试",
                groupId = 0,
                groupName = "测试",
                reason = BlockReason.BLOCK_WINDOW,
                detail = "无障碍遮罩已显示。这不会限制任何应用。",
                releaseAvailable = false,
                test = true,
            ),
        )
        return true
    }

    fun shouldIgnoreForeground(packageName: String): Boolean {
        if (!sticky) return false
        return packageName == graph.app.packageName || packageName == "com.android.systemui"
    }

    fun dismiss() {
        sticky = false
        host?.hideBlock()
    }

    fun requestRelease(
        packageName: String,
        groupId: Long,
        reason: String,
        waitStartedAtMs: Long,
        onDone: (String?) -> Unit,
    ) {
        scope.launch {
            val error = withContext(Dispatchers.IO) {
                grant(packageName, groupId, reason, waitStartedAtMs)
            }
            if (error == null) {
                sticky = false
                host?.hideBlock()
                host?.cancelSchedule()
                EnforcementScheduler.cancel(graph.app)
            }
            onDone(error)
        }
    }

    private suspend fun grant(packageName: String, groupId: Long, reason: String, waitStartedAtMs: Long): String? {
        if (packageName == TEST_PACKAGE || groupId == 0L) return "测试拦截不能放行"
        val now = graph.clock.now()
        val zone = graph.clock.zone()
        val membership = graph.groups.findByPackage(packageName)
        if (membership.none { it.group.id == groupId }) return "这个应用已不在该分组中"
        val grants = graph.overrides.grantedOn(groupId, UsageDay.localDate(now, zone), zone)
        val rejection = EmergencyRelease.rejection(reason, Instant.ofEpochMilli(waitStartedAtMs), now, grants.size)
        if (rejection != null) return rejection
        graph.overrides.insert(
            TemporaryOverride(
                groupId = groupId,
                packageName = packageName,
                reason = reason.trim(),
                grantedAt = now,
                expiresAt = EmergencyRelease.expiresAt(now),
                cancelledAt = null,
                bootId = graph.clock.bootId(),
            ),
        )
        return null
    }

    private suspend fun plan(packageName: String): Plan {
        val now = graph.clock.now()
        val zone = graph.clock.zone()
        val safety = graph.groups.isSafety(packageName)
        val home = graph.groups.isHome(packageName)
        val open = if (session?.packageName == packageName) session else null
        if (safety) {
            return Plan(packageName, BlockDecision.Allow, null, home, "", 0, null)
        }
        val storedGroups = graph.groups.findByPackage(packageName)
        if (storedGroups.isEmpty()) {
            return Plan(packageName, BlockDecision.Allow, null, home, graph.groups.label(packageName), 0, null, emptyList())
        }
        val calendar = graph.workdays.calendar()
        val today = UsageDay.localDate(now, zone)
        val evaluator = RuleEvaluator(calendar)
        val judged = storedGroups.map { stored ->
            judge(stored, packageName, now, zone, today, calendar, evaluator, open)
        }
        val blocks = judged.mapNotNull { row -> (row.decision as? BlockDecision.Block)?.let { row to it } }
        if (blocks.isNotEmpty()) {
            val chosenDecision = RestrictionUnion.strictest(blocks.map { it.second }) as BlockDecision.Block
            val chosen = blocks.first { it.second == chosenDecision }
            return Plan(
                packageName = packageName,
                decision = chosenDecision,
                group = chosen.first.group,
                home = home,
                label = graph.groups.label(packageName),
                grantsToday = chosen.first.grantsToday,
                delayMs = null,
                blocks = blocks.map { it.second },
            )
        }
        return Plan(
            packageName = packageName,
            decision = BlockDecision.Allow,
            group = storedGroups.first().group,
            home = home,
            label = graph.groups.label(packageName),
            grantsToday = 0,
            delayMs = judged.mapNotNull { it.delayMs }.minOrNull(),
            blocks = emptyList(),
        )
    }

    private suspend fun judge(
        stored: app.shijie.data.StoredGroup,
        packageName: String,
        now: Instant,
        zone: java.time.ZoneId,
        today: java.time.LocalDate,
        calendar: app.shijie.domain.WorkdayCalendar,
        evaluator: RuleEvaluator,
        open: OpenSession?,
    ): Judged {
        val group = stored.group
        val grants = graph.overrides.grantedOn(group.id, today, zone)
        val overlapping = graph.overrides.overlapping(group.id, today, zone)
        val active = overlapping.lastOrNull {
            EmergencyRelease.isCurrentlyActive(it, now, graph.clock.bootId(), packageName)
        }
        val used = graph.usage.quotaUsed(stored.packages.toSet(), group, overlapping, open)
        var decision = evaluator.evaluate(
            packageName,
            now,
            GroupUsage(
                group = group,
                usedMillis = used,
                temporaryOverride = active,
                zone = zone,
                isSafetyWhitelisted = false,
                bootId = graph.clock.bootId(),
            ),
        )
        var delay: Long? = null
        if (decision is BlockDecision.Allow) {
            delay = TransitionPlanner.millisUntilEnforcement(
                now, zone, group, used, calendar, active?.expiresAt,
            )
            if (delay != null && delay <= 0L) {
                val minute = GroupSchedule.minuteOf(now, zone)
                val windows = WindowMerger.merge(group.blockWindows)
                val inWindow = windows.any { it.contains(minute) }
                decision = BlockDecision.Block(
                    reason = if (inWindow) BlockReason.BLOCK_WINDOW else BlockReason.QUOTA_EXHAUSTED,
                    groupId = group.id,
                    groupName = group.name,
                    endsAt = if (inWindow) GroupSchedule.currentWindowEnd(now, zone, windows)
                    else UsageDay.nextStart(today, zone),
                )
                delay = null
            }
        }
        return Judged(group, decision, grants.size, delay)
    }

    private fun apply(plan: Plan) {
        val current = host ?: return
        if (session?.packageName != plan.packageName) {
            session = OpenSession(plan.packageName, graph.clock.now())
        }
        when (val decision = plan.decision) {
            BlockDecision.Allow -> {
                val keepOverlay = sticky && (plan.home || plan.packageName == graph.app.packageName)
                if (!keepOverlay) {
                    if (sticky) Log.i(TAG, "hide overlay for ${plan.packageName}")
                    sticky = false
                    current.hideBlock()
                }
                current.cancelSchedule()
                EnforcementScheduler.cancel(graph.app)
                val delay = plan.delayMs
                if (screenOn && delay != null && delay > 0L && plan.group != null) {
                    current.schedule(delay) { onForeground(plan.packageName, immediate = true) }
                    EnforcementScheduler.schedule(graph.app, delay, plan.packageName)
                }
            }
            is BlockDecision.Block -> {
                current.cancelSchedule()
                EnforcementScheduler.cancel(graph.app)
                sticky = true
                current.goHome()
                current.showBlock(
                    BlockOverlayModel(
                        packageName = plan.packageName,
                        appLabel = plan.label,
                        groupId = decision.groupId,
                        groupName = decision.groupName,
                        reason = decision.reason,
                        detail = detail(decision, plan.group, plan.blocks),
                        releaseAvailable = plan.grantsToday < EmergencyRelease.MAX_PER_GROUP_PER_DAY,
                        test = false,
                    ),
                )
                Log.i(TAG, "block ${decision.reason} ${plan.packageName}")
            }
        }
    }

    private fun detail(
        decision: BlockDecision.Block,
        group: RestrictionGroup?,
        blocks: List<BlockDecision.Block>,
    ): String {
        val zone = graph.clock.zone()
        val now = graph.clock.now()
        val until = decision.endsAt?.atZone(zone)
        val untilText = if (until == null) {
            ""
        } else {
            val clock = until.format(CLOCK)
            if (until.toLocalDate() == UsageDay.localDate(now, zone)) "约至 $clock" else "约至次日 $clock"
        }
        if (blocks.size > 1) {
            val parts = blocks.map { block ->
                when (block.reason) {
                    BlockReason.BLOCK_WINDOW -> "「${block.groupName}」正处于禁用时段"
                    BlockReason.QUOTA_EXHAUSTED -> "「${block.groupName}」的今日额度已用完"
                }
            }
            return (parts.joinToString("，") + " " + untilText).trim()
        }
        val windowText = group?.blockWindows?.let { formatWindow(WindowMerger.merge(it).firstOrNull() ?: return@let "") }.orEmpty()
        return when (decision.reason) {
            BlockReason.BLOCK_WINDOW -> "「${decision.groupName}」正处于禁用时段 $windowText $untilText".trim()
            BlockReason.QUOTA_EXHAUSTED -> "「${decision.groupName}」的今日额度已用完 $untilText".trim()
        }
    }

    private data class Judged(
        val group: RestrictionGroup,
        val decision: BlockDecision,
        val grantsToday: Int,
        val delayMs: Long?,
    )

    private data class Plan(
        val packageName: String,
        val decision: BlockDecision,
        val group: RestrictionGroup?,
        val home: Boolean,
        val label: String,
        val grantsToday: Int,
        val delayMs: Long?,
        val blocks: List<BlockDecision.Block> = emptyList(),
    )

    companion object {
        const val TEST_PACKAGE = "__shijie_test__"
        private const val TAG = "Shijie"
        private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
