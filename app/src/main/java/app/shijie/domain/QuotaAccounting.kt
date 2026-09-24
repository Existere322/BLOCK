package app.shijie.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object QuotaAccounting {
    fun countedMillis(
        spans: List<ForegroundSpan>,
        zone: ZoneId,
        windows: List<BlockWindow>,
        releases: List<TemporaryOverride>,
    ): Long {
        val merged = WindowMerger.merge(windows)
        return spans.sumOf { span -> countedInSpan(span, zone, merged, releases) }
    }

    private fun countedInSpan(
        span: ForegroundSpan,
        zone: ZoneId,
        windows: List<BlockWindow>,
        releases: List<TemporaryOverride>,
    ): Long {
        if (!span.end.isAfter(span.start)) return 0L
        var cursor = span.start
        var total = 0L
        while (cursor.isBefore(span.end)) {
            val zoned = cursor.atZone(zone)
            val dayEnd = zoned.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
            val sliceEnd = if (dayEnd.isBefore(span.end)) dayEnd else span.end
            total += countedOnDay(cursor, sliceEnd, zoned.toLocalDate(), zone, windows, span.packageName, releases)
            cursor = sliceEnd
        }
        return total
    }

    private fun countedOnDay(
        start: Instant,
        end: Instant,
        date: LocalDate,
        zone: ZoneId,
        windows: List<BlockWindow>,
        packageName: String,
        releases: List<TemporaryOverride>,
    ): Long {
        val sliceMillis = Duration.between(start, end).toMillis()
        val blockedPieces = mutableListOf<Pair<Instant, Instant>>()
        var blocked = 0L
        for (window in windows) {
            for ((windowStart, windowEnd) in intervalsOn(date, zone, window)) {
                val pieceStart = maxOf(start, windowStart)
                val pieceEnd = minOf(end, windowEnd)
                if (pieceEnd.isAfter(pieceStart)) {
                    blocked += Duration.between(pieceStart, pieceEnd).toMillis()
                    blockedPieces += pieceStart to pieceEnd
                }
            }
        }
        var restored = 0L
        for ((pieceStart, pieceEnd) in blockedPieces) {
            for (release in releases) {
                if (release.packageName != packageName) continue
                val releaseEnd = EmergencyRelease.effectiveEnd(release)
                restored += overlapMillis(pieceStart, pieceEnd, release.grantedAt, releaseEnd)
            }
        }
        return (sliceMillis - blocked + restored).coerceIn(0L, sliceMillis)
    }

    private fun intervalsOn(
        date: LocalDate,
        zone: ZoneId,
        window: BlockWindow,
    ): List<Pair<Instant, Instant>> {
        if (!window.isValid()) return emptyList()
        val startOfDay = date.atStartOfDay(zone)
        return if (window.startMinute < window.endMinute) {
            listOf(startOfDay.plusMinutes(window.startMinute.toLong()).toInstant() to
                startOfDay.plusMinutes(window.endMinute.toLong()).toInstant())
        } else {
            listOf(
                startOfDay.plusMinutes(window.startMinute.toLong()).toInstant() to
                    startOfDay.plusMinutes(1440).toInstant(),
                startOfDay.toInstant() to startOfDay.plusMinutes(window.endMinute.toLong()).toInstant(),
            )
        }
    }

    private fun overlapMillis(a0: Instant, a1: Instant, b0: Instant, b1: Instant): Long {
        val start = maxOf(a0, b0)
        val end = minOf(a1, b1)
        return if (end.isAfter(start)) Duration.between(start, end).toMillis() else 0L
    }
}

object ForegroundSpans {
    fun collect(
        events: List<ForegroundEvent>,
        rangeStart: Instant,
        rangeEnd: Instant,
    ): List<ForegroundSpan> {
        if (!rangeEnd.isAfter(rangeStart)) return emptyList()
        val spans = mutableListOf<ForegroundSpan>()
        val replay = SessionReplay()
        for (event in events.sortedBy { it.at }) {
            if (event.at.isAfter(rangeEnd)) break
            replay.apply(event) { packageName, start, end ->
                spans += clamp(packageName, start, end, rangeStart, rangeEnd)
            }
        }
        replay.finish(rangeEnd) { packageName, start, end ->
            spans += clamp(packageName, start, end, rangeStart, rangeEnd)
        }
        return spans.filter { it.end.isAfter(it.start) }
    }

    /**
     * Continues a saved replay from [cursor] through events in
     * `[cursor.positionMs, readUntilMs)`. The returned cursor sits at [readUntilMs],
     * so the next system query can start there without reading this slice again.
     */
    fun advance(cursor: UsageCursor, events: List<ForegroundEvent>, readUntilMs: Long): UsageAdvance {
        val replay = SessionReplay.from(cursor.replay)
        val closed = mutableListOf<ForegroundSpan>()
        for (event in events.sortedBy { it.at }) {
            val at = event.at.toEpochMilli()
            if (at < cursor.positionMs || at >= readUntilMs) continue
            replay.apply(event) { packageName, start, end ->
                if (end.isAfter(start)) closed += ForegroundSpan(packageName, start, end)
            }
        }
        val position = maxOf(cursor.positionMs, readUntilMs)
        return UsageAdvance(UsageCursor(position, replay.export()), closed)
    }

    fun foregroundPackage(state: ReplayState): String? = SessionReplay.from(state).foregroundPackage()

    /** Package that still has a resumed activity at [at], matching UsageStats activity state. */
    fun foregroundPackage(events: List<ForegroundEvent>, at: Instant): String? {
        val replay = SessionReplay()
        for (event in events.sortedBy { it.at }) {
            if (event.at.isAfter(at)) break
            replay.apply(event) { _, _, _ -> }
        }
        return replay.foregroundPackage()
    }

    private fun clamp(
        packageName: String,
        start: Instant,
        end: Instant,
        rangeStart: Instant,
        rangeEnd: Instant,
    ): ForegroundSpan {
        val clampedStart = if (start.isBefore(rangeStart)) rangeStart else start
        val clampedEnd = if (end.isAfter(rangeEnd)) rangeEnd else end
        return ForegroundSpan(packageName, clampedStart, clampedEnd)
    }
}

/**
 * Replays foreground time from usage events.
 *
 * Activity switches inside one app resume the new screen before the old one pauses.
 * A pause ends the package only when no other activity of that package is still
 * resumed. Stop is ignored. Screen-off ends the open interval; a later resume means
 * the screen is in use again.
 */
private class SessionReplay {
    private val resumed = HashMap<String, HashMap<Int, Boolean>>()
    private val lastResumeAt = HashMap<String, Instant>()
    private val open = HashMap<String, Instant>()
    private var screenOn = true

    fun foregroundPackage(): String? {
        val active = resumed.filterValues { instances -> instances.values.any { it } }.keys
        if (active.isEmpty()) return null
        return active.maxByOrNull { lastResumeAt[it] ?: Instant.EPOCH }
    }

    fun apply(event: ForegroundEvent, emit: (String, Instant, Instant) -> Unit) {
        when (event.kind) {
            ForegroundEventKind.SCREEN_OFF -> {
                screenOn = false
                closeOpen(event.at, emit)
            }
            ForegroundEventKind.SCREEN_ON -> {
                screenOn = true
                for (packageName in resumed.keys) ensureOpen(packageName, event.at)
            }
            ForegroundEventKind.SHUTDOWN -> {
                closeOpen(event.at, emit)
                resumed.clear()
            }
            ForegroundEventKind.RESUME -> {
                if (event.packageName.isEmpty()) return
                screenOn = true
                for (other in resumed.keys.toList()) {
                    if (other == event.packageName) continue
                    val instances = resumed[other] ?: continue
                    if (instances.values.none { it }) continue
                    instances.keys.toList().forEach { instances[it] = false }
                    closeIfIdle(other, event.at, emit)
                }
                resumed.getOrPut(event.packageName) { HashMap() }[event.instanceKey] = true
                lastResumeAt[event.packageName] = event.at
                ensureOpen(event.packageName, event.at)
            }
            ForegroundEventKind.PAUSE -> {
                val instances = resumed[event.packageName] ?: return
                if (instances[event.instanceKey] == true) instances[event.instanceKey] = false
                if (screenOn) closeIfIdle(event.packageName, event.at, emit)
            }
            ForegroundEventKind.STOP -> Unit
        }
    }

    fun finish(at: Instant, emit: (String, Instant, Instant) -> Unit) {
        if (screenOn) closeOpen(at, emit)
    }

    fun export(): ReplayState {
        val activities = mutableListOf<TrackedActivity>()
        resumed.forEach { (packageName, instances) ->
            val last = lastResumeAt[packageName]?.toEpochMilli() ?: 0L
            instances.forEach { (instanceKey, active) ->
                if (active) activities += TrackedActivity(packageName, instanceKey, last)
            }
        }
        return ReplayState(
            screenOn = screenOn,
            openStartsMs = open.mapValues { it.value.toEpochMilli() },
            activities = activities,
        )
    }

    companion object {
        fun from(state: ReplayState): SessionReplay {
            val replay = SessionReplay()
            replay.screenOn = state.screenOn
            state.openStartsMs.forEach { (packageName, startMs) ->
                replay.open[packageName] = Instant.ofEpochMilli(startMs)
            }
            state.activities.forEach { activity ->
                replay.resumed.getOrPut(activity.packageName) { HashMap() }[activity.instanceKey] = true
                val at = Instant.ofEpochMilli(activity.lastResumeMs)
                val previous = replay.lastResumeAt[activity.packageName]
                if (previous == null || at.isAfter(previous)) replay.lastResumeAt[activity.packageName] = at
            }
            return replay
        }
    }

    private fun isResumed(packageName: String): Boolean {
        return resumed[packageName]?.values?.any { it } == true
    }

    private fun ensureOpen(packageName: String, at: Instant) {
        if (!screenOn || !isResumed(packageName) || open.containsKey(packageName)) return
        open[packageName] = at
    }

    private fun closeIfIdle(packageName: String, at: Instant, emit: (String, Instant, Instant) -> Unit) {
        if (isResumed(packageName)) return
        val start = open.remove(packageName) ?: return
        emit(packageName, start, at)
    }

    private fun closeOpen(at: Instant, emit: (String, Instant, Instant) -> Unit) {
        val closing = open.toMap()
        open.clear()
        for ((packageName, start) in closing) emit(packageName, start, at)
    }

}

enum class ForegroundEventKind { RESUME, PAUSE, STOP, SCREEN_ON, SCREEN_OFF, SHUTDOWN }

data class ForegroundEvent(
    val packageName: String,
    val at: Instant,
    val kind: ForegroundEventKind,
    val instanceKey: Int = 0,
)

data class ForegroundSpan(
    val packageName: String,
    val start: Instant,
    val end: Instant,
)
