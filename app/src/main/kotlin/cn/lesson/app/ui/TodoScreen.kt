package cn.lesson.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import cn.lesson.core.*
import java.time.*
import java.time.format.DateTimeFormatter

fun formatMoment(value: Long): String = Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
fun fullMoment(value: Long): String = Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

@Composable fun TodoScreen(state: AppState,onEdit: (Todo) -> Unit,onComplete: (Todo) -> Unit) {
    var archived by rememberSaveable {mutableStateOf(false)}
    val active=state.todos.count {it.doneAt==null}
    val list=state.todos.filter { (it.doneAt!=null)==archived }.sortedWith(if(archived) compareByDescending {it.doneAt} else compareBy<Todo> {it.dueAt ?: Long.MAX_VALUE}.thenBy {it.title})
    val today=LocalDate.now()
    fun group(todo: Todo): String {
        if(archived) return "已完成"
        val due=todo.dueAt ?: return "未设截止时间"
        val date=Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault()).toLocalDate()
        return if(due<System.currentTimeMillis()) "已逾期" else if(date==today) "今天" else "接下来"
    }
    Box(Modifier.fillMaxSize()) {
        Column {
            PageHeading("待办",if(active==0) "给今天留一点从容" else "还有 $active 件事，慢慢完成")
            Row(Modifier.padding(horizontal=20.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                FilterChip(selected=!archived,onClick={archived=false},label={Text("未完成 · $active")})
                FilterChip(selected=archived,onClick={archived=true},label={Text("已归档 · ${state.todos.size-active}")})
            }
            if(list.isEmpty()) EmptyState(if(archived) "这里收藏你的完成" else "暂时没有待办",if(archived) "勾选完成的事情会自动来到这里。" else "点右下角，记下一件要做的事。")
            LazyColumn(contentPadding=PaddingValues(start=20.dp,end=20.dp,top=14.dp,bottom=100.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                list.groupBy(::group).forEach { (name,todos) ->
                    item {Text(name,style=MaterialTheme.typography.labelLarge,color=if(name=="已逾期") MaterialTheme.colorScheme.error else Muted,modifier=Modifier.padding(vertical=6.dp))}
                    items(todos,key={it.id}) { todo ->
                        Surface(shape=RoundedCornerShape(18.dp),color=Color.White.copy(alpha=0.9f),border=BorderStroke(1.dp,Color(0xFFE3EAE1))) {
                            Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.CenterVertically) {
                                Checkbox(checked=todo.doneAt!=null,onCheckedChange={onComplete(todo)})
                                Column(Modifier.weight(1f).clickable {onEdit(todo)}.padding(vertical=8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                    Text(todo.title,fontWeight=FontWeight.Medium,textDecoration=if(archived) TextDecoration.LineThrough else null)
                                    if(todo.notes.isNotBlank()) InfoText(todo.notes)
                                    todo.dueAt?.let {InfoText("截止 · ${formatMoment(it)}")}
                                    todo.remindAt?.takeIf {todo.doneAt==null}?.let {InfoText("提醒 · ${formatMoment(it)}${if(!state.preferences.todoReminders) "（总开关已关）" else ""}")}
                                    todo.doneAt?.let {InfoText("完成于 ${formatMoment(it)} · 点勾选框可恢复")}
                                }
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(onClick={onEdit(Todo())},containerColor=Sage,contentColor=Color.White,modifier=Modifier.align(Alignment.BottomEnd).padding(20.dp)) {Icon(Icons.Default.Add,"新增待办")}
    }
}
