package cn.lesson.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.ViewModelProvider
import cn.lesson.app.ui.LessonRoot

class MainActivity: ComponentActivity() {
    private val model: AppViewModel by viewModels { ViewModelProvider.AndroidViewModelFactory(application) }
    private val requestedTab = mutableIntStateOf(0)
    private val requestVersion = mutableIntStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedTab.intValue=intent.getIntExtra("tab",0)
        enableEdgeToEdge()
        setContent { LessonRoot(model,requestedTab.intValue,requestVersion.intValue) }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent); requestedTab.intValue=intent.getIntExtra("tab",0); requestVersion.intValue++
    }
    override fun onResume() { super.onResume(); model.reschedule() }
}
