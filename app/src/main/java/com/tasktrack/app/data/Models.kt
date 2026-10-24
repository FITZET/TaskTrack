package com.tasktrack.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
enum class TaskCategory(val label: String) {
    PERSONAL("个人发展"), PROJECT("项目工作"), ADMIN("行程与行政"), CHORE("杂事")
}

@Serializable
enum class Importance(val label: String, val weight: Int) {
    CRITICAL("关键", 400), IMPORTANT("重要", 300), NORMAL("一般", 200), OPTIONAL("可放弃", 100)
}

@Serializable
enum class TaskStatus(val label: String) {
    NOT_STARTED("未开始"), IN_PROGRESS("进行中"), COMPLETED("已完成"), SKIPPED("已跳过"), CANCELLED("已取消")
}

@Serializable
enum class EventType(val label: String) {
    CREATED("创建"), STARTED("开始"), COMPLETED("完成"), REOPENED("重新打开"),
    STATUS_CHANGED("状态变更"), DUE_CHANGED("修改截止时间"), IMPORTANCE_CHANGED("修改重要性"),
    SKIPPED("跳过"), CANCELLED("取消"), STEP_ADDED("新增临时步骤"), REMINDER_FIRED("提醒触发"),
    TEMPLATE_APPLIED("应用模板")
}

@Entity(tableName = "tasks", indices = [Index("parentId"), Index("dueAt"), Index("status")])
@Serializable
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String = "",
    val category: TaskCategory = TaskCategory.PERSONAL,
    val importance: Importance = Importance.NORMAL,
    val status: TaskStatus = TaskStatus.NOT_STARTED,
    val dueAt: Long? = null,
    val reminderDays: Int = 3,
    val parentId: Long? = null,
    val templateInstanceId: String? = null,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "task_dependencies",
    primaryKeys = ["taskId", "dependsOnTaskId"],
    foreignKeys = [
        ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["taskId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TaskEntity::class, parentColumns = ["id"], childColumns = ["dependsOnTaskId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("taskId"), Index("dependsOnTaskId")]
)
@Serializable
data class TaskDependencyEntity(val taskId: Long, val dependsOnTaskId: Long)

@Entity(tableName = "task_events", indices = [Index("taskId"), Index("occurredAt")])
@Serializable
data class TaskEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long? = null,
    val type: EventType,
    val occurredAt: Long = System.currentTimeMillis(),
    val detail: String = ""
)

@Entity(tableName = "templates")
@Serializable
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "template_steps",
    foreignKeys = [ForeignKey(entity = TemplateEntity::class, parentColumns = ["id"], childColumns = ["templateId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("templateId")]
)
@Serializable
data class TemplateStepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val title: String,
    val daysBeforeDeadline: Int,
    val sortOrder: Int
)

@Entity(
    tableName = "template_dependencies",
    primaryKeys = ["stepId", "dependsOnStepId"],
    foreignKeys = [
        ForeignKey(entity = TemplateStepEntity::class, parentColumns = ["id"], childColumns = ["stepId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TemplateStepEntity::class, parentColumns = ["id"], childColumns = ["dependsOnStepId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("stepId"), Index("dependsOnStepId")]
)
@Serializable
data class TemplateDependencyEntity(val stepId: Long, val dependsOnStepId: Long)

data class TaskWithMeta(
    val task: TaskEntity,
    val blocked: Boolean,
    val blocksCount: Int,
    val progress: Int?,
    val score: Int,
    val reason: String
)

data class TemplateBundle(
    val template: TemplateEntity,
    val steps: List<TemplateStepEntity>,
    val dependencies: List<TemplateDependencyEntity>
)

data class TemplateStepDraft(
    val title: String,
    val daysBeforeDeadline: Int,
    val dependsOnSteps: List<Int> = emptyList()
)

