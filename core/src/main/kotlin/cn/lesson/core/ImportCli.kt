package cn.lesson.core

import java.io.File

fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "Usage: core:run --args=path-to-schedule.xls" }
    val file = File(args[0])
    val result = Importer.read(file.readBytes(), file.name)
    val report = buildString {
        appendLine("# 课表导入核验")
        appendLine("学期：${result.termName}；${result.occupiedCells} 个非空格；${result.lessons.size} 条上课安排；${result.lessons.map { it.name }.distinct().size} 门课程；${result.issues.size} 项待检查。")
        appendLine("\n| 课程 | 教室 | 星期 | 节次 | 周次 |\n|---|---|---|---|---|")
        result.lessons.forEach { appendLine("| ${it.name} | ${it.room} | ${weekDays[it.day-1]} | ${it.periodLabel} | ${Weeks.format(it.weeks)} |") }
        appendLine("\n## 学期备注")
        result.notes.forEach { appendLine(it) }
        result.issues.forEach { appendLine("${it.location}：${it.message}\n${it.raw}") }
        appendLine("\n## 每周上课安排数（不含仅有周次的实习或课程设计备注）")
        val schedule = Schedule(originals = result.lessons)
        (1..21).forEach { appendLine("第 $it 周：${ScheduleEngine.occurrences(schedule, it).size} 条") }
    }
    if (args.size > 1) File(args[1]).apply { parentFile?.mkdirs() }.writeText(report) else println(report)
    require(result.issues.isEmpty()) { "存在无法解析的内容，请检查报告" }
}
