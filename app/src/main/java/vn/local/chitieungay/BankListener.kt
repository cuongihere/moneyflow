package vn.local.chitieungay

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Only stores plausible transaction notifications from explicitly enabled source apps. */
class BankListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val n = sbn.notification
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return
        val prefs = getSharedPreferences("sources", MODE_PRIVATE)
        val packages = prefs.getString("patterns", "mbbank,vietcombank,vcb,techcombank,bidv,acb,sacombank,tpbank,vpbank,momo")
            .orEmpty().split(',').map { it.trim().lowercase() }.filter { it.length >= 3 }
        if (packages.none { pkg.lowercase().contains(it) }) return
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val parsed = TransactionParser.parse(title, text) ?: return
        val whenPosted = if (sbn.postTime > 0L) sbn.postTime else System.currentTimeMillis()
        val fp = TransactionParser.fingerprint(pkg, title, text, whenPosted)
        val db = Store(applicationContext)
        val id = db.insert(pkg, parsed, fp, whenPosted)
        if (id <= 0L) return // same notification already captured
        val event = db.get(id) ?: return
        Reminder.show(applicationContext, event)
        Reminder.schedule(applicationContext, id)
    }
}
