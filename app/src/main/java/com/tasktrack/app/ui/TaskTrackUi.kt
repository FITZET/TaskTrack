package com.tasktrack.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Schema
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tasktrack.app.data.Importance
import com.tasktrack.app.data.TaskCategory
import com.tasktrack.app.data.TaskEntity
import com.tasktrack.app.data.TaskStatus
import com.tasktrack.app.data.TaskWithMeta
import com.tasktrack.app.data.TemplateEntity
import com.tasktrack.app.data.TemplateStepDraft
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Ink = Color(0xFF173D32)
private val Accent = Color(0xFFF5C451)
private val Canvas = Color(0xFFF7F9F5)
private val Danger = Color(0xFFBA1A1A)

private enum class AppTab(val label: String, val icon: ImageVector) {
    TODAY("当前", Icons.Default.Dashboard), CALENDAR("日历", Icons.Default.CalendarMonth),
    TEMPLATES("模板", Icons.Default.Schema), REPORT("复盘", Icons.Default.Assessment)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskTrackApp(vm: MainViewModel, requestRestore: () -> Unit, saveBackup: (String, String) -> Unit,
                 share: (String, String) -> Unit) {
    var tab by remember { mutableStateOf(AppTab.TODAY) }
    var addTask by remember { mutableStateOf(false) }
    var selectedTaskId by remember { mutableStateOf<Long?>(null) }
    val ranked by vm.ranked.collectAsStateWithLifecycle()
    val tasks by vm.allTasks.collectAsStateWithLifecycle()
    val templates by vm.templates.collectAsStateWithLifecycle()

    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = Ink, secondary = Accent, background = Canvas, surface = Color.White)) {
        Surface(Modifier.fillMaxSize(), color = Canvas) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(if (selectedTaskId == null) "任务指挥台" else "事项详情", fontWeight = FontWeight.Bold)
                                Text(if (selectedTaskId == null) "把精力留给真正重要的事" else "步骤、状态与截止时间", style = MaterialTheme.typography.labelSmall)
                            }
                        },
                        navigationIcon = if (selectedTaskId != null) {{ TextButton(onClick = { selectedTaskId = null }) { Text("返回") } }} else {{ }},
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Canvas)
                    )
                },
                bottomBar = {
                    if (selectedTaskId == null) NavigationBar(containerColor = Color.White) {
                        AppTab.entries.forEach { item ->
                            NavigationBarItem(selected = tab == item, onClick = { tab = item },
                                icon = { Icon(item.icon, null) }, label = { Text(item.label) })
                        }
                    }
                },
                floatingActionButton = {
                    if (selectedTaskId == null && tab == AppTab.TODAY) ExtendedFloatingActionButton(
                        onClick = { addTask = true }, icon = { Icon(Icons.Default.Add, null) }, text = { Text("新事项") },
                        containerColor = Accent, contentColor = Ink
                    )
                }
            ) { padding ->
                Box(Modifier.padding(padding).fillMaxSize()) {
                    val selected = selectedTaskId?.let { id -> tasks.firstOrNull { it.id == id } }
                    if (selected != null) {
                        TaskDetail(selected, tasks.filter { it.parentId == selected.id }, vm)
                    } else when (tab) {
                        AppTab.TODAY -> Dashboard(ranked, onOpen = { selectedTaskId = it })
                        AppTab.CALENDAR -> CalendarScreen(tasks, onOpen = { selectedTaskId = it })
                        AppTab.TEMPLATES -> TemplatesScreen(templates, vm) { selectedTaskId = it }
                        AppTab.REPORT -> ReportScreen(vm, requestRestore, saveBackup, share)
                    }
                }
            }
        }
    }
    if (addTask) TaskEditorDialog(onDismiss = { addTask = false }) { title, desc, category, importance, due, reminder ->
        vm.createTask(title, desc, category, importance, due, reminder) { selectedTaskId = it }
        addTask = false
    }
}

@Composable
private fun Dashboard(tasks: List<TaskWithMeta>, onOpen: (Long) -> Unit) {
    var category by remember { mutableStateOf<TaskCategory?>(null) }
    val visible = tasks.filter { category == null || it.task.category == category }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("现在最值得做", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = category == null, onClick = { category = null }, label = { Text("全部") })
                TaskCategory.entries.forEach { c -> FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c.label) }) }
            }
        }
        if (visible.isEmpty()) item { EmptyState("暂时没有待处理事项", "点击右下角添加第一件事") }
        items(visible, key = { it.task.id }) { meta -> RankedTaskCard(meta) { onOpen(meta.task.id) } }
        item { Spacer(Modifier.height(90.dp)) }
    }
}

@Composable
private fun RankedTaskCard(meta: TaskWithMeta, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ImportanceDot(meta.task.importance)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(meta.task.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${meta.task.category.label} · ${meta.task.importance.label}", style = MaterialTheme.typography.labelMedium, color = Ink.copy(alpha = .7f))
                }
                meta.progress?.let { Text("$it%", fontWeight = FontWeight.Bold, color = Ink) }
            }
            Spacer(Modifier.height(12.dp))
            Text(meta.reason, style = MaterialTheme.typography.bodySmall, color = if (meta.reason.contains("逾期")) Danger else Color.DarkGray)
            meta.task.dueAt?.let { Text("DDL ${formatDate(it)} · 提前 ${meta.task.reminderDays} 天提醒", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun TaskDetail(task: TaskEntity, children: List<TaskEntity>, vm: MainViewModel) {
    var addStep by remember { mutableStateOf(false) }
    val effective = children.filter { it.status !in setOf(TaskStatus.SKIPPED, TaskStatus.CANCELLED) }
    val progress = if (effective.isEmpty()) null else effective.count { it.status == TaskStatus.COMPLETED } * 100 / effective.size
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { ImportanceDot(task.importance); Spacer(Modifier.width(10.dp)); Text(task.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
                    if (task.description.isNotBlank()) { Spacer(Modifier.height(10.dp)); Text(task.description) }
                    Spacer(Modifier.height(12.dp))
                    Text("${task.category.label} · ${task.importance.label} · ${task.status.label}", color = Ink)
                    task.dueAt?.let { Text("DDL：${formatDate(it)}，提前 ${task.reminderDays} 天提醒") }
                    progress?.let { Text("自动进度：$it%（${effective.count { c -> c.status == TaskStatus.COMPLETED }}/${effective.size}）", fontWeight = FontWeight.SemiBold) }
                }
            }
        }
        item {
            Text("更新状态", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusButton(Icons.Default.PlayArrow, "进行中", task.status == TaskStatus.IN_PROGRESS) { vm.setStatus(task, TaskStatus.IN_PROGRESS) }
                StatusButton(Icons.Default.CheckCircle, "完成", task.status == TaskStatus.COMPLETED) { vm.setStatus(task, TaskStatus.COMPLETED) }
                StatusButton(Icons.Default.SkipNext, "跳过", task.status == TaskStatus.SKIPPED) { vm.setStatus(task, TaskStatus.SKIPPED) }
            }
            TextButton(onClick = { vm.setStatus(task, TaskStatus.CANCELLED) }) { Text("取消此事项", color = Danger) }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("流程步骤", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { addStep = true }) { Icon(Icons.Default.Add, null); Text("临时步骤") }
            }
        }
        if (children.isEmpty()) item { EmptyState("没有步骤", "普通事项可直接更新状态，也可以添加临时步骤") }
        items(children, key = { it.id }) { child -> StepRow(child) { vm.setStatus(child, it) } }
        item { Spacer(Modifier.height(32.dp)) }
    }
    if (addStep) SimpleTextDialog("新增临时步骤", "步骤名称", onDismiss = { addStep = false }) {
        vm.addStep(task.id, it, task.dueAt); addStep = false
    }
}

@Composable
private fun StepRow(task: TaskEntity, onStatus: (TaskStatus) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onStatus(if (task.status == TaskStatus.COMPLETED) TaskStatus.NOT_STARTED else TaskStatus.COMPLETED) }) {
                Icon(if (task.status == TaskStatus.COMPLETED) Icons.Default.CheckCircle else Icons.Default.Schedule, null,
                    tint = if (task.status == TaskStatus.COMPLETED) Ink else Color.Gray)
            }
            Column(Modifier.weight(1f)) {
                Text(task.title, fontWeight = FontWeight.Medium, color = if (task.status in setOf(TaskStatus.SKIPPED, TaskStatus.CANCELLED)) Color.Gray else Color.Unspecified)
                Text("${task.status.label}${task.dueAt?.let { " · ${formatDate(it)}" } ?: ""}", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { onStatus(TaskStatus.SKIPPED) }) { Icon(Icons.Default.SkipNext, "跳过") }
            IconButton(onClick = { onStatus(TaskStatus.CANCELLED) }) { Icon(Icons.Default.Close, "取消") }
        }
    }
}

@Composable
private fun CalendarScreen(tasks: List<TaskEntity>, onOpen: (Long) -> Unit) {
    val active = tasks.filter { it.dueAt != null && it.status !in setOf(TaskStatus.COMPLETED, TaskStatus.SKIPPED, TaskStatus.CANCELLED) }
        .sortedBy { it.dueAt }.groupBy { formatDate(it.dueAt!!) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("任务 DDL 日历", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("只展示尚未结束的事项和步骤") }
        if (active.isEmpty()) item { EmptyState("没有待办 DDL", "设置截止日期后会出现在这里") }
        active.forEach { (date, list) ->
            item { Text(date, fontWeight = FontWeight.Bold, color = Ink, modifier = Modifier.padding(top = 12.dp)) }
            items(list, key = { it.id }) { task ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(task.parentId ?: task.id) }, colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        ImportanceDot(task.importance); Spacer(Modifier.width(10.dp)); Column { Text(task.title, fontWeight = FontWeight.Medium); Text("${task.category.label} · ${task.status.label}", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplatesScreen(templates: List<TemplateEntity>, vm: MainViewModel, onCreated: (Long) -> Unit) {
    var apply by remember { mutableStateOf<TemplateEntity?>(null) }
    var create by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("流程模板", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("一次生成整套步骤和相对 DDL") }
                FloatingActionButton(onClick = { create = true }, containerColor = Accent) { Icon(Icons.Default.Add, null) }
            }
        }
        items(templates, key = { it.id }) { template ->
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(template.name, fontWeight = FontWeight.Bold); Text(template.description, style = MaterialTheme.typography.bodySmall) }
                    Button(onClick = { apply = template }) { Text("使用") }
                    IconButton(onClick = { vm.deleteTemplate(template.id) }) { Icon(Icons.Default.Delete, "删除", tint = Color.Gray) }
                }
            }
        }
    }
    apply?.let { template -> TemplateApplyDialog(template, onDismiss = { apply = null }) { title, category, importance, deadline, reminder ->
        vm.applyTemplate(template.id, title, category, importance, deadline, reminder, onCreated)
        apply = null
    } }
    if (create) CreateTemplateDialog(onDismiss = { create = false }) { name, description, steps ->
        vm.createTemplate(name, description, steps); create = false
    }
}

@Composable
private fun ReportScreen(vm: MainViewModel, requestRestore: () -> Unit, saveBackup: (String, String) -> Unit,
                         share: (String, String) -> Unit) {
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf("点击下方按钮生成本月事实数据。报告会包含创建、完成、延期、跳过、取消和 DDL 修改记录。") }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("月度 AI 复盘", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("数据留在手机中，只有主动分享时才会离开应用。") }
        item { Card(colors = CardDefaults.cardColors(containerColor = Color.White)) { Text(preview, Modifier.padding(18.dp), style = MaterialTheme.typography.bodyMedium) } }
        item {
            Button(onClick = { scope.launch { preview = vm.reports().first } }, Modifier.fillMaxWidth()) { Text("生成并预览本月报告") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { scope.launch { val (md, _) = vm.reports(); share("任务指挥台月度报告-${YearMonth.now()}.md", md) } }, Modifier.fillMaxWidth()) { Text("分享 Markdown 给 AI") }
            OutlinedButton(onClick = { scope.launch { val (_, json) = vm.reports(); share("任务指挥台数据-${YearMonth.now()}.json", json) } }, Modifier.fillMaxWidth()) { Text("分享结构化 JSON") }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            OutlinedButton(onClick = { scope.launch { saveBackup("task-track-backup-${LocalDate.now()}.json", vm.exportBackup()) } }, Modifier.fillMaxWidth()) { Text("导出完整本地备份") }
            TextButton(onClick = requestRestore, Modifier.fillMaxWidth()) { Text("从备份文件恢复") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskEditorDialog(onDismiss: () -> Unit, onSave: (String, String, TaskCategory, Importance, Long?, Int) -> Unit) {
    var title by remember { mutableStateOf("") }; var desc by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(TaskCategory.PERSONAL) }; var importance by remember { mutableStateOf(Importance.NORMAL) }
    var due by remember { mutableStateOf<Long?>(null) }; var reminder by remember { mutableStateOf("3") }; var datePicker by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("添加事项") }, confirmButton = {
        Button(onClick = { onSave(title, desc, category, importance, due, reminder.toIntOrNull() ?: 3) }, enabled = title.isNotBlank()) { Text("保存") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }, text = {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { OutlinedTextField(title, { title = it }, label = { Text("事项名称") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(desc, { desc = it }, label = { Text("备注（可选）") }, modifier = Modifier.fillMaxWidth()) }
            item { ChoiceRows(category, { category = it }, importance, { importance = it }) }
            item { OutlinedButton(onClick = { datePicker = true }, Modifier.fillMaxWidth()) { Text(due?.let { "DDL：${formatDate(it)}" } ?: "设置 DDL") } }
            item { OutlinedTextField(reminder, { reminder = it.filter(Char::isDigit) }, label = { Text("提前多少天开始提醒") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        }
    })
    if (datePicker) AppDatePicker(due, { datePicker = false }) { due = deadlineEndOfDay(it); datePicker = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateApplyDialog(template: TemplateEntity, onDismiss: () -> Unit, onApply: (String, TaskCategory, Importance, Long, Int) -> Unit) {
    var title by remember { mutableStateOf(template.name.removeSuffix("后")) }; var category by remember { mutableStateOf(TaskCategory.PERSONAL) }
    var importance by remember { mutableStateOf(Importance.IMPORTANT) }; var deadline by remember { mutableStateOf<Long?>(null) }
    var reminder by remember { mutableStateOf("3") }; var picker by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("使用 ${template.name}") }, confirmButton = {
        Button(onClick = { deadline?.let { onApply(title, category, importance, it, reminder.toIntOrNull() ?: 3) } }, enabled = title.isNotBlank() && deadline != null) { Text("生成流程") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }, text = {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { OutlinedTextField(title, { title = it }, label = { Text("事项名称") }, modifier = Modifier.fillMaxWidth()) }
            item { ChoiceRows(category, { category = it }, importance, { importance = it }) }
            item { OutlinedButton(onClick = { picker = true }, Modifier.fillMaxWidth()) { Text(deadline?.let { "最终 DDL：${formatDate(it)}" } ?: "设置最终 DDL") } }
            item { OutlinedTextField(reminder, { reminder = it.filter(Char::isDigit) }, label = { Text("步骤提前提醒天数") }, modifier = Modifier.fillMaxWidth()) }
        }
    })
    if (picker) AppDatePicker(deadline, { picker = false }) { deadline = deadlineEndOfDay(it); picker = false }
}

@Composable
private fun CreateTemplateDialog(onDismiss: () -> Unit, onSave: (String, String, List<TemplateStepDraft>) -> Unit) {
    var name by remember { mutableStateOf("") }; var description by remember { mutableStateOf("") }
    var steps by remember { mutableStateOf("准备材料 | 14 |\n提交材料 | 7 | 1\n最终确认 | 1 | 2") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("自定义流程模板") }, confirmButton = {
        val parsed = steps.lines().mapNotNull { line ->
            val p = line.split("|")
            p.firstOrNull()?.trim()?.takeIf(String::isNotBlank)?.let { title ->
                TemplateStepDraft(title, p.getOrNull(1)?.trim()?.toIntOrNull() ?: 0,
                    p.getOrNull(2)?.split(",")?.mapNotNull { it.trim().toIntOrNull()?.minus(1) } ?: emptyList())
            }
        }
        Button(onClick = { onSave(name, description, parsed) }, enabled = name.isNotBlank() && parsed.isNotEmpty()) { Text("保存模板") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("模板名称") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(description, { description = it }, label = { Text("说明") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(steps, { steps = it }, label = { Text("步骤 | 提前天数 | 依赖步骤编号") }, minLines = 5, modifier = Modifier.fillMaxWidth())
            Text("依赖多个步骤时用逗号分隔；两个步骤填写同一个前置编号即可并行。", style = MaterialTheme.typography.bodySmall)
        }
    })
}

@Composable
private fun ChoiceRows(category: TaskCategory, onCategory: (TaskCategory) -> Unit, importance: Importance, onImportance: (Importance) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("栏目", fontWeight = FontWeight.SemiBold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) { TaskCategory.entries.forEach { FilterChip(selected = category == it, onClick = { onCategory(it) }, label = { Text(it.label, maxLines = 1) }) } }
        Text("重要性", fontWeight = FontWeight.SemiBold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) { Importance.entries.forEach { FilterChip(selected = importance == it, onClick = { onImportance(it) }, label = { Text(it.label) }) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppDatePicker(initial: Long?, onDismiss: () -> Unit, onSelect: (Long) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial)
    DatePickerDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let(onSelect) }) { Text("确定") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }) { DatePicker(state = state) }
}

@Composable
private fun SimpleTextDialog(title: String, label: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { OutlinedTextField(value, { value = it }, label = { Text(label) }) },
        confirmButton = { Button(onClick = { onSave(value) }, enabled = value.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun StatusButton(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) }, leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) })
}

@Composable
private fun ImportanceDot(importance: Importance) {
    val color = when (importance) { Importance.CRITICAL -> Color(0xFFC23B32); Importance.IMPORTANT -> Color(0xFFE98A2E); Importance.NORMAL -> Color(0xFF3E7C6A); Importance.OPTIONAL -> Color(0xFF9AA19D) }
    Box(Modifier.size(11.dp).background(color, CircleShape))
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }
}

private fun formatDate(epoch: Long): String = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

private fun deadlineEndOfDay(selectedUtcMillis: Long): Long {
    val date = Instant.ofEpochMilli(selectedUtcMillis).atZone(ZoneId.of("UTC")).toLocalDate()
    return date.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

