package com.fts.ttbros.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fts.ttbros.data.repository.EventRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EventNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val eventRepository = EventRepository()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val eventId = inputData.getString("eventId") ?: return@withContext Result.failure()
            val notificationType = inputData.getString("notificationType") ?: return@withContext Result.failure()
            
            val event = eventRepository.getEvent(eventId) ?: return@withContext Result.failure()
            
            val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            val eventDate = dateFormat.format(Date(event.dateTime))
            
            val message = when (notificationType) {
                "24h" -> "Событие \"${event.title}\" начнется завтра ($eventDate)"
                "2h" -> "Событие \"${event.title}\" начнется через 2 часа ($eventDate)"
                else -> "Событие \"${event.title}\" ($eventDate)"
            }
            
            NotificationHelper.showEventNotification(
                applicationContext,
                "Напоминание о событии",
                message,
                event.id.hashCode()
            )
            
            // Update notification status
            val currentStatus = event.notificationsSent
            val updatedStatus = when (notificationType) {
                "24h" -> currentStatus.copy(oneDayBefore = true)
                "2h" -> currentStatus.copy(twoHoursBefore = true)
                else -> currentStatus
            }
            eventRepository.updateNotificationStatus(eventId, updatedStatus)
            
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }
}
