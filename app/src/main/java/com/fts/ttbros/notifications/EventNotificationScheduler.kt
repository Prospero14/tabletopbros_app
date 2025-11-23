package com.fts.ttbros.notifications

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.fts.ttbros.data.model.Event
import java.util.concurrent.TimeUnit

object EventNotificationScheduler {

    fun scheduleEventNotifications(context: Context, event: Event) {
        val currentTime = System.currentTimeMillis()
        val eventTime = event.dateTime

        // Schedule immediate notification (event created)
        if (!event.notificationsSent.onCreate) {
            NotificationHelper.showEventNotification(
                context,
                "Новое событие",
                "Создано событие \"${event.title}\"",
                event.id.hashCode()
            )
        }

        // Schedule 24h before notification
        val oneDayBefore = eventTime - TimeUnit.HOURS.toMillis(24)
        if (oneDayBefore > currentTime && !event.notificationsSent.oneDayBefore) {
            val delay = oneDayBefore - currentTime
            scheduleNotification(context, event.id, "24h", delay)
        }

        // Schedule 2h before notification
        val twoHoursBefore = eventTime - TimeUnit.HOURS.toMillis(2)
        if (twoHoursBefore > currentTime && !event.notificationsSent.twoHoursBefore) {
            val delay = twoHoursBefore - currentTime
            scheduleNotification(context, event.id, "2h", delay)
        }
    }

    private fun scheduleNotification(context: Context, eventId: String, type: String, delayMillis: Long) {
        val data = Data.Builder()
            .putString("eventId", eventId)
            .putString("notificationType", type)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<EventNotificationWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag("event_notification_$eventId")
            .build()

        WorkManager.getInstance(context).enqueue(workRequest)
    }

    fun cancelEventNotifications(context: Context, eventId: String) {
        WorkManager.getInstance(context).cancelAllWorkByTag("event_notification_$eventId")
    }
}
