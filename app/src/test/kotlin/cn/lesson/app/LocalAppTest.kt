package cn.lesson.app

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.exifinterface.media.ExifInterface
import cn.lesson.app.data.*
import cn.lesson.core.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.SQLiteMode
import java.io.File
import java.time.*
import java.time.temporal.TemporalAdjusters

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class LocalAppTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val app: LessonApplication get()=ApplicationProvider.getApplicationContext()
    private val repo get()=app.repository

    @Before fun prepare() {
        assertSame("Activity must use this test application's repository",repo.state,androidx.lifecycle.ViewModelProvider(compose.activity)[AppViewModel::class.java].state)
        val result=ImportResult(listOf(
            Lesson(id="a",name="示例课程一",room="A101",weeks=(1..18).toList()),
            Lesson(id="b",name="示例课程二",room="B202",day=2,startPeriod=5,endPeriod=6,weeks=(1..18).toList())
        ),emptyList(),emptyList(),"演示学期")
        val monday=LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(3)
        val state=Actions.import(AppState(),result,"界面演示课表.xls").copy(
            term=Term(name="2026—2027 · 第一学期（演示日期）",monday=monday.toString()),
            todos=listOf(Todo(title="完成课程作业",notes="整理课堂笔记",dueAt=System.currentTimeMillis()+86400000)))
        runBlocking {repo.load();repo.replace(state)}
        compose.waitForIdle()
    }
    @After fun release() {
        compose.activityRule.scenario.close()
        repo.close()
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val file=File(requireNotNull(System.getProperty("lesson.screenshots")),"$name.png").apply {parentFile!!.mkdirs()}
        compose.runOnIdle {
            val view=compose.activity.window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
        }
    }

    @Test fun editOnlyOneWeekAndRestoreFromHistory() {
        compose.onNodeWithTag("nav-0").performClick()
        compose.onNodeWithText("第 4 周  ⌄").assertIsDisplayed()
        screenshot("01-week")
        compose.onAllNodesWithText("示例课程一").onFirst().performClick()
        compose.onNodeWithText("修改课程").performClick()
        compose.onNodeWithText("教室位置").performTextReplacement("测试楼 B202")
        compose.onNodeWithText("保存课程").performScrollTo().performClick()
        try { compose.waitUntil(10000) {repo.state.value!!.schedule.edits.isNotEmpty()} }
        catch(e: Throwable) {
            val model=androidx.lifecycle.ViewModelProvider(compose.activity)[AppViewModel::class.java]
            val diagnostic="Save diagnostic: busy=${model.busy.value}, message=${model.message.value}, edits=${repo.state.value!!.schedule.edits}\n" +
                compose.onAllNodes(isRoot()).fetchSemanticsNodes().indices.joinToString("\n") { compose.onAllNodes(isRoot())[it].printToString() }
            File(System.getProperty("lesson.screenshots"),"save-diagnostic.txt").writeText(diagnostic)
            throw e
        }
        val edited=repo.state.value!!
        assertEquals(4,edited.schedule.edits.single().week)
        assertTrue(ScheduleEngine.occurrences(edited.schedule,4).any {it.lesson.room=="测试楼 B202"})
        assertFalse(ScheduleEngine.occurrences(edited.schedule,3).any {it.lesson.room=="测试楼 B202"})
        compose.onNodeWithTag("nav-2").performClick()
        compose.onNodeWithText("修改记录").performClick()
        // Dialog has its own root; the data assertions validate both history and restoration.
        compose.onNodeWithText("恢复此安排").performClick()
        compose.onNodeWithText("确认").performClick()
        compose.waitUntil(10000) {repo.state.value!!.schedule.edits.isEmpty()}
        assertEquals("恢复原安排",repo.state.value!!.history.first().title)
    }

    @Test fun createArchiveRestoreTodoAndReopenDatabase() {
        compose.onNodeWithTag("nav-1").performClick()
        compose.onNodeWithContentDescription("新增待办").performClick()
        compose.onNodeWithText("要做什么").performTextInput("复习周三课程")
        compose.onNodeWithText("保存待办").performClick()
        compose.waitUntil(10000) {repo.state.value!!.todos.any {it.title=="复习周三课程"}}
        screenshot("02-todos")
        compose.onAllNodes(isToggleable()).onLast().performClick()
        compose.waitUntil(10000) {repo.state.value!!.todos.any {it.doneAt!=null}}
        compose.onNodeWithText("已归档 · 1").performClick()
        compose.onAllNodes(isToggleable()).onFirst().performClick()
        compose.waitUntil(10000) {repo.state.value!!.todos.all {it.doneAt==null}}
        val reloaded=AppRepository(app).use { reopened -> runBlocking {reopened.load()} }
        assertEquals(repo.state.value,reloaded)
    }

    @Test fun backgroundBackupRoundTripAndSettingsScreen() {
        val bitmap=Bitmap.createBitmap(80,100,Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.rgb(220,235,221))}
        val image=File(app.cacheDir,"background.jpg")
        image.outputStream().use {bitmap.compress(Bitmap.CompressFormat.JPEG,95,it)}
        ExifInterface(image).apply {setAttribute(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_ROTATE_90.toString());saveAttributes()}
        val stored=LocalFiles.saveBackground(app,Uri.fromFile(image))
        val corrected=BitmapFactory.decodeFile(stored)
        assertEquals(100,corrected.width);assertEquals(80,corrected.height)
        runBlocking {repo.update {it.copy(preferences=it.preferences.copy(backgrounds=listOf(stored,"","")))}}
        val original=repo.state.value!!
        val zip=File(app.cacheDir,"backup.zip")
        LocalFiles.exportBackup(app,original,Uri.fromFile(zip))
        val imported=LocalFiles.readBackup(app,Uri.fromFile(zip))
        assertEquals(original.schedule,imported.schedule)
        assertEquals(original.todos,imported.todos)
        assertEquals(original.history,imported.history)
        assertTrue(File(imported.preferences.backgrounds[0]).length()>0)
        compose.onNodeWithTag("nav-2").performClick()
        screenshot("03-settings")
    }

    @Test fun reminderReceiverCreatesNotificationAndDoesNotRepeatDelivery() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val now=System.currentTimeMillis()
        runBlocking {repo.update {it.copy(preferences=it.preferences.copy(todoReminders=true),todos=listOf(
            Todo(id="notify-a",title="待办提醒测试",remindAt=now-2000),Todo(title="完成不提醒",remindAt=now-1000,doneAt=1),
            Todo(id="notify-b",title="延迟期间的下一条",remindAt=now-500)))}}
        val intent=Intent(app,ReminderReceiver::class.java).setAction("cn.lesson.app.REMINDER").putExtra("at",now-2000)
        runBlocking {Reminders.receive(app,intent)}
        val manager=shadowOf(app.getSystemService(NotificationManager::class.java))
        assertEquals(2,manager.allNotifications.size)
        assertEquals(setOf("待办提醒测试","延迟期间的下一条"),manager.allNotifications.map {it.extras.getString("android.title")}.toSet())
        app.getSystemService(NotificationManager::class.java).cancelAll()
        runBlocking {Reminders.receive(app,intent)}
        assertEquals(0,manager.allNotifications.size)
    }
}
