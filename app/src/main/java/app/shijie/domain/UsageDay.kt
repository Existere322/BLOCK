package app.shijie.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DailyUsageBucket(
    val packageName: String,
    val firstTimeStamp: Long,
    val foregroundMillis: Long,
)

object UsageDay {
    fun localDate(now: Instant, zone: ZoneId): LocalDate = now.atZone(zone).toLocalDate()

    fun isBucketForDate(firstTimeStamp: Long, date: LocalDate, zone: ZoneId): Boolean =
        localDate(Instant.ofEpochMilli(firstTimeStamp), zone) == date

    fun totalsForDate(buckets: List<DailyUsageBucket>, date: LocalDate, zone: ZoneId): Map<String, Long> {
        val totals = HashMap<String, Long>()
        buckets.forEach { bucket ->
            if (!isBucketForDate(bucket.firstTimeStamp, date, zone) || bucket.foregroundMillis <= 0L) return@forEach
            totals[bucket.packageName] = (totals[bucket.packageName] ?: 0L) + bucket.foregroundMillis
        }
        return totals
    }

    fun start(date: LocalDate, zone: ZoneId): Instant = date.atStartOfDay(zone).toInstant()

    fun nextStart(date: LocalDate, zone: ZoneId): Instant = date.plusDays(1).atStartOfDay(zone).toInstant()
}

object Retention {
    const val DAYS = 30

    fun oldestKeptDate(today: LocalDate): LocalDate = today.minusDays((DAYS - 1).toLong())
}
