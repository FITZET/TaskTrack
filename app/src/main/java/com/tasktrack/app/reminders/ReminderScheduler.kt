package com.tasktrack.app.reminders

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.tasktrack.app.data.TaskEntity
import com.tasktrack.app.data.Importance
import com.tasktrack.app.data.TaskStatus
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

class ReminderScheduler(private val context: Context) {
    fun schedule(task: TaskEntity) {
        val work = WorkManager.getInstance(context)
        val tag = "task-reminder-${task.id}"
        work.cancelAllWorkByTag(tag)
        if (task.dueAt == null || task.status !in setOf(TaskStatus.NOT_STARTED, TaskStatus.IN_PROGRESS)) {
            return
        }
        val zone = ZoneId.systemDefault()
        val due = Instant.ofEpochMilli(task.dueAt).atZone(zone)
        val stages = when (task.importance) {
            Importance.CRITICAL -> setOf(task.reminderDays, minOf(3, task.reminderDays), minOf(1, task.reminderDays), 0)
            Importance.IMPORTANT -> setOf(task.reminderDays, minOf(1, task.reminderDays), 0)
            Importance.NORMAL, Importance.OPTIONAL -> setOf(task.reminderDays)
        }.filter { it >= 0 }.distinct()
        stages.forEach { daysBefore ->
            val reminder = due.minusDays(daysBefore.toLong()).with(LocalTime.of(9, 0))
            val delay = ChronoUnit.MILLIS.between(Instant.now(), reminder.toInstant()).coerceAtLeast(0)
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInputData(Data.Builder().putLong(ReminderWorker.TASK_ID, task.id)
                    .putInt(ReminderWorker.DAYS_BEFORE, daysBefore).build())
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .addTag(tag)
                .build()
            work.enqueueUniqueWork("$tag-$daysBefore", ExistingWorkPolicy.REPLACE, request)
        }
    }
}

