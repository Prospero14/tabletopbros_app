package com.fts.ttbros.chat.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.fts.ttbros.chat.model.ChatMessage
import com.fts.ttbros.chat.model.ChatType
import kotlinx.coroutines.tasks.await

class ChatRepository(
    private val firestore: FirebaseFirestore = Firebase.firestore
) {

    fun observeMessages(
        teamId: String,
        chatType: ChatType,
        onEvent: (List<ChatMessage>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return messagesCollection(teamId, chatType)
            .orderBy(FIELD_TIMESTAMP, Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                val messages = snapshot?.documents?.map { doc ->
                    ChatMessage(
                        id = doc.id,
                        senderId = doc.getString(FIELD_SENDER_ID).orEmpty(),
                        senderName = doc.getString(FIELD_SENDER_NAME).orEmpty(),
                        text = doc.getString(FIELD_TEXT).orEmpty(),
                        imageUrl = doc.getString(FIELD_IMAGE_URL),
                        timestamp = doc.getTimestamp(FIELD_TIMESTAMP) ?: Timestamp.now(),
                        isPinned = doc.getBoolean(FIELD_IS_PINNED) ?: false,
                        pinnedBy = doc.getString(FIELD_PINNED_BY),
                        pinnedAt = doc.getLong(FIELD_PINNED_AT)
                    )
                }?.sortedWith(compareBy<ChatMessage>(
                    { !it.isPinned }, // Pinned first
                    { if (it.isPinned) -(it.pinnedAt ?: 0L) else -(it.timestamp?.toDate()?.time ?: 0L) } // Most recent first within each group
                )).orEmpty()
                onEvent(messages)
            }
    }

    suspend fun sendMessage(teamId: String, chatType: ChatType, message: ChatMessage) {
        val data = mutableMapOf<String, Any>(
            FIELD_SENDER_ID to message.senderId,
            FIELD_SENDER_NAME to message.senderName,
            FIELD_TEXT to message.text,
            FIELD_TIMESTAMP to FieldValue.serverTimestamp()
        )
        message.imageUrl?.let {
            data[FIELD_IMAGE_URL] = it
        }
        messagesCollection(teamId, chatType).add(data).await()
    }

    private fun messagesCollection(teamId: String, chatType: ChatType) =
        firestore.collection(COLLECTION_TEAMS)
            .document(teamId)
            .collection(COLLECTION_CHATS)
            .document(chatType.key)
            .collection(COLLECTION_MESSAGES)

    suspend fun pinMessage(teamId: String, chatType: ChatType, messageId: String, userId: String) {
        val messageRef = messagesCollection(teamId, chatType).document(messageId)
        messageRef.update(
            FIELD_IS_PINNED to true,
            FIELD_PINNED_BY to userId,
            FIELD_PINNED_AT to System.currentTimeMillis()
        ).await()
    }

    suspend fun unpinMessage(teamId: String, chatType: ChatType, messageId: String) {
        val messageRef = messagesCollection(teamId, chatType).document(messageId)
        val updates = mapOf<String, Any>(
            FIELD_IS_PINNED to false,
            FIELD_PINNED_BY to FieldValue.delete(),
            FIELD_PINNED_AT to FieldValue.delete()
        )
        messageRef.update(updates).await()
    }

    companion object {
        private const val COLLECTION_TEAMS = "teams"
        private const val COLLECTION_CHATS = "chats"
        private const val COLLECTION_MESSAGES = "messages"
        private const val FIELD_SENDER_ID = "senderId"
        private const val FIELD_SENDER_NAME = "senderName"
        private const val FIELD_TEXT = "text"
        private const val FIELD_IMAGE_URL = "imageUrl"
        private const val FIELD_TIMESTAMP = "timestamp"
        private const val FIELD_IS_PINNED = "isPinned"
        private const val FIELD_PINNED_BY = "pinnedBy"
        private const val FIELD_PINNED_AT = "pinnedAt"
    }
}

