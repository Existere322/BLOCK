package app.shijie.domain

/**
 * Usage events on some phones only cover part of the day. Scale each app's
 * hourly slices so they add up to that app's system usage total. An app with
 * a total but no events is placed on [fallbackHour].
 */
object HourAlignment {
    fun scale(
        buckets: List<Map<String, Long>>,
        totals: Map<String, Long>,
        fallbackHour: Int,
    ): List<Map<String, Long>> {
        if (buckets.isEmpty()) return emptyList()
        val result = List(buckets.size) { HashMap<String, Long>() }
        val packages = LinkedHashSet<String>()
        totals.forEach { (pkg, millis) -> if (millis > 0L) packages += pkg }
        buckets.forEach { hour -> hour.forEach { (pkg, millis) -> if (millis > 0L) packages += pkg } }
        val fallback = fallbackHour.coerceIn(0, buckets.lastIndex)
        for (pkg in packages) {
            val event = LongArray(buckets.size) { index -> buckets[index][pkg] ?: 0L }
            val eventSum = event.sum()
            val target = (totals[pkg] ?: eventSum).coerceAtLeast(0L)
            if (target <= 0L) continue
            if (eventSum <= 0L) {
                result[fallback][pkg] = target
                continue
            }
            var assigned = 0L
            var last = fallback
            event.forEachIndexed { index, value ->
                if (value <= 0L) return@forEachIndexed
                val share = value * target / eventSum
                if (share > 0L) result[index][pkg] = share
                assigned += share
                last = index
            }
            val remainder = target - assigned
            if (remainder > 0L) result[last][pkg] = (result[last][pkg] ?: 0L) + remainder
        }
        return result
    }
}
