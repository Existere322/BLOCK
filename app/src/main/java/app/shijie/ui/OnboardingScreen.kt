package app.shijie.ui

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
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
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.refresh()
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(CafeInkSoft)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Text(
            "时界",
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            color = CafeWhite,
            style = MaterialTheme.typography.titleMedium,
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val pageMax = maxHeight
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                ) {
                    Spacer(Modifier.weight(1f))
                    Column(
                        Modifier
                            .heightIn(max = pageMax)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                    when (page) {
                        0 -> {
                            Headline("完全离线")
                            Body("时界不申请联网权限，没有账号、广告、统计或云同步，系统云备份也已关闭。规则、时长和放行记录只留在这台手机上，默认保存 30 天。")
                            Body("它不能阻止你手动关闭无障碍、强行停止或卸载本应用。关闭之后，限制会立即失效。")
                        }
                        1 -> {
                            Headline("使用情况访问")
                            Body("用来统计每个应用的前台时长，并在重新打开后按系统记录校正。时界不读取通知内容。")
                            Body(if (today?.usageAccess == true) "当前：已允许" else "当前：未允许")
                            DarkAction("打开使用情况访问") { SettingsNavigator.open(context, GuideAction.USAGE) }
                        }
                        2 -> {
                            Headline("无障碍服务")
                            Body("只在窗口切换时读取前台应用的包名。不读取屏幕文字、输入内容，也不遍历控件。开启一次并保持系统权限后，保护会长期自动运行，不需要每天手动启动。")
                            Body(if (today?.accessibility == true) "当前：已开启" else "当前：未开启")
                            DarkAction("打开无障碍设置") { SettingsNavigator.open(context, GuideAction.ACCESSIBILITY) }
                        }
                        3 -> {
                            Headline("通知，可选")
                            Body("只有无障碍或使用情况访问被关闭时，才发一条安静的本地提醒。不开启也可以使用。")
                            DarkAction("允许通知") {
                                if (Build.VERSION.SDK_INT >= 33) {
                                    notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    SettingsNavigator.open(context, GuideAction.NOTIFICATIONS)
                                }
                            }
                        }
                        else -> {
                            Headline(if (DeviceProfile.isOplus()) "ColorOS 后台" else "让保护留在后台")
                            Body(DeviceProfile.summary())
                            Body("请允许完全后台运行，并打开自启动。在最近任务里锁定时界。权限还在时，开机和应用更新后都会自动恢复；系统若关闭无障碍权限，时界会发出提醒。")
                            DarkAction("后台耗电") { SettingsNavigator.open(context, GuideAction.BATTERY) }
                            DarkAction("自启动") { SettingsNavigator.open(context, GuideAction.AUTOSTART) }
                            RowCheck(accepted, { accepted = it }, "我已了解：关闭无障碍、强行停止或卸载后，限制会失效。")
                        }
                    }
                    }
                }
            }
        }
        Column(
            Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(5) { index ->
                    val active = index == pager.currentPage
                    val width by animateDpAsState(
                        targetValue = if (active) 28.dp else 8.dp,
                        animationSpec = tween(260),
                        label = "onboarding-progress",
                    )
                    Box(
                        Modifier
                            .height(4.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(if (active) CafeAccent else CafeSearch),
                    )
                }
            }
            CafeButton(
                text = if (pager.currentPage < 4) "继续" else "开始使用",
                enabled = pager.currentPage < 4 || accepted,
                onClick = {
                    if (pager.currentPage < 4) {
                        scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                    } else {
                        vm.completeOnboarding()
                    }
                },
            )
        }
    }
}

@Composable
private fun Headline(text: String) {
    Text(text, color = CafeWhite, style = MaterialTheme.typography.displaySmall)
}

@Composable
private fun Body(text: String) {
    Text(text, color = CafeMuted2, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun DarkAction(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = CafeSearch, contentColor = CafeWhite),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun RowCheck(checked: Boolean, onChange: (Boolean) -> Unit, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = checked,
            onCheckedChange = onChange,
            colors = CheckboxDefaults.colors(
                checkedColor = CafeAccent,
                uncheckedColor = CafeMuted2,
                checkmarkColor = CafeWhite,
            ),
        )
        Text(
            text,
            modifier = Modifier.padding(start = 8.dp),
            color = CafeWhite,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
