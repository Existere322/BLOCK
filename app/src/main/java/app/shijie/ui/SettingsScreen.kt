package app.shijie.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        val lockLabel by vm.lockLabel.collectAsStateWithLifecycle()
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("上锁模式", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                if (lockLabel != null) {
                    Text(lockLabel!!, color = MaterialTheme.colorScheme.onSurface)
                    Text("这里没有解除按钮。卸载或强行停止仍然能停掉普通安装版，那是系统权限，时界拦不住。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("上锁后，到你选的时间之前，不能在时界里修改、关闭或删除规则。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { vm.engageLock(java.time.LocalDate.now().plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()) }, modifier = Modifier.fillMaxWidth()) {
                        Text("锁到明天 0 点")
                    }
                    OutlinedButton(onClick = { vm.engageLock(java.time.Instant.now().plus(java.time.Duration.ofHours(24))) }, modifier = Modifier.fillMaxWidth()) {
                        Text("锁 24 小时")
                    }
                    OutlinedButton(onClick = { vm.engageLock(java.time.Instant.now().plus(java.time.Duration.ofDays(7))) }, modifier = Modifier.fillMaxWidth()) {
                        Text("锁 7 天")
                    }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("权限自检", style = MaterialTheme.typography.titleMedium)
                Text(if (data?.accessibility == true) "无障碍：已开启" else "无障碍：未开启")
                Text(if (data?.usageAccess == true) "使用情况访问：已允许" else "使用情况访问：未允许")
                Text(if (data?.notifications == true) "通知：已允许" else "通知：未允许（可选）")
                Text("最近一次前台事件：${ago(data?.lastEvent)}")
                Text("如果屏幕亮着，但这里长时间不更新，系统可能回收了无障碍服务。")
                Button(onClick = { SettingsNavigator.open(context, GuideAction.ACCESSIBILITY) }) { Text("无障碍") }
                OutlinedButton(onClick = { SettingsNavigator.open(context, GuideAction.USAGE) }) { Text("使用情况访问") }
                Button(onClick = {
                    if (!vm.runTest()) vm.message("请先开启无障碍服务，并确认时界没有被强行停止。")
                }) { Text("一键拦截测试") }
            }
        }
        Button(onClick = onGuide, modifier = Modifier.fillMaxWidth()) { Text("ColorOS / 后台引导") }
        Button(onClick = onWorkdays, modifier = Modifier.fillMaxWidth()) { Text("法定工作日修正") }
        OutlinedButton(onClick = onPrivacy, modifier = Modifier.fillMaxWidth()) { Text("隐私说明") }
        TextButton(onClick = {
            if (lockLabel != null) vm.message("上锁期间不能删除本地数据") else confirmDelete = true
        }) { Text("删除本地数据") }
        Text("版本 1.0.0 · 完全离线")
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
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("法定工作日", style = MaterialTheme.typography.headlineSmall)
            Text("已内置中国大陆 ${ChinaOfficialCalendar.coveredYears.joinToString("、")} 年的放假和调休。手动修正会覆盖当天的官方安排。")
            if (today?.uncovered == true) {
                Text("当前年份没有内置数据，未修正的日期按周一至周五处理。", color = MaterialTheme.colorScheme.error)
            }
            Button(onClick = { picking = true }) { Text(date.toString()) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.setWorkday(date, true) }) { Text("标为工作日") }
                OutlinedButton(onClick = { vm.setWorkday(date, false) }) { Text("标为休息日") }
            }
        }
        items(items) { item ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${item.date} · ${if (item.workday) "工作日" else "休息日"}")
                TextButton({ vm.clearWorkday(item.date) }) { Text("恢复") }
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
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (DeviceProfile.isOplus() && DeviceProfile.isAndroid16OrNewer()) "ColorOS 16 引导" else "后台与自启动", style = MaterialTheme.typography.headlineSmall)
        Text(DeviceProfile.summary())
        Text("每个按钮都会先检查目标页面是否存在。OPPO 的私有入口打不开时，会退回系统设置或应用详情，请再按下面的文字路径操作。")
        ColorOsGuide.steps().forEach { step ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(step.title, style = MaterialTheme.typography.titleMedium)
                    Text(step.detail)
                    Button(onClick = {
                        val opened = SettingsNavigator.open(context, step.action)
                        if (!opened) vm.message("这个入口不存在，请按说明里的路径手动打开。")
                    }) { Text("尝试打开") }
                }
            }
        }
        Text("超级省电、手动关闭无障碍或强行停止后，普通安装版无法继续限制。请在退出超级省电后重新检查上面的权限。")
    }
}

@Composable
fun PrivacyScreen() {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("隐私说明", style = MaterialTheme.typography.headlineSmall)
        Text("时界是个人侧载的离线应用。它不声明联网权限，不包含广告、统计 SDK、账号或云同步，并关闭了系统云备份。")
        Text("使用情况访问只用来计算应用前台时长。无障碍服务只接收窗口和包名变化，不读取界面文字、输入内容或控件树。")
        Text("应急放行会在本地保存理由、时间和应用包名，保留 30 天。删除本地数据会一并清除。")
        Text("普通安装无法阻止卸载、强行停止或关闭权限，也不能在系统层禁止应用启动。这里的“限制”是指打开后很快回到桌面并被遮罩挡住。")
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
