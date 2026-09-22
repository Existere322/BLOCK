package app.shijie.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.shijie.domain.iconGlyph

@Composable
fun TodayScreen(vm: ShijieViewModel) {
    val today by vm.today.collectAsStateWithLifecycle()
    val data = today
    val colors = MaterialTheme.colorScheme
    if (data == null) {
        Text("正在读取今天的记录", modifier = Modifier.padding(24.dp), color = colors.onBackground)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("今日", style = MaterialTheme.typography.headlineMedium, color = colors.onBackground)
            Text(
                when {
                    data.accessibility && data.usageAccess -> "保护中"
                    data.accessibility -> "可以拦截，时长还不能校正"
                    else -> "保护未开启"
                },
                color = colors.secondary,
                style = MaterialTheme.typography.titleMedium,
            )
            androidx.compose.material3.OutlinedButton(onClick = { vm.refresh() }) { Text("按系统使用时间重新计算") }
        }
        if (data.uncovered) {
            item {
                QuietCard {
                    Text(
                        "今年还没有内置的放假安排，法定工作日暂时按周一至周五计算。可以在设置里按天修正。",
                        color = colors.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
        item {
            QuietCard {
                Text("今日总时长", color = colors.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
                Text(data.totalLabel, color = colors.onSurface, style = MaterialTheme.typography.displaySmall)
                Text(
                    "对照系统使用情况，并包含此刻仍在前台的这一小段。",
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (data.groups.isEmpty()) {
            item {
                Text("还没有分组。到“分组”里选择要放慢的应用。", color = colors.onSurfaceVariant)
            }
        }
        items(data.groups) { group ->
            QuietCard {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(group.colorArgb)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(iconGlyph(group.iconKey), color = Color.White)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(group.name, color = colors.onSurface, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (group.enabled) group.usageLabel else "规则已关闭",
                            color = colors.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(group.windowLabel, color = colors.secondary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuietCard(content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.surface, contentColor = colors.onSurface),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = { content() })
    }
}
