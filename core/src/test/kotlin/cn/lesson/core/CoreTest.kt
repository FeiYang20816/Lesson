package cn.lesson.core

import kotlin.test.*
import java.time.*
import java.nio.charset.Charset
import java.io.ByteArrayOutputStream
import jxl.Workbook
import jxl.write.Label

class CoreTest {
    private fun lesson() = Lesson(id = "course-a", name = "课程甲", room = "A101", day = 1, startPeriod = 1, endPeriod = 2, weeks = listOf(1,2,3))
    private fun state() = AppState(term = Term(monday = "2026-08-31"), schedule = Schedule(originals = listOf(lesson())))

    @Test fun discontinuousAndParityWeeks() {
        assertEquals((1..9).toList() + (12..18).toList(), Weeks.parse("1-9,12-18[周]"))
        assertEquals(listOf(11,13,15,17), Weeks.parse("11-18[单周]"))
        assertEquals(listOf(2,4,6,8), Weeks.parse("第 1－8 双周"))
        assertEquals(listOf(9,13), Weeks.parse("9，13[周]"))
        assertEquals(listOf(1,2,3), Weeks.parse("1-3,2"))
    }
    @Test fun malformedWeeksNeverSilentlyBecomeAllWeeks() {
        listOf("", "0-3", "8-1", "1-99", "1,a", "3[双周]", "1-8[单双周]").forEach {
            assertFailsWith<IllegalArgumentException> { Weeks.parse(it) }
        }
    }
    @Test fun formatsWeekSetsWithoutChangingThem() {
        val input = listOf(1,2,3,9,11,12,18)
        assertEquals(input, Weeks.parse(Weeks.format(input)))
    }
    private fun grid() = listOf(
        listOf("学年学期：2026-2027-1"),
        listOf("", "星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六"),
        listOf("1－2", "", "课程甲\n老师甲\n1-8[周]\nA101", "课程乙\n老师乙\n9,13[周]\nB201\n\n课程甲\n老师甲\n1-8[周]\nA101", "课程丙\n老师丙\n11-18[单周]\nC301", "", "", ""),
        listOf("", "备注：实习 10-11周;")
    )
    @Test fun parsesSchoolGridAndPreservesUnscheduledNotes() {
        val r = Importer.parseRows(grid())
        assertEquals(4, r.lessons.size); assertEquals(3, r.occupiedCells); assertTrue(r.issues.isEmpty())
        assertEquals(listOf(11,13,15,17), r.lessons.last().weeks)
        assertEquals(1, r.lessons.first().day)
        assertEquals("2026-2027-1", r.termName); assertTrue(r.notes.single().contains("实习"))
    }
    @Test fun readsActualBiffWorkbookFormat() {
        val bytes = ByteArrayOutputStream()
        val book = Workbook.createWorkbook(bytes); val sheet = book.createSheet("Sheet1", 0)
        grid().forEachIndexed { r, row -> row.forEachIndexed { c, value -> sheet.addCell(Label(c,r,value)) } }
        book.write(); book.close()
        val result = Importer.read(bytes.toByteArray(), "测试.xls")
        assertEquals(4, result.lessons.size); assertTrue(result.issues.isEmpty())
    }
    @Test fun stableIdentifiersSurviveReimportRoomAndWeeksChanges() {
        val first = Importer.parseRows(grid())
        val second = Importer.parseRows(grid().map { row -> row.map { it.replace("A101","A303").replace("1-8[周]","2-7[周]") } })
        assertEquals(first.lessons.map { it.id }, second.lessons.map { it.id })
    }
    @Test fun csvSupportsQuotedCommasNewlinesEscapedQuotesAndGbk() {
        assertEquals(listOf(listOf("a,b","c\nd","a\"b")), Csv.read("\"a,b\",\"c\nd\",\"a\"\"b\"\r\n"))
        val csv = "课程名称,教室,星期,开始节次,结束节次,周次\r\n测试课,A101,周二,3,4,\"9,13[周]\""
        val result = Importer.read(csv.toByteArray(Charset.forName("GB18030")), "课表.csv")
        assertEquals(listOf(9,13), result.lessons.single().weeks)
        assertFailsWith<IllegalArgumentException> { Csv.read("\"broken") }
    }
    @Test fun invalidRowsAreReportedAndNotSilentlyImported() {
        val rows = listOf(listOf("课程名称","教室","星期","开始节次","结束节次","周次"), listOf("测试课","A101","周二","5","3","1-8"))
        val r = Importer.parseRows(rows)
        assertTrue(r.lessons.isEmpty()); assertEquals(1, r.issues.size)
    }
    @Test fun calendarMappingAcrossYearAndLeapDay() {
        assertEquals(LocalDate.of(2024,2,29), Term(monday="2024-02-26").date(1,4))
        assertEquals(LocalDate.of(2027,1,4), Term(monday="2026-12-28").date(2,1))
        assertEquals(0, state().term.currentWeek(LocalDate.of(2026,8,30)))
    }
    @Test fun movingSingleOccurrenceDoesNotChangeOtherWeeks() {
        val original = state()
        val changed = Actions.edit(original, "course-a", 2, lesson().copy(room="B202",day=5,weeks=listOf(3)))
        assertEquals(0, ScheduleEngine.occurrences(changed.schedule,2).size)
        assertEquals(2, ScheduleEngine.occurrences(changed.schedule,3).size)
        assertEquals("A101", ScheduleEngine.occurrences(changed.schedule,1).single().lesson.room)
        assertEquals(2, ScheduleEngine.occurrences(changed.schedule,3).find { it.modified }!!.anchorWeek)
    }
    @Test fun editingWholeArrangementClearsEarlierSingleExceptions() {
        val changed = Actions.edit(state(),"course-a",2,null)
        val all = Actions.edit(changed,"course-a",null,lesson().copy(room="C303"))
        assertEquals(3, ScheduleEngine.occurrences(all.schedule).size)
        assertEquals(setOf("C303"), ScheduleEngine.occurrences(all.schedule).map { it.lesson.room }.toSet())
    }
    @Test fun restoreKeepsTodosAndRecordsRestoration() {
        val todo = Todo(title="任务")
        val changed = Actions.edit(state().copy(todos=listOf(todo)),"course-a",2,null)
        val restored = Actions.restore(changed,"course-a")
        assertEquals(state().schedule.originals, restored.schedule.originals)
        assertTrue(restored.schedule.edits.isEmpty()); assertEquals(listOf(todo),restored.todos)
        assertEquals(2,restored.history.size); assertEquals("恢复原安排",restored.history.first().title)
    }
    @Test fun reimportPreservesManualChangesAndAdditions() {
        var s = Actions.edit(state(),"course-a",2,null)
        s = Actions.add(s,lesson().copy(id="added",name="补课"))
        val result = ImportResult(listOf(lesson().copy(room="A404")),emptyList(),emptyList(),"新学期")
        val imported = Actions.import(s,result,"school.xls")
        assertEquals(s.schedule.edits,imported.schedule.edits); assertEquals(s.schedule.added,imported.schedule.added)
        assertEquals("A404", imported.schedule.originals.single().room)
    }
    @Test fun conflictsAreComputedAfterSingleEdits() {
        val s = Actions.add(state(),lesson().copy(id="b",name="课程乙",startPeriod=2,endPeriod=3))
        assertEquals(setOf("course-a","b"),ScheduleEngine.conflicts(s.schedule,1))
    }
    @Test fun colorIdentityPersistsAcrossImports() {
        val s = Schedule().withColors(listOf("甲","乙","甲"))
        assertNotEquals(s.color("甲"),s.color("乙"))
        assertEquals(s.color("甲"),s.withColors(listOf("丙","甲")).color("甲"))
    }
    @Test fun remindersFollowEditsAndIndependentSwitches() {
        val now = Instant.parse("2026-08-30T00:00:00Z").toEpochMilli()
        val term = state().term.copy(periods=listOf(PeriodTime("08:00","08:45")))
        val s = state().copy(term=term,preferences=Preferences(courseReminders=true),todos=listOf(Todo(title="任务",remindAt=now+10000)))
        val events = ReminderPlan.events(s,now,ZoneId.of("Asia/Shanghai"))
        assertEquals(3,events.size)
        assertEquals(Instant.parse("2026-08-30T23:50:00Z").toEpochMilli(),events.first().at)
        val canceled = Actions.edit(s,"course-a",1,null)
        assertEquals(2,ReminderPlan.events(canceled,now,ZoneId.of("Asia/Shanghai")).size)
        assertEquals(1,ReminderPlan.events(s.copy(preferences=Preferences(todoReminders=true)),now).size)
        assertTrue(ReminderPlan.events(s.copy(term=term.copy(monday=null)),now).isEmpty())
    }
    @Test fun completedTodosAndUnknownTimesDoNotSchedule() {
        val s = state().copy(preferences=Preferences(courseReminders=true,todoReminders=true),todos=listOf(Todo(title="已完成",remindAt=1000,doneAt=2)))
        assertTrue(ReminderPlan.events(s,0).isEmpty())
    }
    @Test fun delayedAlarmDeliversLaterDueRemindersInsteadOfDroppingThem() {
        val s=state().copy(preferences=Preferences(todoReminders=true),todos=listOf(
            Todo(title="第一项",remindAt=1000),Todo(title="第二项",remindAt=2000),Todo(title="将来",remindAt=4000),Todo(title="已完成",remindAt=2000,doneAt=1)))
        assertEquals(listOf("第一项","第二项"),ReminderPlan.dueEvents(s,1000,3000).map {it.title})
        assertTrue(ReminderPlan.dueEvents(s,1000,30000000).isEmpty())
    }
    @Test fun backupRoundTripRetainsManualAndHistoricalData() {
        val s = Actions.edit(state(),"course-a",2,null)
        assertEquals(s,BackupCodec.decode(BackupCodec.encode(s)))
        assertFailsWith<IllegalArgumentException> { BackupCodec.decode("{}") }
        assertFailsWith<IllegalArgumentException> { BackupCodec.decode(BackupCodec.encode(s).replace("2026-08-31","2026-09-01")) }
        val withTodo=s.copy(todos=listOf(Todo(title="任务")))
        assertFailsWith<IllegalArgumentException> { BackupCodec.decode(BackupCodec.encode(withTodo).replace("\"notes\": \"\"","\"notes\": null")) }
    }
}
