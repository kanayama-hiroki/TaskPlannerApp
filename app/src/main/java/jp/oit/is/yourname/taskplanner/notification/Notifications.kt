package jp.oit.`is`.yourname.taskplanner.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.core.app.NotificationCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import jp.oit.`is`.yourname.taskplanner.data.Task
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

private const val CHANNEL_ID = "DEADLINE_CHANNEL"

fun createNotificationChannel(context: Context) {
    val channel = NotificationChannel(
        CHANNEL_ID, "Deadline Reminder", NotificationManager.IMPORTANCE_DEFAULT
    ).apply { description = "Notification for task deadlines" }
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
}

class NotificationWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val taskName = inputData.getString("taskName") ?: "課題"
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return Result.success()

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("明日は締切日です！")
            .setContentText("「$taskName」の締切が明日です。計画通り進んでいますか？")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(System.currentTimeMillis().toInt(), notification)
        return Result.success()
    }
}

private fun workName(taskId: String) = "deadline_$taskId"

/** 締切前日の 9:00 に通知を予約する。同じ課題の再予約は上書きされる。 */
fun scheduleNotification(context: Context, task: Task) {
    val triggerAt = task.deadline.minusDays(1).atTime(LocalTime.of(9, 0))
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val delay = triggerAt - System.currentTimeMillis()
    if (delay <= 0) return

    val request = OneTimeWorkRequestBuilder<NotificationWorker>()
        .setInitialDelay(delay, TimeUnit.MILLISECONDS)
        .setInputData(workDataOf("taskName" to task.name))
        .build()
    WorkManager.getInstance(context)
        .enqueueUniqueWork(workName(task.id), ExistingWorkPolicy.REPLACE, request)
}

fun cancelNotification(context: Context, taskId: String) {
    WorkManager.getInstance(context).cancelUniqueWork(workName(taskId))
}

/** 標準カレンダーアプリに締切を終日予定として追加する画面を開く。 */
fun openCalendarIntent(context: Context, task: Task): Boolean {
    val begin = task.deadline.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val intent = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, "締切: ${task.name}")
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + TimeUnit.DAYS.toMillis(1))
        .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
