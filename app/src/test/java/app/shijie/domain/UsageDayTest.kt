package app.shijie.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageDayTest {
    @Test
    fun expandedDailyQueryDoesNotIncludeThePreviousOrNextDay() {
        val zone = ZoneId.of("Asia/Shanghai")
        val day = LocalDate.of(2026, 9, 24)
        assertFalse(UsageDay.isBucketForDate(Instant.parse("2026-09-23T15:59:59Z").toEpochMilli(), day, zone))
        assertTrue(UsageDay.isBucketForDate(Instant.parse("2026-09-23T16:00:00Z").toEpochMilli(), day, zone))
        assertFalse(UsageDay.isBucketForDate(Instant.parse("2026-09-24T16:00:00Z").toEpochMilli(), day, zone))
    }

    @Test
    fun localDayComparisonAlsoWorksAcrossDst() {
        val zone = ZoneId.of("America/Los_Angeles")
        val day = LocalDate.of(2026, 3, 8)
        assertTrue(UsageDay.isBucketForDate(day.atStartOfDay(zone).toInstant().toEpochMilli(), day, zone))
        assertFalse(UsageDay.isBucketForDate(day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), day, zone))
    }

    @Test
    fun dailyTotalsIgnoreExpandedNeighborsAndMergeSameDaySlices() {
        val zone = ZoneId.of("Asia/Shanghai")
        val day = LocalDate.of(2026, 9, 24)
        val yesterday = UsageDay.start(day.minusDays(1), zone).toEpochMilli()
        val today = UsageDay.start(day, zone).toEpochMilli()
        val tomorrow = UsageDay.nextStart(day, zone).toEpochMilli()
        val totals = UsageDay.totalsForDate(
            listOf(
                DailyUsageBucket("chat", yesterday, 120_000L),
                DailyUsageBucket("chat", today, 60_000L),
                DailyUsageBucket("chat", today, 30_000L),
                DailyUsageBucket("video", today, 45_000L),
                DailyUsageBucket("chat", tomorrow, 300_000L),
                DailyUsageBucket("empty", today, 0L),
            ),
            day,
            zone,
        )
        assertEquals(mapOf("chat" to 90_000L, "video" to 45_000L), totals)
    }
}
