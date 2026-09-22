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
                if (app.shijie.system.Permissions.usageGranted(graph.app)) {
                    val today = UsageDay.localDate(graph.clock.now(), graph.clock.zone())
                    graph.usage.reconcile(today.minusDays(2), today)
                }
            }
            val resumed = withContext(Dispatchers.IO) { graph.usage.lastResumedPackage() }
            if (resumed != null) onForeground(resumed, immediate = true)
        }
    }

    fun onScreenOff() {
        screenOn = false
        host?.cancelSchedule()
        session = null
    }

    fun onScreenOn() {
        screenOn = true
        scope.launch {
            val resumed = withContext(Dispatchers.IO) { graph.usage.lastResumedPackage() } ?: return@launch
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
            }
            onDone(error)
        }
    }

    private suspend fun grant(packageName: String, groupId: Long, reason: String, waitStartedAtMs: Long): String? {
        if (packageName == TEST_PACKAGE || groupId == 0L) return "测试拦截不能放行"
        val now = graph.clock.now()
        val zone = graph.clock.zone()
        val stored = graph.groups.findByPackage(packageName) ?: return "这个应用已不在限制组中"
        if (stored.group.id != groupId) return "分组已变化，请重新打开应用"
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
        val stored = graph.groups.findByPackage(packageName)
            ?: return Plan(packageName, BlockDecision.Allow, null, home, graph.groups.label(packageName), 0, null)
        val group = stored.group
        val calendar = graph.workdays.calendar()
        val today = UsageDay.localDate(now, zone)
        val grants = graph.overrides.grantedOn(group.id, today, zone)
        val overlapping = graph.overrides.overlapping(group.id, today, zone)
        val active = overlapping.lastOrNull {
            EmergencyRelease.isCurrentlyActive(it, now, graph.clock.bootId(), packageName)
        }
        val used = graph.usage.quotaUsed(stored.packages.toSet(), group, overlapping, open)
        var decision = RuleEvaluator(calendar).evaluate(
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
                val inWindow = WindowMerger.merge(group.blockWindows).any { it.contains(minute) }
                decision = BlockDecision.Block(
                    reason = if (inWindow) BlockReason.BLOCK_WINDOW else BlockReason.QUOTA_EXHAUSTED,
                    groupId = group.id,
                    groupName = group.name,
                    endsAt = if (inWindow) GroupSchedule.currentWindowEnd(now, zone, WindowMerger.merge(group.blockWindows))
                    else UsageDay.nextStart(today, zone),
                )
                delay = null
            }
        }
        return Plan(
            packageName = packageName,
            decision = decision,
            group = group,
            home = home,
            label = graph.groups.label(packageName),
            grantsToday = grants.size,
            delayMs = delay,
        )
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
                val delay = plan.delayMs
                if (screenOn && delay != null && delay > 0L && plan.group != null) {
                    current.schedule(delay) { onForeground(plan.packageName, immediate = true) }
                }
            }
            is BlockDecision.Block -> {
                current.cancelSchedule()
                sticky = true
                current.goHome()
                current.showBlock(
                    BlockOverlayModel(
                        packageName = plan.packageName,
                        appLabel = plan.label,
                        groupId = decision.groupId,
                        groupName = decision.groupName,
                        reason = decision.reason,
                        detail = detail(decision, plan.group),
                        releaseAvailable = plan.grantsToday < EmergencyRelease.MAX_PER_GROUP_PER_DAY,
                        test = false,
                    ),
                )
                Log.i(TAG, "block ${decision.reason} ${plan.packageName}")
            }
        }
    }

    private fun detail(decision: BlockDecision.Block, group: RestrictionGroup?): String {
        val zone = graph.clock.zone()
        val now = graph.clock.now()
        val until = decision.endsAt?.atZone(zone)
        val untilText = if (until == null) {
            ""
        } else {
            val clock = until.format(CLOCK)
            if (until.toLocalDate() == UsageDay.localDate(now, zone)) "约至 $clock" else "约至次日 $clock"
        }
        val windowText = group?.blockWindows?.let { formatWindow(WindowMerger.merge(it).firstOrNull() ?: return@let "") }.orEmpty()
        return when (decision.reason) {
            BlockReason.BLOCK_WINDOW -> "「${decision.groupName}」正处于禁用时段 $windowText $untilText".trim()
            BlockReason.QUOTA_EXHAUSTED -> "「${decision.groupName}」的今日额度已用完 $untilText".trim()
        }
    }

    private data class Plan(
        val packageName: String,
        val decision: BlockDecision,
        val group: RestrictionGroup?,
        val home: Boolean,
        val label: String,
        val grantsToday: Int,
        val delayMs: Long?,
    )

    companion object {
        const val TEST_PACKAGE = "__shijie_test__"
        private const val TAG = "Shijie"
        private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
