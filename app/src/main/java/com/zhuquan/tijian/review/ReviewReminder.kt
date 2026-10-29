package com.zhuquan.tijian.review

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.zhuquan.tijian.App
import com.zhuquan.tijian.MainActivity
import com.zhuquan.tijian.data.nextDailyTrigger
import com.zhuquan.tijian.data.reviewNotificationText
import java.util.TimeZone

object ReviewReminder {
    const val EXTRA_OPEN = "open_review"
    private const val ACTION = "com.zhuquan.tijian.REVIEW_ALARM"
    private const val REQUEST = 7101
    private const val CHANNEL = "review"
    private const val NOTICE = 7102

    fun enabled(db: com.zhuquan.tijian.data.Db): Boolean = db.getSetting("review.remind") != "0"

    fun hour(db: com.zhuquan.tijian.data.Db): Int =
        db.getSetting("review.remindHour")?.toIntOrNull()?.coerceIn(0, 23) ?: 20

    fun minute(db: com.zhuquan.tijian.data.Db): Int =
        db.getSetting("review.remindMinute")?.toIntOrNull()?.coerceIn(0, 59) ?: 0

    fun cap(db: com.zhuquan.tijian.data.Db): Int =
        db.getSetting("review.dailyCap")?.toIntOrNull()?.coerceIn(5, 15) ?: 8

    fun notificationsAllowed(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= 33) {
            return context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    fun schedule(context: Context) {
        val app = context.applicationContext as App
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pending(context)
        if (!enabled(app.db)) {
            am.cancel(pi)
            return
        }
        val whenMs = nextDailyTrigger(hour(app.db), minute(app.db), System.currentTimeMillis(), TimeZone.getDefault())
        try {
            val exactOk = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
            if (exactOk) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, pi)
        }
    }

    fun showToday(context: Context) {
        val app = context.applicationContext as? App ?: return
        val db = app.db
        val cap = cap(db)
        val today = db.todayQueue(cap)
        val overflow = (db.dueEligibleCount() - today.size).coerceAtLeast(0)
        schedule(context)
        if (today.isEmpty() || !notificationsAllowed(context)) return
        ensureChannel(context)
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN, true)
        }
        val content = PendingIntent.getActivity(
            context, REQUEST + 1, open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = reviewNotificationText(today.map { it.stem }, overflow)
        val notice = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(com.zhuquan.tijian.R.drawable.ic_launcher_foreground)
            .setContentTitle("今天要复习 ${today.size} 道")
            .setContentText(text.replace("\n", " "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(content)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(NOTICE, notice)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "复习提醒", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "每天在你定的时间提醒今天要复习的题"
            }
        )
    }

    private fun pending(context: Context): PendingIntent {
        val intent = Intent(context, ReviewAlarmReceiver::class.java).setAction(ACTION)
        return PendingIntent.getBroadcast(
            context, REQUEST, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

class ReviewAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        ReviewReminder.showToday(context)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        ReviewReminder.schedule(context)
    }
}
