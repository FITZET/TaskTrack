package com.tasktrack.app

import android.app.Application
import androidx.room.Room
import com.tasktrack.app.data.AppDatabase
import com.tasktrack.app.data.BackupService
import com.tasktrack.app.data.TaskRepository
import com.tasktrack.app.reminders.ReminderScheduler

class TaskTrackApplication : Application() {
    val database by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "task-track.db")
            .fallbackToDestructiveMigration(false)
            .build()
    }
    val repository by lazy { TaskRepository(database, ReminderScheduler(this)) }
    val backups by lazy { BackupService(database) }
}

