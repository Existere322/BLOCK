package app.shijie.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.shijie.domain.formatDuration

@Composable
fun StatsScreen(vm: ShijieViewModel) {
    val stats by vm.stats.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("统计", style = MaterialTheme.typography.headlineMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(stats.range == StatsRange.TODAY, { vm.setRange(StatsRange.TODAY) }, label = { Text("今日") })
                FilterChip(stats.range == StatsRange.WEEK, { vm.setRange(StatsRange.WEEK) }, label = { Text("近 7 天") })
                FilterChip(stats.range == StatsRange.MONTH, { vm.setRange(StatsRange.MONTH) }, label = { Text("近 30 天") })
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("趋势")
                    UsageBars(stats.bars)
                }
            }
        }
        if (stats.ranking.isEmpty()) {
            item { Text("还没有可显示的使用记录。允许使用情况访问后，这里会按系统统计校正。") }
        }
        items(stats.ranking) { app ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(app.label, modifier = Modifier.weight(1f))
                Text(formatDuration(app.millis))
            }
        }
    }
}

@Composable
private fun UsageBars(values: List<Pair<String, Long>>) {
    val max = (values.maxOfOrNull { it.second } ?: 1L).coerceAtLeast(1L)
    val color = MaterialTheme.colorScheme.secondary
    Canvas(Modifier.fillMaxWidth().height(160.dp).padding(top = 12.dp)) {
        if (values.isEmpty()) return@Canvas
        val gap = 4.dp.toPx()
        val width = ((size.width - gap * (values.size - 1)).coerceAtLeast(0f)) / values.size
        values.forEachIndexed { index, (_, value) ->
            val height = size.height * (value.toFloat() / max.toFloat())
            drawRoundRect(
                color = color,
                topLeft = Offset(index * (width + gap), size.height - height),
                size = Size(width.coerceAtLeast(1f), height.coerceAtLeast(0f)),
                cornerRadius = CornerRadius(6f, 6f),
            )
        }
    }
}
