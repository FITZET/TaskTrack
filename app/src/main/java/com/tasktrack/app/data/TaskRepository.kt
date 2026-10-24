package com.tasktrack.app.data

import androidx.room.withTransaction
import com.tasktrack.app.reminders.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

class TaskRepository(
    private val db: AppDatabase,
    private val reminders: ReminderScheduler
) {
    private val taskDao = db.taskDao()
    private val templateDao = db.templateDao()

    fun observeRankedTasks(): Flow<List<TaskWithMeta>> = combine(
        taskDao.observeAll(), taskDao.observeDependencies()
    ) { tasks, dependencies -> rank(tasks, dependencies) }

    fun observeTask(id: Long) = taskDao.observe(id)
    fun observeChildren(id: Long) = taskDao.observeChildren(id)
    fun observeEvents(id: Long) = taskDao.observeEvents(id)
    fun observeAllEvents() = taskDao.observeEvents()
    fun observeTemplates() = templateDao.observeTemplates()

    suspend fun createTask(task: TaskEntity): Long = db.withTransaction {
        val id = taskDao.insert(task)
        taskDao.addEvent(TaskEventEntity(taskId = id, type = EventType.CREATED))
        reminders.schedule(task.copy(id = id))
        id
    }

    suspend fun updateTask(newTask: TaskEntity) = db.withTransaction {
        val old = taskDao.get(newTask.id) ?: return@withTransaction
        val now = System.currentTimeMillis()
        var adjusted = newTask.copy(updatedAt = now)
        if (old.status != newTask.status) {
            val type = when (newTask.status) {
                TaskStatus.IN_PROGRESS -> EventType.STARTED
                TaskStatus.COMPLETED -> EventType.COMPLETED
                TaskStatus.SKIPPED -> EventType.SKIPPED
                TaskStatus.CANCELLED -> EventType.CANCELLED
                TaskStatus.NOT_STARTED -> if (old.status == TaskStatus.COMPLETED) EventType.REOPENED else EventType.STATUS_CHANGED
            }
            adjusted = adjusted.copy(
                startedAt = if (newTask.status == TaskStatus.IN_PROGRESS) old.startedAt ?: now else old.startedAt,
                completedAt = if (newTask.status == TaskStatus.COMPLETED) now else if (old.status == TaskStatus.COMPLETED) null else old.completedAt
            )
            taskDao.addEvent(TaskEventEntity(taskId = newTask.id, type = type, detail = "${old.status.label} → ${newTask.status.label}"))
        }
        if (old.dueAt != newTask.dueAt) taskDao.addEvent(TaskEventEntity(taskId = newTask.id, type = EventType.DUE_CHANGED))
        if (old.importance != newTask.importance) taskDao.addEvent(TaskEventEntity(taskId = newTask.id, type = EventType.IMPORTANCE_CHANGED, detail = "${old.importance.label} → ${newTask.importance.label}"))
        taskDao.update(adjusted)
        reminders.schedule(adjusted)
        adjusted.parentId?.let { parentId -> updateParentFromChildren(parentId) }
    }

    suspend fun addTemporaryStep(parentId: Long, title: String, dueAt: Long?) {
        val parent = taskDao.get(parentId) ?: return
        val stepId = createTask(TaskEntity(title = title, category = parent.category, importance = parent.importance,
            dueAt = dueAt, reminderDays = parent.reminderDays, parentId = parentId, sortOrder = Int.MAX_VALUE))
        taskDao.addEvent(TaskEventEntity(taskId = parentId, type = EventType.STEP_ADDED, detail = "#$stepId $title"))
    }

    suspend fun addDependency(taskId: Long, dependsOnTaskId: Long) {
        if (taskId != dependsOnTaskId) taskDao.addDependency(TaskDependencyEntity(taskId, dependsOnTaskId))
    }

    suspend fun templateBundle(id: Long): TemplateBundle? {
        val template = templateDao.getTemplates().firstOrNull { it.id == id } ?: return null
        val steps = templateDao.getSteps(id)
        return TemplateBundle(template, steps, if (steps.isEmpty()) emptyList() else templateDao.getDependencies(steps.map { it.id }))
    }

    suspend fun createTemplate(name: String, description: String, steps: List<TemplateStepDraft>) = db.withTransaction {
        val templateId = templateDao.addTemplate(TemplateEntity(name = name, description = description))
        val ids = steps.mapIndexed { index, step ->
            templateDao.addStep(TemplateStepEntity(templateId = templateId, title = step.title,
                daysBeforeDeadline = step.daysBeforeDeadline, sortOrder = index))
        }
        steps.forEachIndexed { index, step ->
            step.dependsOnSteps.distinct().filter { it in ids.indices && it != index }.forEach { dependencyIndex ->
                templateDao.addDependency(TemplateDependencyEntity(ids[index], ids[dependencyIndex]))
            }
        }
    }

    suspend fun deleteTemplate(id: Long) = templateDao.deleteTemplate(id)

    suspend fun applyTemplate(templateId: Long, title: String, category: TaskCategory, importance: Importance,
                              deadline: Long, reminderDays: Int): Long = db.withTransaction {
        val bundle = templateBundle(templateId) ?: error("模板不存在")
        val instance = UUID.randomUUID().toString()
        val parentId = taskDao.insert(TaskEntity(title = title, description = bundle.template.description, category = category,
            importance = importance, dueAt = deadline, reminderDays = reminderDays, templateInstanceId = instance))
        taskDao.addEvent(TaskEventEntity(taskId = parentId, type = EventType.CREATED))
        taskDao.addEvent(TaskEventEntity(taskId = parentId, type = EventType.TEMPLATE_APPLIED, detail = bundle.template.name))
        val ids = mutableMapOf<Long, Long>()
        bundle.steps.forEach { step ->
            val due = Instant.ofEpochMilli(deadline).minus(step.daysBeforeDeadline.toLong(), ChronoUnit.DAYS).toEpochMilli()
            val id = taskDao.insert(TaskEntity(title = step.title, category = category, importance = importance,
                dueAt = due, reminderDays = reminderDays, parentId = parentId, templateInstanceId = instance, sortOrder = step.sortOrder))
            ids[step.id] = id
            taskDao.addEvent(TaskEventEntity(taskId = id, type = EventType.CREATED, detail = "来自模板：${bundle.template.name}"))
            reminders.schedule(TaskEntity(id = id, title = step.title, category = category, importance = importance,
                dueAt = due, reminderDays = reminderDays, parentId = parentId))
        }
        bundle.dependencies.forEach { dep ->
            val task = ids[dep.stepId]
            val dependsOn = ids[dep.dependsOnStepId]
            if (task != null && dependsOn != null) taskDao.addDependency(TaskDependencyEntity(task, dependsOn))
        }
        reminders.schedule(taskDao.get(parentId)!!)
        parentId
    }

    suspend fun seedTemplatesIfEmpty() {
        if (templateDao.getTemplates().isNotEmpty()) return
        createTemplate("会议论文中稿后", "会议论文录用后的标准流程", listOf(
            TemplateStepDraft("根据审稿意见修改论文", 28),
            TemplateStepDraft("提交论文终稿", 21, listOf(0)),
            TemplateStepDraft("Copyright", 18, listOf(1)),
            TemplateStepDraft("注册", 14, listOf(2)),
            TemplateStepDraft("提交 PPT", 5, listOf(3)),
            TemplateStepDraft("机票酒店安排", 5, listOf(3))
        ))
        createTemplate("期刊论文中稿后", "期刊论文录用后的标准流程", listOf(
            TemplateStepDraft("根据审稿意见修改论文", 28),
            TemplateStepDraft("提交终稿", 21, listOf(0)),
            TemplateStepDraft("Copyright", 14, listOf(1)),
            TemplateStepDraft("校验核对", 7, listOf(2)),
            TemplateStepDraft("缴费", 3, listOf(3))
        ))
    }

    suspend fun rescheduleAllReminders() {
        taskDao.getAll().forEach(reminders::schedule)
    }

    private suspend fun updateParentFromChildren(parentId: Long) {
        val parent = taskDao.get(parentId) ?: return
        val children = taskDao.getChildren(parentId)
        if (children.isEmpty()) return
        val allTerminal = children.all { it.status in setOf(TaskStatus.COMPLETED, TaskStatus.SKIPPED, TaskStatus.CANCELLED) }
        val now = System.currentTimeMillis()
        if (allTerminal && parent.status != TaskStatus.COMPLETED) {
            taskDao.update(parent.copy(status = TaskStatus.COMPLETED, completedAt = now, updatedAt = now))
            taskDao.addEvent(TaskEventEntity(taskId = parentId, type = EventType.COMPLETED, detail = "全部有效步骤已结束，自动完成"))
            reminders.schedule(parent.copy(status = TaskStatus.COMPLETED))
        } else if (!allTerminal && parent.status == TaskStatus.COMPLETED) {
            taskDao.update(parent.copy(status = TaskStatus.IN_PROGRESS, completedAt = null, updatedAt = now,
                startedAt = parent.startedAt ?: now))
            taskDao.addEvent(TaskEventEntity(taskId = parentId, type = EventType.REOPENED, detail = "步骤重新打开"))
        }
    }

    private fun rank(tasks: List<TaskEntity>, deps: List<TaskDependencyEntity>): List<TaskWithMeta> {
        val byId = tasks.associateBy { it.id }
        val activeStatuses = setOf(TaskStatus.NOT_STARTED, TaskStatus.IN_PROGRESS)
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        return tasks.filter { it.parentId == null && it.status in activeStatuses }.map { task ->
            val children = tasks.filter { it.parentId == task.id }
            val effective = if (children.isEmpty()) listOf(task) else children.filter { it.status !in setOf(TaskStatus.SKIPPED, TaskStatus.CANCELLED) }
            val completed = effective.count { it.status == TaskStatus.COMPLETED }
            val progress = if (children.isEmpty()) null else if (effective.isEmpty()) 100 else completed * 100 / effective.size
            val relevantIds = (children.map { it.id } + task.id).toSet()
            val activeChildren = children.filter { it.status in activeStatuses }
            val executableChildren = activeChildren.filter { child ->
                deps.filter { it.taskId == child.id }.all { byId[it.dependsOnTaskId]?.status == TaskStatus.COMPLETED }
            }
            val blocked = activeChildren.isNotEmpty() && executableChildren.isEmpty()
            val blocks = deps.count { it.dependsOnTaskId in relevantIds && byId[it.taskId]?.status in activeStatuses }
            var score = task.importance.weight + blocks * 25
            val reasons = mutableListOf(task.importance.label)
            val focusTask = executableChildren.minByOrNull { it.dueAt ?: Long.MAX_VALUE }
            val focusDue = focusTask?.dueAt ?: task.dueAt
            val focusReminderDays = focusTask?.reminderDays ?: task.reminderDays
            focusDue?.let { due ->
                val days = ChronoUnit.DAYS.between(LocalDate.now(zone), Instant.ofEpochMilli(due).atZone(zone).toLocalDate()).toInt()
                when {
                    due < now -> { score += 220; reasons += "已逾期" }
                    days <= focusReminderDays -> { score += 150 + (focusReminderDays - days).coerceAtLeast(0) * 4; reasons += "距 DDL ${days.coerceAtLeast(0)} 天" }
                    else -> reasons += "尚未进入提醒期"
                }
            }
            focusTask?.let { reasons += "下一步：${it.title}" }
            if (blocks > 0) reasons += "阻塞 $blocks 项后续任务"
            if (blocked) { score -= 180; reasons += "当前被阻塞" }
            val nextSteps = if (children.isEmpty()) listOf(task) else executableChildren
                .sortedWith(compareBy<TaskEntity> { it.dueAt == null }.thenBy { it.dueAt ?: Long.MAX_VALUE })
            TaskWithMeta(task, blocked, blocks, progress, nextSteps, score, reasons.joinToString(" · "))
        }.sortedWith(compareByDescending<TaskWithMeta> { it.score }.thenBy { it.task.dueAt ?: Long.MAX_VALUE })
    }
}

