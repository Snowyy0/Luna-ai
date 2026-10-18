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
import androidx.work.ExistingWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.OneTimeWorkRequestBuilder
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.TimeUnit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class LunaExam(val id: String, val title: String, val date: String) {
    fun daysUntil(today: LocalDate = LocalDate.now()): Long =
        LocalDate.parse(date).toEpochDay() - today.toEpochDay()
}

internal fun isExamReminderDay(daysUntilExam: Long): Boolean = daysUntilExam in 0..7

class ExamStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun all(): List<LunaExam> {
        val json = preferences.getString(EXAMS, null) ?: return emptyList()
        val type = object : TypeToken<List<LunaExam>>() {}.type
        return gson.fromJson<List<LunaExam>>(json, type) ?: emptyList()
    }

    fun add(title: String, date: LocalDate): LunaExam {
        val cleanTitle = title.trim()
        require(cleanTitle.isNotEmpty()) { "Enter an exam name." }
        require(date >= LocalDate.now()) { "Choose today or a future date." }
        val exam = LunaExam(UUID.randomUUID().toString(), cleanTitle, date.toString())
        save(all() + exam)
        return exam
    }

    fun remove(id: String) {
        save(all().filterNot { it.id == id })
    }

    private fun save(exams: List<LunaExam>) {
        check(preferences.edit().putString(EXAMS, gson.toJson(exams)).commit()) {
            "Could not save the exam calendar."
        }
    }

    private companion object {
        const val PREFERENCES = "luna_reminders"
        const val EXAMS = "exams"
    }
}

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
    private const val EXAM_WORK_PREFIX = "luna_exam_"
    private const val EXAM_TAG = "luna_exam_reminder"
    private const val KIND = "kind"
    private const val EXAM_ID = "exam_id"
    private const val COMEBACK = "comeback"
    private const val EXAM = "exam"

    fun sync(context: Context) {
        ensureNotificationChannel(context)
        val workManager = WorkManager.getInstance(context)
        if (!ReminderPreferences.enabled(context)) {
            workManager.cancelUniqueWork(COMEBACK_WORK)
            workManager.cancelAllWorkByTag(EXAM_TAG)
            return
        }
        ExamStore(context).all().forEach { scheduleExam(context, it) }
        val request = PeriodicWorkRequestBuilder<LunaReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delayUntil(19, 0), TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putString(KIND, COMEBACK).build())
            .build()
        workManager.enqueueUniquePeriodicWork(COMEBACK_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun scheduleExam(context: Context, exam: LunaExam) {
        if (!ReminderPreferences.enabled(context)) return
        enqueueExam(context, exam, nextMorningDelay())
    }

    fun cancelExam(context: Context, id: String) {
        WorkManager.getInstance(context).cancelAllWorkByTag("$EXAM_TAG:$id")
    }

    private fun enqueueExam(context: Context, exam: LunaExam, delayMillis: Long) {
        val request = OneTimeWorkRequestBuilder<LunaReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putString(KIND, EXAM).putString(EXAM_ID, exam.id).build())
            .addTag(EXAM_TAG)
            .addTag("$EXAM_TAG:${exam.id}")
            .build()
        val today = LocalDate.now(ZoneId.systemDefault())
        val nextDate = if (LocalTime.now(ZoneId.systemDefault()).isBefore(LocalTime.of(9, 0))) today else today.plusDays(1)
        WorkManager.getInstance(context).enqueueUniqueWork(
            "$EXAM_WORK_PREFIX${exam.id}_${nextDate}",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    private fun nextMorningDelay(): Long = delayUntil(9, 0)

    private fun delayUntil(hour: Int, minute: Int): Long {
        val now = LocalDateTime.now()
        var target = now.toLocalDate().atTime(LocalTime.of(hour, minute))
        if (!target.isAfter(now)) target = target.plusDays(1)
        return Duration.between(now, target).toMillis()
    }

    internal fun scheduleNextExamDay(context: Context, exam: LunaExam) {
        enqueueExam(context, exam, nextMorningDelay())
    }

    private fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Luna reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Optional chat and exam reminders from Luna." }
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
            "exam" -> remindAboutExam()
            else -> Result.failure()
        }
    }

    private fun remindAboutExam(): Result {
        val id = inputData.getString("exam_id") ?: return Result.failure()
        val exam = ExamStore(applicationContext).all().firstOrNull { it.id == id } ?: return Result.success()
        val daysLeft = exam.daysUntil()
        if (isExamReminderDay(daysLeft)) {
            val timing = when (daysLeft) {
                0L -> "today"
                1L -> "tomorrow"
                else -> "in $daysLeft days"
            }
            postNotification(
                exam.id.hashCode() and Int.MAX_VALUE,
                "${exam.title} exam $timing",
                "Your ${exam.title} exam is $timing. Luna is here if you’d like to talk or plan a small step."
            )
        }
        if (daysLeft > 0) ReminderScheduler.scheduleNextExamDay(applicationContext, exam)
        return Result.success()
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
