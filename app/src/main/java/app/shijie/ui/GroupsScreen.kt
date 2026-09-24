@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.shijie.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.shijie.data.LaunchableRow
import app.shijie.domain.BlockWindow
import app.shijie.domain.DayPolicy
import app.shijie.domain.GROUP_ICON_KEYS
import app.shijie.domain.formatWindow
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun GroupsScreen(vm: ShijieViewModel, onOpen: (Long) -> Unit) {
    val groups by vm.groups.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().statusBarsPadding().aboveTabBar()) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("分组", color = CafeInk, style = MaterialTheme.typography.titleLarge)
            Text(
                "把相似的应用放在同一段时间边界里。一个应用可以同时属于多个分组，各组限制都会生效。",
                color = CafeMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (groups.isEmpty()) {
                item {
                    Text(
                        "还没有分组。一个应用可以同时属于多个分组，限制会叠加。电话、桌面、系统界面、设置、安装器和时界本身不能加入。",
                        color = CafeMuted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            items(groups, key = { it.group.id }) { stored ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .clip(CardShape)
                        .background(CafeWhite)
                        .noRippleClickable { onOpen(stored.group.id) }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CafeMark(groupIcon(stored.group.iconKey), Color(stored.group.colorArgb), size = 32.dp)
                    Column(Modifier.padding(start = 16.dp, end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            stored.group.name,
                            color = CafeInk,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (stored.group.enabled) "已启用 · ${stored.packages.size} 个应用" else "已关闭 · ${stored.packages.size} 个应用",
                            color = CafeMuted,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Box(Modifier.padding(horizontal = 48.dp, vertical = 12.dp)) {
            CafeButton(
                text = "新建",
                height = 48.dp,
                onClick = { onOpen(0L) },
            )
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
        Text(
            "正在打开分组",
            modifier = Modifier.padding(24.dp),
            color = CafeMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }
    val palette = remember(current.id) { mutableStateListOf<Int>().apply { addAll(GroupPalette) } }
    LaunchedEffect(current.id, current.colorArgb) {
        if (current.colorArgb !in palette) palette.add(current.colorArgb)
    }
    if (picking) {
        AppPicker(current, vm) { draft = it; picking = false }
        return
    }
    val lockLabel by vm.lockLabel.collectAsStateWithLifecycle()
    val rulesLocked = lockLabel != null && current.id != 0L
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (rulesLocked) {
                Text(
                    "上锁期间只能改名称、颜色和图标。时间限制、使用时长和应用名单保持原样。",
                    color = CafeMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else if (lockLabel != null) {
                Text(
                    "上锁期间仍可以新建分组。新建后，这组的时间限制和使用时长会立刻生效。",
                    color = CafeMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(202.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CafeInkSoft)
                    .padding(20.dp),
            ) {
                Column(Modifier.align(Alignment.BottomStart), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CafeMark(
                        groupIcon(current.iconKey),
                        Color(current.colorArgb),
                        size = 40.dp,
                    )
                    Text(
                        current.name.ifBlank { "未命名分组" },
                        color = CafeWhite,
                        style = MaterialTheme.typography.displaySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (current.enabled) "已启用 · ${current.apps.size} 个应用" else "已关闭 · ${current.apps.size} 个应用",
                        color = CafeMuted2,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Text("名称", color = CafeInk, style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                current.name,
                { draft = current.copy(name = it) },
                label = { Text("名称") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = oceanTextFieldColors(),
            )
            Text("颜色", color = CafeInk, style = MaterialTheme.typography.titleMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                palette.forEach { color ->
                    val selected = current.colorArgb == color
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(color))
                            .border(
                                width = if (selected) 2.dp else 0.dp,
                                color = if (selected) CafeWhite else Color.Transparent,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .noRippleClickable { draft = current.copy(colorArgb = color) },
                    )
                }
            }
            Text("图标", color = CafeInk, style = MaterialTheme.typography.titleMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GROUP_ICON_KEYS.forEach { key ->
                    CafeChoice(
                        icon = groupIcon(key),
                        selected = current.iconKey == key,
                        onClick = { draft = current.copy(iconKey = key) },
                        modifier = Modifier.size(width = 48.dp, height = 41.dp),
                    )
                }
            }
            CafeRow {
                Text("启用规则", modifier = Modifier.weight(1f), color = CafeInk, style = MaterialTheme.typography.titleMedium)
                Switch(
                    checked = current.enabled,
                    onCheckedChange = { if (!rulesLocked) draft = current.copy(enabled = it) },
                    enabled = !rulesLocked,
                )
            }
            Text("日期策略", color = CafeInk, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PolicyChip("每天", DayPolicy.EVERY_DAY, current, Modifier.weight(1f), !rulesLocked) { draft = it }
                PolicyChip("法定工作日", DayPolicy.LEGAL_WORKDAY, current, Modifier.weight(1.4f), !rulesLocked) { draft = it }
                PolicyChip("自定义", DayPolicy.CUSTOM_WEEKDAYS, current, Modifier.weight(1f), !rulesLocked) { draft = it }
            }
            if (current.dayPolicy == DayPolicy.CUSTOM_WEEKDAYS) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DayOfWeek.entries.forEach { day ->
                        val names = listOf("一", "二", "三", "四", "五", "六", "日")
                        CafeChoice(
                            text = names[day.value - 1],
                            selected = day in current.weekdays,
                            onClick = {
                                if (rulesLocked) return@CafeChoice
                                val next = current.weekdays.toMutableSet()
                                if (!next.add(day)) next.remove(day)
                                draft = current.copy(weekdays = next)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            CafeRow {
                Text("限制生效日期", modifier = Modifier.weight(1f), color = CafeInk, style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = current.useRange,
                    onCheckedChange = { if (!rulesLocked) draft = current.copy(useRange = it) },
                    enabled = !rulesLocked,
                )
            }
            if (current.useRange) {
                DateField("开始", current.startDate, !rulesLocked) { draft = current.copy(startDate = it) }
                DateField("结束", current.endDate, !rulesLocked) { draft = current.copy(endDate = it) }
            }
            Text(
                "禁用时段之外，全组共享下面的每日额度，本地午夜清零，不结转到明天。",
                color = CafeMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                current.quotaMinutes,
                { if (!rulesLocked) draft = current.copy(quotaMinutes = it.filter { ch -> ch.isDigit() }.take(4)) },
                label = { Text("每日额度（分钟）") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = rulesLocked,
                enabled = !rulesLocked,
                shape = RoundedCornerShape(12.dp),
                colors = oceanTextFieldColors(),
            )
            Text("禁用时段", color = CafeInk, style = MaterialTheme.typography.titleMedium)
            current.windows.forEach { window ->
                CafeRow {
                    Text(formatWindow(window), modifier = Modifier.weight(1f), color = CafeInk, style = MaterialTheme.typography.bodyMedium)
                    if (!rulesLocked) {
                        TextButton({ draft = current.copy(windows = current.windows - window) }) {
                            Text("删除", color = CafeMuted)
                        }
                    }
                }
            }
            if (!rulesLocked) {
                WindowAdder { draft = current.copy(windows = current.windows + it) }
            }
            var systemUsedLabel by remember(current.apps) { mutableStateOf("正在读取系统使用时间") }
            LaunchedEffect(current.apps) {
                systemUsedLabel = if (current.apps.isEmpty()) {
                    "还没有选择应用"
                } else {
                    "这些应用今天系统已用 ${app.shijie.domain.formatDuration(vm.systemUsed(current.apps))}"
                }
            }
            Text(systemUsedLabel, color = CafeInk, style = MaterialTheme.typography.bodyMedium)
            CafeRow(onClick = {
                if (rulesLocked) onMessage("上锁期间不能调整分组里的应用") else picking = true
            }) {
                Text(
                    "选择应用（${current.apps.size}）",
                    modifier = Modifier.weight(1f),
                    color = CafeInk,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text("›", color = CafeMuted, style = MaterialTheme.typography.titleMedium)
            }
            if (id != 0L) {
                TextButton(onClick = {
                    if (rulesLocked) onMessage("上锁期间不能删除分组") else confirmDelete = true
                }) {
                    Text("删除这个分组", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(CafeWhite)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(0.8f)) {
                Text("每日额度", color = CafeMuted, style = MaterialTheme.typography.bodySmall)
                Text(
                    "${current.quotaMinutes.ifBlank { "0" }} 分钟",
                    color = CafeAccent,
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            CafeButton(
                text = "保存",
                onClick = { vm.save(current) { error -> if (error == null) onDone() else onMessage(error) } },
                modifier = Modifier.weight(1.4f),
            )
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
private fun PolicyChip(
    label: String,
    policy: DayPolicy,
    draft: GroupDraft,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onChange: (GroupDraft) -> Unit,
) {
    CafeChoice(
        text = label,
        selected = draft.dayPolicy == policy,
        onClick = { if (enabled) onChange(draft.copy(dayPolicy = policy)) },
        modifier = modifier,
    )
}

@Composable
private fun DateField(
    label: String,
    date: LocalDate?,
    enabled: Boolean = true,
    onChange: (LocalDate) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    CafeRow(onClick = { if (enabled) open = true }) {
        Text(
            if (date == null) "$label：未选择" else "$label：$date",
            color = CafeInk,
            style = MaterialTheme.typography.bodyMedium,
        )
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
    TextButton(onClick = { pickingStart = true }) { Text("添加时段", color = CafeAccent) }
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
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text("选择应用", color = CafeInk, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            query,
            { query = it },
            label = { Text("搜索") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = oceanTextFieldColors(),
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }) { row ->
                CafeRow {
                    Checkbox(row.packageName in selected, {
                        selected = if (it) selected + row.packageName else selected - row.packageName
                    })
                    AppGlyph(row.packageName, row.label)
                    Column(Modifier.padding(start = 8.dp).weight(1f)) {
                        Text(row.label, color = CafeInk, style = MaterialTheme.typography.bodyMedium)
                        if (row.otherGroups.isNotEmpty()) {
                            Text(
                                "也在${row.otherGroups.joinToString("、") { "「$it」" }}。勾选后会同时受本组限制",
                                color = CafeMuted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
        CafeButton(
            text = "完成",
            onClick = { onClose(draft.copy(apps = selected)) },
            modifier = Modifier.navigationBarsPadding().padding(top = 12.dp),
        )
    }
}

@Composable
private fun AppGlyph(packageName: String, label: String) {
    val context = LocalContext.current
    var icon by remember(packageName) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(packageName) {
        icon = withContext(Dispatchers.IO) { loadAppIcon(context, packageName) }
    }
    val bitmap = icon
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = label,
            modifier = Modifier
                .padding(start = 8.dp)
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
    } else {
        Box(
            Modifier
                .padding(start = 8.dp)
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CafeCream),
            contentAlignment = Alignment.Center,
        ) {
            Text(label.take(1), color = CafeAccent, style = MaterialTheme.typography.labelMedium)
        }
    }
}
