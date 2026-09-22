package app.shijie.domain

import java.time.LocalDate

/**
 * 中国大陆全体公民放假安排。
 * 2025：国办发明电〔2024〕12号。
 * 2026：国办发明电〔2025〕7号，见国务院公报 2025 年第 32 号。
 */
object ChinaOfficialCalendar {
    const val SOURCE_2025 = "https://www.gov.cn/zhengce/content/202411/content_6986382.htm"
    const val SOURCE_2026 = "https://www.gov.cn/gongbao/2025/issue_12406/202511/content_7048922.html"

    val coveredYears: Set<Int> = setOf(2025, 2026)

    val restDays: Set<LocalDate> = buildSet {
        addAll(range(2025, 1, 1, 2025, 1, 1))
        addAll(range(2025, 1, 28, 2025, 2, 4))
        addAll(range(2025, 4, 4, 2025, 4, 6))
        addAll(range(2025, 5, 1, 2025, 5, 5))
        addAll(range(2025, 5, 31, 2025, 6, 2))
        addAll(range(2025, 10, 1, 2025, 10, 8))

        addAll(range(2026, 1, 1, 2026, 1, 3))
        addAll(range(2026, 2, 15, 2026, 2, 23))
        addAll(range(2026, 4, 4, 2026, 4, 6))
        addAll(range(2026, 5, 1, 2026, 5, 5))
        addAll(range(2026, 6, 19, 2026, 6, 21))
        addAll(range(2026, 9, 25, 2026, 9, 27))
        addAll(range(2026, 10, 1, 2026, 10, 7))
    }

    val extraWorkdays: Set<LocalDate> = setOf(
        LocalDate.of(2025, 1, 26),
        LocalDate.of(2025, 2, 8),
        LocalDate.of(2025, 4, 27),
        LocalDate.of(2025, 9, 28),
        LocalDate.of(2025, 10, 11),
        LocalDate.of(2026, 1, 4),
        LocalDate.of(2026, 2, 14),
        LocalDate.of(2026, 2, 28),
        LocalDate.of(2026, 5, 9),
        LocalDate.of(2026, 9, 20),
        LocalDate.of(2026, 10, 10),
    )

    private fun range(
        startYear: Int,
        startMonth: Int,
        startDay: Int,
        endYear: Int,
        endMonth: Int,
        endDay: Int,
    ): List<LocalDate> {
        val dates = mutableListOf<LocalDate>()
        var cursor = LocalDate.of(startYear, startMonth, startDay)
        val end = LocalDate.of(endYear, endMonth, endDay)
        while (!cursor.isAfter(end)) {
            dates += cursor
            cursor = cursor.plusDays(1)
        }
        return dates
    }
}
