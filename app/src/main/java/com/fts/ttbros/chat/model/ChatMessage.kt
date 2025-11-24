package com.fts.ttbros.chat.model

import com.google.firebase.Timestamp

data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val imageUrl: String? = null,
    val type: String = "text", // "text", "character"
    val attachmentId: String? = null,
    val attachmentUrl: String? = null,
    val timestamp: Timestamp? = null,
    val isPinned: Boolean = false,
    val pinnedBy: String? = null,
    val pinnedAt: Long? = null,
    val importedBy: List<String> = emptyList()
) {
    val formattedTime: String
        get() = timestamp?.toDate()?.toString().orEmpty()
    
    val hasImage: Boolean
        get() = !imageUrl.isNullOrBlank()
}

