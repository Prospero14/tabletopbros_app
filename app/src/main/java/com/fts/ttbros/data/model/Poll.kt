package com.fts.ttbros.data.model

import java.util.UUID

data class Poll(
    val id: String = "",
    val teamId: String = "",
    val chatType: String = "team", // team, announcements, master_player
    val question: String = "",
    val options: List<PollOption> = emptyList(),
    val isAnonymous: Boolean = false,
    val createdBy: String = "",
    val createdByName: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val votes: Map<String, String> = emptyMap(), // userId -> optionId
    val voterNames: Map<String, String> = emptyMap(), // userId -> userName (for open polls)
    val isPinned: Boolean = false,
    val pinnedBy: String? = null,
    val pinnedAt: Long? = null
)

data class PollOption(
    val id: String = UUID.randomUUID().toString(),
    val text: String = ""
)
