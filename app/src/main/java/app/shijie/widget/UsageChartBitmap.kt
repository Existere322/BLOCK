package app.shijie.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import androidx.core.content.res.ResourcesCompat
import app.shijie.R
import app.shijie.domain.ChartAxis
import app.shijie.domain.ChartColumn
import app.shijie.domain.ChartLayout
import app.shijie.domain.UsageChartLayout
import app.shijie.theme.Palette
import kotlin.math.min

private const val HOUR_MAX = 60L * 60_000L
private const val DAY_MAX = 24L * 60L * 60_000L

/** Draws the stats-page stacked bars, grid, and axis labels into one bitmap. */
object UsageChartBitmap {
    fun render(
        context: Context,
        columns: List<ChartColumn>,
        hourly: Boolean,
        palette: Palette,
        widthPx: Int,
        heightPx: Int,
    ): Bitmap {
        val (width, height) = UsageChartLayout.fittedBitmapSize(widthPx, heightPx)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.density = context.resources.displayMetrics.densityDpi
        val canvas = Canvas(bitmap)
        val density = context.resources.displayMetrics.density * width.toFloat() / widthPx.coerceAtLeast(1)
        canvas.drawColor(palette.white)
        if (columns.isEmpty() || height <= 1) return bitmap

        val tickGap = 8f * density
        val tickWidth = 52f * density
        val labelStrip = 22f * density
        val chartWidth = (width - tickGap - tickWidth).coerceAtLeast(1f)
        val chartHeight = (height - labelStrip).coerceAtLeast(1f)
        val corner = 4f * density

        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = lighten(palette.line, 0.55f)
            strokeWidth = density
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(5f * density, 5f * density), 0f)
        }
        val levels = if (hourly) listOf(0.5f) else listOf(16f / 24f, 8f / 24f)
        levels.forEach { level ->
            val y = chartHeight * (1f - level)
            canvas.drawLine(0f, y, chartWidth, y, gridPaint)
        }

        val gap = (if (hourly || columns.size > 12) 3f else 8f) * density
        val maxBar = (if (hourly || columns.size > 12) 14f else 28f) * density
        val geometry = ChartLayout.bars(chartWidth, columns.size, gap, maxBar)
        val max = if (hourly) HOUR_MAX else DAY_MAX
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.track }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val clip = Path()
        val rect = RectF()
        columns.forEachIndexed { index, column ->
            val left = geometry.left(index)
            val barWidth = geometry.bar
            val trackRadius = min(corner, barWidth / 2f)
            canvas.drawRoundRect(left, 0f, left + barWidth, chartHeight, trackRadius, trackRadius, trackPaint)
            val drawn = column.slices.filter { it.millis > 0L }
            val attributed = drawn.sumOf { it.millis }
            if (attributed <= 0L) return@forEachIndexed
            val unique = column.uniqueMillis.coerceAtMost(max).coerceAtLeast(0L)
            val barHeight = chartHeight * (unique.toFloat() / max.toFloat())
            if (barHeight <= 0.5f) return@forEachIndexed
            val radius = minOf(corner, barWidth / 2f, barHeight / 2f)
            rect.set(left, chartHeight - barHeight, left + barWidth, chartHeight)
            clip.rewind()
            clip.addRoundRect(rect, radius, radius, Path.Direction.CW)
            canvas.save()
            canvas.clipPath(clip)
            var top = chartHeight
            drawn.forEach { slice ->
                val sliceHeight = barHeight * (slice.millis.toFloat() / attributed.toFloat())
                top -= sliceHeight
                fillPaint.color = slice.colorArgb
                canvas.drawRect(left, top, left + barWidth, top + sliceHeight, fillPaint)
            }
            canvas.restore()
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.muted
            textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                12f,
                context.resources.displayMetrics,
            ) * width.toFloat() / widthPx.coerceAtLeast(1)
            typeface = labelTypeface(context)
        }
        val metrics = textPaint.fontMetrics
        val textHeight = metrics.descent - metrics.ascent
        val ticks = if (hourly) listOf("60分钟", "30分钟", "0") else listOf("24小时", "16小时", "8小时", "0")
        val tickGapY = if (ticks.size <= 1) 0f else (chartHeight - ticks.size * textHeight) / (ticks.size - 1)
        textPaint.textAlign = Paint.Align.RIGHT
        ticks.forEachIndexed { index, tick ->
            val top = index * (textHeight + tickGapY)
            canvas.drawText(tick, width.toFloat(), top - metrics.ascent, textPaint)
        }

        textPaint.textAlign = Paint.Align.CENTER
        val baseline = chartHeight + (labelStrip - textHeight) / 2f - metrics.ascent
        ChartAxis.indexes(columns.size).forEach { index ->
            if (index !in columns.indices) return@forEach
            val label = ChartAxis.label(columns[index].label)
            val x = UsageChartLayout.axisLabelCenter(
                center = geometry.center(index),
                textWidth = textPaint.measureText(label),
                plotWidth = chartWidth,
            )
            canvas.drawText(label, x, baseline, textPaint)
        }
        return bitmap
    }

    private fun labelTypeface(context: Context): Typeface {
        return ResourcesCompat.getFont(context, R.font.sora_regular) ?: Typeface.DEFAULT
    }

    private fun lighten(argb: Int, amount: Float): Int {
        val red = android.graphics.Color.red(argb) / 255f
        val green = android.graphics.Color.green(argb) / 255f
        val blue = android.graphics.Color.blue(argb) / 255f
        val alpha = android.graphics.Color.alpha(argb)
        fun channel(value: Float) = ((value + (1f - value) * amount) * 255f).toInt().coerceIn(0, 255)
        return android.graphics.Color.argb(alpha, channel(red), channel(green), channel(blue))
    }
}
