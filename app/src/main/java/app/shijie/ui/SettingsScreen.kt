package app.shijie.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.shijie.domain.ChinaOfficialCalendar
import app.shijie.system.ColorOsGuide
import app.shijie.system.DeviceProfile
import app.shijie.system.GuideAction
import app.shijie.system.SettingsNavigator
import java.time.LocalDate

@Composable
fun SettingsScreen(
    vm: ShijieViewModel,
    onWorkdays: () -> Unit,
    onGuide: () -> Unit,
    onPrivacy: () -> Unit,
) {
    val today by vm.today.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    val data = today
    val lockLabel by vm.lockLabel.collectAsStateWithLifecycle()
    val locked = lockLabel
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .aboveTabBar()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("设置", color = CafeInk, style = MaterialTheme.typography.titleLarge)
        Text(
            "权限、规则与本机数据都在这里。",
            color = CafeMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text("上锁模式", color = CafeInk, style = MaterialTheme.typography.titleMedium)
        OceanCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (locked != null) {
                    Text(locked, color = CafeInk, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "这里没有解除按钮。卸载或强行停止仍然能停掉普通安装版，那是系统权限，时界拦不住。",
                        color = CafeMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(
                        "上锁后，到你选的时间之前，不能修改已有分组的时间限制和使用时长，也不能关闭、删除或调整应用名单。名称和图标仍可修改，也可以新建分组。",
                        color = CafeMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    CafeRow(onClick = {
                        vm.engageLock(LocalDate.now().plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant())
                    }) {
                        Text("锁到明天 0 点", modifier = Modifier.weight(1f), color = CafeInk, style = MaterialTheme.typography.titleMedium)
                    }
                    CafeRow(onClick = { vm.engageLock(java.time.Instant.now().plus(java.time.Duration.ofHours(24))) }) {
                        Text("锁 24 小时", modifier = Modifier.weight(1f), color = CafeInk, style = MaterialTheme.typography.titleMedium)
                    }
                    CafeRow(onClick = { vm.engageLock(java.time.Instant.now().plus(java.time.Duration.ofDays(7))) }) {
                        Text("锁 7 天", modifier = Modifier.weight(1f), color = CafeInk, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        Text("权限自检", color = CafeInk, style = MaterialTheme.typography.titleMedium)
        OceanCard {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (data?.accessibility == true) "无障碍：已开启" else "无障碍：未开启", color = CafeInk, style = MaterialTheme.typography.bodyMedium)
                Text(if (data?.usageAccess == true) "使用情况访问：已允许" else "使用情况访问：未允许", color = CafeInk, style = MaterialTheme.typography.bodyMedium)
                Text(if (data?.notifications == true) "通知：已允许" else "通知：未允许（可选）", color = CafeInk, style = MaterialTheme.typography.bodyMedium)
                Text("最近一次前台事件：${ago(data?.lastEvent)}", color = CafeMuted, style = MaterialTheme.typography.bodySmall)
                Text(
                    "时界没有每日保护开关：无障碍与使用情况访问保持开启时会长期自动运行；到期拦截由精确闹钟备份，并每 15 分钟巡检一次。",
                    color = CafeMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "如果屏幕亮着却长时间不拦截，多半是 ColorOS 关掉了无障碍——请到「完全后台」和「自启动」里放行时界。",
                    color = CafeMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        CafeButton(text = "一键拦截测试", onClick = {
            if (!vm.runTest()) vm.message("请先开启无障碍服务，并确认时界没有被强行停止。")
        })
        CafeRow(onClick = { SettingsNavigator.open(context, GuideAction.ACCESSIBILITY) }) {
            Text("检查无障碍权限", modifier = Modifier.weight(1f), color = CafeInk, style = MaterialTheme.typography.titleMedium)
            Icon(CafeIcons.Chevron, contentDescription = null, tint = CafeMuted)
        }
        CafeRow(onClick = { SettingsNavigator.open(context, GuideAction.USAGE) }) {
            Text("检查使用情况访问", modifier = Modifier.weight(1f), color = CafeInk, style = MaterialTheme.typography.titleMedium)
            Icon(CafeIcons.Chevron, contentDescription = null, tint = CafeMuted)
        }
        CafeRow(onClick = onGuide) {
            Text("ColorOS / 后台引导", modifier = Modifier.weight(1f), color = CafeInk, style = MaterialTheme.typography.titleMedium)
            Icon(CafeIcons.Chevron, contentDescription = null, tint = CafeMuted)
        }
        CafeRow(onClick = onWorkdays) {
            Text("法定工作日修正", modifier = Modifier.weight(1f), color = CafeInk, style = MaterialTheme.typography.titleMedium)
            Icon(CafeIcons.Chevron, contentDescription = null, tint = CafeMuted)
        }
        CafeRow(onClick = onPrivacy) {
            Text("隐私说明", modifier = Modifier.weight(1f), color = CafeInk, style = MaterialTheme.typography.titleMedium)
            Icon(CafeIcons.Chevron, contentDescription = null, tint = CafeMuted)
        }
        TextButton(onClick = {
            if (locked != null) vm.message("上锁期间不能删除本地数据") else confirmDelete = true
        }) { Text("删除本地数据", color = MaterialTheme.colorScheme.error) }
        val versionName = remember {
            val manager = context.packageManager
            val info = if (android.os.Build.VERSION.SDK_INT >= 33) {
                manager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                manager.getPackageInfo(context.packageName, 0)
            }
            info.versionName ?: "1.1.0"
        }
        Text("版本 $versionName · 完全离线", color = CafeMuted, style = MaterialTheme.typography.bodySmall)
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除本地数据") },
            text = { Text("分组、修正、统计和放行记录都会清除，并回到首次引导。") },
            confirmButton = { TextButton({ confirmDelete = false; vm.deleteAll() }) { Text("删除") } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkdayScreen(vm: ShijieViewModel) {
    val items by vm.workdays.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    var date by remember { mutableStateOf(LocalDate.now()) }
    var picking by remember { mutableStateOf(false) }
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                "已内置中国大陆 ${ChinaOfficialCalendar.coveredYears.joinToString("、")} 年的放假和调休。手动修正会覆盖当天的官方安排。",
                color = CafeMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (today?.uncovered == true) {
                Text(
                    "当前年份没有内置数据，未修正的日期按周一至周五处理。",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        item {
            CafeRow(onClick = { picking = true }) {
                Text(date.toString(), color = CafeInk, style = MaterialTheme.typography.titleMedium)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CafeChoice("标为工作日", selected = false, onClick = { vm.setWorkday(date, true) }, modifier = Modifier.weight(1f))
                CafeChoice("标为休息日", selected = false, onClick = { vm.setWorkday(date, false) }, modifier = Modifier.weight(1f))
            }
        }
        items(items) { item ->
            CafeRow {
                Text(
                    "${item.date} · ${if (item.workday) "工作日" else "休息日"}",
                    modifier = Modifier.weight(1f),
                    color = CafeInk,
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton({ vm.clearWorkday(item.date) }) { Text("恢复", color = CafeAccent) }
            }
        }
    }
    if (picking) {
        val state = androidx.compose.material3.rememberDatePickerState()
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        date = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                    }
                    picking = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton({ picking = false }) { Text("取消") } },
        ) { androidx.compose.material3.DatePicker(state) }
    }
}

@Composable
fun ColorOsScreen(vm: ShijieViewModel) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(DeviceProfile.summary(), color = CafeInk, style = MaterialTheme.typography.bodyMedium)
        Text(
            "每个按钮都会先检查目标页面是否存在。OPPO 的私有入口打不开时，会退回系统设置或应用详情，请再按下面的文字路径操作。",
            color = CafeMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        ColorOsGuide.steps().forEach { step ->
            OceanCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(step.title, color = CafeInk, style = MaterialTheme.typography.titleMedium)
                    Text(step.detail, color = CafeMuted, style = MaterialTheme.typography.bodyMedium)
                    CafeButton(text = "尝试打开", onClick = {
                        val opened = SettingsNavigator.open(context, step.action)
                        if (!opened) vm.message("这个入口不存在，请按说明里的路径手动打开。")
                    })
                }
            }
        }
        Text(
            "超级省电、手动关闭无障碍或强行停止后，普通安装版无法继续限制。请在退出超级省电后重新检查上面的权限。",
            color = CafeMuted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
fun PrivacyScreen() {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        OceanCard {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "时界是个人侧载的离线应用。它不声明联网权限，不包含广告、统计 SDK、账号或云同步，并关闭了系统云备份。",
                    color = CafeInk,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "使用情况访问只用来计算应用前台时长。无障碍服务只接收窗口和包名变化，不读取界面文字、输入内容或控件树。",
                    color = CafeInk,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "应急放行会在本地保存理由、时间和应用包名，保留 30 天。删除本地数据会一并清除。",
                    color = CafeInk,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "普通安装无法阻止卸载、强行停止或关闭权限，也不能在系统层禁止应用启动。这里的“限制”是指打开后很快回到桌面并被遮罩挡住。",
                    color = CafeInk,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun ago(epoch: Long?): String {
    if (epoch == null || epoch <= 0L) return "尚无"
    val delta = System.currentTimeMillis() - epoch
    return when {
        delta < 5_000 -> "刚刚"
        delta < 60_000 -> "${delta / 1000} 秒前"
        delta < 3_600_000 -> "${delta / 60_000} 分钟前"
        else -> "${delta / 3_600_000} 小时前"
    }
}
