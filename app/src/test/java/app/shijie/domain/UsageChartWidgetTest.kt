package app.shijie.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartAxisTest {
    @Test
    fun hourlyTicksKeepTheFirstMiddayAndLastHour() {
        assertEquals(listOf(0, 6, 12, 18, 23), ChartAxis.indexes(24))
    }

    @Test
    fun weekShowsEveryDay() {
        assertEquals((0 until 7).toList(), ChartAxis.indexes(7))
    }

    @Test
    fun hourLabelsDropTheLeadingZero() {
        assertEquals("0时", ChartAxis.label("00"))
        assertEquals("8时", ChartAxis.label("08"))
        assertEquals("9/26", ChartAxis.label("9/26"))
    }
}

class UsageChartLayoutTest {
    @Test
    fun bitmapStaysInsideTheRemoteViewsBudget() {
        val (width, height) = UsageChartLayout.fittedBitmapSize(1200, 800)
        assertTrue(width * height <= UsageChartLayout.MAX_BITMAP_PIXELS)
        assertTrue(width >= 1)
        assertTrue(height >= 1)
    }

    @Test
    fun smallBitmapIsUnchanged() {
        assertEquals(400 to 200, UsageChartLayout.fittedBitmapSize(400, 200))
    }

    @Test
    fun shortWidgetDropsTheLegendSoTheChartStaysInside() {
        val height = 180f
        val rows = UsageChartLayout.visibleLegendRows(legendCount = 4, widgetHeightDp = height)
        assertEquals(0, rows)
        assertTrue(UsageChartLayout.chartHeightDp(height, rows) + UsageChartLayout.chromeDp(rows) <= height + 0.1f)
    }

    @Test
    fun tallWidgetKeepsLegendRows() {
        val height = 360f
        val rows = UsageChartLayout.visibleLegendRows(legendCount = 4, widgetHeightDp = height)
        assertEquals(2, rows)
        assertTrue(UsageChartLayout.chartHeightDp(height, rows) + UsageChartLayout.chromeDp(rows) <= height + 0.1f)
    }

    @Test
    fun oppoCardKeepsTheLegendWithoutOverflowing() {
        val height = 250f
        val rows = UsageChartLayout.visibleLegendRows(legendCount = 2, widgetHeightDp = height)
        assertEquals(1, rows)
        val used = UsageChartLayout.chartHeightDp(height, rows) + UsageChartLayout.chromeDp(rows)
        assertTrue(used <= height + 0.1f)
        assertTrue(UsageChartLayout.chartHeightDp(height, rows) >= 1f)
    }

    @Test
    fun firstHourLabelStaysInsideThePlot() {
        assertEquals(16f, UsageChartLayout.axisLabelCenter(center = 4f, textWidth = 32f, plotWidth = 200f), 0.1f)
        assertEquals(184f, UsageChartLayout.axisLabelCenter(center = 196f, textWidth = 32f, plotWidth = 200f), 0.1f)
        assertEquals(100f, UsageChartLayout.axisLabelCenter(center = 100f, textWidth = 32f, plotWidth = 200f), 0.1f)
    }
}
