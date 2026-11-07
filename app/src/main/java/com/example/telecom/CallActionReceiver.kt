package com.example.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.ui.call.OngoingCallActivity

class CallActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ANSWER = "com.example.telecom.ACTION_ANSWER"
        const val ACTION_DECLINE = "com.example.telecom.ACTION_DECLINE"
        const val ACTION_HANGUP = "com.example.telecom.ACTION_HANGUP"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ANSWER -> {
                CallManager.answer()
                CallNotificationHelper.cancelAll(context)
                val callIntent = Intent(context, OngoingCallActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                }
                context.startActivity(callIntent)
            }
            // Only the incoming notification is dismissed: another call may still be in progress,
            // and CallManager clears the ongoing notification itself once the last call ends.
            ACTION_DECLINE -> {
                CallManager.reject()
                CallNotificationHelper.cancelIncomingNotification(context)
            }
            ACTION_HANGUP -> CallManager.disconnect()
        }
    }
}
