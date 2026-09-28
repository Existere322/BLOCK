package app.shijie.domain

import kotlin.math.sqrt

/**
 * Sizes the home-screen chart so the total, bars, and group legend stay inside an OPPO 4×4 card.
 * The chart shrinks before the legend is dropped, and the sum never exceeds the widget height.
 */
object UsageChartLayout {
    const val OUTER_PADDING_DP = 12f
    const val SECTION_GAP_DP = 8f
    const val CARD_PADDING_DP = 12f
    const val TITLE_HEIGHT_DP = 32f
    const val MIN_CHART_DP = 48f
    const val LEGEND_ROW_DP = 52f
    const val LEGEND_ROW_GAP_DP = 8f
    const val MAX_BITMAP_PIXELS = 320_000

    fun fittedBitmapSize(width: Int, height: Int, maxPixels: Int = MAX_BITMAP_PIXELS): Pair<Int, Int> {
        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(1)
        val pixels = safeWidth.toLong() * safeHeight
        if (pixels <= maxPixels) return safeWidth to safeHeight
        val scale = sqrt(maxPixels.toDouble() / pixels.toDouble())
        return (safeWidth * scale).toInt().coerceAtLeast(1) to (safeHeight * scale).toInt().coerceAtLeast(1)
    }

    fun visibleLegendRows(legendCount: Int, widgetHeightDp: Float, fontScale: Float = 1f): Int {
        if (legendCount <= 0 || widgetHeightDp <= 0f) return 0
        val rows = (legendCount + 1) / 2
        var shown = rows
        while (shown > 0 && widgetHeightDp - chromeDp(shown, fontScale) < MIN_CHART_DP) {
            shown--
        }
        return shown
    }

    fun chartHeightDp(widgetHeightDp: Float, legendRows: Int, fontScale: Float = 1f): Float {
        return (widgetHeightDp - chromeDp(legendRows, fontScale)).coerceAtLeast(1f)
    }

    fun chromeDp(legendRows: Int, fontScale: Float = 1f): Float {
        val legend = if (legendRows <= 0) 0f else SECTION_GAP_DP + legendBlockDp(legendRows, fontScale)
        return OUTER_PADDING_DP * 2 +
            CARD_PADDING_DP * 2 +
            TITLE_HEIGHT_DP +
            SECTION_GAP_DP +
            legend
    }

    fun legendBlockDp(rowCount: Int, fontScale: Float = 1f): Float {
        if (rowCount <= 0) return 0f
        val row = legendRowDp(fontScale)
        return row * rowCount + LEGEND_ROW_GAP_DP * (rowCount - 1)
    }

    fun legendRowDp(fontScale: Float = 1f): Float = LEGEND_ROW_DP * fontScale.coerceAtLeast(1f)

    /** Keeps an axis label inside the plot so the first and last hour are not cut off. */
    fun axisLabelCenter(center: Float, textWidth: Float, plotWidth: Float): Float {
        if (plotWidth <= 0f) return 0f
        val half = textWidth / 2f
        if (plotWidth <= textWidth) return plotWidth / 2f
        return center.coerceIn(half, plotWidth - half)
    }
}
