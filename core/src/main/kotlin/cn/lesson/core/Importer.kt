package cn.lesson.core

import jxl.Workbook
import jxl.WorkbookSettings
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.Charset
import java.security.MessageDigest

data class ImportIssue(val location: String, val message: String, val raw: String)
data class ImportResult(
    val lessons: List<Lesson>, val notes: List<String>, val issues: List<ImportIssue>, val termName: String,
    val occupiedCells: Int = 0
)

object Importer {
    private val dayMap = mapOf("星期一" to 1, "星期二" to 2, "星期三" to 3, "星期四" to 4, "星期五" to 5, "星期六" to 6, "星期日" to 7, "星期天" to 7,
        "周一" to 1, "周二" to 2, "周三" to 3, "周四" to 4, "周五" to 5, "周六" to 6, "周日" to 7)

    fun read(bytes: ByteArray, fileName: String): ImportResult {
        require(bytes.size <= 10 * 1024 * 1024) { "课表文件不能超过 10 MB" }
        val xls = bytes.size >= 8 && bytes.take(4).map { it.toInt() and 255 } == listOf(0xD0, 0xCF, 0x11, 0xE0)
        val rows = if (xls) {
            val workbook = Workbook.getWorkbook(ByteArrayInputStream(bytes), WorkbookSettings().apply { encoding = "GB18030" })
            try {
                require(workbook.numberOfSheets > 0) { "文件没有工作表" }
                val sheet = workbook.getSheet(0)
                require(sheet.rows <= 5000 && sheet.columns <= 100) { "课表范围过大" }
                List(sheet.rows) { r -> List(sheet.columns) { c -> sheet.getCell(c, r).contents } }
            } finally { workbook.close() }
        } else {
            require(!fileName.endsWith(".xlsx", true) && !(bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte())) {
                "目前支持学校导出的 .xls 和 CSV，请将 .xlsx 另存为这两种格式"
            }
            Csv.read(decode(bytes))
        }
        return parseRows(rows)
    }

    private fun decode(bytes: ByteArray): String {
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return bytes.toString(Charsets.UTF_16LE).removePrefix("\uFEFF")
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return bytes.toString(Charsets.UTF_16BE).removePrefix("\uFEFF")
        return try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
        } catch (_: java.nio.charset.CharacterCodingException) { bytes.toString(Charset.forName("GB18030")) }
    }

    fun parseRows(rows: List<List<String>>): ImportResult {
        val headerIndex = rows.indexOfFirst { row -> row.count { dayMap.containsKey(it.trim()) } >= 5 }
        if (headerIndex < 0) return parseList(rows)
        val columns = rows[headerIndex].mapIndexedNotNull { col, title -> dayMap[title.trim()]?.let { col to it } }
        val lessons = mutableListOf<Lesson>(); val issues = mutableListOf<ImportIssue>(); val notes = mutableListOf<String>()
        val ids = mutableMapOf<String, Int>(); var occupied = 0
        rows.drop(headerIndex + 1).forEachIndexed { offset, row ->
            val rowNumber = headerIndex + offset + 2
            val label = row.firstOrNull()?.trim().orEmpty().replace('－','-').replace('—','-').replace('–','-')
            val match = Regex("^(?:第)?(\\d{1,2})(?:[-~至](\\d{1,2}))?(?:节)?$").matchEntire(label)
            if (match == null) {
                row.filter { it.isNotBlank() }.forEach { notes.add(it.trim()) }
            } else {
                val start = match.groupValues[1].toInt(); val end = match.groupValues[2].toIntOrNull() ?: start
                columns.forEach { (col, day) ->
                    val raw = row.getOrElse(col) { "" }.trim()
                    if (raw.isNotBlank()) {
                        occupied++
                        val location = "第 $rowNumber 行 · ${weekDays[day - 1]}"
                        // A blank line separates courses in the school export; recognize week markers even when blank lines are absent.
                        val lines = raw.lines().map { it.trim() }.filter { it.isNotBlank() }
                        val weekIndices = lines.indices.filter { i -> lines[i].contains(Regex("\\d")) && lines[i].contains("周") &&
                            lines[i].matches(Regex("[0-9\\s,，、;；\\-－—–~～至\\[\\]【】()（）第单双周次]+")) }
                        if (weekIndices.isEmpty()) issues.add(ImportIssue(location, "没有识别到周次，请手动检查", raw))
                        else {
                            var consumed = 0
                            weekIndices.forEach { wi ->
                                try {
                                    require(wi > consumed) { "缺少课程名称" }
                                    val before = lines.subList(consumed, wi)
                                    require(before.size <= 2) { "课程信息结构不明确，请在预览中核对" }
                                    val name = before.first()
                                    val teacher = before.getOrNull(1).orEmpty()
                                    val room = lines.getOrNull(wi + 1).orEmpty()
                                    require(room.isNotBlank() && wi + 1 !in weekIndices) { "缺少教室信息" }
                                    val lesson = Lesson(id = stableId(name, day, start, end, ids), name = name, teacher = teacher,
                                        room = room, day = day, startPeriod = start, endPeriod = end, weeks = Weeks.parse(lines[wi]), source = location)
                                    lesson.validate(); lessons.add(lesson)
                                } catch (e: IllegalArgumentException) { issues.add(ImportIssue(location, e.message ?: "解析失败", raw)) }
                                consumed = wi + 2
                            }
                            if (consumed < lines.size) issues.add(ImportIssue(location, "存在额外内容，请核对", lines.drop(consumed).joinToString("\n")))
                        }
                    }
                }
            }
        }
        val term = rows.take(headerIndex).flatten().firstNotNullOfOrNull {
            Regex("(?:学年学期[：:]\\s*)?(20\\d{2}-20\\d{2}-[12])").find(it)?.groupValues?.get(1)
        }.orEmpty()
        return ImportResult(lessons, notes.distinct(), issues, term, occupied)
    }

    private fun parseList(rows: List<List<String>>): ImportResult {
        val index = rows.indexOfFirst { row -> "课程名称" in row.map { it.trim() } }
        require(index >= 0) { "无法识别课表。请选择学校原始 XLS，或使用 CSV 模板" }
        val header = rows[index].map { it.trim() }; val required = listOf("课程名称", "教室", "星期", "开始节次", "结束节次", "周次")
        require(required.all { it in header }) { "CSV 需要表头：${required.joinToString("、")}" }
        val lessons = mutableListOf<Lesson>(); val issues = mutableListOf<ImportIssue>(); val ids = mutableMapOf<String, Int>()
        rows.drop(index + 1).forEachIndexed { n, row ->
            if (row.all { it.isBlank() }) return@forEachIndexed
            fun field(name: String) = row.getOrElse(header.indexOf(name)) { "" }.trim()
            try {
                val name = field("课程名称"); val day = dayMap[field("星期")] ?: field("星期").toInt()
                val start = field("开始节次").toInt(); val end = field("结束节次").toInt()
                val lesson = Lesson(id = stableId(name, day, start, end, ids), name = name, room = field("教室"), day = day,
                    startPeriod = start, endPeriod = end, weeks = Weeks.parse(field("周次")), teacher = field("教师"), source = "CSV 第 ${index + n + 2} 行")
                lesson.validate(); lessons.add(lesson)
            } catch (e: IllegalArgumentException) { issues.add(ImportIssue("第 ${index + n + 2} 行", e.message ?: "格式错误", row.joinToString(" | "))) }
        }
        return ImportResult(lessons, emptyList(), issues, "")
    }

    private fun stableId(name: String, day: Int, start: Int, end: Int, counts: MutableMap<String, Int>): String {
        val key = "$name|$day|$start|$end"
        val nth = counts.getOrDefault(key, 0); counts[key] = nth + 1
        return "import-" + MessageDigest.getInstance("SHA-256").digest("$key|$nth".toByteArray()).take(12).joinToString("") { "%02x".format(it) }
    }
}

object Csv {
    fun read(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>(); var row = mutableListOf<String>(); val cell = StringBuilder()
        var quoted = false; var closedQuote = false; var i = 0
        val separator = if (text.lineSequence().firstOrNull().orEmpty().contains('\t')) '\t' else ','
        fun endCell() { row.add(cell.toString()); cell.setLength(0); closedQuote = false }
        fun endRow() { endCell(); rows.add(row); row = mutableListOf() }
        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { cell.append('"'); i++ }
                    else { quoted = false; closedQuote = true }
                } else cell.append(c)
            } else {
                when (c) {
                    '"' -> { require(cell.isEmpty() && !closedQuote) { "CSV 引号格式有误" }; quoted = true }
                    separator -> endCell()
                    '\r', '\n' -> { endRow(); if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++ }
                    else -> { require(!closedQuote || c.isWhitespace()) { "CSV 引号后存在多余字符" }; if (!closedQuote) cell.append(c) }
                }
            }
            i++
        }
        require(!quoted) { "CSV 引号未闭合" }
        if (cell.isNotEmpty() || row.isNotEmpty() || closedQuote) endRow()
        require(rows.size <= 5000 && rows.all { it.size <= 100 }) { "课表范围过大" }
        return rows
    }
}
