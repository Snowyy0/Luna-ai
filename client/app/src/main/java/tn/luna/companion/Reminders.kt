package tn.luna.companion

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

internal object ReminderPreferences {
    private const val PREFERENCES = "luna_reminders"
    private const val ENABLED = "reminders_enabled"
    private const val LAST_ACTIVITY = "last_activity"

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getBoolean(ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        check(context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putBoolean(ENABLED, enabled).commit()) { "Could not save reminder settings." }
    }

    fun recordActivity(context: Context) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putLong(LAST_ACTIVITY, System.currentTimeMillis()).apply()
    }

    fun lastActivity(context: Context): Long =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getLong(LAST_ACTIVITY, 0L)
}

internal object ReminderScheduler {
    private const val COMEBACK_WORK = "luna_comeback_reminder"
    private const val KIND = "kind"
    private const val COMEBACK = "comeback"

    fun sync(context: Context) {
        ensureNotificationChannel(context)
        val workManager = WorkManager.getInstance(context)
        if (!ReminderPreferences.enabled(context)) {
            workManager.cancelUniqueWork(COMEBACK_WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<LunaReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delayUntil(19, 0), TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putString(KIND, COMEBACK).build())
            .build()
        workManager.enqueueUniquePeriodicWork(COMEBACK_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private fun delayUntil(hour: Int, minute: Int): Long {
        val now = LocalDateTime.now()
        var target = now.toLocalDate().atTime(LocalTime.of(hour, minute))
        if (!target.isAfter(now)) target = target.plusDays(1)
        return Duration.between(now, target).toMillis()
    }

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Luna reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Optional chat reminders from Luna." }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    internal const val CHANNEL_ID = "luna_reminders"
}

class LunaReminderWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result {
        if (!ReminderPreferences.enabled(applicationContext)) return Result.success()
        return when (inputData.getString("kind")) {
            "comeback" -> {
                val lastActivity = ReminderPreferences.lastActivity(applicationContext)
                if (System.currentTimeMillis() - lastActivity >= TimeUnit.HOURS.toMillis(24)) {
                    postNotification(
                        1,
                        "A little hello from Luna",
                        "Whenever you feel like talking, Luna is here."
                    )
                }
                Result.success()
            }
            else -> Result.failure()
        }
    }

    private fun postNotification(id: Int, title: String, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) return
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_luna)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(id, notification)
    }
}

internal fun notificationsAllowed(context: Context): Boolean =
    (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        NotificationManagerCompat.from(context).areNotificationsEnabled()
