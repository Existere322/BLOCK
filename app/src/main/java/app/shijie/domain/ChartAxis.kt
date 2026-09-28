package app.shijie.domain

/** Horizontal tick indexes and labels shared by the stats page and the home widget. */
object ChartAxis {
    fun indexes(count: Int): List<Int> = when {
        count <= 1 -> listOf(0)
        count <= 8 -> (0 until count).toList()
        count <= 24 -> listOf(0, 6, 12, 18, count - 1).filter { it in 0 until count }.distinct()
        else -> listOf(0, count / 4, count / 2, (count * 3) / 4, count - 1).filter { it in 0 until count }.distinct()
    }

    fun label(raw: String): String {
        if (raw.length == 2 && raw.all { it.isDigit() }) return "${raw.toInt()}时"
        return raw
    }
}
