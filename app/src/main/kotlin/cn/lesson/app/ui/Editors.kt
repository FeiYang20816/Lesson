@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package cn.lesson.app.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cn.lesson.core.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun LessonEditor(state: AppState,request: LessonEditorRequest,onDismiss: () -> Unit,onSave: (Lesson,Int?) -> Unit) {
    val occurrence=request.occurrence
    var single by rememberSaveable {mutableStateOf(occurrence!=null)}
    val base=occurrence?.let { item ->
        val global=state.schedule.edits.find {it.lessonId==item.lesson.id && it.week==null}
        (global?.replacement ?: state.schedule.base(item.lesson.id)) ?: item.lesson
    } ?: Lesson(day=request.day,startPeriod=request.period,endPeriod=minOf(request.period+1,maxOf(request.period,state.term.periods.size)),weeks=listOf(request.week))
    val initial=if(single && occurrence!=null) occurrence.lesson.copy(weeks=listOf(occurrence.week)) else base
    var name by rememberSaveable(single) {mutableStateOf(initial.name)}
    var room by rememberSaveable(single) {mutableStateOf(initial.room)}
    var day by rememberSaveable(single) {mutableIntStateOf(initial.day)}
    var start by rememberSaveable(single) {mutableStateOf(initial.startPeriod.toString())}
    var end by rememberSaveable(single) {mutableStateOf(initial.endPeriod.toString())}
    var weeks by rememberSaveable(single) {mutableStateOf(Weeks.format(initial.weeks))}
    var reminder by rememberSaveable(single) {mutableStateOf(initial.reminder)}
    var error by remember {mutableStateOf<String?>(null)}
    // Keep an ID stable while recomposing a new-course form.
    val id=remember {base.id}
    FullPageDialog(if(occurrence==null) "新增课程" else "修改课程",onDismiss) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            if(occurrence!=null) SoftCard {
                Text("修改范围",fontWeight=FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth().clickable {single=true},verticalAlignment=Alignment.CenterVertically) {
                    RadioButton(selected=single,onClick={single=true}); Text("仅这次（原第 ${occurrence.anchorWeek} 周）")
                }
                Row(Modifier.fillMaxWidth().clickable {single=false},verticalAlignment=Alignment.CenterVertically) {
                    RadioButton(selected=!single,onClick={single=false}); Text("此上课安排的全部周次")
                }
                if(!single && state.schedule.edits.any {it.lessonId==initial.id && it.week!=null}) InfoText("修改全部周次会替换这条安排已有的单次调整；记录仍会保留。")
            }
            OutlinedTextField(value=name,onValueChange={name=it},label={Text("课程名称")},singleLine=false,maxLines=3,modifier=Modifier.fillMaxWidth())
            OutlinedTextField(value=room,onValueChange={room=it},label={Text("教室位置")},placeholder={Text("例如：世 A104")},modifier=Modifier.fillMaxWidth())
            Text("上课星期",fontWeight=FontWeight.SemiBold)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                weekDays.forEachIndexed { index,label -> FilterChip(selected=day==index+1,onClick={day=index+1},label={Text(label)}) }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value=start,onValueChange={start=it.filter(Char::isDigit).take(2)},label={Text("开始节次")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,modifier=Modifier.weight(1f))
                OutlinedTextField(value=end,onValueChange={end=it.filter(Char::isDigit).take(2)},label={Text("结束节次")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,modifier=Modifier.weight(1f))
            }
            OutlinedTextField(value=weeks,onValueChange={weeks=it},label={Text(if(single) "调整到第几周" else "上课周次")},supportingText={Text(if(single) "填写一个周次，可将这次课程移动到另一周" else "例如 1-8,12 或 1-18[单周]")},modifier=Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {Column(Modifier.weight(1f)) {Text("此课程提醒");InfoText("同时受“我的”中的课程提醒总开关控制")};Switch(checked=reminder,onCheckedChange={reminder=it})}
            error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            Button(onClick={
                try {
                    val parsed=Weeks.parse(weeks)
                    require(!single || parsed.size==1) {"单次调课只能选择一个目标周次"}
                    val first=start.toIntOrNull() ?: throw IllegalArgumentException("请填写开始节次")
                    val last=end.toIntOrNull() ?: throw IllegalArgumentException("请填写结束节次")
                    val lesson=initial.copy(id=id,name=name.trim(),room=room.trim(),day=day,startPeriod=first,endPeriod=last,weeks=parsed,reminder=reminder)
                    lesson.validate(state.term.totalWeeks)
                    onSave(lesson,if(single) occurrence?.anchorWeek else null)
                } catch(e: Exception) {error=e.message ?: "请检查填写的内容"}
            },modifier=Modifier.fillMaxWidth()) {Text("保存课程")}
            InfoText("相同课程名称会使用同一种颜色。具体上课时刻可在“我的 → 学期与节次”中设置。")
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable fun TodoEditor(todo: Todo,onDismiss: () -> Unit,onSave: (Todo) -> Unit,onDelete: () -> Unit) {
    var title by rememberSaveable {mutableStateOf(todo.title)}
    var notes by rememberSaveable {mutableStateOf(todo.notes)}
    var due by rememberSaveable {mutableStateOf(todo.dueAt)}
    var remind by rememberSaveable {mutableStateOf(todo.remindAt)}
    var error by remember {mutableStateOf<String?>(null)}
    FullPageDialog(if(todo.title.isBlank()) "新增待办" else "编辑待办",onDismiss) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(value=title,onValueChange={title=it},label={Text("要做什么")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(value=notes,onValueChange={notes=it},label={Text("补充说明（可选）")},minLines=3,modifier=Modifier.fillMaxWidth())
            MomentField("截止时间",due,onValue={due=it})
            MomentField("提醒时间",remind,onValue={remind=it})
            InfoText("截止与提醒可分别设置。开启“我的 → 待办提醒”后，未完成的待办才会发送通知。")
            error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            Button(onClick={
                if(title.isBlank() || title.length>200) error="请填写待办标题（最多 200 字）"
                else if(remind!=null && remind!!<=System.currentTimeMillis() && remind!=todo.remindAt && todo.doneAt==null) error="请选择未来的提醒时间"
                else onSave(todo.copy(title=title.trim(),notes=notes.trim(),dueAt=due,remindAt=remind))
            },modifier=Modifier.fillMaxWidth()) {Text("保存待办")}
            if(todo.title.isNotBlank()) TextButton(onClick=onDelete,modifier=Modifier.fillMaxWidth()) {Text("删除待办",color=MaterialTheme.colorScheme.error)}
        }
    }
}

@Composable private fun MomentField(label: String,value: Long?,onValue: (Long?) -> Unit) {
    val context=LocalContext.current
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(label,fontWeight=FontWeight.SemiBold)
        Row(verticalAlignment=Alignment.CenterVertically) {
            OutlinedButton(onClick={
                val initial=value?.let {Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime()} ?: LocalDateTime.now().plusHours(1)
                DatePickerDialog(context,{_,year,month,day ->
                    TimePickerDialog(context,{_,hour,minute -> onValue(LocalDateTime.of(year,month+1,day,hour,minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())},initial.hour,initial.minute,true).show()
                },initial.year,initial.monthValue-1,initial.dayOfMonth).show()
            },modifier=Modifier.weight(1f)) {Text(value?.let(::fullMoment) ?: "选择日期和时间")}
            if(value!=null) TextButton(onClick={onValue(null)}) {Text("清除")}
        }
    }
}

@Composable fun TermEditor(state: AppState,onDismiss: () -> Unit,onSave: (Term) -> Unit) {
    val context=LocalContext.current
    var name by rememberSaveable {mutableStateOf(state.term.name)}
    var monday by rememberSaveable {mutableStateOf(state.term.monday)}
    var weeks by rememberSaveable {mutableStateOf(state.term.totalWeeks.toString())}
    var periods by remember {mutableStateOf(state.term.periods)}
    var error by remember {mutableStateOf<String?>(null)}
    FullPageDialog("学期与节次",onDismiss) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(value=name,onValueChange={name=it},label={Text("学期名称")},modifier=Modifier.fillMaxWidth())
            Text("第 1 周星期一",fontWeight=FontWeight.SemiBold)
            OutlinedButton(onClick={
                val initial=monday?.let(LocalDate::parse) ?: LocalDate.now()
                DatePickerDialog(context,{_,year,month,day ->
                    val date=LocalDate.of(year,month+1,day)
                    if(date.dayOfWeek!=DayOfWeek.MONDAY) error="请选择第 1 周的星期一"
                    else {monday=date.toString();error=null}
                },initial.year,initial.monthValue-1,initial.dayOfMonth).show()
            },modifier=Modifier.fillMaxWidth()) {Text(monday ?: "选择开学第 1 周的周一日期")}
            InfoText("它决定每一周对应的日期。学校文件未提供这一日期，请按校历填写。")
            OutlinedTextField(value=weeks,onValueChange={weeks=it.filter(Char::isDigit).take(2)},label={Text("学期总周数")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth())
            Text("节次时间",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            InfoText("具体时刻用于课程提醒，可留空。课表始终显示节次，时刻默认隐藏。")
            periods.forEachIndexed { index,time ->
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("第 ${index+1} 节",modifier=Modifier.width(58.dp))
                    TimeField(time.start,"上课",Modifier.weight(1f)) {value -> periods=periods.toMutableList().apply {set(index,time.copy(start=value))} }
                    TimeField(time.end,"下课",Modifier.weight(1f)) {value -> periods=periods.toMutableList().apply {set(index,time.copy(end=value))} }
                }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                if(periods.size<24) TextButton(onClick={periods=periods+PeriodTime()}) {Text("增加一节")}
                if(periods.size>1) TextButton(onClick={periods=periods.dropLast(1)}) {Text("移除最后一节")}
            }
            error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            Button(onClick={
                try {
                    val totalWeeks=weeks.toIntOrNull() ?: throw IllegalArgumentException("请填写学期总周数")
                    val term=state.term.copy(name=name.trim().ifBlank {"我的学期"},monday=monday,totalWeeks=totalWeeks,periods=periods)
                    val lessons=state.schedule.originals+state.schedule.added+state.schedule.edits.mapNotNull {it.replacement}
                    require(term.totalWeeks >= (lessons.flatMap {it.weeks}.maxOrNull() ?: 1)) {"总周数不能少于已有课程的最大周次"}
                    require(term.periods.size >= (lessons.maxOfOrNull {it.endPeriod} ?: 1)) {"节次数不能少于已有课程的结束节次"}
                    state.copy(term=term).validate();onSave(term)
                } catch(e: Exception) {error=e.message ?: "请检查学期设置"}
            },modifier=Modifier.fillMaxWidth()) {Text("保存学期设置")}
        }
    }
}

@Composable private fun TimeField(value: String,placeholder: String,modifier: Modifier,onValue: (String) -> Unit) {
    val context=LocalContext.current
    Column(modifier) {
        OutlinedButton(onClick={
            val initial=value.takeIf {it.isNotBlank()}?.let(LocalTime::parse) ?: LocalTime.of(8,0)
            TimePickerDialog(context,{_,hour,minute -> onValue(LocalTime.of(hour,minute).format(DateTimeFormatter.ofPattern("HH:mm")))},initial.hour,initial.minute,true).show()
        },contentPadding=PaddingValues(horizontal=8.dp),modifier=Modifier.fillMaxWidth()) {Text(value.ifBlank {placeholder})}
        if(value.isNotBlank()) TextButton(onClick={onValue("")},contentPadding=PaddingValues(0.dp),modifier=Modifier.align(Alignment.CenterHorizontally).height(30.dp)) {Text("清除",style=MaterialTheme.typography.labelSmall)}
    }
}
