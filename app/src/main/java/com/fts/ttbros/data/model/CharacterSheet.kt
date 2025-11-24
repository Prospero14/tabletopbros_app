package com.fts.ttbros.data.model

import com.google.firebase.Timestamp

data class CharacterSheet(
    val id: String = "",
    val userId: String = "",
    val characterName: String = "",
    val system: String = "", // e.g., "dnd_5e", "vtm_5e"
    val pdfUrl: String = "", // Original PDF file URL
    val parsedData: Map<String, Any> = emptyMap(), // Parsed data from PDF
    val attributes: Map<String, Int> = emptyMap(), // Character attributes (Strength, Dexterity, etc.)
    val skills: Map<String, Int> = emptyMap(), // Character skills
    val stats: Map<String, Any> = emptyMap(), // Other stats (HP, AC, etc.)
    val notes: String = "",
    val isTemplate: Boolean = false, // true = билдер (шаблон), false = персонаж
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null
)

