package app.shijie.ui

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.shijie.data.RankedApp
import app.shijie.domain.ChartColumn
import app.shijie.domain.ChartLayout
import app.shijie.domain.ChartSlice
import app.shijie.domain.formatDurationMinutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val HOUR_MAX = 60L * 60_000L
private const val DAY_MAX = 24L * 60L * 60_000L

@Composable
fun StatsScreen(vm: ShijieViewModel) {
    val stats by vm.stats.collectAsStateWithLifecycle()
    var page by remember { mutableIntStateOf(rangeIndex(stats.range)) }
    val cache = remember { androidx.compose.runtime.mutableStateMapOf<StatsRange, StatsUi>() }
    cache[stats.range] = stats
    Column(Modifier.fillMaxSize().statusBarsPadding().aboveTabBar()) {
        Column(
            Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("统计", color = CafeInk, style = MaterialTheme.typography.titleLarge)
            CafeSegmented(
                labels = listOf("今日", "近 7 天"),
                selected = page,
                onSelect = { index ->
                    page = index
                    vm.setRange(rangeAt(index))
                },
            )
        }
        AnimatedContent(
            targetState = page,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds(),
            transitionSpec = {
                val direction = if (targetState > initialState) 1 else -1
                (
                    slideInHorizontally(statsSlide) { full -> full * direction } togetherWith
                        slideOutHorizontally(statsSlide) { full -> -full * direction }
                    ).using(SizeTransform(clip = true))
            },
            label = "stats-range",
        ) { index ->
            val data = cache[rangeAt(index)]
            if (data == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("正在读取", color = CafeMuted, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                StatsRangePage(data)
            }
        }
    }
}

private val statsSlide = tween<IntOffset>(durationMillis = 300, easing = FastOutSlowInEasing)

private fun rangeIndex(range: StatsRange): Int = when (range) {
    StatsRange.TODAY -> 0
    StatsRange.WEEK, StatsRange.MONTH -> 1
}

private fun rangeAt(index: Int): StatsRange = when (index) {
    0 -> StatsRange.TODAY
    else -> StatsRange.WEEK
}

@Composable
private fun StatsRangePage(stats: StatsUi) {
    val total = stats.columns.sumOf { it.uniqueMillis }
    val hourly = stats.range == StatsRange.TODAY
    val card = RoundedCornerShape(10.dp)
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(card)
                .background(CafeWhite)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "合计 ${formatDurationMinutes(total)}",
                color = CafeInk,
                style = MaterialTheme.typography.titleLarge,
            )
            UsageBars(
                columns = stats.columns,
                hourly = hourly,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
            )
            if (stats.legend.isNotEmpty()) {
                CategoryLegend(stats.legend)
            }
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(card)
                .background(CafeWhite),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    Text("应用", color = CafeInk, style = MaterialTheme.typography.titleMedium)
                }
                if (stats.ranking.isEmpty()) {
                    item {
                        Text(
                            "还没有可显示的使用记录。允许使用情况访问后，这里会按系统统计校正。",
                            color = CafeMuted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                items(stats.ranking, key = { it.packageName }) { app ->
                    AppUsageRow(app)
                }
            }
        }
    }
}

@Composable
private fun CategoryLegend(items: List<ChartSlice>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { slice ->
                    Row(
                        Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(slice.colorArgb)),
                        )
                        Column(Modifier.padding(start = 8.dp)) {
                            Text(
                                slice.name,
                                color = CafeInkSoft,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                formatDurationMinutes(slice.millis),
                                color = CafeInk,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun UsageBars(columns: List<ChartColumn>, hourly: Boolean, modifier: Modifier = Modifier) {
    if (columns.isEmpty()) {
        Text(
            "这个范围还没有记录",
            color = CafeMuted,
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier.padding(top = 12.dp),
        )
        return
    }
    val max = if (hourly) HOUR_MAX else DAY_MAX
    val ticks = if (hourly) listOf("60分钟", "30分钟", "0") else listOf("24小时", "16小时", "8小时", "0")
    var played by remember(columns) { mutableStateOf(false) }
    val growth by animateFloatAsState(if (played) 1f else 0f, tween(700), label = "bars")
    LaunchedEffect(columns) { played = true }
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = CafeMuted)
    val corner = 4.dp
    Column(modifier) {
        Row(Modifier.fillMaxWidth().weight(1f)) {
            Canvas(Modifier.weight(1f).fillMaxHeight()) {
                val levels = if (hourly) listOf(0.5f) else listOf(16f / 24f, 8f / 24f)
                val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx()), 0f)
                val grid = lighten(CafeLine, 0.55f)
                levels.forEach { level ->
                    val y = size.height * (1f - level)
                    drawLine(
                        color = grid,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = dash,
                    )
                }
                val geometry = chartGeometry(size.width, columns.size, hourly, this)
                val radius = corner.toPx()
                columns.forEachIndexed { index, column ->
                    val left = geometry.left(index)
                    val barWidth = geometry.bar
                    drawRoundRect(
                        color = CafeTrack,
                        topLeft = Offset(left, 0f),
                        size = Size(barWidth, size.height),
                        cornerRadius = CornerRadius(minOf(radius, barWidth / 2f)),
                    )
                    val drawn = column.slices.filter { it.millis > 0L }
                    val attributed = drawn.sumOf { it.millis }
                    if (attributed <= 0L) return@forEachIndexed
                    val unique = column.uniqueMillis.coerceAtMost(max).coerceAtLeast(0L)
                    val barHeight = size.height * (unique.toFloat() / max.toFloat()) * growth
                    if (barHeight <= 0.5f) return@forEachIndexed
                    val roundedFill = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = left,
                                top = size.height - barHeight,
                                right = left + barWidth,
                                bottom = size.height,
                                cornerRadius = CornerRadius(minOf(radius, barWidth / 2f, barHeight / 2f)),
                            ),
                        )
                    }
                    drawContext.canvas.save()
                    drawContext.canvas.clipPath(roundedFill)
                    var top = size.height
                    drawn.forEach { slice ->
                        val height = barHeight * (slice.millis.toFloat() / attributed.toFloat())
                        top -= height
                        drawRect(
                            color = Color(slice.colorArgb),
                            topLeft = Offset(left, top),
                            size = Size(barWidth, height),
                        )
                    }
                    drawContext.canvas.restore()
                }
            }
            Column(
                Modifier
                    .padding(start = 8.dp)
                    .width(52.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                ticks.forEach { tick ->
                    Text(tick, style = labelStyle, maxLines = 1)
                }
            }
        }
        AxisLabels(
            columns = columns,
            hourly = hourly,
            style = labelStyle,
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 60.dp),
        )
    }
}

@Composable
private fun AxisLabels(
    columns: List<ChartColumn>,
    hourly: Boolean,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
) {
    val indexes = axisIndexes(columns.size)
    val density = LocalDensity.current
    Layout(
        content = {
            indexes.forEach { index ->
                Text(
                    prettyAxisLabel(columns[index].label),
                    style = style,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        },
        modifier = modifier.height(22.dp),
    ) { measurables, constraints ->
        val width = constraints.maxWidth.toFloat()
        val geometry = chartGeometry(width, columns.size, hourly, density)
        val loose = constraints.copy(minWidth = 0, minHeight = 0, maxWidth = Constraints.Infinity)
        val placeables = measurables.map { it.measure(loose) }
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(constraints.maxWidth, height) {
            placeables.forEachIndexed { slot, placeable ->
                val center = geometry.center(indexes[slot])
                val x = (center - placeable.width / 2f).toInt()
                placeable.placeRelative(x, 0)
            }
        }
    }
}

private fun chartGeometry(
    width: Float,
    count: Int,
    hourly: Boolean,
    density: androidx.compose.ui.unit.Density,
): app.shijie.domain.ChartGeometry {
    val gap = with(density) { (if (hourly || count > 12) 3.dp else 8.dp).toPx() }
    val maxBar = with(density) { (if (hourly || count > 12) 14.dp else 28.dp).toPx() }
    return ChartLayout.bars(width, count, gap, maxBar)
}

private fun lighten(color: Color, amount: Float): Color {
    return Color(
        red = color.red + (1f - color.red) * amount,
        green = color.green + (1f - color.green) * amount,
        blue = color.blue + (1f - color.blue) * amount,
        alpha = color.alpha,
    )
}

@Composable
private fun AppUsageRow(app: RankedApp) {
    val context = LocalContext.current
    var icon by remember(app.packageName) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(app.packageName) {
        icon = withContext(Dispatchers.IO) { loadAppIcon(context, app.packageName) }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val iconBitmap = icon
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap,
                contentDescription = app.label,
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
        } else {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CafeCream),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    app.label.take(1),
                    color = CafeAccent,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        Text(
            app.label,
            modifier = Modifier.weight(1f),
            color = CafeInk,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(formatDurationMinutes(app.millis), color = CafeMuted, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun axisIndexes(count: Int): List<Int> = when {
    count <= 1 -> listOf(0)
    count <= 8 -> (0 until count).toList()
    count <= 24 -> listOf(0, 6, 12, 18, count - 1).filter { it in 0 until count }.distinct()
    else -> listOf(0, count / 4, count / 2, (count * 3) / 4, count - 1).filter { it in 0 until count }.distinct()
}

private fun prettyAxisLabel(raw: String): String {
    if (raw.length == 2 && raw.all { it.isDigit() }) return "${raw.toInt()}时"
    return raw
}

internal fun loadAppIcon(context: Context, packageName: String): ImageBitmap? = try {
    context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
} catch (_: Exception) {
    null
}
