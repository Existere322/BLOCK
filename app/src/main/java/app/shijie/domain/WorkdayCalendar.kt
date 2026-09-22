package app.shijie.domain

import java.time.DayOfWeek
import java.time.LocalDate

class WorkdayCalendar(
    private val overrides: Map<LocalDate, Boolean> = emptyMap(),
) {
    fun isLegalWorkday(date: LocalDate): Boolean = status(date).workday

    fun status(date: LocalDate): WorkdayStatus {
        val uncovered = date.year !in ChinaOfficialCalendar.coveredYears
        overrides[date]?.let { forced ->
            return WorkdayStatus(
                workday = forced,
                uncoveredYear = uncovered,
                source = WorkdaySource.USER_OVERRIDE,
            )
        }
        if (uncovered) {
            return WorkdayStatus(
                workday = date.isWeekday(),
                uncoveredYear = true,
                source = WorkdaySource.WEEKDAY_FALLBACK,
            )
        }
        val official = when {
            date in ChinaOfficialCalendar.restDays -> false
            date in ChinaOfficialCalendar.extraWorkdays -> true
            else -> date.isWeekday()
        }
        return WorkdayStatus(
            workday = official,
            uncoveredYear = false,
            source = WorkdaySource.OFFICIAL,
        )
    }

    private fun LocalDate.isWeekday(): Boolean {
        return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY
    }
}
