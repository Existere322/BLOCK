package app.shijie.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.shijie.system.GuideAction
import app.shijie.system.SettingsNavigator
import kotlinx.coroutines.delay

@Composable
fun TodayScreen(vm: ShijieViewModel) {
    val today by vm.today.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val data = today
    LaunchedEffect(Unit) {
        delay(1_500)
        while (true) {
            vm.refresh()
            delay(60_000)
        }
    }
    if (data == null) {
        Text(
            "正在读取今天的记录",
            modifier = Modifier.padding(24.dp).statusBarsPadding().aboveTabBar(),
            color = CafeMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }
    val action = when {
        !data.accessibility -> GuideAction.ACCESSIBILITY
        !data.usageAccess -> GuideAction.USAGE
        else -> null
    }
    val status = when {
        data.accessibility && data.guardConnected && data.usageAccess -> "自动保护中 · 长期生效"
        data.accessibility && data.usageAccess -> "权限正常 · 服务正在自动恢复"
        data.accessibility -> "还需打开「使用情况访问」"
        else -> "保护未生效 · 系统关闭了无障碍"
    }
    val detail = if (data.accessibility && data.guardConnected) {
        "禁用时段与额度耗尽会持续拦截"
    } else {
        "拦截依赖系统无障碍服务。ColorOS 省电清理后常会关掉它，点下方恢复即可继续长期自动运行。"
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().aboveTabBar(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .background(CafeInkSoft),
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .padding(horizontal = 24.dp)
                            .padding(top = 8.dp, bottom = 64.dp),
                    ) {
                        Text(
                            "今日",
                            color = CafeWhite,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            status,
                            color = CafeWhite,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.weight(1f))
                        Row(
                            Modifier.fillMaxWidth().height(52.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(CafeSearch)
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(
                                    detail,
                                    color = CafeMuted2,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (action != null) {
                                Box(
                                    Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(CafeAccent)
                                        .noRippleClickable { SettingsNavigator.open(context, action) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        CafeIcons.Go,
                                        contentDescription = "恢复权限",
                                        tint = CafeWhite,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                        }
                    }
                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 24.dp)
                            .offset(y = 56.dp)
                            .fillMaxWidth()
                            .height(112.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(CafeAccent)
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("今日总时长", color = CafeWhite.copy(alpha = 0.86f), style = MaterialTheme.typography.bodySmall)
                        AnimatedContent(
                            targetState = data.totalLabel,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "today-total",
                        ) { label ->
                            Text(
                                label,
                                color = CafeWhite,
                                style = MaterialTheme.typography.displaySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(64.dp))
            }
        }
        if (data.uncovered) {
            item {
                Text(
                    "今年还没有内置的放假安排，法定工作日暂时按周一至周五计算。可以在设置里按天修正。",
                    modifier = Modifier.padding(horizontal = 24.dp),
                    color = CafeMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (data.groups.isEmpty()) {
            item {
                Text(
                    "还没有分组。到“分组”里选择想要放慢的应用。",
                    modifier = Modifier.padding(horizontal = 24.dp),
                    color = CafeMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(data.groups) { group ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CafeMark(groupIcon(group.iconKey), Color(group.colorArgb))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        group.name,
                        color = CafeInk,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = CafeMuted)) {
                                append(if (group.enabled) group.usageLabel else "规则已关闭")
                            }
                            withStyle(SpanStyle(color = CafeMuted)) { append("  ·  ") }
                            withStyle(SpanStyle(color = CafeAccent)) { append(group.windowLabel) }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
