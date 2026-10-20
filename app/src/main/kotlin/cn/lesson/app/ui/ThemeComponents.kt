@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package cn.lesson.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

val Paper = Color(0xFFF6F8F5)
val Ink = Color(0xFF223A31)
val Sage = Color(0xFF386653)
val Muted = Color(0xFF66776D)

@Composable fun LessonTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme=lightColorScheme(primary=Sage,onPrimary=Color.White,primaryContainer=Color(0xFFDCEBDF),
        onPrimaryContainer=Ink,secondary=Color(0xFF607B6B),secondaryContainer=Color(0xFFDCEBDF),onSecondaryContainer=Ink,background=Paper,surface=Paper,
        surfaceContainer=Color(0xFFEDF2EC),surfaceContainerLow=Color(0xFFF1F5EF),onSurface=Ink,onSurfaceVariant=Muted,
        outline=Color(0xFFADBEB1),outlineVariant=Color(0xFFDCE4DB)),content=content)
}

fun courseColor(index: Int): Color {
    val palette = listOf(0xFFDCEBDD,0xFFDDE8F5,0xFFF6E7C8,0xFFE9DEF2,0xFFF5DFD9,0xFFD6ECE8,0xFFE8E9CF,0xFFE0E3F4)
    return if(index < palette.size) Color(palette[index.coerceAtLeast(0)]) else Color.hsl((index*137.508f)%360,0.43f,0.86f)
}

@Composable fun PageHeading(title: String, subtitle: String, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal=22.dp,vertical=18.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(4.dp)); Text(subtitle,style=MaterialTheme.typography.bodySmall,color=Muted)
        }
        action?.invoke()
    }
}

@Composable fun SoftCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier=modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),color=Color.White.copy(alpha=0.9f),
        border=BorderStroke(1.dp,Color(0xFFE3EAE1))) {
        Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
    }
}

@Composable fun InfoText(text: String) {
    Text(text,style=MaterialTheme.typography.bodySmall,color=Muted,lineHeight=20.sp)
}

@Composable fun EmptyState(title: String, text: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(24.dp))
        Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
        Text(text,style=MaterialTheme.typography.bodyMedium,color=Muted)
        if(action != null) Button(onClick=onAction) { Text(action) }
    }
}

@Composable fun FullPageDialog(title: String,onDismiss: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        LessonTheme {
            Scaffold(modifier=Modifier.fillMaxSize().imePadding(),containerColor=Paper,
                topBar={ TopAppBar(title={Text(title)},navigationIcon={IconButton(onClick=onDismiss) { Icon(Icons.Default.Close,"关闭") }}) },content=content)
        }
    }
}

data class Confirmation(val title: String,val text: String,val action: () -> Unit)
@Composable fun ConfirmationDialog(value: Confirmation,onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={Text(value.title)},text={Text(value.text)},
        confirmButton={TextButton(onClick={value.action();onDismiss()}) {Text("确认")}},
        dismissButton={TextButton(onClick=onDismiss) {Text("取消")}})
}
