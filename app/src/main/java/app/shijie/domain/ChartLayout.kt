package app.shijie.domain

data class ChartGeometry(
    val origin: Float,
    val bar: Float,
    val gap: Float,
) {
    fun left(index: Int): Float = origin + index * (bar + gap)

    fun center(index: Int): Float = left(index) + bar / 2f

    fun right(index: Int): Float = left(index) + bar
}

object ChartLayout {
    /** Bars share the width and stay inside it, so the last label can sit on its own bar. */
    fun bars(width: Float, count: Int, gap: Float, maxBar: Float): ChartGeometry {
        if (count <= 0 || width <= 0f) return ChartGeometry(0f, 0f, gap)
        val gapTotal = gap * (count - 1).coerceAtLeast(0)
        val available = (width - gapTotal).coerceAtLeast(count.toFloat())
        val bar = (available / count).coerceAtMost(maxBar).coerceAtLeast(1f)
        val used = count * bar + gapTotal
        val origin = if (used < width) (width - used) / 2f else 0f
        return ChartGeometry(origin, bar, gap)
    }
}
