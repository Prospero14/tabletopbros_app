package com.fts.ttbros.data.repository

import com.fts.ttbros.data.model.Event
import com.fts.ttbros.data.model.NotificationStatus
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class EventRepository {
    private val db = FirebaseFirestore.getInstance()
    private val eventsCollection = db.collection("events")

    suspend fun createEvent(event: Event): String {
        val docRef = eventsCollection.document()
        val eventWithId = event.copy(id = docRef.id)
        docRef.set(eventWithId).await()
        return docRef.id
    }

    suspend fun updateEvent(event: Event) {
        eventsCollection.document(event.id).set(event).await()
    }

    fun getTeamEvents(teamId: String): Flow<List<Event>> = callbackFlow {
        val listener = eventsCollection
            .whereEqualTo("teamId", teamId)
            .orderBy("dateTime", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // Log error but don't close - try to continue
                    android.util.Log.e("EventRepository", "Error loading events: ${error.message}", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val events = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(Event::class.java)
                    } catch (e: Exception) {
                        android.util.Log.e("EventRepository", "Error parsing event ${doc.id}: ${e.message}", e)
                        null
                    }
                } ?: emptyList()
                trySend(events)
            }
        awaitClose { listener.remove() }
    }

    suspend fun deleteEvent(eventId: String) {
        eventsCollection.document(eventId).delete().await()
    }

    suspend fun updateNotificationStatus(eventId: String, status: NotificationStatus) {
        eventsCollection.document(eventId)
            .update("notificationsSent", status)
            .await()
    }

    suspend fun getEvent(eventId: String): Event? {
        return eventsCollection.document(eventId)
            .get()
            .await()
            .toObject(Event::class.java)
    }

    /**
     * Получить все события команды один раз (не Flow)
     */
    suspend fun getTeamEventsOnce(teamId: String): List<Event> {
        return eventsCollection
            .whereEqualTo("teamId", teamId)
            .orderBy("dateTime", Query.Direction.ASCENDING)
            .get()
            .await()
            .documents
            .mapNotNull { doc ->
                try {
                    doc.toObject(Event::class.java)
                } catch (e: Exception) {
                    android.util.Log.e("EventRepository", "Error parsing event ${doc.id}: ${e.message}", e)
                    null
                }
            }
    }
}
