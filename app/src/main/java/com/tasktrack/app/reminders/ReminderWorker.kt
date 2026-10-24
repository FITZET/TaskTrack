package com.tasktrack.app.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tasktrack.app.R
import com.tasktrack.app.TaskTrackApplication
import com.tasktrack.app.data.EventType
import com.tasktrack.app.data.TaskEventEntity
import com.tasktrack.app.data.TaskStatus

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val taskId = inputData.getLong(TASK_ID, -1)
        val daysBefore = inputData.getInt(DAYS_BEFORE, 0)
        val app = applicationContext as TaskTrackApplication
        val task = app.database.taskDao().get(taskId) ?: return Result.success()
        if (task.status !in setOf(TaskStatus.NOT_STARTED, TaskStatus.IN_PROGRESS)) return Result.success()
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "任务提醒", NotificationManager.IMPORTANCE_HIGH))
        if (ActivityCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            manager.notify((taskId * 10 + daysBefore).toInt(), NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("${task.importance.label}任务即将到期")
                .setContentText(if (daysBefore == 0) "${task.title} · 今天截止" else "${task.title} · 还有 $daysBefore 天")
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build())
        }
        app.database.taskDao().addEvent(TaskEventEntity(taskId = taskId, type = EventType.REMINDER_FIRED,
            detail = if (daysBefore == 0) "DDL 当天" else "DDL 前 $daysBefore 天"))
        return Result.success()
    }

    companion object {
        const val TASK_ID = "task_id"
        const val DAYS_BEFORE = "days_before"
        private const val CHANNEL_ID = "task_deadlines"
    }
}

