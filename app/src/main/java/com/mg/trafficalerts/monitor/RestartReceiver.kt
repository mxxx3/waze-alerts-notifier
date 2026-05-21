package com.mg.trafficalerts.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mg.trafficalerts.AppLogger
import com.mg.trafficalerts.settings.AppSettings

class RestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        AppLogger.init(context)
        val settings = AppSettings(context)
        if (!settings.monitoringEnabled) {
            AppLogger.d(TAG, "Restart broadcast ignored — monitoring disabled")
            return
        }
        AppLogger.w(TAG, "Restart broadcast received — bringing AlertMonitorService back up")
        ServiceWatchdog.startMonitoring(context)
    }

    companion object {
        private const val TAG = "RestartReceiver"
    }
}
