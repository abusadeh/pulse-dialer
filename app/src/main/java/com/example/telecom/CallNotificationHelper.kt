package com.example.telecom

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.ui.call.OngoingCallActivity

object CallNotificationHelper {

    private const val CHANNEL_INCOMING = "pulse_incoming_call_channel_v2"
    private const val LEGACY_CHANNEL_INCOMING = "pulse_incoming_call_channel"
    private const val CHANNEL_ONGOING = "pulse_ongoing_call_channel"
    const val NOTIFICATION_ID_INCOMING = 1001
    const val NOTIFICATION_ID_ONGOING = 1002

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                        ?: return

                // Delete legacy channel with sound to ensure no cached ringtone playback
                try {
                    notificationManager.deleteNotificationChannel(LEGACY_CHANNEL_INCOMING)
                } catch (_: Exception) {}

                // Incoming calls channel: High priority for full screen heads-up display.
                // setSound(null, null) is set explicitly because Android Telecom framework
                // manages system incoming-call ringtone audio when set as default dialer.
                val incomingChannel = NotificationChannel(
                    CHANNEL_INCOMING,
                    "Incoming Calls",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Incoming call alerts with full screen UI"
                    setSound(null, null)
                    enableVibration(true)
                    setShowBadge(true)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                }

                // Ongoing calls channel: Low/Default priority ongoing notification
                val ongoingChannel = NotificationChannel(
                    CHANNEL_ONGOING,
                    "Ongoing Calls",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Active call status"
                    setShowBadge(false)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                }

                notificationManager.createNotificationChannel(incomingChannel)
                notificationManager.createNotificationChannel(ongoingChannel)
            } catch (e: Exception) {
                android.util.Log.e("CallNotificationHelper", "Error creating notification channels", e)
            }
        }
    }

    fun showIncomingCallNotification(context: Context, callerName: String, callerNumber: String) {
        try {
            createNotificationChannels(context)
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    ?: return

            // Fullscreen activity intent
            val fullScreenIntent = Intent(context, OngoingCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                0,
                fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Answer PendingIntent
            val answerIntent = Intent(context, CallActionReceiver::class.java).apply {
                action = CallActionReceiver.ACTION_ANSWER
            }
            val answerPendingIntent = PendingIntent.getBroadcast(
                context,
                1,
                answerIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Decline PendingIntent
            val declineIntent = Intent(context, CallActionReceiver::class.java).apply {
                action = CallActionReceiver.ACTION_DECLINE
            }
            val declinePendingIntent = PendingIntent.getBroadcast(
                context,
                2,
                declineIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_INCOMING)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Incoming Call")
                .setContentText(callerName)
                .setSubText(callerNumber)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setSound(null)
                .setAutoCancel(false)
                .setOngoing(true)
                .setContentIntent(fullScreenPendingIntent)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .addAction(
                    android.R.drawable.ic_menu_call,
                    "Answer",
                    answerPendingIntent
                )
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Decline",
                    declinePendingIntent
                )
                .build()

            notificationManager.notify(NOTIFICATION_ID_INCOMING, notification)
        } catch (e: Exception) {
            android.util.Log.e("CallNotificationHelper", "Failed to show incoming call notification", e)
        }
    }

    /**
     * Ongoing-call notification. Tapping it returns to the call screen; "End call" hangs up.
     * The elapsed time is drawn by the system chronometer from [connectTimeMillis], so the
     * notification does not need re-posting every second.
     */
    fun showOngoingCallNotification(
        context: Context,
        callerName: String,
        connectTimeMillis: Long,
        statusText: String
    ) {
        try {
            createNotificationChannels(context)
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    ?: return

            val activityIntent = Intent(context, OngoingCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val activityPendingIntent = PendingIntent.getActivity(
                context,
                0,
                activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val hangupIntent = Intent(context, CallActionReceiver::class.java).apply {
                action = CallActionReceiver.ACTION_HANGUP
            }
            val hangupPendingIntent = PendingIntent.getBroadcast(
                context,
                3,
                hangupIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ONGOING)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(callerName)
                .setContentText(statusText)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
            if (connectTimeMillis > 0L) {
                builder.setWhen(connectTimeMillis).setShowWhen(true).setUsesChronometer(true)
            } else {
                builder.setShowWhen(false)
            }
            val notification = builder
                .setContentIntent(activityPendingIntent)
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "End Call",
                    hangupPendingIntent
                )
                .build()

            notificationManager.notify(NOTIFICATION_ID_ONGOING, notification)
        } catch (e: Exception) {
            android.util.Log.e("CallNotificationHelper", "Failed to show ongoing call notification", e)
        }
    }

    fun cancelIncomingNotification(context: Context) {
        try {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    ?: return
            notificationManager.cancel(NOTIFICATION_ID_INCOMING)
        } catch (_: Exception) {}
    }

    fun cancelAll(context: Context) {
        try {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    ?: return
            notificationManager.cancel(NOTIFICATION_ID_INCOMING)
            notificationManager.cancel(NOTIFICATION_ID_ONGOING)
        } catch (_: Exception) {}
    }
}
