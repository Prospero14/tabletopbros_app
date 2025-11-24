package com.fts.ttbros.notifications

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.fts.ttbros.data.model.Event
import com.fts.ttbros.data.repository.EventRepository
import com.fts.ttbros.data.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

object EventNotificationScheduler {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val eventRepository = EventRepository()
    private val userRepository = UserRepository()

    /**
     * Перепланировать все уведомления для будущих событий
     * Вызывается при запуске приложения
     */
    fun rescheduleAllEventNotifications(context: Context) {
        scope.launch {
            try {
                val profile = userRepository.currentProfile() ?: return@launch
                val teamId = profile.currentTeamId ?: profile.teamId ?: return@launch
                
                // Получить все события один раз
                val currentTime = System.currentTimeMillis()
                val eventsList = eventRepository.getTeamEventsOnce(teamId)
                
                // Фильтруем только будущие события
                val futureEvents = eventsList.filter { it.dateTime > currentTime }
                
                android.util.Log.d("EventNotificationScheduler", "Rescheduling notifications for ${futureEvents.size} future events")
                
                // Отменить все старые уведомления для событий
                futureEvents.forEach { event ->
                    cancelEventNotifications(context, event.id)
                }
                
                // Перепланировать уведомления для всех будущих событий
                futureEvents.forEach { event ->
                    scheduleEventNotifications(context, event, skipImmediate = true)
                }
            } catch (e: Exception) {
                android.util.Log.e("EventNotificationScheduler", "Error rescheduling notifications: ${e.message}", e)
            }
        }
    }

    fun scheduleEventNotifications(context: Context, event: Event, skipImmediate: Boolean = false) {
        val currentTime = System.currentTimeMillis()
        val eventTime = event.dateTime

        // Если событие уже прошло, не планируем уведомления
        if (eventTime <= currentTime) {
            return
        }

        // Schedule immediate notification (event created) - только если не пропускаем
        if (!skipImmediate && !event.notificationsSent.onCreate) {
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
            // Планируем только если задержка больше 1 минуты (чтобы избежать немедленных уведомлений)
            if (delay > TimeUnit.MINUTES.toMillis(1)) {
                scheduleNotification(context, event.id, "24h", delay)
            }
        }

        // Schedule 2h before notification
        val twoHoursBefore = eventTime - TimeUnit.HOURS.toMillis(2)
        if (twoHoursBefore > currentTime && !event.notificationsSent.twoHoursBefore) {
            val delay = twoHoursBefore - currentTime
            // Планируем только если задержка больше 1 минуты
            if (delay > TimeUnit.MINUTES.toMillis(1)) {
                scheduleNotification(context, event.id, "2h", delay)
            }
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
