package cn.lesson.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cn.lesson.app.data.Reminders
import cn.lesson.core.*

@Composable fun SettingsScreen(state: AppState,permissionVersion: Int,onImport: () -> Unit,onTerm: () -> Unit,onHistory: () -> Unit,onRestore: () -> Unit,
    onCourseToggle: (Boolean) -> Unit,onTodoToggle: (Boolean) -> Unit,onPreferences: ((Preferences) -> Preferences) -> Unit,onPhoto: (Int) -> Unit,
    onBackup: () -> Unit,onRestoreBackup: () -> Unit,onNotifications: () -> Unit,onExact: () -> Unit) {
    val context=LocalContext.current
    val notifications=remember(permissionVersion) {Reminders.notificationsAllowed(context)}
    val exact=remember(permissionVersion) {Reminders.exactAllowed(context)}
    LazyColumn(contentPadding=PaddingValues(bottom=28.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {PageHeading("我的","课序 · 把课业安排得清清楚楚")}
        item {SoftCard(Modifier.padding(horizontal=20.dp)) {
            Text("课表与学期",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            SettingsAction("导入课表","支持学校原始 XLS 和 CSV",onImport)
            HorizontalDivider()
            SettingsAction("学期与节次",state.term.monday?.let {"第 1 周周一：$it"} ?: "尚未设置开学日期",onTerm)
            HorizontalDivider()
            SettingsAction("修改记录","${state.history.size} 条记录 · 调整前后都可追溯",onHistory)
            if(state.schedule.edits.isNotEmpty() || state.schedule.added.isNotEmpty()) TextButton(onClick=onRestore) {Text("恢复整个课表的原安排")}
        }}
        item {SoftCard(Modifier.padding(horizontal=20.dp)) {
            Text("提醒",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            SettingsSwitch("课程提醒","按实际上课日期与节次时间提醒",state.preferences.courseReminders,onCourseToggle)
            SettingsSwitch("待办提醒","只提醒未完成且设有提醒时间的待办",state.preferences.todoReminders,onTodoToggle)
            if(state.preferences.courseReminders) {
                if(state.term.monday==null || state.term.periods.all {it.start.isBlank()}) InfoText("请先在“学期与节次”中填写开学日期和上课时间，课程提醒才能生效。")
                else {
                    val missing=ScheduleEngine.occurrences(state.schedule).map {it.lesson.startPeriod}.distinct().count {state.term.periods.getOrNull(it-1)?.start.isNullOrBlank()}
                    if(missing>0) InfoText("有 $missing 个课程起始节次还没有填写上课时间，对应课程暂时无法提醒。")
                }
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("提前 ${state.preferences.leadMinutes} 分钟",modifier=Modifier.weight(1f))
                    listOf(0,5,10,15,30).forEach { value -> TextButton(onClick={onPreferences {it.copy(leadMinutes=value)}},contentPadding=PaddingValues(6.dp),modifier=Modifier.widthIn(min=30.dp)) {Text("$value")} }
                }
            }
            if(!notifications) SettingsAction("通知权限尚未开启","点击前往系统通知设置",onNotifications)
            if(!exact && (state.preferences.courseReminders || state.preferences.todoReminders)) SettingsAction("允许准时提醒","当前使用系统近似提醒，可能延后",onExact)
            if(notifications && exact) InfoText("通知与准时提醒权限已就绪。系统省电和强制停止仍可能影响提醒，建议在常用手机上试一次。")
        }}
        item {SoftCard(Modifier.padding(horizontal=20.dp)) {
            Text("显示与背景",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            SettingsSwitch("显示具体上课时刻","关闭后仍然显示节次",state.preferences.showTimes) {enabled -> onPreferences {it.copy(showTimes=enabled)}}
            listOf("课表背景","待办背景","我的背景").forEachIndexed { index,label ->
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(label,modifier=Modifier.weight(1f))
                    TextButton(onClick={onPhoto(index)}) {Text("从相册选择")}
                    if(state.preferences.backgrounds[index].isNotBlank()) TextButton(onClick={onPreferences {it.copy(backgrounds=it.backgrounds.toMutableList().apply {set(index,"")})}}) {Text("清除")}
                }
            }
            if(state.preferences.backgrounds.any {it.isNotBlank()}) {
                var shade by remember(state.preferences.backgroundShade) {mutableFloatStateOf(state.preferences.backgroundShade)}
                Text("背景遮罩 · ${(shade*100).toInt()}%",style=MaterialTheme.typography.bodySmall)
                Slider(value=shade,onValueChange={shade=it},onValueChangeFinished={onPreferences {it.copy(backgroundShade=shade)}},valueRange=0.4f..1f)
            }
            InfoText("课程颜色自动分配，同一门课在所有周保持同色。")
        }}
        item {SoftCard(Modifier.padding(horizontal=20.dp)) {
            Text("本地数据",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            SettingsAction("导出备份","包含课表、待办、设置、修改记录和背景",onBackup)
            HorizontalDivider()
            SettingsAction("从备份恢复","恢复前会展示备份内容数量",onRestoreBackup)
            InfoText("数据保存在本机。卸载应用前请先导出备份。")
        }}
        item {Text("课序 0.1.0 · 个人课表与待办",modifier=Modifier.fillMaxWidth().padding(horizontal=22.dp),style=MaterialTheme.typography.labelSmall,color=Muted)}
    }
}

@Composable private fun SettingsAction(title: String,description: String,onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {Text(title,fontWeight=FontWeight.Medium);InfoText(description)}
        Icon(Icons.Default.ChevronRight,null,tint=Muted)
    }
}
@Composable private fun SettingsSwitch(title: String,description: String,checked: Boolean,onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end=10.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {Text(title,fontWeight=FontWeight.Medium);InfoText(description)}
        Switch(checked=checked,onCheckedChange=onChange)
    }
}

@Composable fun HistoryDialog(state: AppState,onDismiss: () -> Unit,onRestore: (String?) -> Unit) {
    val modifiedIds=(state.schedule.edits.map {it.lessonId}+state.schedule.added.map {it.id}).distinct()
    FullPageDialog("修改记录与恢复",onDismiss) { padding ->
        LazyColumn(Modifier.padding(padding),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(modifiedIds.isNotEmpty()) item {SoftCard {
                Text("当前有 ${modifiedIds.size} 条安排经过调整",fontWeight=FontWeight.Bold)
                InfoText("可恢复单条安排，也可恢复整个课表。历史记录会保留。")
                OutlinedButton(onClick={onRestore(null)},modifier=Modifier.fillMaxWidth()) {Text("恢复整个课表的原安排")}
            }}
            items(modifiedIds,key={"modified:$it"}) { id ->
                val base=state.schedule.base(id)
                SoftCard {
                    Text(base?.name ?: "未匹配到新课表的调整",fontWeight=FontWeight.SemiBold)
                    base?.let {InfoText(it.description())}
                    val edits=state.schedule.edits.filter {it.lessonId==id}
                    if(edits.isNotEmpty()) InfoText(edits.joinToString("；") { edit -> (edit.week?.let {"原第 $it 周"} ?: "全部周次") + if(edit.replacement==null) "已取消" else "已调整" })
                    TextButton(onClick={onRestore(id)}) {Text(if(base==null) "移除未匹配调整" else if(state.schedule.added.any {it.id==id}) "移除手动新增" else "恢复此安排")}
                }
            }
            item {Text("全部记录",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)}
            if(state.history.isEmpty()) item {InfoText("导入、调课、取消和恢复操作会记录在这里。")}
            items(state.history,key={it.id}) { record -> SoftCard {
                Text(record.title,fontWeight=FontWeight.SemiBold)
                InfoText(fullMoment(record.time))
                Text("修改前",style=MaterialTheme.typography.labelMedium,color=Muted);Text(record.before,style=MaterialTheme.typography.bodySmall)
                Text("修改后",style=MaterialTheme.typography.labelMedium,color=Muted);Text(record.after,style=MaterialTheme.typography.bodySmall)
            } }
        }
    }
}
