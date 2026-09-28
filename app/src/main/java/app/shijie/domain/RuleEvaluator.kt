package app.shijie.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object GroupSchedule {
    fun isActive(group: RestrictionGroup, date: LocalDate, calendar: WorkdayCalendar): Boolean {
        if (!group.enabled) return false
        if (group.startDate != null && date.isBefore(group.startDate)) return false
        if (group.endDate != null && date.isAfter(group.endDate)) return false
        return when (group.dayPolicy) {
            DayPolicy.EVERY_DAY -> true
            DayPolicy.LEGAL_WORKDAY -> calendar.isLegalWorkday(date)
            DayPolicy.CUSTOM_WEEKDAYS -> date.dayOfWeek in group.customWeekdays
        }
    }

    /**
     * Overnight windows (e.g. 23:00–11:00) belong to the calendar day they started.
     * After midnight, keep using yesterday's day-policy so a Friday night block
     * still holds on Saturday morning.
     */
    fun effectiveScheduleDate(now: Instant, zone: ZoneId, windows: List<BlockWindow>): LocalDate {
        val date = now.atZone(zone).toLocalDate()
        val minute = minuteOf(now, zone)
        val inOvernightContinuation = windows.any { window ->
            window.startMinute > window.endMinute && minute < window.endMinute
        }
        return if (inOvernightContinuation) date.minusDays(1) else date
    }

    fun minuteOf(now: Instant, zone: ZoneId): Int {
        val zoned = now.atZone(zone)
        return zoned.hour * 60 + zoned.minute
    }

    /** True when this group's merged block window covers [now] and today's policy is active. */
    fun inBlockWindow(
        now: Instant,
        zone: ZoneId,
        group: RestrictionGroup,
        calendar: WorkdayCalendar,
    ): Boolean {
        val windows = WindowMerger.merge(group.blockWindows)
        if (windows.isEmpty()) return false
        val scheduleDate = effectiveScheduleDate(now, zone, windows)
        if (!isActive(group, scheduleDate, calendar)) return false
        return windows.any { it.contains(minuteOf(now, zone)) }
    }

    fun currentWindowEnd(now: Instant, zone: ZoneId, windows: List<BlockWindow>): Instant? {
        val minute = minuteOf(now, zone)
        val date = now.atZone(zone).toLocalDate()
        var best: Instant? = null
        for (window in windows) {
            if (!window.contains(minute)) continue
            val end = if (window.startMinute < window.endMinute) {
                date.atStartOfDay(zone).plusMinutes(window.endMinute.toLong()).toInstant()
            } else if (minute >= window.startMinute) {
                date.plusDays(1).atStartOfDay(zone).plusMinutes(window.endMinute.toLong()).toInstant()
            } else {
                date.atStartOfDay(zone).plusMinutes(window.endMinute.toLong()).toInstant()
            }
            if (best == null || end.isBefore(best)) best = end
        }
        return best
    }

    fun nextWindowStart(
        now: Instant,
        zone: ZoneId,
        group: RestrictionGroup,
        calendar: WorkdayCalendar,
    ): Instant? {
        val merged = WindowMerger.merge(group.blockWindows)
        if (merged.isEmpty()) return null
        var date = now.atZone(zone).toLocalDate()
        repeat(21) {
            if (isActive(group, date, calendar)) {
                val starts = merged.map { window ->
                    date.atStartOfDay(zone).plusMinutes(window.startMinute.toLong()).toInstant()
                }.filter { it.isAfter(now) }
                if (starts.isNotEmpty()) return starts.min()
            }
            date = date.plusDays(1)
        }
        return null
    }
}

class RuleEvaluator(
    private val calendar: WorkdayCalendar,
) {
    fun evaluate(packageName: String, now: Instant, groupUsage: GroupUsage?): BlockDecision {
        if (groupUsage == null || groupUsage.isSafetyWhitelisted) return BlockDecision.Allow
        val group = groupUsage.group
        val zone = groupUsage.zone
        val calendarDate = UsageDay.localDate(now, zone)
        val windows = WindowMerger.merge(group.blockWindows)
        val scheduleDate = GroupSchedule.effectiveScheduleDate(now, zone, windows)
        if (!GroupSchedule.isActive(group, scheduleDate, calendar)) return BlockDecision.Allow
        val release = groupUsage.temporaryOverride
        if (release != null && EmergencyRelease.isCurrentlyActive(release, now, groupUsage.bootId, packageName)) {
            return BlockDecision.Allow
        }
        val minute = GroupSchedule.minuteOf(now, zone)
        if (windows.any { it.contains(minute) }) {
            return BlockDecision.Block(
                reason = BlockReason.BLOCK_WINDOW,
                groupId = group.id,
                groupName = group.name,
                endsAt = GroupSchedule.currentWindowEnd(now, zone, windows),
            )
        }
        // Quota follows the local calendar day, not the overnight schedule day.
        if (!GroupSchedule.isActive(group, calendarDate, calendar)) return BlockDecision.Allow
        if (groupUsage.usedMillis >= group.dailyQuota.toMillis()) {
            return BlockDecision.Block(
                reason = BlockReason.QUOTA_EXHAUSTED,
                groupId = group.id,
                groupName = group.name,
                endsAt = UsageDay.nextStart(calendarDate, zone),
            )
        }
        return BlockDecision.Allow
    }
}

object TransitionPlanner {
    fun millisUntilEnforcement(
        now: Instant,
        zone: ZoneId,
        group: RestrictionGroup,
        usedMillis: Long,
        calendar: WorkdayCalendar,
        releaseExpiresAt: Instant?,
    ): Long? {
        if (releaseExpiresAt != null && releaseExpiresAt.isAfter(now)) {
            return Duration.between(now, releaseExpiresAt).toMillis()
        }
        val today = UsageDay.localDate(now, zone)
        val merged = WindowMerger.merge(group.blockWindows)
        val scheduleDate = GroupSchedule.effectiveScheduleDate(now, zone, merged)
        if (GroupSchedule.isActive(group, scheduleDate, calendar) &&
            merged.any { it.contains(GroupSchedule.minuteOf(now, zone)) }
        ) {
            return 0L
        }
        val candidates = mutableListOf<Long>()
        if (GroupSchedule.isActive(group, today, calendar)) {
            val remaining = group.dailyQuota.toMillis() - usedMillis
            candidates += remaining.coerceAtLeast(0L)
        }
        val next = GroupSchedule.nextWindowStart(now, zone, group, calendar)
        if (next != null) {
            candidates += Duration.between(now, next).toMillis().coerceAtLeast(0L)
        }
        return candidates.minOrNull()
    }

    fun windowStatusText(
        now: Instant,
        zone: ZoneId,
        group: RestrictionGroup,
        calendar: WorkdayCalendar,
    ): String {
        val today = UsageDay.localDate(now, zone)
        val merged = WindowMerger.merge(group.blockWindows)
        val scheduleDate = GroupSchedule.effectiveScheduleDate(now, zone, merged)
        if (!GroupSchedule.isActive(group, scheduleDate, calendar) &&
            !GroupSchedule.isActive(group, today, calendar)
        ) {
            return "今日规则不生效"
        }
        if (merged.isEmpty()) return "未设置禁用时段"
        val minute = GroupSchedule.minuteOf(now, zone)
        val current = merged.firstOrNull { it.contains(minute) }
        if (current != null && GroupSchedule.isActive(group, scheduleDate, calendar)) {
            return "进行中 ${formatWindow(current)}"
        }
        val next = GroupSchedule.nextWindowStart(now, zone, group, calendar) ?: return "近期没有禁用时段"
        val zoned = next.atZone(zone)
        val window = merged.firstOrNull { it.startMinute == zoned.hour * 60 + zoned.minute }
            ?: return "近期没有禁用时段"
        val dayLabel = when (zoned.toLocalDate()) {
            today -> "今天"
            today.plusDays(1) -> "明天"
            else -> "${zoned.monthValue}月${zoned.dayOfMonth}日"
        }
        return "$dayLabel ${formatWindow(window)}"
    }
}
