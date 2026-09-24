package app.shijie.domain

data class ChartSlice(
    val name: String,
    val colorArgb: Int,
    val millis: Long,
)

data class ChartColumn(
    val label: String,
    val slices: List<ChartSlice>,
    /** Real time in this bucket. Slices can overlap when one app is in several groups. */
    val uniqueMillis: Long = slices.sumOf { it.millis },
)

/** Turns per-package buckets into stacked columns. Other apps use the default accent. */
object UsageCategories {
    const val OTHER = "其他"
    const val OTHER_COLOR = 0xFFAAB4C2.toInt()

    fun stack(
        labels: List<String>,
        perBucket: List<Map<String, Long>>,
        membership: Map<String, List<Pair<String, Int>>>,
        groupOrder: List<Pair<String, Int>>,
    ): Pair<List<ChartColumn>, List<ChartSlice>> {
        val totals = LinkedHashMap<String, Long>()
        val colors = LinkedHashMap<String, Int>()
        colors[OTHER] = OTHER_COLOR
        groupOrder.forEach { (name, color) -> colors.putIfAbsent(name, color) }
        val columns = labels.zip(perBucket).map { (label, packages) ->
            val byName = LinkedHashMap<String, Long>()
            var unique = 0L
            packages.forEach { (pkg, millis) ->
                if (millis <= 0L) return@forEach
                unique += millis
                val groups = membership[pkg].orEmpty().ifEmpty { listOf(OTHER to OTHER_COLOR) }
                groups.forEach { (name, color) ->
                    byName[name] = (byName[name] ?: 0L) + millis
                    colors.putIfAbsent(name, color)
                    totals[name] = (totals[name] ?: 0L) + millis
                }
            }
            ChartColumn(label, slicesFor(byName, colors, groupOrder), unique)
        }
        val legend = ArrayList<ChartSlice>()
        val otherTotal = totals[OTHER] ?: 0L
        if (otherTotal > 0L) legend += ChartSlice(OTHER, colors.getValue(OTHER), otherTotal)
        groupOrder.forEach { (name, _) ->
            val value = totals[name] ?: 0L
            if (value > 0L) legend += ChartSlice(name, colors.getValue(name), value)
        }
        totals.keys.filter { it != OTHER && groupOrder.none { group -> group.first == it } }.forEach { name ->
            legend += ChartSlice(name, colors.getValue(name), totals.getValue(name))
        }
        return columns to legend
    }

    private fun slicesFor(
        byName: Map<String, Long>,
        colors: Map<String, Int>,
        groupOrder: List<Pair<String, Int>>,
    ): List<ChartSlice> {
        val slices = ArrayList<ChartSlice>()
        val other = byName[OTHER] ?: 0L
        if (other > 0L) slices += ChartSlice(OTHER, colors.getValue(OTHER), other)
        groupOrder.forEach { (name, _) ->
            val value = byName[name] ?: 0L
            if (value > 0L) slices += ChartSlice(name, colors.getValue(name), value)
        }
        byName.keys.filter { it != OTHER && groupOrder.none { group -> group.first == it } }.forEach { name ->
            slices += ChartSlice(name, colors.getValue(name), byName.getValue(name))
        }
        return slices
    }
}
