package cn.lesson.app

import android.app.Application
import cn.lesson.app.data.AppRepository
import cn.lesson.app.data.Reminders
import androidx.work.Configuration

class LessonApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration get() = Configuration.Builder().build()
    val repository by lazy { AppRepository(this) }
    override fun onCreate() {
        super.onCreate()
        Reminders.createChannels(this)
        repository.start()
        Reminders.ensurePeriodicSync(this)
    }
}
