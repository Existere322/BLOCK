package app.shijie.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object UsageDay {
    fun localDate(now: Instant, zone: ZoneId): LocalDate = now.atZone(zone).toLocalDate()

    fun start(date: LocalDate, zone: ZoneId): Instant = date.atStartOfDay(zone).toInstant()

    fun nextStart(date: LocalDate, zone: ZoneId): Instant = date.plusDays(1).atStartOfDay(zone).toInstant()
}

object Retention {
    const val DAYS = 30

    fun oldestKeptDate(today: LocalDate): LocalDate = today.minusDays((DAYS - 1).toLong())
}
