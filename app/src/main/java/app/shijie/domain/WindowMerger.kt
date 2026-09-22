package app.shijie.domain

object WindowMerger {
    fun merge(windows: List<BlockWindow>): List<BlockWindow> {
        val pieces = mutableListOf<Pair<Int, Int>>()
        for (window in windows) {
            if (!window.isValid()) continue
            if (window.startMinute < window.endMinute) {
                pieces += window.startMinute to window.endMinute
            } else {
                pieces += window.startMinute to MINUTES_PER_DAY
                if (window.endMinute > 0) {
                    pieces += 0 to window.endMinute
                }
            }
        }
        if (pieces.isEmpty()) return emptyList()
        val sorted = pieces.sortedWith(compareBy({ it.first }, { it.second }))
        val merged = mutableListOf(sorted.first())
        for (index in 1 until sorted.size) {
            val (start, end) = sorted[index]
            val last = merged.last()
            if (start <= last.second) {
                merged[merged.lastIndex] = last.first to maxOf(last.second, end)
            } else {
                merged += start to end
            }
        }
        val spansMidnight = merged.size >= 2 &&
            merged.first().first == 0 &&
            merged.last().second == MINUTES_PER_DAY
        if (!spansMidnight) {
            return merged.map { BlockWindow(it.first, it.second) }
        }
        val head = merged.first()
        val tail = merged.last()
        val middle = if (merged.size > 2) merged.subList(1, merged.lastIndex) else emptyList()
        return listOf(BlockWindow(tail.first, head.second)) + middle.map { BlockWindow(it.first, it.second) }
    }

    private const val MINUTES_PER_DAY = 1440
}
