package cn.lesson.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cn.lesson.app.data.*
import cn.lesson.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class PendingImport(val fileName: String,val result: ImportResult)
class AppViewModel(application: Application): AndroidViewModel(application) {
    private val repo = (application as LessonApplication).repository
    val state = repo.state
    val loadError = repo.error
    val message = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val pendingImport = MutableStateFlow<PendingImport?>(null)
    val pendingBackup = MutableStateFlow<AppState?>(null)

    private fun task(block: suspend () -> Unit) {
        viewModelScope.launch {
            busy.value=true
            try { withContext(Dispatchers.IO) { block() } }
            catch (e: Exception) { message.value=e.message ?: "操作未完成，请重试" }
            finally { busy.value=false }
        }
    }
    fun update(success: String? = null, after: () -> Unit = {}, transform: (AppState) -> AppState) = task {
        repo.update(transform)
        withContext(Dispatchers.Main) { after() }
        if (success != null) message.value=success
    }
    fun importFile(uri: Uri) = task {
        val context = getApplication<Application>()
        val name = LocalFiles.name(context,uri)
        pendingImport.value=PendingImport(name,Importer.read(LocalFiles.read(context,uri),name))
    }
    fun confirmImport() {
        val pending = pendingImport.value ?: return
        task { repo.update { Actions.import(it,pending.result,pending.fileName) }; pendingImport.value=null; message.value="已导入 ${pending.result.lessons.size} 条上课安排" }
    }
    fun background(tab: Int,uri: Uri) = task {
        val path = LocalFiles.saveBackground(getApplication(),uri)
        repo.update { it.copy(preferences=it.preferences.copy(backgrounds=it.preferences.backgrounds.toMutableList().apply { set(tab,path) })) }
        message.value="背景已更新"
    }
    fun export(uri: Uri) = task {
        LocalFiles.exportBackup(getApplication(),requireNotNull(state.value),uri); message.value="备份已保存，包含课表、待办、修改记录和背景"
    }
    fun readBackup(uri: Uri) = task { pendingBackup.value=LocalFiles.readBackup(getApplication(),uri) }
    fun confirmBackup() {
        val backup = pendingBackup.value ?: return
        task { repo.replace(backup); pendingBackup.value=null; message.value="备份已恢复" }
    }
    fun reschedule() = task { repo.reschedule() }
}
