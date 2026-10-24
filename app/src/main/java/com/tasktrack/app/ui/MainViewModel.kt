package com.tasktrack.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tasktrack.app.TaskTrackApplication
import com.tasktrack.app.data.Importance
import com.tasktrack.app.data.TaskCategory
import com.tasktrack.app.data.TaskEntity
import com.tasktrack.app.data.TaskStatus
import com.tasktrack.app.data.TemplateStepDraft
import com.tasktrack.app.report.ReportBuilder
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.ZoneId

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TaskTrackApplication
    private val repository = app.repository

    val ranked = repository.observeRankedTasks().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val templates = repository.observeTemplates().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val allTasks: StateFlow<List<TaskEntity>> = app.database.taskDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init { viewModelScope.launch { repository.seedTemplatesIfEmpty() } }

    fun createTask(title: String, description: String, category: TaskCategory, importance: Importance,
                   dueAt: Long?, reminderDays: Int, parentId: Long? = null, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch {
            onCreated(repository.createTask(TaskEntity(title = title.trim(), description = description.trim(), category = category,
                importance = importance, dueAt = dueAt, reminderDays = reminderDays.coerceAtLeast(0), parentId = parentId)))
        }
    }

    fun setStatus(task: TaskEntity, status: TaskStatus) = viewModelScope.launch { repository.updateTask(task.copy(status = status)) }
    fun updateTask(task: TaskEntity) = viewModelScope.launch { repository.updateTask(task) }
    fun addStep(parentId: Long, title: String, dueAt: Long?) = viewModelScope.launch { repository.addTemporaryStep(parentId, title, dueAt) }

    fun applyTemplate(templateId: Long, title: String, category: TaskCategory, importance: Importance,
                      deadline: Long, reminderDays: Int, onCreated: (Long) -> Unit = {}) = viewModelScope.launch {
        onCreated(repository.applyTemplate(templateId, title.trim(), category, importance, deadline, reminderDays))
    }

    fun createTemplate(name: String, description: String, steps: List<TemplateStepDraft>) = viewModelScope.launch {
        repository.createTemplate(name.trim(), description.trim(), steps)
    }

    fun deleteTemplate(id: Long) = viewModelScope.launch { repository.deleteTemplate(id) }

    suspend fun reports(month: YearMonth = YearMonth.now()): Pair<String, String> {
        val zone = ZoneId.systemDefault()
        val from = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val tasks = app.database.taskDao().getAll()
        val events = app.database.taskDao().eventsBetween(from, to)
        return ReportBuilder.markdown(month, tasks, events) to ReportBuilder.json(month, tasks, events)
    }

    suspend fun exportBackup(): String = app.backups.export()

    fun restoreBackup(text: String, onResult: (Result<Unit>) -> Unit = {}) = viewModelScope.launch {
        onResult(runCatching { app.backups.restore(text); repository.rescheduleAllReminders() })
    }
}

