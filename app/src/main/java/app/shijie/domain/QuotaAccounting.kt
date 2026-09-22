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
        val sorted = events.sortedWith(compareBy<ForegroundEvent> { it.at }.thenBy { if (it.kind == ForegroundEventKind.PAUSE) 0 else 1 })
        val open = linkedMapOf<String, Instant>()
        val spans = mutableListOf<ForegroundSpan>()
        fun emit(packageName: String, start: Instant, end: Instant) {
            val clampedStart = if (start.isBefore(rangeStart)) rangeStart else start
            val clampedEnd = if (end.isAfter(rangeEnd)) rangeEnd else end
            if (clampedEnd.isAfter(clampedStart)) {
                spans += ForegroundSpan(packageName, clampedStart, clampedEnd)
            }
        }
        for (event in sorted) {
            if (event.at.isAfter(rangeEnd)) break
            when (event.kind) {
                ForegroundEventKind.RESUME -> {
                    open[event.packageName]?.let { previous -> emit(event.packageName, previous, event.at) }
                    open[event.packageName] = event.at
                }
                ForegroundEventKind.PAUSE -> {
                    open.remove(event.packageName)?.let { previous -> emit(event.packageName, previous, event.at) }
                }
            }
        }
        for ((packageName, start) in open) {
            emit(packageName, start, rangeEnd)
        }
        return spans
    }
}

enum class ForegroundEventKind { RESUME, PAUSE }

data class ForegroundEvent(
    val packageName: String,
    val at: Instant,
    val kind: ForegroundEventKind,
)

data class ForegroundSpan(
    val packageName: String,
    val start: Instant,
    val end: Instant,
)
