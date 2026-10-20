package cn.lesson.core

import java.time.LocalTime
import java.time.ZoneId

data class ReminderEvent(val key: String, val at: Long, val title: String, val text: String, val course: Boolean)
object ReminderPlan {
    /** An inexact alarm can be delayed past other reminders; deliver that whole due batch. */
    fun dueEvents(state: AppState, scheduledAt: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<ReminderEvent> =
        events(state,maxOf(scheduledAt-1,now-6*60*60*1000L),zone).filter {it.at <= now}

    fun events(state: AppState, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<ReminderEvent> = buildList {
        if (state.preferences.courseReminders && state.term.monday != null) {
            ScheduleEngine.occurrences(state.schedule).filter { it.lesson.reminder }.forEach { occurrence ->
                val lesson = occurrence.lesson
                val time = state.term.periods.getOrNull(lesson.startPeriod - 1)?.start.orEmpty()
                if (time.isNotBlank()) {
                    val date = state.term.date(occurrence.week, lesson.day)!!
                    val at = date.atTime(LocalTime.parse(time)).atZone(zone).toInstant().toEpochMilli() - state.preferences.leadMinutes * 60000L
                    if (at > now) add(ReminderEvent("course:${lesson.id}:${occurrence.anchorWeek}", at, lesson.name,
                        "${lesson.room.ifBlank { "教室待定" }} · ${lesson.periodLabel} · $time 上课", true))
                }
            }
        }
        if (state.preferences.todoReminders) state.todos.filter { it.doneAt == null && it.remindAt != null && it.remindAt > now }.forEach {
            add(ReminderEvent("todo:${it.id}", it.remindAt!!, it.title, it.notes.ifBlank { "你有一项待办需要处理" }, false))
        }
    }.sortedBy { it.at }
}
