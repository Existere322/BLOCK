package app.shijie.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Activity still resumed when the usage log was last folded. */
data class TrackedActivity(
    val packageName: String,
    val instanceKey: Int,
    val lastResumeMs: Long,
)

/** Replay position saved between reads of the system usage log. */
data class ReplayState(
    val screenOn: Boolean = true,
    val openStartsMs: Map<String, Long> = emptyMap(),
    val activities: List<TrackedActivity> = emptyList(),
)

data class UsageCursor(
    val positionMs: Long,
    val replay: ReplayState,
)

data class UsageAdvance(
    val cursor: UsageCursor,
    val closed: List<ForegroundSpan>,
)

object ReplayStateText {
    fun encode(state: ReplayState): String {
        val lines = ArrayList<String>()
        lines += if (state.screenOn) "on" else "off"
        state.openStartsMs.toSortedMap().forEach { (packageName, startMs) ->
            lines += "open\t$packageName\t$startMs"
        }
        state.activities
            .sortedWith(compareBy({ it.packageName }, { it.instanceKey }))
            .forEach { activity ->
                lines += "act\t${activity.packageName}\t${activity.instanceKey}\t${activity.lastResumeMs}"
            }
        return lines.joinToString("\n")
    }

    fun decode(text: String): ReplayState {
        var screenOn = true
        val open = HashMap<String, Long>()
        val activities = mutableListOf<TrackedActivity>()
        text.lineSequence().forEach { line ->
            if (line.isBlank()) return@forEach
            val parts = line.split('\t')
            when (parts[0]) {
                "on" -> screenOn = true
                "off" -> screenOn = false
                "open" -> if (parts.size == 3) {
                    val startMs = parts[2].toLongOrNull()
                    if (startMs != null) open[parts[1]] = startMs
                }
                "act" -> if (parts.size == 4) {
                    val instanceKey = parts[2].toIntOrNull()
                    val lastResumeMs = parts[3].toLongOrNull()
                    if (instanceKey != null && lastResumeMs != null) {
                        activities += TrackedActivity(parts[1], instanceKey, lastResumeMs)
                    }
                }
            }
        }
        return ReplayState(screenOn, open, activities)
    }
}

object UsageLedger {
    data class Addition(
        val date: LocalDate,
        val hour: Int,
        val packageName: String,
        val millis: Long,
    )

    /** Splits a span on local day and hour boundaries. */
    fun additions(span: ForegroundSpan, zone: ZoneId): List<Addition> {
        if (span.packageName.isEmpty() || !span.end.isAfter(span.start)) return emptyList()
        val result = mutableListOf<Addition>()
        var cursor = span.start
        while (cursor.isBefore(span.end)) {
            val zoned = cursor.atZone(zone)
            val dayEnd = UsageDay.nextStart(zoned.toLocalDate(), zone)
            val hourEnd = zoned.withMinute(0).withSecond(0).withNano(0).plusHours(1).toInstant()
            var sliceEnd = span.end
            if (dayEnd.isBefore(sliceEnd)) sliceEnd = dayEnd
            if (hourEnd.isBefore(sliceEnd)) sliceEnd = hourEnd
            if (!sliceEnd.isAfter(cursor)) break
            val millis = Duration.between(cursor, sliceEnd).toMillis()
            if (millis > 0L) {
                result += Addition(zoned.toLocalDate(), zoned.hour, span.packageName, millis)
            }
            cursor = sliceEnd
        }
        return result
    }
}

object OpenUsage {
    /** Time still open at [now]. This is not written; the next pause closes it once. */
    fun spans(state: ReplayState, now: Instant): List<ForegroundSpan> {
        if (!state.screenOn) return emptyList()
        return state.openStartsMs.mapNotNull { (packageName, startMs) ->
            val start = Instant.ofEpochMilli(startMs)
            if (packageName.isEmpty() || !now.isAfter(start)) null
            else ForegroundSpan(packageName, start, now)
        }
    }
}
