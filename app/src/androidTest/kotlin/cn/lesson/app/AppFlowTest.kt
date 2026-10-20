package cn.lesson.app

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cn.lesson.app.data.*
import cn.lesson.core.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek

@RunWith(AndroidJUnit4::class)
class AppFlowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val context: Context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val repo: AppRepository get()=(context.applicationContext as LessonApplication).repository

    private fun fixture(): AppState {
        val result=ImportResult(listOf(
            Lesson(id="a",name="示例课程一",room="A101",day=1,weeks=(1..18).toList()),
            Lesson(id="b",name="示例课程二",room="B202",day=2,startPeriod=5,endPeriod=6,weeks=(1..18).toList()),
            Lesson(id="c",name="示例课程三",room="C303",day=3,startPeriod=7,endPeriod=8,weeks=(1..18).toList())
        ),listOf("虚构示例数据"),emptyList(),"演示学期")
        val monday=LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(3)
        return Actions.import(AppState(term=Term(name="界面演示",monday=monday.toString())),result,"界面演示课表.xls").copy(
            todos=listOf(Todo(title="完成课程作业",notes="整理课堂笔记",dueAt=System.currentTimeMillis()+86400000)),
            term=Term(name="2026—2027 · 第一学期（演示日期）",monday=monday.toString()))
    }

    @Before fun prepare() {
        runBlocking {repo.load();repo.replace(fixture())}
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val file=File(context.getExternalFilesDir("screenshots"),"$name.png")
        file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        bitmap.recycle()
    }

    @Test fun weeklyCalendarEditRestoreAndHistory() {
        compose.onNodeWithTag("nav-0").performClick()
        compose.onNodeWithText("第 4 周  ⌄").assertIsDisplayed()
        capture("01-week")
        compose.onAllNodesWithText("示例课程一").onFirst().performClick()
        compose.onNodeWithText("A101").assertIsDisplayed()
        compose.onNodeWithText("修改课程").performClick()
        compose.onNodeWithText("教室位置").performTextReplacement("测试楼 B202")
        compose.onNodeWithText("保存课程").performScrollTo().performClick()
        compose.waitUntil(10000) {repo.state.value!!.schedule.edits.isNotEmpty()}
        val edited=repo.state.value!!
        assertEquals(1,edited.schedule.edits.size)
        assertEquals(4,edited.schedule.edits.single().week)
        assertTrue(ScheduleEngine.occurrences(edited.schedule,4).any {it.lesson.room=="测试楼 B202"})
        assertFalse(ScheduleEngine.occurrences(edited.schedule,3).any {it.lesson.room=="测试楼 B202"})
        compose.onNodeWithTag("nav-2").performClick()
        compose.onNodeWithText("修改记录").performClick()
        capture("04-history")
        compose.onNodeWithText("恢复此安排").performClick()
        compose.onNodeWithText("确认").performClick()
        compose.waitUntil(10000) {repo.state.value!!.schedule.edits.isEmpty()}
        assertEquals("恢复原安排",repo.state.value!!.history.first().title)
    }

    @Test fun createCompleteAndRestoreTodo() {
        compose.onNodeWithTag("nav-1").performClick()
        compose.onNodeWithContentDescription("新增待办").performClick()
        compose.onNodeWithText("要做什么").performTextInput("复习周三课程")
        compose.onNodeWithText("保存待办").performClick()
        compose.waitUntil(10000) {repo.state.value!!.todos.any {it.title=="复习周三课程"}}
        capture("02-todos")
        compose.onAllNodes(isToggleable()).onLast().performClick()
        compose.waitUntil(10000) {repo.state.value!!.todos.any {it.doneAt!=null}}
        compose.onNodeWithText("已归档 · 1").performClick()
        compose.onAllNodes(isToggleable()).onFirst().performClick()
        compose.waitUntil(10000) {repo.state.value!!.todos.all {it.doneAt==null}}
    }

    @Test fun databaseReopenAndBackupPreserveImageAndHistory() {
        val bmp=Bitmap.createBitmap(80,100,Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.rgb(220,235,221))}
        val photo=File(context.cacheDir,"test-image.png")
        photo.outputStream().use {bmp.compress(Bitmap.CompressFormat.PNG,100,it)};bmp.recycle()
        val imagePath=LocalFiles.saveBackground(context,Uri.fromFile(photo))
        val original=runBlocking {
            repo.update {s -> Actions.edit(s,s.schedule.originals.first().id,4,null).copy(preferences=s.preferences.copy(backgrounds=listOf(imagePath,"","")))}
            repo.state.value!!
        }
        val reopened=AppRepository(context).use { repository -> runBlocking {repository.load()} }
        assertEquals(original,reopened)
        val file=File(context.cacheDir,"roundtrip.zip")
        LocalFiles.exportBackup(context,original,Uri.fromFile(file))
        val restored=LocalFiles.readBackup(context,Uri.fromFile(file))
        assertEquals(original.schedule,restored.schedule);assertEquals(original.history,restored.history);assertEquals(original.todos,restored.todos)
        assertTrue(File(restored.preferences.backgrounds[0]).isFile)
        assertNotEquals(imagePath,restored.preferences.backgrounds[0])
        compose.onNodeWithTag("nav-2").performClick()
        capture("03-settings")
    }

    @Test fun reminderActuallyArrivesAndCompletedTodoIsNotScheduled() {
        if(Build.VERSION.SDK_INT>=33) InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName,Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(Reminders.notificationsAllowed(context))
        val manager=context.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        val at=System.currentTimeMillis()+15000
        runBlocking {repo.update {it.copy(todos=listOf(Todo(title="提醒到达测试",remindAt=at),Todo(title="已完成不提醒",remindAt=at,doneAt=1)),preferences=it.preferences.copy(todoReminders=true))}}
        compose.waitUntil(90000) {manager.activeNotifications.any {it.notification.extras.getString("android.title")=="提醒到达测试"}}
        assertFalse(manager.activeNotifications.any {it.notification.extras.getString("android.title")=="已完成不提醒"})
    }
}
