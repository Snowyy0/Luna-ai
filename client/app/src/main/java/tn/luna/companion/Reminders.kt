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

internal fun firstExamReminderDate(today: LocalDate, now: LocalTime, daysUntilExam: Long): LocalDate? =
    when {
        daysUntilExam < 0 -> null
        daysUntilExam == 0L && !now.isBefore(LocalTime.of(9, 0)) -> today
        !now.isBefore(LocalTime.of(9, 0)) -> today.plusDays(1)
        else -> today
    }

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

    fun examNotifiedOn(context: Context, id: String): String? =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString("exam_notified_$id", null)

    fun recordExamNotification(context: Context, id: String, date: LocalDate) {
        check(context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putString("exam_notified_$id", date.toString()).commit()) {
            "Could not save exam reminder state."
        }
    }
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
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val daysUntil = exam.daysUntil(today)
        if (daysUntil == 0L && ReminderPreferences.examNotifiedOn(context, exam.id) == today.toString()) return
        val targetDate = firstExamReminderDate(today, LocalTime.now(zone), daysUntil) ?: return
        enqueueExam(context, exam, targetDate)
    }

    fun cancelExam(context: Context, id: String) {
        WorkManager.getInstance(context).cancelAllWorkByTag("$EXAM_TAG:$id")
    }

    private fun enqueueExam(context: Context, exam: LunaExam, targetDate: LocalDate) {
        val targetTime = targetDate.atTime(9, 0).atZone(ZoneId.systemDefault())
        val delayMillis = Duration.between(java.time.ZonedDateTime.now(), targetTime).toMillis().coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<LunaReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putString(KIND, EXAM).putString(EXAM_ID, exam.id).build())
            .addTag(EXAM_TAG)
            .addTag("$EXAM_TAG:${exam.id}")
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "$EXAM_WORK_PREFIX${exam.id}_${targetDate}",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    private fun delayUntil(hour: Int, minute: Int): Long {
        val now = LocalDateTime.now()
        var target = now.toLocalDate().atTime(LocalTime.of(hour, minute))
        if (!target.isAfter(now)) target = target.plusDays(1)
        return Duration.between(now, target).toMillis()
    }

    internal fun scheduleNextExamDay(context: Context, exam: LunaExam) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val targetDate = firstExamReminderDate(today, LocalTime.now(zone), exam.daysUntil(today)) ?: return
        enqueueExam(context, exam, targetDate)
    }

    private fun ensureNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Luna reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Optional chat and exam reminders from Luna." }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
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
            val today = LocalDate.now()
            if (ReminderPreferences.examNotifiedOn(applicationContext, exam.id) != today.toString()) {
                val timing = when (daysLeft) {
                    0L -> "today"
                    1L -> "tomorrow"
                    else -> "in $daysLeft days"
                }
                val posted = postNotification(
                    exam.id.hashCode() and Int.MAX_VALUE,
                    "${exam.title} exam $timing",
                    "Your ${exam.title} exam is $timing. Luna is here if you’d like to talk or plan a small step."
                )
                if (posted) ReminderPreferences.recordExamNotification(applicationContext, exam.id, today)
            }
        }
        if (daysLeft > 0) ReminderScheduler.scheduleNextExamDay(applicationContext, exam)
        return Result.success()
    }

    private fun postNotification(id: Int, title: String, message: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        val notificationManager = NotificationManagerCompat.from(applicationContext)
        if (!notificationManager.areNotificationsEnabled()) return false
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
        notificationManager.notify(id, notification)
        return true
    }
}

internal fun notificationsAllowed(context: Context): Boolean =
    (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        NotificationManagerCompat.from(context).areNotificationsEnabled()
