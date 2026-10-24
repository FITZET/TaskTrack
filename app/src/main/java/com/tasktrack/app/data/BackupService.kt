package com.tasktrack.app.data

import androidx.room.withTransaction
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BackupBundle(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val tasks: List<TaskEntity>,
    val taskDependencies: List<TaskDependencyEntity>,
    val events: List<TaskEventEntity>,
    val templates: List<TemplateEntity>,
    val templateSteps: List<TemplateStepEntity>,
    val templateDependencies: List<TemplateDependencyEntity>
)

class BackupService(private val database: AppDatabase) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    suspend fun export(): String {
        val tasks = database.taskDao()
        val templates = database.templateDao()
        return json.encodeToString(BackupBundle(
            tasks = tasks.getAll(),
            taskDependencies = tasks.getDependencies(),
            events = tasks.getAllEvents(),
            templates = templates.getTemplates(),
            templateSteps = templates.getAllSteps(),
            templateDependencies = templates.getAllDependencies()
        ))
    }

    suspend fun restore(text: String) {
        val bundle = json.decodeFromString<BackupBundle>(text)
        require(bundle.version == 1) { "不支持的备份版本：${bundle.version}" }
        database.withTransaction {
            val tasks = database.taskDao()
            val templates = database.templateDao()
            tasks.clearEvents(); tasks.clearDependencies(); tasks.clearTasks()
            templates.clearDependencies(); templates.clearSteps(); templates.clearTemplates()
            templates.addTemplates(bundle.templates)
            templates.addSteps(bundle.templateSteps)
            templates.addDependencies(bundle.templateDependencies)
            tasks.insertAll(bundle.tasks)
            tasks.addDependencies(bundle.taskDependencies)
            tasks.addEvents(bundle.events)
        }
    }
}
