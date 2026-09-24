package app.shijie.domain

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HourAlignmentTest {
    @Test
    fun scalesPartialEventsUpToTheSystemTotal() {
        val buckets = listOf(
            mapOf("game.app" to 10L),
            mapOf("game.app" to 30L),
        )
        val scaled = HourAlignment.scale(buckets, mapOf("game.app" to 80L), fallbackHour = 0)
        assertEquals(20L, scaled[0]["game.app"])
        assertEquals(60L, scaled[1]["game.app"])
    }

    @Test
    fun placesAppsWithoutEventsOnTheFallbackHour() {
        val scaled = HourAlignment.scale(
            buckets = listOf(emptyMap(), emptyMap(), emptyMap()),
            totals = mapOf("game.app" to 50L),
            fallbackHour = 2,
        )
        assertNull(scaled[0]["game.app"])
        assertEquals(50L, scaled[2]["game.app"])
    }
}

class GroupLockTest {
    @Test
    fun nameAndIconCanChangeWhileRulesStay() {
        val before = sample()
        val renamed = before.copy(name = "新名字", iconKey = "game", colorArgb = 2)
        assertNull(GroupLock.rejection(before, renamed, setOf("a"), setOf("a")))
    }

    @Test
    fun quotaWindowAndMembershipStayLocked() {
        val before = sample()
        assertEquals(
            "上锁期间不能修改时间限制或使用时长",
            GroupLock.rejection(before, before.copy(dailyQuota = Duration.ofMinutes(5)), setOf("a"), setOf("a")),
        )
        assertEquals(
            "上锁期间不能调整分组里的应用",
            GroupLock.rejection(before, before, setOf("a"), setOf("a", "b")),
        )
    }

    private fun sample() = RestrictionGroup(
        id = 1,
        name = "娱乐",
        colorArgb = 1,
        iconKey = "book",
        enabled = true,
        startDate = null,
        endDate = null,
        dayPolicy = DayPolicy.EVERY_DAY,
        customWeekdays = emptySet(),
        blockWindows = listOf(BlockWindow(22 * 60, 23 * 60)),
        dailyQuota = Duration.ofMinutes(30),
    )
}

class RestrictionUnionTest {
    @Test
    fun anyBlockingGroupKeepsTheAppBlockedUntilTheLatestEnd() {
        val sooner = Instant.parse("2026-09-24T12:00:00Z")
        val later = Instant.parse("2026-09-24T16:00:00Z")
        val decision = RestrictionUnion.strictest(
            listOf(
                BlockDecision.Allow,
                BlockDecision.Block(BlockReason.BLOCK_WINDOW, 1, "娱乐", sooner),
                BlockDecision.Block(BlockReason.QUOTA_EXHAUSTED, 2, "购物", later),
            ),
        ) as BlockDecision.Block
        assertEquals(2L, decision.groupId)
        assertEquals(later, decision.endsAt)
    }
}
