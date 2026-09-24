package app.shijie.domain

import org.junit.Assert.assertTrue
import org.junit.Test

class ChartLayoutTest {
    @Test
    fun lastWeekBarStaysInsideTheChartInsteadOfShiftingItsLabel() {
        val width = 220f
        val geometry = ChartLayout.bars(width = width, count = 7, gap = 8f, maxBar = 28f)
        val last = 6
        assertTrue(geometry.left(last) >= 0f)
        assertTrue(geometry.right(last) <= width + 0.5f)
        val previousCenter = geometry.center(last - 1)
        assertTrue(geometry.center(last) - previousCenter > geometry.bar * 0.8f)
    }
}
