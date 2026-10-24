package com.tasktrack.app.report

import com.tasktrack.app.data.EventType
import com.tasktrack.app.data.Importance
import com.tasktrack.app.data.TaskCategory
import com.tasktrack.app.data.TaskEntity
import com.tasktrack.app.data.TaskEventEntity
import com.tasktrack.app.data.TaskStatus
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneId

class ReportBuilderTest {
    @Test
    fun monthlyReportIncludesFactsAndEscapesJson() {
        val month = YearMonth.now()
        val timestamp = month.atDay(10).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val task = TaskEntity(id = 7, title = "论文 \"终稿\"", category = TaskCategory.PERSONAL,
            importance = Importance.CRITICAL, status = TaskStatus.COMPLETED,
            dueAt = timestamp - 1_000, completedAt = timestamp)
        val events = listOf(
            TaskEventEntity(taskId = 7, type = EventType.CREATED, occurredAt = timestamp),
            TaskEventEntity(taskId = 7, type = EventType.COMPLETED, occurredAt = timestamp)
        )

        val markdown = ReportBuilder.markdown(month, listOf(task), events)
        val json = ReportBuilder.json(month, listOf(task), events)

        assertTrue(markdown.contains("完成任务：1"))
        assertTrue(markdown.contains("完成时已逾期：1"))
        assertTrue(json.contains("论文 \\\"终稿\\\""))
        assertTrue(json.contains("\"type\":\"COMPLETED\""))
    }
}

