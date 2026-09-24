package app.shijie.domain

import java.io.File
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowMergerTest {
    @Test
    fun mergesOverlapAndAdjacentWindows() {
        val merged = WindowMerger.merge(
            listOf(
                BlockWindow(9 * 60, 12 * 60),
                BlockWindow(11 * 60, 13 * 60),
                BlockWindow(14 * 60, 15 * 60),
                BlockWindow(15 * 60, 16 * 60),
            ),
        )
        assertEquals(
            listOf(BlockWindow(9 * 60, 13 * 60), BlockWindow(14 * 60, 16 * 60)),
            merged,
        )
    }

    @Test
    fun mergesContainedWindow() {
        assertEquals(
            listOf(BlockWindow(8 * 60, 12 * 60)),
            WindowMerger.merge(listOf(BlockWindow(8 * 60, 12 * 60), BlockWindow(9 * 60, 10 * 60))),
        )
    }

    @Test
    fun mergesCrossMidnightOverlap() {
        assertEquals(
            listOf(BlockWindow(22 * 60, 3 * 60)),
            WindowMerger.merge(listOf(BlockWindow(22 * 60, 2 * 60), BlockWindow(1 * 60, 3 * 60))),
        )
    }

    @Test
    fun keepsDisjointCrossMidnightAndDaytime() {
        assertEquals(
            listOf(BlockWindow(1 * 60, 2 * 60), BlockWindow(22 * 60, 23 * 60)),
            WindowMerger.merge(listOf(BlockWindow(22 * 60, 23 * 60), BlockWindow(1 * 60, 2 * 60))),
        )
    }

    @Test
    fun dropsInvalidWindows() {
        assertTrue(WindowMerger.merge(listOf(BlockWindow(10, 10), BlockWindow(-1, 20))).isEmpty())
    }
}

class WorkdayCalendarTest {
    private val calendar = WorkdayCalendar()

    @Test
    fun officialRestAndMakeupDays() {
        assertFalse(calendar.isLegalWorkday(LocalDate.of(2025, 1, 1)))
        assertFalse(calendar.isLegalWorkday(LocalDate.of(2025, 2, 1)))
        assertTrue(calendar.isLegalWorkday(LocalDate.of(2025, 1, 26)))
        assertTrue(calendar.isLegalWorkday(LocalDate.of(2025, 2, 8)))
        assertTrue(calendar.isLegalWorkday(LocalDate.of(2025, 4, 27)))
        assertFalse(calendar.isLegalWorkday(LocalDate.of(2025, 5, 1)))
        assertFalse(calendar.isLegalWorkday(LocalDate.of(2025, 6, 2)))
        assertTrue(calendar.isLegalWorkday(LocalDate.of(2025, 9, 28)))
        assertFalse(calendar.isLegalWorkday(LocalDate.of(2025, 10, 8)))
        assertTrue(calendar.isLegalWorkday(LocalDate.of(2025, 10, 11)))
        assertTrue(calendar.isLegalWorkday(LocalDate.of(2025, 3, 5)))
        assertFalse(calendar.isLegalWorkday(LocalDate.of(2025, 3, 8)))

        assertFalse(calendar.isLegalWorkday(LocalDate.of(2026, 1, 1)))
        assertTrue(calendar.isLegalWorkday(LocalDate.of(2026, 1, 4)))
        assertTrue(calendar.isLegalWorkday(LocalDate.of(2026, 2, 14)))
        assertFalse(calendar.isLegalWorkday(LocalDate.of(2026, 2, 15)))
        assertFalse(calendar.isLegalWorkday(LocalDate.of(2026, 2, 16)))
        assertTrue(calendar.isLegalWorkday(LocalDate.of(2026, 2, 28)))
        assertFalse(calendar.isLegalWorkday(LocalDate.of(2026, 4, 5)))
        assertFalse(calendar.isLegalWorkday(LocalDate.of(2026, 5, 1)))
        assertTrue(calendar.isLegalWorkday(LocalDate.of(2026, 5, 9)))
        assertFalse(calendar.isLegalWorkday(LocalDate.of(2026, 6, 20)))
        assertTrue(calendar.isLegalWorkday(LocalDate.of(2026, 9, 20)))
        assertFalse(calendar.isLegalWorkday(LocalDate.of(2026, 9, 26)))
        assertFalse(calendar.isLegalWorkday(LocalDate.of(2026, 10, 1)))
        assertTrue(calendar.isLegalWorkday(LocalDate.of(2026, 10, 10)))
        assertTrue(calendar.isLegalWorkday(LocalDate.of(2026, 3, 4)))
        assertFalse(calendar.isLegalWorkday(LocalDate.of(2026, 3, 7)))
    }

    @Test
    fun officialSetsDoNotOverlapAndMatchAnnouncedLengths() {
        assertTrue(ChinaOfficialCalendar.restDays.intersect(ChinaOfficialCalendar.extraWorkdays).isEmpty())
        assertEquals(28, ChinaOfficialCalendar.restDays.count { it.year == 2025 })
        assertEquals(5, ChinaOfficialCalendar.extraWorkdays.count { it.year == 2025 })
        assertEquals(33, ChinaOfficialCalendar.restDays.count { it.year == 2026 })
        assertEquals(6, ChinaOfficialCalendar.extraWorkdays.count { it.year == 2026 })
    }

    @Test
    fun userOverrideWins() {
        val overridden = WorkdayCalendar(mapOf(LocalDate.of(2026, 2, 14) to false))
        val status = overridden.status(LocalDate.of(2026, 2, 14))
        assertFalse(status.workday)
        assertEquals(WorkdaySource.USER_OVERRIDE, status.source)
        assertFalse(status.uncoveredYear)
    }

    @Test
    fun uncoveredYearFallsBackToMondayThroughFridayAndWarns() {
        val monday = calendar.status(LocalDate.of(2027, 1, 4))
        val saturday = calendar.status(LocalDate.of(2027, 1, 2))
        assertTrue(monday.workday)
        assertTrue(monday.uncoveredYear)
        assertEquals(WorkdaySource.WEEKDAY_FALLBACK, monday.source)
        assertFalse(saturday.workday)
        assertTrue(saturday.uncoveredYear)
        val forced = WorkdayCalendar(mapOf(LocalDate.of(2027, 1, 2) to true))
        val forcedStatus = forced.status(LocalDate.of(2027, 1, 2))
        assertTrue(forcedStatus.workday)
        assertTrue(forcedStatus.uncoveredYear)
    }
}

class RuleEvaluatorTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val calendar = WorkdayCalendar()
    private val evaluator = RuleEvaluator(calendar)

    @Test
    fun allowsWhenUngroupedDisabledOrWhitelisted() {
        val blocking = sample(windows = listOf(BlockWindow(0, 24 * 60)))
        assertTrue(evaluator.evaluate("app.game", instant("2026-03-04T10:00:00"), null) is BlockDecision.Allow)
        assertTrue(
            evaluator.evaluate(
                "app.game",
                instant("2026-03-04T10:00:00"),
                usage(blocking.copy(enabled = false), used = blocking.dailyQuota.toMillis()),
            ) is BlockDecision.Allow,
        )
        assertTrue(
            evaluator.evaluate(
                "com.android.settings",
                instant("2026-03-04T10:00:00"),
                usage(blocking, safety = true, used = blocking.dailyQuota.toMillis()),
            ) is BlockDecision.Allow,
        )
    }

    @Test
    fun dateRangeIsInclusive() {
        val group = sample(
            start = LocalDate.of(2026, 9, 22),
            end = LocalDate.of(2026, 9, 22),
            windows = listOf(BlockWindow(0, 23 * 60)),
        )
        assertTrue(evaluator.evaluate("app.game", instant("2026-09-21T10:00:00"), usage(group)) is BlockDecision.Allow)
        val onDay = evaluator.evaluate("app.game", instant("2026-09-22T10:00:00"), usage(group))
        assertEquals(BlockReason.BLOCK_WINDOW, (onDay as BlockDecision.Block).reason)
        assertTrue(evaluator.evaluate("app.game", instant("2026-09-23T10:00:00"), usage(group)) is BlockDecision.Allow)
    }

    @Test
    fun crossMidnightAndMergedOverlap() {
        val overnight = sample(windows = listOf(BlockWindow(23 * 60, 60)))
        assertEquals(BlockReason.BLOCK_WINDOW, reasonAt(overnight, "2026-03-04T23:30:00"))
        assertEquals(BlockReason.BLOCK_WINDOW, reasonAt(overnight, "2026-03-05T00:30:00"))
        assertTrue(evaluator.evaluate("app.game", instant("2026-03-05T01:00:00"), usage(overnight)) is BlockDecision.Allow)
        assertTrue(evaluator.evaluate("app.game", instant("2026-03-04T22:59:00"), usage(overnight)) is BlockDecision.Allow)

        val overlap = sample(windows = listOf(BlockWindow(9 * 60, 12 * 60), BlockWindow(11 * 60, 13 * 60)))
        assertEquals(BlockReason.BLOCK_WINDOW, reasonAt(overlap, "2026-03-04T12:30:00"))
        assertTrue(evaluator.evaluate("app.game", instant("2026-03-04T13:00:00"), usage(overlap)) is BlockDecision.Allow)
    }

    @Test
    fun legalWorkdaySkipsHolidaysAndKeepsMakeupDays() {
        val group = sample(policy = DayPolicy.LEGAL_WORKDAY, windows = listOf(BlockWindow(9 * 60, 18 * 60)))
        assertTrue(evaluator.evaluate("app.game", instant("2026-02-16T10:00:00"), usage(group)) is BlockDecision.Allow)
        assertTrue(evaluator.evaluate("app.game", instant("2026-01-02T10:00:00"), usage(group)) is BlockDecision.Allow)
        assertEquals(BlockReason.BLOCK_WINDOW, reasonAt(group, "2026-02-14T10:00:00"))
        assertEquals(BlockReason.BLOCK_WINDOW, reasonAt(group, "2026-01-04T10:00:00"))
    }

    @Test
    fun customWeekdaysAndExactQuota() {
        val group = sample(
            policy = DayPolicy.CUSTOM_WEEKDAYS,
            days = setOf(DayOfWeek.WEDNESDAY),
            windows = listOf(BlockWindow(22 * 60, 23 * 60)),
            quotaMinutes = 1,
        )
        assertEquals(BlockReason.BLOCK_WINDOW, reasonAt(group, "2026-03-04T22:10:00"))
        assertTrue(evaluator.evaluate("app.game", instant("2026-03-05T10:00:00"), usage(group, used = 60_000)) is BlockDecision.Allow)
        val quotaGroup = sample(quotaMinutes = 1)
        assertTrue(evaluator.evaluate("app.game", instant("2026-03-04T10:00:00"), usage(quotaGroup, used = 59_999)) is BlockDecision.Allow)
        val exhausted = evaluator.evaluate("app.game", instant("2026-03-04T10:00:00"), usage(quotaGroup, used = 60_000))
        assertEquals(BlockReason.QUOTA_EXHAUSTED, (exhausted as BlockDecision.Block).reason)
        val windowFirst = evaluator.evaluate(
            "app.game",
            instant("2026-03-04T22:10:00"),
            usage(group, used = 60_000),
        )
        assertEquals(BlockReason.BLOCK_WINDOW, (windowFirst as BlockDecision.Block).reason)
    }

    @Test
    fun legalWorkdayOvernightContinuesIntoWeekendMorning() {
        // Friday night → Saturday morning must stay blocked when policy is legal workday.
        val group = sample(policy = DayPolicy.LEGAL_WORKDAY, windows = listOf(BlockWindow(23 * 60, 11 * 60)))
        assertEquals(BlockReason.BLOCK_WINDOW, reasonAt(group, "2026-03-06T23:30:00")) // Friday
        assertEquals(BlockReason.BLOCK_WINDOW, reasonAt(group, "2026-03-07T10:30:00")) // Saturday morning
        assertTrue(evaluator.evaluate("app.game", instant("2026-03-07T12:00:00"), usage(group)) is BlockDecision.Allow)
        assertTrue(evaluator.evaluate("app.game", instant("2026-03-07T23:30:00"), usage(group)) is BlockDecision.Allow)
    }

    @Test
    fun temporaryReleaseBypassesWindowAndQuotaUntilExpiryOrReboot() {
        val group = sample(windows = listOf(BlockWindow(0, 24 * 60)), quotaMinutes = 1)
        val now = instant("2026-03-04T10:00:00")
        val active = release(now.minusSeconds(60), now.plusSeconds(60))
        assertTrue(evaluator.evaluate("app.game", now, usage(group, used = 60_000, release = active)) is BlockDecision.Allow)
        val expired = active.copy(expiresAt = now)
        assertEquals(BlockReason.BLOCK_WINDOW, (evaluator.evaluate("app.game", now, usage(group, release = expired)) as BlockDecision.Block).reason)
        val otherBoot = active.copy(bootId = "boot-2")
        assertTrue(evaluator.evaluate("app.game", now, usage(group, release = otherBoot)) is BlockDecision.Block)
        val otherApp = active.copy(packageName = "app.other")
        assertTrue(evaluator.evaluate("app.game", now, usage(group, release = otherApp)) is BlockDecision.Block)
    }

    @Test
    fun timeZoneChangesWhetherTheSameInstantIsInsideTheWindow() {
        val group = sample(windows = listOf(BlockWindow(30, 60)))
        val now = Instant.parse("2026-01-04T16:30:00Z")
        val shanghai = evaluator.evaluate("app.game", now, usage(group, zone = ZoneId.of("Asia/Shanghai")))
        val newYork = evaluator.evaluate("app.game", now, usage(group, zone = ZoneId.of("America/New_York")))
        assertEquals(BlockReason.BLOCK_WINDOW, (shanghai as BlockDecision.Block).reason)
        assertTrue(newYork is BlockDecision.Allow)
    }

    private fun reasonAt(group: RestrictionGroup, local: String): BlockReason {
        return (evaluator.evaluate("app.game", instant(local), usage(group)) as BlockDecision.Block).reason
    }

    private fun instant(local: String): Instant = LocalDateTime.parse(local).atZone(zone).toInstant()

    private fun sample(
        policy: DayPolicy = DayPolicy.EVERY_DAY,
        windows: List<BlockWindow> = emptyList(),
        quotaMinutes: Long = 120,
        start: LocalDate? = null,
        end: LocalDate? = null,
        days: Set<DayOfWeek> = emptySet(),
    ) = RestrictionGroup(
        id = 7,
        name = "学习",
        colorArgb = 0,
        iconKey = "book",
        enabled = true,
        startDate = start,
        endDate = end,
        dayPolicy = policy,
        customWeekdays = days,
        blockWindows = windows,
        dailyQuota = Duration.ofMinutes(quotaMinutes),
    )

    private fun usage(
        group: RestrictionGroup,
        used: Long = 0,
        zone: ZoneId = this.zone,
        safety: Boolean = false,
        release: TemporaryOverride? = null,
    ) = GroupUsage(group, used, release, zone, safety, "boot-1")

    private fun release(granted: Instant, expires: Instant) = TemporaryOverride(
        groupId = 7,
        packageName = "app.game",
        reason = "接电话后要回消息",
        grantedAt = granted,
        expiresAt = expires,
        cancelledAt = null,
        bootId = "boot-1",
    )
}

class EmergencyReleaseTest {
    @Test
    fun requiresReasonWaitAndOneGrantPerGroupEachLocalDay() {
        val start = Instant.parse("2026-03-04T02:00:00Z")
        assertEquals("理由不能为空", EmergencyRelease.rejection("  ", start, start.plusSeconds(30), 0))
        assertEquals("请在拦截页等待 30 秒", EmergencyRelease.rejection("取快递", start, start.plusSeconds(29), 0))
        assertNull(EmergencyRelease.rejection("取快递", start, start.plusSeconds(30), 0))
        assertEquals("本组今日已使用应急放行", EmergencyRelease.rejection("再来一次", start, start.plusSeconds(30), 1))
        assertNull(EmergencyRelease.rejection("另一组", start, start.plusSeconds(30), 0))
        assertEquals(start.plus(EmergencyRelease.duration), EmergencyRelease.expiresAt(start))
        assertEquals(5, EmergencyRelease.duration.toMinutes())
    }

    @Test
    fun rebootAndCancellationEndTheReleaseImmediately() {
        val now = Instant.parse("2026-03-04T03:00:00Z")
        val release = TemporaryOverride(1, "app.game", "原因", now.minusSeconds(10), now.plusSeconds(100), null, "boot-1")
        assertTrue(EmergencyRelease.isCurrentlyActive(release, now, "boot-1", "app.game"))
        assertFalse(EmergencyRelease.isCurrentlyActive(release, now, "boot-2", "app.game"))
        assertFalse(EmergencyRelease.isCurrentlyActive(release.copy(cancelledAt = now.minusSeconds(1)), now, "boot-1", "app.game"))
    }
}

class UsageAndQuotaTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun localDateFollowsZoneAndManualClockChanges() {
        val evening = Instant.parse("2026-03-01T15:50:00Z")
        val afterMidnight = evening.plusSeconds(20 * 60)
        assertEquals(LocalDate.of(2026, 3, 1), UsageDay.localDate(evening, zone))
        assertEquals(LocalDate.of(2026, 3, 2), UsageDay.localDate(afterMidnight, zone))
        assertEquals(LocalDate.of(2026, 3, 1), UsageDay.localDate(evening, ZoneId.of("America/New_York")))
        val jumpedBack = afterMidnight.minusSeconds(3 * 3600)
        assertEquals(LocalDate.of(2026, 3, 1), UsageDay.localDate(jumpedBack, zone))
    }

    @Test
    fun quotaIgnoresBlockWindowsUnlessAReleaseCoversThem() {
        val day = LocalDate.of(2026, 3, 4)
        val span = span("app.game", "2026-03-04T10:00:00", "2026-03-04T11:00:00")
        assertEquals(60 * 60_000L, count(listOf(span), emptyList(), emptyList()))
        assertEquals(45 * 60_000L, count(listOf(span), listOf(BlockWindow(10 * 60, 10 * 60 + 15)), emptyList()))
        assertEquals(0L, count(listOf(span), listOf(BlockWindow(10 * 60, 11 * 60)), emptyList()))
        val release = TemporaryOverride(
            1, "app.game", "原因",
            instant("2026-03-04T10:00:00"),
            instant("2026-03-04T10:20:00"),
            null, "boot",
        )
        assertEquals(20 * 60_000L, count(listOf(span), listOf(BlockWindow(10 * 60, 11 * 60)), listOf(release)))
        val other = release.copy(packageName = "app.other")
        assertEquals(0L, count(listOf(span), listOf(BlockWindow(10 * 60, 11 * 60)), listOf(other)))
        val longSpan = span("app.game", "2026-03-04T10:00:00", "2026-03-04T13:00:00")
        assertEquals(
            60 * 60_000L,
            count(listOf(longSpan), listOf(BlockWindow(10 * 60, 11 * 60 + 30), BlockWindow(11 * 60, 12 * 60)), emptyList()),
        )
        val overnight = span("app.game", "2026-03-04T23:00:00", "2026-03-05T01:00:00")
        assertEquals(120 * 60_000L, count(listOf(overnight), emptyList(), emptyList()))
        assertEquals(0L, count(listOf(overnight), listOf(BlockWindow(23 * 60, 60)), emptyList()))
        assertEquals(day, day)
    }

    @Test
    fun foregroundSpansClampToTheRequestedRange() {
        val start = instant("2026-03-04T10:00:00")
        val end = instant("2026-03-04T11:00:00")
        val events = listOf(
            ForegroundEvent("app.game", instant("2026-03-04T09:00:00"), ForegroundEventKind.RESUME),
            ForegroundEvent("app.game", instant("2026-03-04T10:20:00"), ForegroundEventKind.PAUSE),
            ForegroundEvent("app.video", instant("2026-03-04T10:20:00"), ForegroundEventKind.RESUME),
        )
        val spans = ForegroundSpans.collect(events, start, end)
        assertEquals(2, spans.size)
        assertEquals(20 * 60_000L, Duration.between(spans[0].start, spans[0].end).toMillis())
        assertEquals("app.video", spans[1].packageName)
        assertEquals(end, spans[1].end)
    }

    @Test
    fun screenOffClosesTheOpenSession() {
        val start = instant("2026-03-04T00:00:00")
        val end = instant("2026-03-04T02:00:00")
        val events = listOf(
            ForegroundEvent("app.game", instant("2026-03-03T23:30:00"), ForegroundEventKind.RESUME),
            ForegroundEvent("", instant("2026-03-04T00:10:00"), ForegroundEventKind.SCREEN_OFF),
        )
        val spans = ForegroundSpans.collect(events, start, end)
        assertEquals(1, spans.size)
        assertEquals(10 * 60_000L, Duration.between(spans[0].start, spans[0].end).toMillis())
    }

    @Test
    fun missingScreenEventsKeepCountingUntilPause() {
        val start = instant("2026-03-04T00:00:00")
        val end = instant("2026-03-04T01:00:00")
        val events = listOf(
            ForegroundEvent("app.game", instant("2026-03-03T23:40:00"), ForegroundEventKind.RESUME),
            ForegroundEvent("app.game", instant("2026-03-04T00:20:00"), ForegroundEventKind.PAUSE),
        )
        val spans = ForegroundSpans.collect(events, start, end)
        assertEquals(1, spans.size)
        assertEquals(20 * 60_000L, Duration.between(spans[0].start, spans[0].end).toMillis())
    }

    @Test
    fun stoppingTheLeftActivityDoesNotCutTheOneThatReplacedIt() {
        val start = instant("2026-03-04T10:00:00")
        val end = instant("2026-03-04T11:00:00")
        val events = listOf(
            ForegroundEvent("app.game", instant("2026-03-04T10:00:00"), ForegroundEventKind.RESUME, 1),
            ForegroundEvent("app.game", instant("2026-03-04T10:01:00"), ForegroundEventKind.PAUSE, 1),
            ForegroundEvent("app.game", instant("2026-03-04T10:01:00"), ForegroundEventKind.RESUME, 2),
            ForegroundEvent("app.game", instant("2026-03-04T10:02:00"), ForegroundEventKind.STOP, 1),
            ForegroundEvent("app.game", instant("2026-03-04T10:30:00"), ForegroundEventKind.PAUSE, 2),
        )
        val spans = ForegroundSpans.collect(events, start, end)
        assertEquals(30 * 60_000L, spans.sumOf { Duration.between(it.start, it.end).toMillis() })
        assertEquals(
            "app.game",
            ForegroundSpans.foregroundPackage(events, instant("2026-03-04T10:03:00")),
        )
    }

    @Test
    fun overlappingResumedActivitiesCountOnce() {
        val start = instant("2026-03-04T10:00:00")
        val end = instant("2026-03-04T11:00:00")
        val events = listOf(
            ForegroundEvent("app.game", instant("2026-03-04T10:00:00"), ForegroundEventKind.RESUME, 1),
            ForegroundEvent("app.game", instant("2026-03-04T10:10:00"), ForegroundEventKind.RESUME, 2),
            ForegroundEvent("app.game", instant("2026-03-04T10:20:00"), ForegroundEventKind.PAUSE, 1),
            ForegroundEvent("app.game", instant("2026-03-04T10:30:00"), ForegroundEventKind.PAUSE, 2),
        )
        val spans = ForegroundSpans.collect(events, start, end)
        assertEquals(30 * 60_000L, spans.sumOf { Duration.between(it.start, it.end).toMillis() })
    }

    @Test
    fun stopAfterASwitchDoesNotCutTheCurrentScreen() {
        val start = instant("2026-03-04T10:00:00")
        val end = instant("2026-03-04T11:00:00")
        val events = listOf(
            ForegroundEvent("app.game", instant("2026-03-04T10:00:00"), ForegroundEventKind.RESUME),
            ForegroundEvent("app.game", instant("2026-03-04T10:20:00"), ForegroundEventKind.RESUME),
            ForegroundEvent("app.game", instant("2026-03-04T10:20:01"), ForegroundEventKind.PAUSE),
            ForegroundEvent("app.game", instant("2026-03-04T10:20:02"), ForegroundEventKind.STOP),
            ForegroundEvent("app.game", instant("2026-03-04T10:50:00"), ForegroundEventKind.PAUSE),
        )
        val spans = ForegroundSpans.collect(events, start, end)
        assertEquals(50 * 60_000L, spans.sumOf { Duration.between(it.start, it.end).toMillis() })
        assertEquals("app.game", ForegroundSpans.foregroundPackage(events, instant("2026-03-04T10:21:00")))
    }

    @Test
    fun resumeCountsEvenWhenTheScreenFlagWasStuckOff() {
        val start = instant("2026-03-04T10:00:00")
        val end = instant("2026-03-04T11:00:00")
        val events = listOf(
            ForegroundEvent("", instant("2026-03-04T09:00:00"), ForegroundEventKind.SCREEN_OFF),
            ForegroundEvent("app.game", instant("2026-03-04T10:00:00"), ForegroundEventKind.RESUME),
            ForegroundEvent("app.game", instant("2026-03-04T10:40:00"), ForegroundEventKind.PAUSE),
        )
        val spans = ForegroundSpans.collect(events, start, end)
        assertEquals(40 * 60_000L, spans.sumOf { Duration.between(it.start, it.end).toMillis() })
    }

    @Test
    fun stopAloneDoesNotEndTheOpenSession() {
        val start = instant("2026-03-04T10:00:00")
        val end = instant("2026-03-04T11:00:00")
        val events = listOf(
            ForegroundEvent("app.game", instant("2026-03-04T10:00:00"), ForegroundEventKind.RESUME, 1),
            ForegroundEvent("app.game", instant("2026-03-04T10:15:00"), ForegroundEventKind.STOP, 1),
        )
        val spans = ForegroundSpans.collect(events, start, end)
        assertEquals(60 * 60_000L, spans.sumOf { Duration.between(it.start, it.end).toMillis() })
        assertEquals("app.game", ForegroundSpans.foregroundPackage(events, instant("2026-03-04T10:16:00")))
    }

    @Test
    fun screenOnResumesCountingWhenTheActivityStayedResumed() {
        val start = instant("2026-03-04T00:00:00")
        val end = instant("2026-03-04T02:00:00")
        val events = listOf(
            ForegroundEvent("app.game", instant("2026-03-03T23:30:00"), ForegroundEventKind.RESUME),
            ForegroundEvent("", instant("2026-03-04T00:10:00"), ForegroundEventKind.SCREEN_OFF),
            ForegroundEvent("", instant("2026-03-04T00:40:00"), ForegroundEventKind.SCREEN_ON),
            ForegroundEvent("app.game", instant("2026-03-04T01:00:00"), ForegroundEventKind.PAUSE),
        )
        val spans = ForegroundSpans.collect(events, start, end)
        assertEquals(30 * 60_000L, spans.sumOf { Duration.between(it.start, it.end).toMillis() })
    }

    @Test
    fun shutdownClosesForegroundTime() {
        val start = instant("2026-03-04T10:00:00")
        val end = instant("2026-03-04T11:00:00")
        val events = listOf(
            ForegroundEvent("app.game", instant("2026-03-04T10:00:00"), ForegroundEventKind.RESUME),
            ForegroundEvent("", instant("2026-03-04T10:10:00"), ForegroundEventKind.SHUTDOWN),
        )
        val spans = ForegroundSpans.collect(events, start, end)
        assertEquals(10 * 60_000L, spans.sumOf { Duration.between(it.start, it.end).toMillis() })
    }

    @Test
    fun plannerPicksTheSoonerOfQuotaAndWindowAndSkipsHolidays() {
        val calendar = WorkdayCalendar()
        val plain = sample(quotaMinutes = 10)
        val now = instant("2026-03-04T08:00:00")
        assertEquals(10 * 60_000L, TransitionPlanner.millisUntilEnforcement(now, zone, plain, 0, calendar, null))
        val soon = sample(windows = listOf(BlockWindow(8 * 60 + 3, 9 * 60)), quotaMinutes = 10)
        assertEquals(3 * 60_000L, TransitionPlanner.millisUntilEnforcement(now, zone, soon, 0, calendar, null))
        val inside = sample(windows = listOf(BlockWindow(8 * 60, 9 * 60)))
        assertEquals(0L, TransitionPlanner.millisUntilEnforcement(now, zone, inside, 0, calendar, null))
        val releaseEnd = now.plusSeconds(5 * 60)
        assertEquals(5 * 60_000L, TransitionPlanner.millisUntilEnforcement(now, zone, inside, 999_999, calendar, releaseEnd))

        val workday = sample(policy = DayPolicy.LEGAL_WORKDAY, windows = listOf(BlockWindow(9 * 60, 10 * 60)))
        val holiday = instant("2026-02-16T08:00:00")
        val holidayDelay = TransitionPlanner.millisUntilEnforcement(holiday, zone, workday, 0, calendar, null)
        assertTrue(holidayDelay != null && holidayDelay > Duration.ofHours(24).toMillis())
        val makeup = instant("2026-02-14T08:00:00")
        assertEquals(60 * 60_000L, TransitionPlanner.millisUntilEnforcement(makeup, zone, workday, 0, calendar, null))
        val disabled = workday.copy(enabled = false)
        assertNull(TransitionPlanner.millisUntilEnforcement(makeup, zone, disabled, 0, calendar, null))
    }

    private fun count(
        spans: List<ForegroundSpan>,
        windows: List<BlockWindow>,
        releases: List<TemporaryOverride>,
    ) = QuotaAccounting.countedMillis(spans, zone, windows, releases)

    private fun span(pkg: String, start: String, end: String) = ForegroundSpan(pkg, instant(start), instant(end))

    private fun instant(local: String) = LocalDateTime.parse(local).atZone(zone).toInstant()

    private fun sample(
        policy: DayPolicy = DayPolicy.EVERY_DAY,
        windows: List<BlockWindow> = emptyList(),
        quotaMinutes: Long = 60,
    ) = RestrictionGroup(
        id = 1,
        name = "学习",
        colorArgb = 0,
        iconKey = "book",
        enabled = true,
        startDate = null,
        endDate = null,
        dayPolicy = policy,
        customWeekdays = emptySet(),
        blockWindows = windows,
        dailyQuota = Duration.ofMinutes(quotaMinutes),
    )
}

class SafetyAndSourcePolicyTest {
    @Test
    fun criticalPackagesCannotBeRestricted() {
        val homes = setOf("com.example.launcher")
        assertTrue(SafetyPackages.isSafety("com.android.systemui", "app.shijie"))
        assertTrue(SafetyPackages.isSafety("com.android.settings", "app.shijie"))
        assertTrue(SafetyPackages.isSafety("app.shijie", "app.shijie"))
        assertTrue(SafetyPackages.isSafety("com.android.dialer", "app.shijie", dialerPackage = "com.vendor.dialer"))
        assertTrue(SafetyPackages.isSafety("com.vendor.dialer", "app.shijie", dialerPackage = "com.vendor.dialer"))
        assertTrue(SafetyPackages.isSafety("com.android.packageinstaller", "app.shijie"))
        assertTrue(SafetyPackages.isSafety("com.example.launcher", "app.shijie", homePackages = homes))
        assertTrue(SafetyPackages.isSafety("com.foo.launcher", "app.shijie"))
        assertFalse(SafetyPackages.isSafety("app.game", "app.shijie", homePackages = homes))
    }

    @Test
    fun manifestAndSourcesDoNotEnableNetworkPollingOrForegroundService() {
        val root = mainRoot()
        val manifest = root.resolve("AndroidManifest.xml").readText()
        val operational = manifest.lineSequence().filterNot { it.contains("tools:node=\"remove\"") }.joinToString("\n")
        assertFalse(operational.contains("android.permission.INTERNET"))
        assertFalse(operational.contains("QUERY_ALL_PACKAGES"))
        assertFalse(operational.contains("SYSTEM_ALERT_WINDOW"))
        assertFalse(operational.contains("ACCESS_FINE_LOCATION"))
        assertFalse(operational.contains("READ_CONTACTS"))
        val sources = root.walkTopDown().filter { it.extension == "kt" || it.extension == "xml" }.joinToString("\n") { it.readText() }
        assertFalse(sources.contains("startForeground("))
        assertFalse(sources.contains("WakeLock"))
        assertFalse(sources.contains("PARTIAL_WAKE_LOCK"))
        assertFalse(sources.contains("scheduleAtFixedRate"))
        assertFalse(sources.contains("scheduleWithFixedDelay"))
    }

    private fun mainRoot(): File {
        val direct = File("src/main")
        if (direct.exists()) return direct
        val nested = File("app/src/main")
        if (nested.exists()) return nested
        error("找不到 src/main")
    }
}
