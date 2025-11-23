package com.fts.ttbros.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        
        Log.d("FCM", "Message received: ${message.data}")
        
        val title = message.notification?.title ?: message.data["title"] ?: "New Notification"
        val body = message.notification?.body ?: message.data["body"] ?: ""
        val type = message.data["type"] ?: "event"
        
        when (type) {
            "event" -> {
                NotificationHelper.showEventNotification(
                    this,
                    title,
                    body,
                    System.currentTimeMillis().toInt()
                )
            }
            "poll" -> {
                NotificationHelper.showPollNotification(
                    this,
                    title,
                    body,
                    System.currentTimeMillis().toInt()
                )
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "New token: $token")
        // TODO: Send token to server if needed
    }
}
