@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package cn.lesson.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.*
import cn.lesson.app.*
import cn.lesson.app.data.Reminders
import cn.lesson.core.*
import coil.compose.AsyncImage
import java.io.File
import java.time.LocalDate

data class LessonEditorRequest(val occurrence: Occurrence? = null,val day: Int = 1,val period: Int = 1,val week: Int = 1)

@Composable fun LessonRoot(model: AppViewModel,requestedTab: Int = 0,requestVersion: Int = 0) = LessonTheme {
    val state by model.state.collectAsStateWithLifecycle()
    val loadError by model.loadError.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val pending by model.pendingImport.collectAsStateWithLifecycle()
    val backup by model.pendingBackup.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var permissionVersion by remember { mutableIntStateOf(0) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _,event -> if(event == Lifecycle.Event.ON_RESUME) permissionVersion++ }
        owner.lifecycle.addObserver(observer); onDispose { owner.lifecycle.removeObserver(observer) }
    }
    var tab by rememberSaveable { mutableIntStateOf(requestedTab.coerceIn(0,2)) }
    LaunchedEffect(requestedTab,requestVersion) { tab=requestedTab.coerceIn(0,2) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); model.message.value=null } }
    var detail by remember { mutableStateOf<Occurrence?>(null) }
    var editor by remember { mutableStateOf<LessonEditorRequest?>(null) }
    var todoEditor by remember { mutableStateOf<Todo?>(null) }
    var termEditor by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<Confirmation?>(null) }
    var backgroundTab by rememberSaveable { mutableIntStateOf(0) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::importFile) }
    val backupReader = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(model::readBackup) }
    val backupWriter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { it?.let(model::export) }
    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let { uri -> model.background(backgroundTab,uri) } }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionVersion++; model.reschedule()
    }
    fun enableReminders(course: Boolean,enabled: Boolean) {
        model.update { it.copy(preferences=if(course) it.preferences.copy(courseReminders=enabled) else it.preferences.copy(todoReminders=enabled)) }
        if(enabled && Build.VERSION.SDK_INT >= 33 && !Reminders.notificationsAllowed(context)) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    fun restore(id: String?) {
        val s = state ?: return
        confirmation=Confirmation("恢复原安排",if(id == null)
            "将清除 ${s.schedule.edits.size} 项课表调整和 ${s.schedule.added.size} 项手动新增，恢复最近一次导入的安排。修改记录、待办和设置会保留。"
            else "将恢复这条上课安排的全部原始周次、教室与节次。若它是手动新增的课程，则移除该课程。") {
            model.update("已恢复原安排") { Actions.restore(it,id) }; detail=null
        }
    }
    Scaffold(containerColor=Color.Transparent,snackbarHost={SnackbarHost(snackbar)},
        bottomBar={ NavigationBar(containerColor=Paper) {
            listOf("课表" to Icons.Default.CalendarMonth,"待办" to Icons.Default.CheckCircleOutline,"我的" to Icons.Default.PersonOutline).forEachIndexed { index,(label,icon) ->
                NavigationBarItem(selected=tab==index,onClick={tab=index},icon={Icon(icon,label)},label={Text(label)},modifier=Modifier.testTag("nav-$index"))
            }
        } }) { padding ->
        Box(Modifier.fillMaxSize().background(Paper).padding(padding)) {
            state?.preferences?.backgrounds?.getOrNull(tab)?.takeIf { it.isNotBlank() }?.let {
                AsyncImage(model=File(it),contentDescription=null,contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Paper.copy(alpha=state!!.preferences.backgroundShade)))
            }
            val s = state
            if(s == null) {
                if(loadError != null) EmptyState("本地数据暂时无法读取",loadError!!,"从备份恢复") { backupReader.launch(arrayOf("*/*")) }
                else CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else when(tab) {
                0 -> ScheduleScreen(s,onImport={importer.launch(arrayOf("*/*"))},onSettings={termEditor=true},onLesson={detail=it},onAdd={day,period,week -> editor=LessonEditorRequest(day=day,period=period,week=week)})
                1 -> TodoScreen(s,onEdit={todoEditor=it},onComplete={todo -> model.update { Actions.saveTodo(it,todo.copy(doneAt=if(todo.doneAt==null) System.currentTimeMillis() else null)) }})
                2 -> SettingsScreen(s,permissionVersion,onImport={importer.launch(arrayOf("*/*"))},onTerm={termEditor=true},onHistory={history=true},
                    onRestore={restore(null)},onCourseToggle={enableReminders(true,it)},onTodoToggle={enableReminders(false,it)},
                    onPreferences={transform -> model.update { it.copy(preferences=transform(it.preferences)) }},
                    onPhoto={index -> backgroundTab=index; photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},
                    onBackup={backupWriter.launch("课序备份-${LocalDate.now()}.zip")},onRestoreBackup={backupReader.launch(arrayOf("*/*"))},
                    onNotifications={context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))},
                    onExact={if(Build.VERSION.SDK_INT>=31) context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:${context.packageName}")))})
            }
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
    val s = state
    if(s != null) {
        detail?.let { selected ->
            CourseDetail(s,selected,onDismiss={detail=null},onEdit={editor=LessonEditorRequest(selected,week=selected.week);detail=null},onRestore={restore(selected.lesson.id)},onCancel={all ->
                confirmation=Confirmation("取消课程",if(all) "取消这条上课安排的全部周次？可以在修改记录中恢复。" else "取消原第 ${selected.anchorWeek} 周的这次课程？") {
                    model.update("课程已取消") { Actions.edit(it,selected.lesson.id,if(all) null else selected.anchorWeek,null) }; detail=null
                }
            })
        }
        editor?.let { request -> LessonEditor(s,request,onDismiss={editor=null},onSave={lesson,anchor ->
            model.update("课程已保存",after={editor=null}) { if(request.occurrence==null) Actions.add(it,lesson) else Actions.edit(it,lesson.id,anchor,lesson) }
        }) }
        todoEditor?.let { todo -> TodoEditor(todo,onDismiss={todoEditor=null},onSave={updated ->
            model.update("待办已保存",after={todoEditor=null}) { Actions.saveTodo(it,updated) }
        },onDelete={
            confirmation=Confirmation("删除待办","删除“${todo.title}”？此操作无法撤销。") {
                model.update("待办已删除") { it.copy(todos=it.todos.filterNot { t -> t.id==todo.id }) }; todoEditor=null
            }
        }) }
        if(termEditor) TermEditor(s,onDismiss={termEditor=false},onSave={term -> model.update("学期设置已保存",after={termEditor=false}) { it.copy(term=term) }})
        if(history) HistoryDialog(s,onDismiss={history=false},onRestore={restore(it)})
        pending?.let { ImportPreview(s,it,busy,onDismiss={model.pendingImport.value=null},onConfirm=model::confirmImport) }
    }
    backup?.let { value -> AlertDialog(onDismissRequest={model.pendingBackup.value=null},title={Text("恢复本地备份")},
        text={Text("备份包含 ${value.schedule.originals.size+value.schedule.added.size} 条课程、${value.todos.size} 条待办和 ${value.history.size} 条修改记录。恢复后会替换手机中的当前数据与背景。")},
        confirmButton={TextButton(enabled=!busy,onClick=model::confirmBackup) {Text("替换并恢复")}},
        dismissButton={TextButton(onClick={model.pendingBackup.value=null}) {Text("取消")}}) }
    confirmation?.let { ConfirmationDialog(it) { confirmation=null } }
}

@Composable fun CourseDetail(state: AppState,item: Occurrence,onDismiss: () -> Unit,onEdit: () -> Unit,onRestore: () -> Unit,onCancel: (Boolean) -> Unit) {
    val lesson=item.lesson
    var more by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest=onDismiss,title={Text(lesson.name,fontWeight=FontWeight.Bold)},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Surface(color=courseColor(state.schedule.color(lesson.name)),shape=MaterialTheme.shapes.large) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) { Text(lesson.room.ifBlank { "教室待定" },style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold); Text(lesson.periodLabel) }
            }
            Text("第 ${item.week} 周 · ${weekDays[lesson.day-1]}" + (state.term.date(item.week,lesson.day)?.let { " · $it" } ?: ""))
            if(item.modified) InfoText("此安排已调整，原始记录仍然保留。")
            InfoText("提醒：${if(lesson.reminder) "跟随课程提醒总开关" else "此课程已关闭"}")
            TextButton(onClick={more=!more}) {Text(if(more) "收起更多信息" else "更多信息")}
            if(more) { InfoText("教师：${lesson.teacher.ifBlank { "未填写" }}"); InfoText("来源：${lesson.source}") }
            OutlinedButton(onClick=onEdit,modifier=Modifier.fillMaxWidth()) {Text("修改课程")}
            TextButton(onClick={onCancel(false)}) {Text("取消这一次课程")}
            TextButton(onClick={onCancel(true)}) {Text("取消此安排的全部周次")}
            if(state.schedule.edits.any { it.lessonId==lesson.id } || state.schedule.added.any { it.id==lesson.id }) TextButton(onClick=onRestore) {Text("恢复原安排")}
        }
    },confirmButton={TextButton(onClick=onDismiss) {Text("关闭")}})
}

@Composable fun ImportPreview(state: AppState,pending: PendingImport,busy: Boolean,onDismiss: () -> Unit,onConfirm: () -> Unit) {
    var acceptIssues by remember { mutableStateOf(false) }
    val result=pending.result
    val old=state.schedule.originals.associateBy {it.id}
    val incoming=result.lessons.associateBy {it.id}
    val changed=result.lessons.filter {next -> old[next.id]?.let {previous -> previous.copy(source="")==next.copy(source="")}==false}
    val added=result.lessons.count {it.id !in old}
    val removed=state.schedule.originals.filter {it.id !in incoming}
    val unmatched=state.schedule.edits.count { edit -> result.lessons.none { it.id==edit.lessonId } && state.schedule.added.none { it.id==edit.lessonId } }
    FullPageDialog("导入预览",onDismiss) { padding ->
        LazyColumn(Modifier.padding(padding),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item { Text("${result.lessons.map { it.name }.distinct().size} 门课程 · ${result.lessons.size} 条安排",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold) }
            item { InfoText(pending.fileName + if(result.termName.isNotBlank()) " · ${result.termName}" else "") }
            if(state.schedule.originals.isNotEmpty()) item { SoftCard {
                Text("更新导入原安排")
                InfoText("新增 $added 条 · 变化 ${changed.size} 条 · 移除 ${removed.size} 条")
                changed.forEach {lesson -> InfoText("原：${old[lesson.id]!!.description()}\n新：${lesson.description()}")}
                removed.forEach {InfoText("移除：${it.description()}")}
                InfoText("这次导入将替换原课表。已有的人工修改和新增仍会保留。")
            } }
            if(unmatched>0) item { InfoText("有 $unmatched 项人工修改暂时无法与新课表匹配，将保留在修改记录中，不会自动安排到课表上。") }
            items(result.lessons,key={it.id}) { lesson -> SoftCard {
                Text(lesson.name,fontWeight=FontWeight.SemiBold)
                Text("${lesson.room} · ${weekDays[lesson.day-1]} · ${lesson.periodLabel}")
                InfoText("第 ${Weeks.format(lesson.weeks)} 周")
            } }
            if(result.notes.isNotEmpty()) item { SoftCard { Text("学期备注"); result.notes.forEach {InfoText(it)}; InfoText("没有星期、节次的实习与课程设计仅作为备注保留。") } }
            items(result.issues) { issue -> SoftCard { Text("${issue.location} · ${issue.message}",color=MaterialTheme.colorScheme.error); InfoText(issue.raw) } }
            if(result.issues.isNotEmpty()) item { Row(verticalAlignment=Alignment.CenterVertically) {
                Checkbox(checked=acceptIssues,onCheckedChange={acceptIssues=it}); Text("我已核对，先导入可识别的课程",modifier=Modifier.weight(1f))
            } }
            item { Button(onClick=onConfirm,enabled=!busy && result.lessons.isNotEmpty() && (result.issues.isEmpty() || acceptIssues),modifier=Modifier.fillMaxWidth()) {Text("确认导入 ${result.lessons.size} 条安排")} }
        }
    }
}
