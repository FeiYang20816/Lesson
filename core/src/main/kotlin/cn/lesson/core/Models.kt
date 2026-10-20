package cn.lesson.core

import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class PeriodTime(val start: String = "", val end: String = "")
data class Term(
    val name: String = "我的学期",
    val monday: String? = null,
    val totalWeeks: Int = 21,
    val periods: List<PeriodTime> = List(12) { PeriodTime() }
) {
    fun date(week: Int, day: Int): LocalDate? = monday?.let {
        LocalDate.parse(it).plusDays(((week - 1) * 7 + day - 1).toLong())
    }
    fun currentWeek(today: LocalDate = LocalDate.now()): Int = monday?.let {
        Math.floorDiv(java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(it), today).toInt(), 7) + 1
    } ?: 1
}

data class Lesson(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val room: String = "",
    val day: Int = 1,
    val startPeriod: Int = 1,
    val endPeriod: Int = 2,
    val weeks: List<Int> = listOf(1),
    val teacher: String = "",
    val reminder: Boolean = true,
    val source: String = "手动添加"
) {
    val periodLabel: String get() = if (startPeriod == endPeriod) "第 ${startPeriod} 节" else "第 $startPeriod–$endPeriod 节"
    fun validate(maxWeeks: Int = 60) {
        require(id.isNotBlank() && room.length <= 500 && teacher.length <= 2000 && source.length <= 1000) { "课程信息无效" }
        require(name.isNotBlank() && name.length <= 200) { "请填写课程名称（最多 200 字）" }
        require(day in 1..7) { "星期必须在周一至周日之间" }
        require(startPeriod in 1..24 && endPeriod in startPeriod..24) { "请检查起止节次（1–24）" }
        require(weeks.isNotEmpty() && weeks.all { it in 1..maxWeeks }) { "请检查周次（1–$maxWeeks）" }
    }
}

/** A null week changes the entire repeating arrangement; a null replacement cancels it. */
data class LessonEdit(val lessonId: String, val week: Int?, val replacement: Lesson?)
data class CourseColor(val name: String, val index: Int)
data class Schedule(
    val originals: List<Lesson> = emptyList(),
    val added: List<Lesson> = emptyList(),
    val edits: List<LessonEdit> = emptyList(),
    val notes: List<String> = emptyList(),
    val colors: List<CourseColor> = emptyList(),
    val sourceName: String = ""
) {
    fun base(id: String): Lesson? = (originals + added).find { it.id == id }
    fun color(name: String): Int = colors.find { it.name == name }?.index ?: 0
    fun withColors(names: List<String>): Schedule {
        val updated = colors.toMutableList()
        names.distinct().filter { n -> updated.none { it.name == n } }.forEach {
            updated.add(CourseColor(it, (updated.maxOfOrNull { c -> c.index } ?: -1) + 1))
        }
        return copy(colors = updated)
    }
}

data class Occurrence(val lesson: Lesson, val anchorWeek: Int, val week: Int, val modified: Boolean)
data class Todo(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val notes: String = "",
    val dueAt: Long? = null,
    val remindAt: Long? = null,
    val doneAt: Long? = null
)
data class ChangeRecord(
    val id: String = UUID.randomUUID().toString(),
    val time: Long = System.currentTimeMillis(),
    val title: String,
    val before: String,
    val after: String,
    val lessonId: String? = null
)
data class Preferences(
    val courseReminders: Boolean = false,
    val todoReminders: Boolean = false,
    val leadMinutes: Int = 10,
    val showTimes: Boolean = false,
    val backgrounds: List<String> = listOf("", "", ""),
    val backgroundShade: Float = 0.85f
)
data class AppState(
    val schemaVersion: Int = 1,
    val term: Term = Term(),
    val schedule: Schedule = Schedule(),
    val todos: List<Todo> = emptyList(),
    val history: List<ChangeRecord> = emptyList(),
    val preferences: Preferences = Preferences()
)

fun AppState.validate() {
    require(schemaVersion == 1) { "不支持此备份版本" }
    require(term.totalWeeks in 1..60) { "学期周数应为 1–60" }
    require(term.name.isNotBlank() && term.name.length <= 200) { "学期名称无效" }
    term.monday?.let { require(LocalDate.parse(it).dayOfWeek.value == 1) { "第 1 周起始日期应为星期一" } }
    require(term.periods.size in 1..24) { "节次数应为 1–24" }
    term.periods.forEach {
        if (it.start.isNotBlank()) LocalTime.parse(it.start)
        if (it.end.isNotBlank()) LocalTime.parse(it.end)
        if (it.start.isNotBlank() && it.end.isNotBlank()) {
            require(LocalTime.parse(it.end).isAfter(LocalTime.parse(it.start))) { "下课时间须晚于上课时间" }
        }
    }
    val all = schedule.originals + schedule.added
    require(all.map { it.id }.distinct().size == all.size) { "备份包含重复课程标识" }
    require(all.size <= 5000 && todos.size <= 10000) { "备份数据量过大" }
    all.forEach { it.validate() }
    schedule.edits.forEach { e ->
        require(e.week == null || e.week in 1..60) { "修改记录周次无效" }
        e.replacement?.validate()
        require(e.replacement == null || e.replacement.id == e.lessonId) { "修改记录与课程不匹配" }
        require(e.week == null || e.replacement == null || e.replacement.weeks.size == 1) { "单次修改只能包含一个目标周次" }
    }
    require(schedule.edits.map { it.lessonId to it.week }.distinct().size == schedule.edits.size) { "备份包含重复修改" }
    require(todos.map { it.id }.distinct().size == todos.size) { "备份包含重复待办" }
    require(todos.all { it.id.isNotBlank() && it.title.isNotBlank() && it.title.length <= 200 && it.notes.length <= 20000 }) { "待办内容无效" }
    require(preferences.leadMinutes in 0..1440) { "提醒提前时间无效" }
    require(preferences.backgrounds.size == 3 && preferences.backgroundShade in 0.4f..1f) { "背景设置无效" }
    require(preferences.backgrounds.all { it.length <= 2000 }) { "背景路径无效" }
    require(schedule.colors.all { it.index >= 0 && it.name.isNotBlank() }) { "课程颜色无效" }
    require(schedule.sourceName.length <= 2000 && schedule.notes.all {it.length <= 100000}) { "课表备注无效" }
    require(history.all {it.id.isNotBlank() && it.title.isNotBlank() && it.before.length <= 200000 && it.after.length <= 200000}) { "修改记录无效" }
}

val weekDays = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
fun Lesson.description(): String = "$name · ${room.ifBlank { "教室待定" }} · ${weekDays[day - 1]} $periodLabel · 第 ${Weeks.format(weeks)} 周"
