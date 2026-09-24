package app.shijie.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageSyncTest {
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    @Test
    fun hourlyAdvancesMatchAFullReplay() {
        val origin = instant("2026-03-03T22:00:00")
        val end = instant("2026-03-04T02:00:00")
        val events = listOf(
            ForegroundEvent("app.game", instant("2026-03-03T23:30:00"), ForegroundEventKind.RESUME, 1),
            ForegroundEvent("", instant("2026-03-04T00:10:00"), ForegroundEventKind.SCREEN_OFF),
            ForegroundEvent("", instant("2026-03-04T00:40:00"), ForegroundEventKind.SCREEN_ON),
            ForegroundEvent("app.game", instant("2026-03-04T01:00:00"), ForegroundEventKind.PAUSE, 1),
            ForegroundEvent("app.mail", instant("2026-03-04T01:10:00"), ForegroundEventKind.RESUME, 2),
            ForegroundEvent("app.mail", instant("2026-03-04T01:10:01"), ForegroundEventKind.PAUSE, 2),
        )
        val full = ForegroundSpans.collect(events, origin, end)
        val folded = fold(events, origin, end)
        assertEquals(totals(full), totals(folded))
    }

    @Test
    fun theSameSliceIsNotCountedTwice() {
        val start = instant("2026-03-04T10:00:00")
        val middle = instant("2026-03-04T10:30:00")
        val end = instant("2026-03-04T11:00:00")
        val events = listOf(
            ForegroundEvent("app.game", instant("2026-03-04T10:00:00"), ForegroundEventKind.RESUME, 1),
            ForegroundEvent("app.game", instant("2026-03-04T10:20:00"), ForegroundEventKind.PAUSE, 1),
        )
        var cursor = UsageCursor(start.toEpochMilli(), ReplayState())
        val first = ForegroundSpans.advance(cursor, events, middle.toEpochMilli())
        cursor = first.cursor
        val second = ForegroundSpans.advance(cursor, events, end.toEpochMilli())
        val again = ForegroundSpans.advance(cursor, events, middle.toEpochMilli())
        assertEquals(20 * 60_000L, millis(first.closed))
        assertEquals(0L, millis(second.closed))
        assertEquals(0L, millis(again.closed))
        assertEquals(middle.toEpochMilli(), again.cursor.positionMs)
    }

    @Test
    fun anOpenVisitIsAddedUntilItPausesAndThenStoredOnce() {
        val start = instant("2026-03-04T10:00:00")
        val middle = instant("2026-03-04T10:20:00")
        val end = instant("2026-03-04T10:50:00")
        val resume = ForegroundEvent("app.game", start, ForegroundEventKind.RESUME, 1)
        val pause = ForegroundEvent("app.game", end, ForegroundEventKind.PAUSE, 1)
        val open = ForegroundSpans.advance(UsageCursor(start.toEpochMilli(), ReplayState()), listOf(resume), middle.toEpochMilli())
        val tail = OpenUsage.spans(open.cursor.replay, middle)
        assertEquals(0L, millis(open.closed))
        assertEquals(20 * 60_000L, millis(tail))
        val closed = ForegroundSpans.advance(open.cursor, listOf(resume, pause), end.toEpochMilli() + 1)
        assertEquals(50 * 60_000L, millis(closed.closed))
        assertTrue(OpenUsage.spans(closed.cursor.replay, end).isEmpty())
    }

    @Test
    fun screenOffDoesNotKeepAddingTime() {
        val start = instant("2026-03-04T10:00:00")
        val off = instant("2026-03-04T10:05:00")
        val later = instant("2026-03-04T12:00:00")
        val events = listOf(
            ForegroundEvent("app.game", start, ForegroundEventKind.RESUME, 1),
            ForegroundEvent("", off, ForegroundEventKind.SCREEN_OFF),
        )
        val folded = ForegroundSpans.advance(
            UsageCursor(start.toEpochMilli(), ReplayState()),
            events,
            later.toEpochMilli(),
        )
        assertEquals(5 * 60_000L, millis(folded.closed))
        assertTrue(OpenUsage.spans(folded.cursor.replay, later).isEmpty())
    }

    @Test
    fun aSpanSplitsAcrossMidnightAndTheHour() {
        val span = ForegroundSpan(
            "app.game",
            instant("2026-03-04T23:30:00"),
            instant("2026-03-05T00:20:00"),
        )
        val additions = UsageLedger.additions(span, zone)
        assertEquals(30 * 60_000L, additions.filter { it.date.dayOfMonth == 4 && it.hour == 23 }.sumOf { it.millis })
        assertEquals(20 * 60_000L, additions.filter { it.date.dayOfMonth == 5 && it.hour == 0 }.sumOf { it.millis })
    }

    @Test
    fun replayStateRoundTrips() {
        val state = ReplayState(
            screenOn = false,
            openStartsMs = mapOf("app.game" to 10L),
            activities = listOf(TrackedActivity("app.game", 7, 10L)),
        )
        assertEquals(state, ReplayStateText.decode(ReplayStateText.encode(state)))
    }

    private fun fold(events: List<ForegroundEvent>, origin: Instant, end: Instant): List<ForegroundSpan> {
        var cursor = UsageCursor(origin.toEpochMilli(), ReplayState())
        val closed = mutableListOf<ForegroundSpan>()
        var position = origin
        while (position.isBefore(end)) {
            val sliceEnd = minOf(position.plus(Duration.ofHours(1)), end)
            val slice = events.filter { !it.at.isBefore(position) && it.at.isBefore(sliceEnd) }
            val step = ForegroundSpans.advance(cursor, slice, sliceEnd.toEpochMilli())
            closed += step.closed
            cursor = step.cursor
            position = sliceEnd
        }
        return closed + OpenUsage.spans(cursor.replay, end)
    }

    private fun totals(spans: List<ForegroundSpan>): Map<String, Long> = FocusedUsage.totals(spans)

    private fun millis(spans: List<ForegroundSpan>): Long = spans.sumOf { Duration.between(it.start, it.end).toMillis() }

    private fun instant(local: String): Instant = LocalDateTime.parse(local).atZone(zone).toInstant()
}
