package com.fts.ttbros.data.model

import com.google.firebase.Timestamp

data class Character(
    val id: String = "",
    val userId: String = "",
    val name: String = "",
    val system: String = "", // e.g., "vtm_5e", "dnd_5e"
    val clan: String = "", // Specific to VTM, but useful to have at top level
    val concept: String = "",
    val data: Map<String, Any> = emptyMap(), // Dynamic fields
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)
