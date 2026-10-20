package cn.lesson.app.data

import android.content.Context
import androidx.room.*
import cn.lesson.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Entity(tableName="app_state")
data class StoredState(@PrimaryKey val id: Int = 1, val json: String)
@Dao interface StateDao {
    @Query("SELECT * FROM app_state WHERE id = 1") suspend fun read(): StoredState?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun write(row: StoredState)
}
@Database(entities=[StoredState::class],version=1,exportSchema=true)
abstract class LessonDatabase : RoomDatabase() { abstract fun stateDao(): StateDao }

/** One atomic document keeps imported baseline, edits and the audit entry consistent. */
class AppRepository(private val context: Context) : AutoCloseable {
    private val db = Room.databaseBuilder(context,LessonDatabase::class.java,"lesson.db").build()
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<AppState?>(null)
    val state: StateFlow<AppState?> = _state.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    fun start() { scope.launch { runCatching { load() }.onFailure { _error.value = "本地数据读取失败：${it.message}" } } }
    suspend fun load(): AppState = mutex.withLock {
        _state.value?.let { return@withLock it }
        val state = db.stateDao().read()?.let { BackupCodec.decode(it.json) } ?: AppState()
        _state.value = state
        Reminders.schedule(context,state)
        state
    }
    suspend fun update(transform: (AppState) -> AppState) = mutex.withLock {
        val current = _state.value ?: error("本地数据尚未加载")
        persist(transform(current))
    }
    suspend fun replace(state: AppState) = mutex.withLock { persist(state) }
    private suspend fun persist(state: AppState) {
        state.validate()
        db.stateDao().write(StoredState(json=BackupCodec.encode(state)))
        _state.value = state
        _error.value = null
        Reminders.schedule(context,state)
    }
    suspend fun reschedule() { mutex.withLock { _state.value?.let { Reminders.schedule(context,it) } } }
    override fun close() { scope.cancel(); db.close() }
}
