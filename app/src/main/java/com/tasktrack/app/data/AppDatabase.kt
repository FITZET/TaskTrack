package com.tasktrack.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter fun category(value: String) = TaskCategory.valueOf(value)
    @TypeConverter fun category(value: TaskCategory) = value.name
    @TypeConverter fun importance(value: String) = Importance.valueOf(value)
    @TypeConverter fun importance(value: Importance) = value.name
    @TypeConverter fun status(value: String) = TaskStatus.valueOf(value)
    @TypeConverter fun status(value: TaskStatus) = value.name
    @TypeConverter fun event(value: String) = EventType.valueOf(value)
    @TypeConverter fun event(value: EventType) = value.name
}

@Database(
    entities = [TaskEntity::class, TaskDependencyEntity::class, TaskEventEntity::class,
        TemplateEntity::class, TemplateStepEntity::class, TemplateDependencyEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun templateDao(): TemplateDao
}

