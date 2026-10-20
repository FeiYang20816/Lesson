package cn.lesson.core

object ScheduleEngine {
    fun occurrences(schedule: Schedule, week: Int? = null): List<Occurrence> = buildList {
        (schedule.originals + schedule.added).forEach { original ->
            val edits = schedule.edits.filter { it.lessonId == original.id }
            val global = edits.find { it.week == null }
            val effective = if (global != null) global.replacement else original
            if (effective != null) {
                effective.weeks.forEach { anchor ->
                    if (edits.none { it.week == anchor }) add(Occurrence(effective, anchor, anchor, global != null))
                }
                edits.filter { it.week != null && it.replacement != null }.forEach { edit ->
                    val replacement = edit.replacement!!
                    replacement.weeks.forEach { target -> add(Occurrence(replacement, edit.week!!, target, true)) }
                }
            }
        }
    }.filter { week == null || it.week == week }
        .sortedWith(compareBy({ it.week }, { it.lesson.day }, { it.lesson.startPeriod }, { it.lesson.name }))

    fun conflicts(schedule: Schedule, week: Int): Set<String> {
        val list = occurrences(schedule, week)
        return buildSet {
            list.forEachIndexed { index, a ->
                list.drop(index + 1).forEach { b ->
                    if (a.lesson.day == b.lesson.day && a.lesson.startPeriod <= b.lesson.endPeriod && b.lesson.startPeriod <= a.lesson.endPeriod) {
                        add(a.lesson.id); add(b.lesson.id)
                    }
                }
            }
        }
    }
}

object Actions {
    private fun AppState.log(title: String, before: String, after: String, id: String? = null) =
        copy(history = listOf(ChangeRecord(title = title, before = before, after = after, lessonId = id)) + history)

    fun import(state: AppState, result: ImportResult, sourceName: String): AppState {
        require(result.lessons.isNotEmpty()) { "没有可以导入的课程" }
        val schedule = state.schedule.copy(originals = result.lessons, notes = result.notes, sourceName = sourceName)
            .withColors(result.lessons.map { it.name })
        val maxWeek = result.lessons.flatMap { it.weeks }.maxOrNull() ?: 1
        return state.copy(schedule = schedule, term = state.term.copy(
            name = result.termName.ifBlank { state.term.name }, totalWeeks = maxOf(state.term.totalWeeks, maxWeek)
        )).log("导入课表", "原 ${state.schedule.originals.size} 条上课安排", "$sourceName：${result.lessons.size} 条；人工修改与新增已保留")
    }

    fun add(state: AppState, lesson: Lesson): AppState {
        lesson.validate(state.term.totalWeeks)
        require(state.schedule.base(lesson.id) == null)
        return state.copy(schedule = state.schedule.copy(added = state.schedule.added + lesson).withColors(listOf(lesson.name)))
            .log("新增课程", "无", lesson.description(), lesson.id)
    }

    fun edit(state: AppState, id: String, anchorWeek: Int?, replacement: Lesson?): AppState {
        val base = requireNotNull(state.schedule.base(id)) { "原课程不存在" }
        replacement?.validate(state.term.totalWeeks)
        require(replacement == null || replacement.id == id)
        require(anchorWeek == null || replacement == null || replacement.weeks.size == 1) { "单次修改只能选择一个周次" }
        val before = if (anchorWeek == null) {
            val global = state.schedule.edits.find { it.lessonId == id && it.week == null }
            if (global == null) base.description() else global.replacement?.description() ?: "已取消"
        } else ScheduleEngine.occurrences(state.schedule).find { it.lesson.id == id && it.anchorWeek == anchorWeek }?.let {
            it.lesson.copy(weeks=listOf(it.week)).description()
        } ?: "已取消"
        val kept = state.schedule.edits.filterNot { it.lessonId == id && (anchorWeek == null || it.week == anchorWeek) }
        val schedule = state.schedule.copy(edits = kept + LessonEdit(id, anchorWeek, replacement)).withColors(listOfNotNull(replacement?.name))
        return state.copy(schedule = schedule).log(
            (if (replacement == null) "取消课程" else "调整课程") + (anchorWeek?.let { " · 原第 $it 周" } ?: " · 全部周次"),
            before, replacement?.description() ?: "已取消该安排", id
        )
    }

    fun restore(state: AppState, id: String? = null): AppState {
        val schedule = state.schedule
        val before = if (id == null) "${schedule.edits.size} 项调整、${schedule.added.size} 项新增" else
            ScheduleEngine.occurrences(schedule).filter { it.lesson.id == id }.map { it.lesson.description() }.distinct().joinToString("\n").ifBlank { "已取消或未匹配的安排" }
        val updated = if (id == null) schedule.copy(edits = emptyList(), added = emptyList()) else
            schedule.copy(edits = schedule.edits.filterNot { it.lessonId == id }, added = schedule.added.filterNot { it.id == id })
        val after = if (id == null) "恢复最近一次导入的 ${schedule.originals.size} 条原安排" else
            schedule.originals.find { it.id == id }?.description() ?: "移除手动新增或未匹配的调整"
        return state.copy(schedule = updated).log("恢复原安排", before, after, id)
    }

    fun saveTodo(state: AppState, todo: Todo): AppState {
        require(todo.title.isNotBlank() && todo.title.length <= 200) { "请输入待办标题（最多 200 字）" }
        return state.copy(todos = state.todos.filterNot { it.id == todo.id } + todo)
    }
}
