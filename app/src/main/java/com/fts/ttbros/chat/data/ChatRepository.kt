package com.fts.ttbros.chat.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ListenerRegistration
import com.fts.ttbros.chat.model.ChatMessage
import com.fts.ttbros.chat.model.ChatType
import com.fts.ttbros.data.repository.YandexDiskRepository
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ChatRepository(
    private val yandexDiskRepository: YandexDiskRepository = YandexDiskRepository(),
    private val ioScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {

    private val gson = Gson()
    private val chatMutexes = ConcurrentHashMap<String, Mutex>()
    private val pollIntervalMs = 3_000L
    private val maxMessages = 500

    fun observeMessages(
        teamId: String,
        chatType: ChatType,
        onEvent: (List<ChatMessage>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        val key = chatKey(teamId, chatType)
        val job = ioScope.launch {
            while (isActive) {
                try {
                    val messages = loadMessages(teamId, chatType)
                    withContext(Dispatchers.Main) {
                        onEvent(messages)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        onError(e)
                    }
                }
                delay(pollIntervalMs)
            }
        }
        return object : ListenerRegistration {
            override fun remove() {
                job.cancel()
            }
        }
    }

    suspend fun sendMessage(teamId: String, chatType: ChatType, message: ChatMessage) {
        val mutex = mutexFor(teamId, chatType)
        mutex.withLock {
            val current = getStoredMessages(teamId, chatType).toMutableList()
            val now = System.currentTimeMillis()
            val messageId = if (message.id.isBlank()) UUID.randomUUID().toString() else message.id
            val storedMessage = StoredChatMessage(
                id = messageId,
                senderId = message.senderId,
                senderName = message.senderName,
                text = message.text,
                imageUrl = message.imageUrl,
                type = message.type,
                attachmentId = message.attachmentId,
                timestamp = now,
                isPinned = message.isPinned,
                pinnedBy = message.pinnedBy,
                pinnedAt = message.pinnedAt,
                importedBy = message.importedBy
            )
            current.add(storedMessage)
            val trimmed = current.takeLast(maxMessages)
            saveMessages(teamId, chatType, trimmed)
        }
    }

    suspend fun pinMessage(teamId: String, chatType: ChatType, messageId: String, userId: String) {
        updateMessage(teamId, chatType, messageId) { message ->
            message.copy(
                isPinned = true,
                pinnedBy = userId,
                pinnedAt = System.currentTimeMillis()
            )
        }
    }

    suspend fun unpinMessage(teamId: String, chatType: ChatType, messageId: String) {
        updateMessage(teamId, chatType, messageId) { message ->
            message.copy(
                isPinned = false,
                pinnedBy = null,
                pinnedAt = null
            )
        }
    }

    suspend fun markAsImported(teamId: String, chatType: ChatType, messageId: String, userId: String) {
        updateMessage(teamId, chatType, messageId) { message ->
            if (message.importedBy.contains(userId)) {
                message
            } else {
                message.copy(importedBy = message.importedBy + userId)
            }
        }
    }

    private suspend fun loadMessages(teamId: String, chatType: ChatType): List<ChatMessage> {
        val storedMessages = getStoredMessages(teamId, chatType)
        return storedMessages
            .sortedBy { it.timestamp }
            .map { it.toChatMessage() }
    }

    private suspend fun getStoredMessages(teamId: String, chatType: ChatType): List<StoredChatMessage> {
        val path = messagesPath(teamId, chatType)
        val container = yandexDiskRepository.readJson(path, StoredChatMessages::class.java)
        return container?.messages ?: emptyList()
    }

    private suspend fun saveMessages(teamId: String, chatType: ChatType, messages: List<StoredChatMessage>) {
        val path = messagesPath(teamId, chatType)
        val container = StoredChatMessages(messages)
        val json = gson.toJson(container)
        yandexDiskRepository.uploadJson(path, json)
    }

    private suspend fun updateMessage(
        teamId: String,
        chatType: ChatType,
        messageId: String,
        transform: (StoredChatMessage) -> StoredChatMessage
    ) {
        val mutex = mutexFor(teamId, chatType)
        mutex.withLock {
            val messages = getStoredMessages(teamId, chatType).toMutableList()
            val index = messages.indexOfFirst { it.id == messageId }
            if (index != -1) {
                messages[index] = transform(messages[index])
                saveMessages(teamId, chatType, messages)
            }
        }
    }

    private fun chatKey(teamId: String, chatType: ChatType) = "$teamId-${chatType.key}"

    private fun mutexFor(teamId: String, chatType: ChatType): Mutex {
        val key = chatKey(teamId, chatType)
        return chatMutexes.getOrPut(key) { Mutex() }
    }

    private fun messagesPath(teamId: String, chatType: ChatType): String {
        return "/TTBros/teams/$teamId/chats/${chatType.key}/messages.json"
    }

    private data class StoredChatMessages(
        val messages: List<StoredChatMessage> = emptyList()
    )

    private data class StoredChatMessage(
        val id: String,
        val senderId: String,
        val senderName: String,
        val text: String,
        val imageUrl: String? = null,
        val type: String = "text",
        val attachmentId: String? = null,
        val timestamp: Long = System.currentTimeMillis(),
        val isPinned: Boolean = false,
        val pinnedBy: String? = null,
        val pinnedAt: Long? = null,
        val importedBy: List<String> = emptyList()
    ) {
        fun toChatMessage(): ChatMessage {
            val date = java.util.Date(timestamp)
            return ChatMessage(
                id = id,
                senderId = senderId,
                senderName = senderName,
                text = text,
                imageUrl = imageUrl,
                type = type,
                attachmentId = attachmentId,
                timestamp = Timestamp(date),
                isPinned = isPinned,
                pinnedBy = pinnedBy,
                pinnedAt = pinnedAt,
                importedBy = importedBy
            )
        }
    }
}
