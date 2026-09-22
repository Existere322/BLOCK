package app.shijie.ui

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.shijie.system.DeviceProfile
import app.shijie.system.GuideAction
import app.shijie.system.SettingsNavigator
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(vm: ShijieViewModel) {
    val pager = rememberPagerState { 5 }
    val scope = rememberCoroutineScope()
    val today by vm.today.collectAsStateWithLifecycle()
    var accepted by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.refresh()
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Text("时界", style = MaterialTheme.typography.headlineLarge, color = colors.onBackground)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(5) { index ->
                val active = index == pager.currentPage
                Box(
                    Modifier
                        .height(8.dp)
                        .width(if (active) 28.dp else 8.dp)
                        .clip(CircleShape)
                        .background(if (active) colors.secondary else colors.outlineVariant),
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = colors.surface,
                    contentColor = colors.onSurface,
                ),
            ) {
                Column(
                    Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    when (page) {
                        0 -> {
                            Text("完全离线", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                            Text(
                                "时界不申请联网权限，没有账号、广告、统计或云同步，系统云备份也已关闭。规则、时长和放行记录只留在这台手机上，默认保存 30 天。",
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.onSurfaceVariant,
                            )
                            Text(
                                "它不能阻止你手动关闭无障碍、强行停止或卸载本应用。关闭之后，限制会立即失效。",
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        1 -> {
                            Text("使用情况访问", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                            Text(
                                "用来统计每个应用的前台时长，并在重新打开后按系统记录校正。时界不读取通知内容。",
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.onSurfaceVariant,
                            )
                            Text(
                                if (today?.usageAccess == true) "当前：已允许" else "当前：未允许",
                                color = colors.secondary,
                            )
                            Button(onClick = { SettingsNavigator.open(context, GuideAction.USAGE) }) { Text("打开使用情况访问") }
                        }
                        2 -> {
                            Text("无障碍服务", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                            Text(
                                "只在窗口切换时读取前台应用的包名。不读取屏幕文字、输入内容，也不遍历控件。命中规则时回到桌面，并停下一小会儿。",
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.onSurfaceVariant,
                            )
                            Text(
                                if (today?.accessibility == true) "当前：已开启" else "当前：未开启",
                                color = colors.secondary,
                            )
                            Button(onClick = { SettingsNavigator.open(context, GuideAction.ACCESSIBILITY) }) { Text("打开无障碍设置") }
                        }
                        3 -> {
                            Text("通知，可选", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                            Text(
                                "只有无障碍或使用情况访问被关闭时，才发一条安静的本地提醒。不开启也可以使用。",
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.onSurfaceVariant,
                            )
                            OutlinedButton(onClick = {
                                if (Build.VERSION.SDK_INT >= 33) {
                                    notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    SettingsNavigator.open(context, GuideAction.NOTIFICATIONS)
                                }
                            }) { Text("允许通知") }
                        }
                        else -> {
                            Text(
                                if (DeviceProfile.isOplus()) "ColorOS 后台" else "让保护留在后台",
                                style = MaterialTheme.typography.titleLarge,
                                color = colors.onSurface,
                            )
                            Text(DeviceProfile.summary(), color = colors.onSurfaceVariant)
                            Text(
                                "请允许完全后台运行，并打开自启动。在最近任务里锁定时界。权限还在时，重启或清理最近任务后会自动恢复。",
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.onSurfaceVariant,
                            )
                            Button(onClick = { SettingsNavigator.open(context, GuideAction.BATTERY) }) { Text("后台耗电") }
                            OutlinedButton(onClick = { SettingsNavigator.open(context, GuideAction.AUTOSTART) }) { Text("自启动") }
                            RowCheck(accepted, { accepted = it }, "我已了解：关闭无障碍、强行停止或卸载后，限制会失效。")
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                if (pager.currentPage < 4) {
                    scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                } else {
                    vm.completeOnboarding()
                }
            },
            enabled = pager.currentPage < 4 || accepted,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
                disabledContainerColor = colors.surfaceVariant,
                disabledContentColor = colors.onSurfaceVariant,
            ),
        ) {
            Text(if (pager.currentPage < 4) "继续" else "开始使用", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun RowCheck(checked: Boolean, onChange: (Boolean) -> Unit, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(
            text,
            modifier = Modifier.padding(start = 8.dp),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
