package cn.lesson.core

object Weeks {
    fun parse(raw: String): List<Int> {
        var text = raw.trim().replace('，', ',').replace('、', ',').replace('；', ',').replace(';', ',')
            .replace('－', '-').replace('—', '-').replace('–', '-').replace('～', '-').replace('~', '-').replace("至", "-")
        val odd = text.contains("单")
        val even = text.contains("双")
        require(!(odd && even)) { "周次不能同时包含单周和双周：$raw" }
        text = text.replace(Regex("[\\[\\]【】()（）周次第单双\\s]"), "")
        require(text.isNotEmpty()) { "请填写周次，例如 1-8,12 或 1-18[单周]" }
        val weeks = sortedSetOf<Int>()
        text.split(',').forEach { token ->
            require(token.matches(Regex("\\d{1,2}(-\\d{1,2})?"))) { "无法识别周次：$raw" }
            val bounds = token.split('-').map { it.toInt() }
            val start = bounds.first(); val end = bounds.last()
            require(start in 1..60 && end in start..60) { "周次范围应在 1–60，且结束周不小于开始周" }
            weeks.addAll(start..end)
        }
        return weeks.filter { (!odd || it % 2 == 1) && (!even || it % 2 == 0) }.also {
            require(it.isNotEmpty()) { "该周次范围内没有符合单双周条件的课程" }
        }
    }

    fun format(values: List<Int>): String {
        val sorted = values.distinct().sorted()
        if (sorted.isEmpty()) return ""
        val result = mutableListOf<String>()
        var start = sorted[0]; var end = start
        fun emit() { result.add(if (start == end) "$start" else "$start-$end") }
        for (v in sorted.drop(1)) {
            if (v == end + 1) end = v else { emit(); start = v; end = v }
        }
        emit()
        return result.joinToString(",")
    }
}
