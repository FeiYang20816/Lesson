@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package cn.lesson.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import cn.lesson.core.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable fun ScheduleScreen(state: AppState,onImport: () -> Unit,onSettings: () -> Unit,onLesson: (Occurrence) -> Unit,onAdd: (Int,Int,Int) -> Unit) {
    var week by rememberSaveable { mutableIntStateOf(state.term.currentWeek().coerceIn(1,state.term.totalWeeks)) }
    var overview by rememberSaveable { mutableStateOf(false) }
    var weekPicker by remember { mutableStateOf(false) }
    LaunchedEffect(state.term.monday,state.term.totalWeeks) { week=state.term.currentWeek().coerceIn(1,state.term.totalWeeks) }
    val hasCourses=state.schedule.originals.isNotEmpty() || state.schedule.added.isNotEmpty()
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            PageHeading("课表",state.term.name) {
                IconButton(onClick=onImport) { Icon(Icons.Default.FileUpload,"导入课表") }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(selected=!overview,onClick={overview=false},label={Text("按周查看")})
                FilterChip(selected=overview,onClick={overview=true},label={Text("全学期总览")})
                Spacer(Modifier.weight(1f))
                TextButton(onClick={week=state.term.currentWeek().coerceIn(1,state.term.totalWeeks);overview=false}) {Text("回到本周")}
            }
            if(!hasCourses) {
                EmptyState("让这一周清晰起来","导入学校课表，课程会按周次自动排列。","导入课表",onImport)
                TextButton(onClick={onAdd(1,1,week)},modifier=Modifier.align(Alignment.CenterHorizontally)) {Text("也可以手动添加课程")}
            } else if(overview) SemesterOverview(state,onLesson)
            else {
                Row(Modifier.fillMaxWidth().padding(horizontal=10.dp),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick={week--},enabled=week>1) {Icon(Icons.Default.ChevronLeft,"上一周")}
                    Column(Modifier.weight(1f).clickable {weekPicker=true}.padding(8.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                        Text("第 $week 周  ⌄",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
                        val start=state.term.date(week,1); val end=state.term.date(week,7)
                        Text(if(start!=null) "${start.format(DateTimeFormatter.ofPattern("M月d日"))} — ${end!!.format(DateTimeFormatter.ofPattern("M月d日"))}" else "设置开学日期后显示日历日期",style=MaterialTheme.typography.bodySmall,color=Muted)
                    }
                    IconButton(onClick={week++},enabled=week<state.term.totalWeeks) {Icon(Icons.Default.ChevronRight,"下一周")}
                }
                if(state.term.monday==null) TextButton(onClick=onSettings,modifier=Modifier.align(Alignment.CenterHorizontally)) {Text("设置第 1 周星期一的日期")}
                Row(Modifier.padding(horizontal=22.dp,vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text("左右滑动查看整周 · 点空白处添加",style=MaterialTheme.typography.labelSmall,color=Muted)
                    if(ScheduleEngine.conflicts(state.schedule,week).isNotEmpty()) Text("有时间冲突",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.error)
                }
                WeekGrid(state,week,onLesson,onAdd,Modifier.weight(1f))
            }
        }
        if(hasCourses) FloatingActionButton(onClick={onAdd(1,1,week)},containerColor=Sage,contentColor=Color.White,modifier=Modifier.align(Alignment.BottomEnd).padding(20.dp)) {Icon(Icons.Default.Add,"新增课程")}
    }
    if(weekPicker) AlertDialog(onDismissRequest={weekPicker=false},title={Text("选择周次")},text={
        FlowRow(Modifier.verticalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            (1..state.term.totalWeeks).forEach { n -> FilterChip(selected=week==n,onClick={week=n;weekPicker=false},label={Text("第 $n 周")}) }
        }
    },confirmButton={TextButton(onClick={weekPicker=false}) {Text("关闭")}})
}

@Composable private fun WeekGrid(state: AppState,week: Int,onLesson: (Occurrence) -> Unit,onAdd: (Int,Int,Int) -> Unit,modifier: Modifier = Modifier) {
    val lessons=remember(state.schedule,week) {ScheduleEngine.occurrences(state.schedule,week)}
    val conflicts=remember(state.schedule,week) {ScheduleEngine.conflicts(state.schedule,week)}
    val periods=maxOf(state.term.periods.size,lessons.maxOfOrNull {it.lesson.endPeriod} ?: 12)
    val periodHeight=59.dp
    val horizontal=rememberScrollState(); val vertical=rememberScrollState()
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal=8.dp)) {
        val columnWidth=maxOf((maxWidth-42.dp)/7,82.dp)
        Column(Modifier.fillMaxHeight().horizontalScroll(horizontal)) {
            Row(Modifier.width(42.dp+columnWidth*7).background(Paper.copy(alpha=0.94f))) {
                Box(Modifier.width(42.dp).height(48.dp),contentAlignment=Alignment.Center) {Text("节次",style=MaterialTheme.typography.labelSmall,color=Muted)}
                (1..7).forEach { day ->
                    val date=state.term.date(week,day); val today=date==LocalDate.now()
                    Column(Modifier.width(columnWidth).height(48.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                        Text(weekDays[day-1],color=if(today) Sage else Ink,fontWeight=if(today) FontWeight.Bold else FontWeight.Normal)
                        if(date!=null) Text("${date.monthValue}/${date.dayOfMonth}",style=MaterialTheme.typography.labelSmall,color=if(today) Sage else Muted)
                    }
                }
            }
            Row(Modifier.weight(1f).verticalScroll(vertical).padding(bottom=90.dp)) {
                Column(Modifier.width(42.dp)) {
                    (1..periods).forEach { p -> Column(Modifier.height(periodHeight).fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                        Text("$p",style=MaterialTheme.typography.labelLarge,fontWeight=FontWeight.SemiBold)
                        if(state.preferences.showTimes) state.term.periods.getOrNull(p-1)?.let { t ->
                            if(t.start.isNotBlank()) Text(t.start,fontSize=8.sp,color=Muted)
                            if(t.end.isNotBlank()) Text(t.end,fontSize=8.sp,color=Muted)
                        }
                    } }
                }
                (1..7).forEach { day ->
                    val dayLessons=lessons.filter {it.lesson.day==day}
                    val lanes=mutableListOf<Int>(); val assigned=dayLessons.map { item ->
                        val lane=lanes.indexOfFirst {it < item.lesson.startPeriod}.let {if(it<0) lanes.size else it}
                        if(lane==lanes.size) lanes.add(item.lesson.endPeriod) else lanes[lane]=item.lesson.endPeriod
                        item to lane
                    }
                    val count=lanes.size.coerceAtLeast(1)
                    Box(Modifier.width(columnWidth).height(periodHeight*periods)) {
                        Column {
                            (1..periods).forEach { period -> Box(Modifier.fillMaxWidth().height(periodHeight)
                                .border(0.5.dp,Color(0xFFE0E6DE)).clickable {onAdd(day,period,week)}) }
                        }
                        assigned.forEach { (item,lane) ->
                            val lesson=item.lesson; val length=lesson.endPeriod-lesson.startPeriod+1
                            Surface(Modifier.offset(x=columnWidth/count*lane+2.dp,y=periodHeight*(lesson.startPeriod-1)+2.dp)
                                .width(columnWidth/count-4.dp).height(periodHeight*length-4.dp).clickable {onLesson(item)},
                                shape=RoundedCornerShape(10.dp),color=courseColor(state.schedule.color(lesson.name)),
                                border=if(lesson.id in conflicts) BorderStroke(1.dp,MaterialTheme.colorScheme.error) else null) {
                                Column(Modifier.padding(horizontal=6.dp,vertical=6.dp)) {
                                    Text(lesson.name,fontSize=12.sp,lineHeight=16.sp,fontWeight=FontWeight.SemiBold,
                                        maxLines=if(length==1) 1 else 4,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f,false))
                                    Spacer(Modifier.weight(1f))
                                    Text(lesson.room.ifBlank {"教室待定"},fontSize=11.sp,lineHeight=14.sp,fontWeight=FontWeight.Medium,maxLines=if(length==1) 1 else 2,overflow=TextOverflow.Ellipsis)
                                    Text("${lesson.startPeriod}${if(lesson.endPeriod!=lesson.startPeriod) "–${lesson.endPeriod}" else ""}节${if(item.modified) " · 调" else ""}",fontSize=9.sp,lineHeight=12.sp,color=Muted)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun SemesterOverview(state: AppState,onLesson: (Occurrence) -> Unit) {
    val grouped=remember(state.schedule) {ScheduleEngine.occurrences(state.schedule).groupBy {it.lesson.name}}
    LazyColumn(contentPadding=PaddingValues(start=20.dp,end=20.dp,top=12.dp,bottom=100.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        if(grouped.isEmpty()) item {EmptyState("当前没有上课安排","已取消的课程可以在“我的 → 修改记录”中恢复。")}
        grouped.forEach { (name,occurrences) -> item(key=name) {
            Surface(shape=RoundedCornerShape(20.dp),color=courseColor(state.schedule.color(name))) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text(name,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                    occurrences.groupBy { Triple(it.lesson.day,it.lesson.startPeriod to it.lesson.endPeriod,it.lesson.room) }.forEach { (_,list) ->
                        val first=list.first()
                        Column(Modifier.fillMaxWidth().clickable {onLesson(first)}.padding(vertical=4.dp)) {
                            Text("${first.lesson.room.ifBlank {"教室待定"}} · ${weekDays[first.lesson.day-1]} · ${first.lesson.periodLabel}",fontWeight=FontWeight.Medium)
                            InfoText("第 ${Weeks.format(list.map {it.week})} 周" + if(list.any {it.modified}) " · 包含调整" else "")
                        }
                    }
                }
            }
        } }
        if(state.schedule.notes.isNotEmpty()) item {SoftCard {Text("学期备注",fontWeight=FontWeight.SemiBold);state.schedule.notes.forEach {InfoText(it)}}}
    }
}
