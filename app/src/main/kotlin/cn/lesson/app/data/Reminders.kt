package cn.lesson.app.data

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import cn.lesson.app.*
import cn.lesson.core.*
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

object Reminders {
    private const val FIRE = "cn.lesson.app.REMINDER"
    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(listOf(
            NotificationChannel("courses","课程提醒",NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel("todos","待办提醒",NotificationManager.IMPORTANCE_HIGH)
        ))
    }
    fun notificationsAllowed(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    fun exactAllowed(context: Context): Boolean = Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    private fun alarmIntent(context: Context, at: Long = 0) = PendingIntent.getBroadcast(context,100,
        Intent(context,ReminderReceiver::class.java).setAction(FIRE).putExtra("at",at),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun schedule(context: Context, state: AppState) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        alarm.cancel(alarmIntent(context))
        if (!notificationsAllowed(context)) return
        val next = ReminderPlan.events(state,System.currentTimeMillis()).firstOrNull() ?: return
        val intent = alarmIntent(context,next.at)
        try {
            if (exactAllowed(context)) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next.at,intent)
            else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next.at,intent)
        } catch (_: SecurityException) { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next.at,intent) }
    }

    suspend fun receive(context: Context, intent: Intent) {
        val repo = (context.applicationContext as LessonApplication).repository
        val state = repo.load()
        if (intent.action == FIRE) {
            val at = intent.getLongExtra("at",0)
            val now = System.currentTimeMillis()
            if (at > 0 && now >= at - 1000) {
                val prefs = context.getSharedPreferences("reminder_delivery",Context.MODE_PRIVATE)
                val delivered = prefs.getStringSet("keys",emptySet()).orEmpty().toMutableSet()
                ReminderPlan.dueEvents(state,at,now).forEach { event ->
                    val key = "${event.key}:${event.at}"
                    if (key !in delivered && notify(context,event)) delivered.add(key)
                }
                prefs.edit().putStringSet("keys",delivered.toList().takeLast(512).toSet()).apply()
            }
        }
        repo.reschedule()
    }

    private fun notify(context: Context, event: ReminderEvent): Boolean {
        if (!notificationsAllowed(context)) return false
        val intent = PendingIntent.getActivity(context,if(event.course) 1 else 2,
            Intent(context,MainActivity::class.java).putExtra("tab",if(event.course) 0 else 1).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context,if(event.course) "courses" else "todos")
            .setSmallIcon(cn.lesson.app.R.drawable.ic_notification).setContentTitle(event.title).setContentText(event.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(event.text)).setContentIntent(intent)
            .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).build()
        return try { NotificationManagerCompat.from(context).notify(event.key,0,notification); true } catch (_: SecurityException) { false }
    }

    fun ensurePeriodicSync(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("reminder-recovery",ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReminderWorker>(12,TimeUnit.HOURS).setInitialDelay(12,TimeUnit.HOURS).build())
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        if (intent.action !in setOf("cn.lesson.app.REMINDER",Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED,Intent.ACTION_TIMEZONE_CHANGED,"android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED")) return
        val result = goAsync()
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {
            try { withTimeout(20000) { Reminders.receive(context,intent) } }
            catch (e: Exception) { android.util.Log.e("Lesson","Reminder recovery failed",e) }
            finally { result.finish() }
        }
    }
}
class ReminderWorker(context: Context,params: WorkerParameters): CoroutineWorker(context,params) {
    override suspend fun doWork(): Result = try {
        val repo = (applicationContext as LessonApplication).repository
        repo.load(); repo.reschedule(); Result.success()
    } catch (_: Exception) { Result.retry() }
}
