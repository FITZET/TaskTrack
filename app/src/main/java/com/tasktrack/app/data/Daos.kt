package com.tasktrack.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY updatedAt DESC") fun observeAll(): Flow<List<TaskEntity>>
    @Query("SELECT * FROM tasks WHERE id = :id") fun observe(id: Long): Flow<TaskEntity?>
    @Query("SELECT * FROM tasks WHERE id = :id") suspend fun get(id: Long): TaskEntity?
    @Query("SELECT * FROM tasks") suspend fun getAll(): List<TaskEntity>
    @Query("SELECT * FROM tasks WHERE parentId = :parentId ORDER BY sortOrder, createdAt") fun observeChildren(parentId: Long): Flow<List<TaskEntity>>
    @Query("SELECT * FROM tasks WHERE parentId = :parentId ORDER BY sortOrder, createdAt") suspend fun getChildren(parentId: Long): List<TaskEntity>
    @Insert suspend fun insert(task: TaskEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(tasks: List<TaskEntity>)
    @Update suspend fun update(task: TaskEntity)
    @Delete suspend fun delete(task: TaskEntity)

    @Query("SELECT * FROM task_dependencies") fun observeDependencies(): Flow<List<TaskDependencyEntity>>
    @Query("SELECT * FROM task_dependencies") suspend fun getDependencies(): List<TaskDependencyEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addDependency(item: TaskDependencyEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addDependencies(items: List<TaskDependencyEntity>)
    @Query("DELETE FROM task_dependencies WHERE taskId = :taskId AND dependsOnTaskId = :dependsOn") suspend fun removeDependency(taskId: Long, dependsOn: Long)

    @Insert suspend fun addEvent(event: TaskEventEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addEvents(events: List<TaskEventEntity>)
    @Query("SELECT * FROM task_events") suspend fun getAllEvents(): List<TaskEventEntity>
    @Query("SELECT * FROM task_events ORDER BY occurredAt DESC") fun observeEvents(): Flow<List<TaskEventEntity>>
    @Query("SELECT * FROM task_events WHERE taskId = :taskId ORDER BY occurredAt DESC") fun observeEvents(taskId: Long): Flow<List<TaskEventEntity>>
    @Query("SELECT * FROM task_events WHERE occurredAt >= :from AND occurredAt < :to ORDER BY occurredAt") suspend fun eventsBetween(from: Long, to: Long): List<TaskEventEntity>
    @Query("DELETE FROM task_events") suspend fun clearEvents()
    @Query("DELETE FROM task_dependencies") suspend fun clearDependencies()
    @Query("DELETE FROM tasks") suspend fun clearTasks()
}

@Dao
interface TemplateDao {
    @Query("SELECT * FROM templates ORDER BY name") fun observeTemplates(): Flow<List<TemplateEntity>>
    @Query("SELECT * FROM templates ORDER BY name") suspend fun getTemplates(): List<TemplateEntity>
    @Query("SELECT * FROM template_steps WHERE templateId = :templateId ORDER BY sortOrder") suspend fun getSteps(templateId: Long): List<TemplateStepEntity>
    @Query("SELECT * FROM template_steps ORDER BY templateId, sortOrder") suspend fun getAllSteps(): List<TemplateStepEntity>
    @Query("SELECT * FROM template_dependencies WHERE stepId IN (:stepIds)") suspend fun getDependencies(stepIds: List<Long>): List<TemplateDependencyEntity>
    @Query("SELECT * FROM template_dependencies") suspend fun getAllDependencies(): List<TemplateDependencyEntity>
    @Insert suspend fun addTemplate(template: TemplateEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addTemplates(items: List<TemplateEntity>)
    @Insert suspend fun addStep(step: TemplateStepEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addSteps(items: List<TemplateStepEntity>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addDependency(item: TemplateDependencyEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addDependencies(items: List<TemplateDependencyEntity>)
    @Query("DELETE FROM templates WHERE id = :id") suspend fun deleteTemplate(id: Long)
    @Query("DELETE FROM template_dependencies") suspend fun clearDependencies()
    @Query("DELETE FROM template_steps") suspend fun clearSteps()
    @Query("DELETE FROM templates") suspend fun clearTemplates()
}

