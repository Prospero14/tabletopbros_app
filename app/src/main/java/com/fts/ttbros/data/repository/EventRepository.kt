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

    fun getTeamEvents(teamId: String): Flow<List<Event>> = callbackFlow {
        val listener = eventsCollection
            .whereEqualTo("teamId", teamId)
            .orderBy("dateTime", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val events = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Event::class.java)
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
}
