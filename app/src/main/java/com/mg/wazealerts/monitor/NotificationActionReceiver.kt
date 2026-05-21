package com.mg.wazealerts.monitor

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mg.wazealerts.AppLogger
import com.mg.wazealerts.store.AlertStore

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_MARK_READ) return

        val ids = buildList {
            intent.getStringExtra(EXTRA_ALERT_ID)?.let { add(it) }
            intent.getStringArrayExtra(EXTRA_ALERT_IDS)?.forEach { add(it) }
        }.distinct()
        if (ids.isEmpty()) return

        val store = AlertStore(context)
        val nm = context.getSystemService(NotificationManager::class.java)
        ids.forEach { id ->
            store.markPassed(id)
            nm.cancel(id.hashCode())
        }
        nm.cancel(SUMMARY_NOTIFICATION_ID)
        store.clearActiveNotificationIds()
        AppLogger.init(context)
        AppLogger.i(TAG, "Dismissed ${ids.size} alert notification(s)")
    }

    companion object {
        private const val TAG = "NotificationAction"
        private const val ACTION_MARK_READ = "com.mg.wazealerts.ACTION_MARK_READ"
        private const val EXTRA_ALERT_ID = "extra_alert_id"
        private const val EXTRA_ALERT_IDS = "extra_alert_ids"
        private const val SUMMARY_NOTIFICATION_ID = 4101
    }
}
