package com.meilluer.smartspacerFlashScore

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class FlashScoreNotificationService : NotificationListenerService() {
    companion object {
        private const val TAG = "FlashScoreService"
        const val FLASHSCORE_PACKAGE = "eu.livesport.FlashScore_com"
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification listener connected successfully")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn?.let {
            val packageName = it.packageName
            if (packageName == FLASHSCORE_PACKAGE) {
                val extras = it.notification.extras
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
                val subtitle = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

                Log.d(TAG, "Intercepted Flashscore Notification!")
                Log.d(TAG, "Raw Title: \"$title\"")
                Log.d(TAG, "Raw Subtitle: \"$subtitle\"")

                // Parse and update state
                FlashScoreNotificationParser.parse(this, title, subtitle)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        sbn?.let {
            if (it.packageName == FLASHSCORE_PACKAGE) {
                Log.d(TAG, "FlashScore notification removed from status bar.")
            }
        }
    }
}
