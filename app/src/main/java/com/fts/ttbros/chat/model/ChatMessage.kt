package com.fts.ttbros.chat.model

import com.google.firebase.Timestamp

data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val text: String = "",
    val timestamp: Timestamp? = null
) {
    val formattedTime: String
        get() = timestamp?.toDate()?.toString().orEmpty()
}

