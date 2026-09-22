package app.shijie.domain

import java.time.Duration
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class DayPolicy {
    EVERY_DAY,
    LEGAL_WORKDAY,
    CUSTOM_WEEKDAYS,
}

enum class BlockReason {
    BLOCK_WINDOW,
    QUOTA_EXHAUSTED,
}

sealed interface BlockDecision {
    data object Allow : BlockDecision

    data class Block(
        val reason: BlockReason,
        val groupId: Long,
        val groupName: String,
        val endsAt: Instant?,
    ) : BlockDecision
}

data class BlockWindow(
    val startMinute: Int,
    val endMinute: Int,
) {
    fun isValid(): Boolean {
        return startMinute in 0..1439 && endMinute in 0..1440 && startMinute != endMinute
    }

    fun contains(minuteOfDay: Int): Boolean {
        if (!isValid() || minuteOfDay !in 0..1439) return false
        return if (startMinute < endMinute) {
            minuteOfDay in startMinute until endMinute
        } else {
            minuteOfDay >= startMinute || minuteOfDay < endMinute
        }
    }
}

data class RestrictionGroup(
    val id: Long,
    val name: String,
    val colorArgb: Int,
    val iconKey: String,
    val enabled: Boolean,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val dayPolicy: DayPolicy,
    val customWeekdays: Set<DayOfWeek>,
    val blockWindows: List<BlockWindow>,
    val dailyQuota: Duration,
)

data class GroupApp(
    val packageName: String,
    val groupId: Long,
)

data class DailyUsage(
    val date: LocalDate,
    val packageName: String,
    val foreground: Duration,
)

data class WorkdayOverride(
    val date: LocalDate,
    val workday: Boolean,
)

data class TemporaryOverride(
    val groupId: Long,
    val packageName: String,
    val reason: String,
    val grantedAt: Instant,
    val expiresAt: Instant,
    val cancelledAt: Instant?,
    val bootId: String,
)

data class OpenSession(
    val packageName: String,
    val since: Instant,
)

data class GroupUsage(
    val group: RestrictionGroup,
    val usedMillis: Long,
    val temporaryOverride: TemporaryOverride?,
    val zone: ZoneId,
    val isSafetyWhitelisted: Boolean,
    val bootId: String,
)

enum class WorkdaySource {
    USER_OVERRIDE,
    OFFICIAL,
    WEEKDAY_FALLBACK,
}

data class WorkdayStatus(
    val workday: Boolean,
    val uncoveredYear: Boolean,
    val source: WorkdaySource,
)

fun Set<DayOfWeek>.toWeekdayMask(): Int {
    return fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }
}

fun Int.toWeekdays(): Set<DayOfWeek> {
    return DayOfWeek.entries.filter { day -> this and (1 shl (day.value - 1)) != 0 }.toSet()
}
