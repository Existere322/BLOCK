@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.shijie.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.shijie.data.LaunchableRow
import app.shijie.domain.BlockWindow
import app.shijie.domain.DayPolicy
import app.shijie.domain.GROUP_ICON_KEYS
import app.shijie.domain.formatWindow
import app.shijie.domain.iconGlyph
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun GroupsScreen(vm: ShijieViewModel, onOpen: (Long) -> Unit) {
    val groups by vm.groups.collectAsStateWithLifecycle()
    val lockLabel by vm.lockLabel.collectAsStateWithLifecycle()
    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (lockLabel != null) vm.message("上锁期间不能新建分组") else onOpen(0L)
                },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("新建") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text("分组", style = MaterialTheme.typography.headlineMedium) }
            if (groups.isEmpty()) {
                item { Text("每个应用只能属于一个分组。电话、桌面、系统界面、设置、安装器和时界本身不能加入。") }
            }
            items(groups, key = { it.group.id }) { stored ->
                Card(
                    Modifier.fillMaxWidth().clickable { onOpen(stored.group.id) },
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "${iconGlyph(stored.group.iconKey)}  ${stored.group.name}",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            if (stored.group.enabled) "已启用 · ${stored.packages.size} 个应用" else "已关闭 · ${stored.packages.size} 个应用",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GroupEditorScreen(id: Long, vm: ShijieViewModel, onDone: () -> Unit, onMessage: (String) -> Unit) {
    var draft by remember { mutableStateOf<GroupDraft?>(null) }
    var picking by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(id) { draft = vm.loadDraft(id) }
    val current = draft
    if (current == null) {
        Text("正在打开分组", modifier = Modifier.padding(24.dp))
        return
    }
    if (picking) {
        AppPicker(current, vm) { draft = it; picking = false }
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(if (id == 0L) "新建分组" else "编辑分组", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        val lockLabel by vm.lockLabel.collectAsStateWithLifecycle()
        if (lockLabel != null) {
            Text(lockLabel!!, color = MaterialTheme.colorScheme.error)
            return
        }
        OutlinedTextField(current.name, { draft = current.copy(name = it) }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth())
        Text("点一个颜色。选中的会套上琥珀圈。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GroupPalette.forEach { color ->
                val selected = current.colorArgb == color
                Box(
                    Modifier
                        .size(52.dp)
                        .border(
                            width = if (selected) 4.dp else 2.dp,
                            color = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
                            shape = CircleShape,
                        )
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(Color(color))
                        .clickable { draft = current.copy(colorArgb = color) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GROUP_ICON_KEYS.forEach { key ->
                FilterChip(current.iconKey == key, { draft = current.copy(iconKey = key) }, label = { Text(iconGlyph(key)) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("启用规则")
            Switch(current.enabled, { draft = current.copy(enabled = it) })
        }
        Text("日期策略")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PolicyChip("每天", DayPolicy.EVERY_DAY, current) { draft = it }
            PolicyChip("法定工作日", DayPolicy.LEGAL_WORKDAY, current) { draft = it }
            PolicyChip("自定义", DayPolicy.CUSTOM_WEEKDAYS, current) { draft = it }
        }
        if (current.dayPolicy == DayPolicy.CUSTOM_WEEKDAYS) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DayOfWeek.entries.forEach { day ->
                    val names = listOf("一", "二", "三", "四", "五", "六", "日")
                    FilterChip(
                        selected = day in current.weekdays,
                        onClick = {
                            val next = current.weekdays.toMutableSet()
                            if (!next.add(day)) next.remove(day)
                            draft = current.copy(weekdays = next)
                        },
                        label = { Text(names[day.value - 1]) },
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("限制生效日期")
            Switch(current.useRange, { draft = current.copy(useRange = it) })
        }
        if (current.useRange) {
            DateField("开始", current.startDate) { draft = current.copy(startDate = it) }
            DateField("结束", current.endDate) { draft = current.copy(endDate = it) }
        }
        Text("禁用时段之外，全组共享下面的每日额度，本地午夜清零，不结转到明天。")
        OutlinedTextField(
            current.quotaMinutes,
            { draft = current.copy(quotaMinutes = it.filter { ch -> ch.isDigit() }.take(4)) },
            label = { Text("每日额度（分钟）") },
            modifier = Modifier.fillMaxWidth(),
        )
        Text("禁用时段")
        current.windows.forEach { window ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(formatWindow(window))
                TextButton({ draft = current.copy(windows = current.windows - window) }) { Text("删除") }
            }
        }
        WindowAdder { draft = current.copy(windows = current.windows + it) }
        var systemUsedLabel by remember(current.apps) { mutableStateOf("正在读取系统使用时间") }
        LaunchedEffect(current.apps) {
            systemUsedLabel = if (current.apps.isEmpty()) {
                "还没有选择应用"
            } else {
                "这些应用今天系统已用 ${app.shijie.domain.formatDuration(vm.systemUsed(current.apps))}"
            }
        }
        Text(systemUsedLabel, color = MaterialTheme.colorScheme.onSurface)
        Button(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
            Text("选择应用（${current.apps.size}）")
        }
        Button(
            onClick = { vm.save(current) { error -> if (error == null) onDone() else onMessage(error) } },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("保存") }
        if (id != 0L) {
            TextButton(onClick = { confirmDelete = true }) { Text("删除这个分组") }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除分组") },
            text = { Text("分组里的应用会解除限制。已保存的使用统计仍会保留到 30 天。") },
            confirmButton = { TextButton({ vm.deleteGroup(id); onDone() }) { Text("删除") } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun PolicyChip(label: String, policy: DayPolicy, draft: GroupDraft, onChange: (GroupDraft) -> Unit) {
    FilterChip(draft.dayPolicy == policy, { onChange(draft.copy(dayPolicy = policy)) }, label = { Text(label) })
}

@Composable
private fun DateField(label: String, date: LocalDate?, onChange: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Text(if (date == null) "$label：未选择" else "$label：$date")
    }
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton({ open = false }) { Text("取消") } },
        ) { DatePicker(state) }
    }
}

@Composable
private fun WindowAdder(onAdd: (BlockWindow) -> Unit) {
    var pickingStart by remember { mutableStateOf(false) }
    var start by remember { mutableStateOf<Int?>(null) }
    TextButton(onClick = { pickingStart = true }) { Text("添加时段") }
    if (pickingStart) {
        MinuteDialog("开始时间", start ?: 22 * 60, { pickingStart = false }) {
            start = it
            pickingStart = false
        }
    }
    val chosen = start
    if (chosen != null && !pickingStart) {
        MinuteDialog("结束时间", (chosen + 60) % 1440, { start = null }) { end ->
            if (end == chosen) {
                start = null
            } else {
                onAdd(BlockWindow(chosen, end))
                start = null
            }
        }
    }
}

@Composable
private fun MinuteDialog(title: String, initial: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton({ onConfirm(state.hour * 60 + state.minute) }) { Text("确定") } },
        dismissButton = { TextButton(onDismiss) { Text("取消") } },
        title = { Text(title) },
        text = { TimePicker(state) },
    )
}

@Composable
private fun AppPicker(draft: GroupDraft, vm: ShijieViewModel, onClose: (GroupDraft) -> Unit) {
    var rows by remember { mutableStateOf<List<LaunchableRow>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(draft.apps) }
    LaunchedEffect(draft.id) { rows = vm.launchable(draft.id) }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("选择可启动的应用", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(query, { query = it }, label = { Text("搜索") }, modifier = Modifier.fillMaxWidth())
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(rows.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }) { row ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(row.packageName in selected, {
                        selected = if (it) selected + row.packageName else selected - row.packageName
                    })
                    Column {
                        Text(row.label)
                        if (row.otherGroup != null) Text("当前在「${row.otherGroup}」，保存后会移到本组", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Button(onClick = { onClose(draft.copy(apps = selected)) }, modifier = Modifier.fillMaxWidth()) { Text("完成") }
    }
}
