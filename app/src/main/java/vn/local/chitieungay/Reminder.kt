package vn.local.chitieungay

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

object Reminder {
    private const val CHANNEL_ID = "classify_new_transactions"
    private const val INTERVAL_MS = 30 * 60 * 1000L

    private fun manager(c: Context): NotificationManager = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun show(c: Context, event: MoneyEvent) {
        val nm = manager(c)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Giao dịch cần phân loại", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Nhắc ngay khi ghi nhận biến động số dư chưa phân loại"
            enableVibration(true)
        })
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val click = Intent(c, MainActivity::class.java).apply {
            putExtra("event_id", event.id)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pi = PendingIntent.getActivity(c, event.id.toInt(), click, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val direction = when(event.direction) { "income" -> "Khoản thu"; "expense" -> "Khoản chi"; else -> "Giao dịch" }
        val n = Notification.Builder(c, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("$direction ${Store.money(event.amount)} — khoản gì?")
            .setContentText("Chạm để nhập danh mục và nội dung ngay")
            .setStyle(Notification.BigTextStyle().bigText("Đã nhận giao dịch từ ${event.bank}. Chạm để phân loại và ghi rõ khoản thu/chi này."))
            .setContentIntent(pi)
            .setAutoCancel(false)
            .setOngoing(true) // Android 14+ may still allow dismissal; reminders remain scheduled
            .setCategory(Notification.CATEGORY_REMINDER)
            .setPriority(Notification.PRIORITY_HIGH)
            .build()
        nm.notify(event.id.toInt(), n)
    }

    fun clear(c: Context, eventId: Long) {
        manager(c).cancel(eventId.toInt())
        val alarm = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.cancel(alarmIntent(c, eventId))
    }

    fun schedule(c: Context, eventId: Long) {
        val alarm = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Inexact by design: Android battery restrictions may postpone a reminder.
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + INTERVAL_MS, alarmIntent(c, eventId))
    }

    private fun alarmIntent(c: Context, id: Long): PendingIntent {
        val i = Intent(c, ReminderReceiver::class.java).apply { putExtra("event_id", id) }
        return PendingIntent.getBroadcast(c, id.toInt(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("event_id", -1L)
        if (id <= 0) return
        val event = Store(context).get(id)
        if (event?.status == "pending") {
            Reminder.show(context, event)
            Reminder.schedule(context, id)
        }
    }
}
