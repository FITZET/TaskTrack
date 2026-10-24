package com.tasktrack.app.report

import com.tasktrack.app.data.EventType
import com.tasktrack.app.data.TaskEntity
import com.tasktrack.app.data.TaskEventEntity
import com.tasktrack.app.data.TaskStatus
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ReportBuilder {
    fun markdown(month: YearMonth, tasks: List<TaskEntity>, events: List<TaskEventEntity>): String {
        val zone = ZoneId.systemDefault()
        val completedIds = events.filter { it.type == EventType.COMPLETED }.mapNotNull { it.taskId }.toSet()
        val created = events.count { it.type == EventType.CREATED }
        val completed = events.count { it.type == EventType.COMPLETED }
        val skipped = events.count { it.type == EventType.SKIPPED }
        val cancelled = events.count { it.type == EventType.CANCELLED }
        val changedDue = events.count { it.type == EventType.DUE_CHANGED }
        val overdue = tasks.filter { it.id in completedIds && it.dueAt != null && it.completedAt != null && it.completedAt > it.dueAt }
        val byCategory = tasks.filter { it.id in completedIds }.groupingBy { it.category.label }.eachCount()
        val bottlenecks = tasks.filter { it.status in setOf(TaskStatus.NOT_STARTED, TaskStatus.IN_PROGRESS) && it.dueAt != null && it.dueAt < System.currentTimeMillis() }
        return buildString {
            appendLine("# ${month.year} 年 ${month.monthValue} 月任务复盘数据")
            appendLine()
            appendLine("> 这是一份由任务指挥台自动生成的事实数据。请分析时间安排、延期模式、流程瓶颈，并提出下月可执行的改进建议。")
            appendLine()
            appendLine("## 月度概览")
            appendLine("- 新建任务：$created")
            appendLine("- 完成任务：$completed")
            appendLine("- 跳过任务：$skipped")
            appendLine("- 取消任务：$cancelled")
            appendLine("- 修改 DDL：$changedDue 次")
            appendLine("- 完成时已逾期：${overdue.size}")
            appendLine()
            appendLine("## 各栏目完成情况")
            if (byCategory.isEmpty()) appendLine("- 本月没有完成记录")
            byCategory.forEach { (name, count) -> appendLine("- $name：$count") }
            appendLine()
            appendLine("## 当前逾期且未完成")
            if (bottlenecks.isEmpty()) appendLine("- 无")
            bottlenecks.forEach { appendLine("- [${it.importance.label}] ${it.title}（DDL：${format(it.dueAt!!, zone)}）") }
            appendLine()
            appendLine("## 本月事件流水")
            events.forEach { event ->
                val task = tasks.firstOrNull { it.id == event.taskId }
                appendLine("- ${format(event.occurredAt, zone)} · ${event.type.label} · ${task?.title ?: "系统"}${event.detail.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}")
            }
        }
    }

    fun json(month: YearMonth, tasks: List<TaskEntity>, events: List<TaskEventEntity>): String = buildString {
        append("{\"month\":\"").append(month).append("\",\"tasks\":[")
        tasks.forEachIndexed { index, t ->
            if (index > 0) append(',')
            append("{\"id\":${t.id},\"title\":\"").append(escape(t.title)).append("\",\"category\":\"")
                .append(t.category.name).append("\",\"importance\":\"").append(t.importance.name)
                .append("\",\"status\":\"").append(t.status.name).append("\",\"dueAt\":").append(t.dueAt ?: "null")
                .append(",\"createdAt\":${t.createdAt},\"startedAt\":${t.startedAt ?: "null"},\"completedAt\":${t.completedAt ?: "null"}}")
        }
        append("],\"events\":[")
        events.forEachIndexed { index, e ->
            if (index > 0) append(',')
            append("{\"taskId\":${e.taskId ?: "null"},\"type\":\"").append(e.type.name)
                .append("\",\"occurredAt\":${e.occurredAt},\"detail\":\"").append(escape(e.detail)).append("\"}")
        }
        append("]}")
    }

    private fun escape(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
    private fun format(epoch: Long, zone: ZoneId) = Instant.ofEpochMilli(epoch).atZone(zone).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
}

