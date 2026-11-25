package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class NetworkReconnectReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        if (intent.action == CompetitionReminderManager.ACTION_NETWORK_AVAILABLE) {
            CompetitionReminderManager.maybeNotifyOnReconnect(
                context = context.applicationContext,
                source = ReconnectSource.PENDING_INTENT
            )
        }
    }
}
