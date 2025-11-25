package com.fts.ttbros.data.model

/**
 * Represents a parsed section from a PDF character sheet
 */
data class ParsedSection(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sectionType: SectionType,
    val title: String,
    val content: String,
    val confidence: Float, // 0.0 to 1.0
    val isEditable: Boolean = true,
    val rawData: Map<String, Any> = emptyMap()
)

/**
 * Types of sections that can be parsed from character sheets
 */
enum class SectionType {
    HEADER,           // Character name, player, etc.
    ATTRIBUTES,       // Physical, Social, Mental attributes
    SKILLS,           // All skills
    DISCIPLINES,      // VTM disciplines
    ADVANTAGES,       // Merits, advantages
    FLAWS,           // Flaws, disadvantages
    NOTES,           // Notes, history, background
    TRACKERS,        // Health, willpower, hunger, etc.
    BLOOD,           // Blood potency, resonance, etc.
    EXPERIENCE,      // XP tracking
    CONVICTIONS,     // Convictions and touchstones
    GOALS,           // Ambition, desire
    IDENTITY,        // Mortal identity, mask, etc.
    COTERIE,         // Coterie and domain info
    OTHER            // Any other content
}

/**
 * Result of PDF parsing operation
 */
data class ParseResult(
    val sections: List<ParsedSection>,
    val overallConfidence: Float,
    val detectedSystem: String?, // "vtm_5e", "dnd_5e", etc.
    val characterName: String?,
    val errors: List<String> = emptyList()
)
