package com.fts.ttbros.parser

import android.content.Context
import android.net.Uri
import com.fts.ttbros.data.model.ParsedSection
import com.fts.ttbros.data.model.ParseResult
import com.fts.ttbros.data.model.SectionType
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream

/**
 * Advanced PDF parser with improved section detection and extraction
 */
class AdvancedPdfParser {
    
    /**
     * Parse a PDF file and extract structured sections
     */
    suspend fun parse(uri: Uri, context: Context): ParseResult {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                parseStream(stream)
            } ?: ParseResult(
                sections = emptyList(),
                overallConfidence = 0.0f,
                detectedSystem = null,
                characterName = null,
                errors = listOf("Failed to open PDF file")
            )
        } catch (e: Exception) {
            android.util.Log.e("AdvancedPdfParser", "Error parsing PDF: ${e.message}", e)
            ParseResult(
                sections = emptyList(),
                overallConfidence = 0.0f,
                detectedSystem = null,
                characterName = null,
                errors = listOf("Error parsing PDF: ${e.message}")
            )
        }
    }
    
    private fun parseStream(stream: InputStream): ParseResult {
        var document: PDDocument? = null
        return try {
            document = PDDocument.load(stream)
            val stripper = PDFTextStripper()
            val fullText = stripper.getText(document)
            val lines = fullText.lines()
            
            android.util.Log.d("AdvancedPdfParser", "Extracted ${lines.size} lines from PDF")
            
            // Detect game system
            val detectedSystem = detectGameSystem(fullText)
            android.util.Log.d("AdvancedPdfParser", "Detected system: $detectedSystem")
            
            // Extract character name
            val characterName = extractCharacterName(fullText, lines)
            android.util.Log.d("AdvancedPdfParser", "Character name: $characterName")
            
            // Parse sections based on detected system
            val sections = when (detectedSystem) {
                "vtm_5e" -> parseVtmSections(lines, fullText)
                "dnd_5e" -> parseDndSections(lines, fullText)
                "whrp" -> parseWhrpSections(lines, fullText)
                "wh_darkheresy" -> parseDarkHeresySections(lines, fullText)
                else -> parseGenericSections(lines, fullText)
            }
            
            // Calculate overall confidence
            val overallConfidence = if (sections.isNotEmpty()) {
                sections.map { it.confidence }.average().toFloat()
            } else {
                0.0f
            }
            
            ParseResult(
                sections = sections,
                overallConfidence = overallConfidence,
                detectedSystem = detectedSystem,
                characterName = characterName,
                errors = emptyList()
            )
        } catch (e: Exception) {
            android.util.Log.e("AdvancedPdfParser", "Error in parseStream: ${e.message}", e)
            ParseResult(
                sections = emptyList(),
                overallConfidence = 0.0f,
                detectedSystem = null,
                characterName = null,
                errors = listOf("Parse error: ${e.message}")
            )
        } finally {
            document?.close()
        }
    }
    
    private fun detectGameSystem(text: String): String {
        val lowerText = text.lowercase()
        return when {
            lowerText.contains("vampire") || lowerText.contains("clan") || 
            lowerText.contains("disciplines") || lowerText.contains("blood potency") -> "vtm_5e"
            
            lowerText.contains("dungeons") || lowerText.contains("dragons") || 
            lowerText.contains("armor class") || lowerText.contains("d&d") -> "dnd_5e"
            
            lowerText.contains("dark heresy") || lowerText.contains("imperium") ||
            lowerText.contains("inquisition") || lowerText.contains("acolyte") ||
            lowerText.contains("throne agent") -> "wh_darkheresy"
            
            lowerText.contains("warhammer fantasy roleplay") || lowerText.contains("whrp") ||
            lowerText.contains("warhammer roleplay") || lowerText.contains("fate points") ||
            lowerText.contains("fortune points") -> "whrp"
            
            else -> "unknown"
        }
    }
    
    private fun extractCharacterName(fullText: String, lines: List<String>): String? {
        // Try multiple patterns
        val patterns = listOf(
            Regex("(?:name|имя)\\s*[:=]\\s*([^\\n]{1,50})", RegexOption.IGNORE_CASE),
            Regex("character\\s+name\\s*[:=]\\s*([^\\n]{1,50})", RegexOption.IGNORE_CASE)
        )
        
        for (pattern in patterns) {
            val match = pattern.find(fullText)
            if (match != null && match.groupValues.size > 1) {
                val name = match.groupValues[1].trim()
                if (name.isNotBlank() && name.length > 2) {
                    return name
                }
            }
        }
        
        return null
    }
    
    private fun parseVtmSections(lines: List<String>, fullText: String): List<ParsedSection> {
        val sections = mutableListOf<ParsedSection>()
        
        // Extract header info
        extractHeaderSection(lines, fullText)?.let { sections.add(it) }
        
        // Extract attributes
        extractAttributesSection(lines, fullText, "vtm")?.let { sections.add(it) }
        
        // Extract skills
        extractSkillsSection(lines, fullText, "vtm")?.let { sections.add(it) }
        
        // Extract disciplines
        extractDisciplinesSection(lines, fullText)?.let { sections.add(it) }
        
        // Extract advantages/flaws
        extractAdvantagesSection(lines, fullText)?.let { sections.add(it) }
        extractFlawsSection(lines, fullText)?.let { sections.add(it) }
        
        // Extract trackers
        extractTrackersSection(lines, fullText)?.let { sections.add(it) }
        
        // Extract blood info
        extractBloodSection(lines, fullText)?.let { sections.add(it) }
        
        // Extract convictions
        extractConvictionsSection(lines, fullText)?.let { sections.add(it) }
        
        // Extract goals
        extractGoalsSection(lines, fullText)?.let { sections.add(it) }
        
        // Extract notes
        extractNotesSection(lines, fullText)?.let { sections.add(it) }
        
        return sections
    }
    
    private fun parseDndSections(lines: List<String>, fullText: String): List<ParsedSection> {
        val sections = mutableListOf<ParsedSection>()
        
        // Similar structure but for D&D
        extractHeaderSection(lines, fullText)?.let { sections.add(it) }
        extractAttributesSection(lines, fullText, "dnd")?.let { sections.add(it) }
        extractSkillsSection(lines, fullText, "dnd")?.let { sections.add(it) }
        extractAdvantagesSection(lines, fullText)?.let { sections.add(it) }
        extractFlawsSection(lines, fullText)?.let { sections.add(it) }
        extractNotesSection(lines, fullText)?.let { sections.add(it) }
        
        return sections
    }
    
    private fun parseGenericSections(lines: List<String>, fullText: String): List<ParsedSection> {
        // Fallback generic parsing
        return listOf(
            ParsedSection(
                sectionType = SectionType.OTHER,
                title = "Full Text",
                content = fullText.take(1000), // Limit to first 1000 chars
                confidence = 0.3f
            )
        )
    }
    
    // Section extraction methods
    
    private fun extractHeaderSection(lines: List<String>, fullText: String): ParsedSection? {
        val headerData = mutableMapOf<String, String>()
        
        // Extract common header fields
        val fields = mapOf(
            "name" to listOf("Name", "Имя", "Character Name"),
            "player" to listOf("Player", "Игрок"),
            "chronicle" to listOf("Chronicle", "Хроника"),
            "concept" to listOf("Concept", "Концепция"),
            "clan" to listOf("Clan", "Клан"),
            "generation" to listOf("Generation", "Поколение")
        )
        
        fields.forEach { (key, labels) ->
            labels.forEach { label ->
                val pattern = Regex("$label\\s*[:=]\\s*([^\\n]{1,100})", RegexOption.IGNORE_CASE)
                pattern.find(fullText)?.let { match ->
                    val value = match.groupValues[1].trim()
                    if (value.isNotBlank() && !value.matches(Regex("^\\d+$"))) {
                        headerData[key] = value
                    }
                }
            }
        }
        
        if (headerData.isEmpty()) return null
        
        val content = headerData.entries.joinToString("\n") { "${it.key}: ${it.value}" }
        val confidence = if (headerData.size >= 3) 0.9f else 0.6f
        
        return ParsedSection(
            sectionType = SectionType.HEADER,
            title = "Character Info",
            content = content,
            confidence = confidence,
            rawData = headerData
        )
    }
    
    private fun extractAttributesSection(lines: List<String>, fullText: String, system: String): ParsedSection? {
        val attributes = mutableMapOf<String, Int>()
        
        val attrNames = when (system) {
            "vtm" -> listOf(
                "Strength", "Dexterity", "Stamina",
                "Charisma", "Manipulation", "Composure",
                "Intelligence", "Wits", "Resolve"
            )
            "dnd" -> listOf(
                "Strength", "Dexterity", "Constitution",
                "Intelligence", "Wisdom", "Charisma"
            )
            else -> listOf("Strength", "Dexterity", "Intelligence")
        }
        
        attrNames.forEach { attr ->
            val pattern = Regex("$attr\\s*[:=]?\\s*(\\d+)", RegexOption.IGNORE_CASE)
            pattern.findAll(fullText).forEach { match ->
                val value = match.groupValues[1].toIntOrNull()
                if (value != null && value in 1..10) {
                    attributes[attr] = value
                }
            }
        }
        
        if (attributes.isEmpty()) return null
        
        val content = attributes.entries.joinToString("\n") { "${it.key}: ${it.value}" }
        val confidence = if (attributes.size >= 6) 0.9f else 0.7f
        
        return ParsedSection(
            sectionType = SectionType.ATTRIBUTES,
            title = "Attributes",
            content = content,
            confidence = confidence,
            rawData = attributes.mapValues { it.value as Any }
        )
    }
    
    private fun extractSkillsSection(lines: List<String>, fullText: String, system: String): ParsedSection? {
        val skills = mutableMapOf<String, Int>()
        
        val skillNames = when (system) {
            "vtm" -> listOf(
                "Athletics", "Brawl", "Craft", "Drive", "Firearms", "Larceny", "Melee", "Stealth", "Survival",
                "Animal Ken", "Etiquette", "Insight", "Intimidation", "Leadership", "Performance", "Persuasion", "Streetwise", "Subterfuge",
                "Academics", "Awareness", "Finance", "Investigation", "Medicine", "Occult", "Politics", "Science", "Technology"
            )
            else -> listOf("Athletics", "Stealth", "Perception", "Investigation")
        }
        
        skillNames.forEach { skill ->
            val pattern = Regex("$skill\\s*[:=]?\\s*(\\d+)", RegexOption.IGNORE_CASE)
            pattern.findAll(fullText).forEach { match ->
                val value = match.groupValues[1].toIntOrNull()
                if (value != null && value in 0..5) {
                    skills[skill] = value
                }
            }
        }
        
        if (skills.isEmpty()) return null
        
        val content = skills.entries.joinToString("\n") { "${it.key}: ${it.value}" }
        val confidence = if (skills.size >= 10) 0.85f else 0.65f
        
        return ParsedSection(
            sectionType = SectionType.SKILLS,
            title = "Skills",
            content = content,
            confidence = confidence,
            rawData = skills.mapValues { it.value as Any }
        )
    }
    
    private fun extractDisciplinesSection(lines: List<String>, fullText: String): ParsedSection? {
        // Look for discipline section
        val disciplinePattern = Regex("Disciplines?\\s*[:=]?", RegexOption.IGNORE_CASE)
        val match = disciplinePattern.find(fullText) ?: return null
        
        val startIndex = match.range.last
        val endIndex = minOf(startIndex + 500, fullText.length)
        val sectionText = fullText.substring(startIndex, endIndex)
        
        // Extract discipline names and levels
        val disciplines = mutableListOf<String>()
        val lines = sectionText.lines().take(10)
        
        lines.forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotBlank() && trimmed.length > 3 && !trimmed.contains(":")) {
                disciplines.add(trimmed)
            }
        }
        
        if (disciplines.isEmpty()) return null
        
        return ParsedSection(
            sectionType = SectionType.DISCIPLINES,
            title = "Disciplines",
            content = disciplines.joinToString("\n"),
            confidence = 0.7f
        )
    }
    
    private fun extractMultilineSection(
        lines: List<String>,
        fullText: String,
        sectionType: SectionType,
        title: String,
        labels: List<String>
    ): ParsedSection? {
        var sectionStart = -1
        
        // Find section start
        lines.forEachIndexed { index, line ->
            if (labels.any { line.contains(it, ignoreCase = true) && line.contains(":") }) {
                sectionStart = index
            }
        }
        
        if (sectionStart < 0) return null
        
        // Extract content
        val content = StringBuilder()
        val firstLine = lines[sectionStart]
        labels.forEach { label ->
            val pattern = Regex("$label\\s*[:=]\\s*([^\\n]+)", RegexOption.IGNORE_CASE)
            pattern.find(firstLine)?.let {
                content.append(it.groupValues[1].trim())
            }
        }
        
        // Collect following lines
        var i = sectionStart + 1
        while (i < lines.size && i < sectionStart + 15) {
            val line = lines[i].trim()
            if (line.isBlank()) {
                i++
                continue
            }
            // Stop at next section
            if (line.contains(":") && line.split(":")[0].length < 30) {
                break
            }
            if (content.isNotEmpty()) content.append("\n")
            content.append(line)
            i++
        }
        
        val text = content.toString().trim()
        if (text.length < 3) return null
        
        return ParsedSection(
            sectionType = sectionType,
            title = title,
            content = text,
            confidence = if (text.length > 50) 0.8f else 0.6f
        )
    }
    
    private fun extractAdvantagesSection(lines: List<String>, fullText: String): ParsedSection? {
        return extractMultilineSection(
            lines, fullText,
            SectionType.ADVANTAGES,
            "Advantages",
            listOf("Advantages", "Достоинства", "Merits")
        )
    }
    
    private fun extractFlawsSection(lines: List<String>, fullText: String): ParsedSection? {
        return extractMultilineSection(
            lines, fullText,
            SectionType.FLAWS,
            "Flaws",
            listOf("Flaws", "Недостатки")
        )
    }
    
    private fun extractNotesSection(lines: List<String>, fullText: String): ParsedSection? {
        return extractMultilineSection(
            lines, fullText,
            SectionType.NOTES,
            "Notes",
            listOf("Notes", "Заметки", "History", "История")
        )
    }
    
    private fun extractTrackersSection(lines: List<String>, fullText: String): ParsedSection? {
        val trackers = mutableMapOf<String, String>()
        
        val trackerFields = mapOf(
            "Health" to listOf("Health", "Здоровье"),
            "Willpower" to listOf("Willpower", "Сила воли"),
            "Hunger" to listOf("Hunger", "Голод"),
            "Humanity" to listOf("Humanity", "Человечность")
        )
        
        trackerFields.forEach { (key, labels) ->
            labels.forEach { label ->
                val pattern = Regex("$label\\s*[:=]?\\s*([\\d/]+)", RegexOption.IGNORE_CASE)
                pattern.find(fullText)?.let {
                    trackers[key] = it.groupValues[1]
                }
            }
        }
        
        if (trackers.isEmpty()) return null
        
        return ParsedSection(
            sectionType = SectionType.TRACKERS,
            title = "Trackers",
            content = trackers.entries.joinToString("\n") { "${it.key}: ${it.value}" },
            confidence = 0.75f,
            rawData = trackers.mapValues { it.value as Any }
        )
    }
    
    private fun extractBloodSection(lines: List<String>, fullText: String): ParsedSection? {
        return extractMultilineSection(
            lines, fullText,
            SectionType.BLOOD,
            "Blood & Resonance",
            listOf("Blood Potency", "Мощь крови", "Blood Resonance")
        )
    }
    
    private fun extractConvictionsSection(lines: List<String>, fullText: String): ParsedSection? {
        return extractMultilineSection(
            lines, fullText,
            SectionType.CONVICTIONS,
            "Convictions & Touchstones",
            listOf("Convictions", "Убеждения", "Touchstones", "Якоря")
        )
    }
    
    private fun extractGoalsSection(lines: List<String>, fullText: String): ParsedSection? {
        return extractMultilineSection(
            lines, fullText,
            SectionType.GOALS,
            "Goals",
            listOf("Ambition", "Амбиция", "Desire", "Желание")
        )
    }
    
    // WHRP (Warhammer Fantasy Roleplay) parsing
    private fun parseWhrpSections(lines: List<String>, fullText: String): List<ParsedSection> {
        val sections = mutableListOf<ParsedSection>()
        
        extractHeaderSection(lines, fullText)?.let { sections.add(it) }
        extractWhrpAttributesSection(lines, fullText)?.let { sections.add(it) }
        extractWhrpSkillsSection(lines, fullText)?.let { sections.add(it) }
        extractWhrpTrackersSection(lines, fullText)?.let { sections.add(it) }
        extractAdvantagesSection(lines, fullText)?.let { sections.add(it) }
        extractFlawsSection(lines, fullText)?.let { sections.add(it) }
        extractNotesSection(lines, fullText)?.let { sections.add(it) }
        
        return sections
    }
    
    private fun extractWhrpAttributesSection(lines: List<String>, fullText: String): ParsedSection? {
        val attributes = mutableMapOf<String, Int>()
        
        val attrNames = listOf(
            "Weapon Skill", "Ballistic Skill", "Strength", "Toughness",
            "Initiative", "Agility", "Dexterity", "Intelligence",
            "Willpower", "Fellowship"
        )
        
        attrNames.forEach { attr ->
            val pattern = Regex("$attr\\s*[:=]?\\s*(\\d+)", RegexOption.IGNORE_CASE)
            pattern.findAll(fullText).forEach { match ->
                val value = match.groupValues[1].toIntOrNull()
                if (value != null && value in 1..100) {
                    attributes[attr] = value
                }
            }
        }
        
        if (attributes.isEmpty()) return null
        
        return ParsedSection(
            sectionType = SectionType.ATTRIBUTES,
            title = "WHRP Attributes",
            content = attributes.entries.joinToString("\n") { "${it.key}: ${it.value}" },
            confidence = if (attributes.size >= 6) 0.85f else 0.65f,
            rawData = attributes.mapValues { it.value as Any }
        )
    }
    
    private fun extractWhrpSkillsSection(lines: List<String>, fullText: String): ParsedSection? {
        val skills = mutableMapOf<String, Int>()
        
        val skillNames = listOf(
            "Melee", "Ranged", "Athletics", "Stealth", "Perception",
            "Charm", "Intimidate", "Leadership", "Lore", "Intuition"
        )
        
        skillNames.forEach { skill ->
            val pattern = Regex("$skill\\s*[:=]?\\s*(\\d+)", RegexOption.IGNORE_CASE)
            pattern.findAll(fullText).forEach { match ->
                val value = match.groupValues[1].toIntOrNull()
                if (value != null && value in 0..100) {
                    skills[skill] = value
                }
            }
        }
        
        if (skills.isEmpty()) return null
        
        return ParsedSection(
            sectionType = SectionType.SKILLS,
            title = "WHRP Skills",
            content = skills.entries.joinToString("\n") { "${it.key}: ${it.value}" },
            confidence = if (skills.size >= 5) 0.8f else 0.6f,
            rawData = skills.mapValues { it.value as Any }
        )
    }
    
    private fun extractWhrpTrackersSection(lines: List<String>, fullText: String): ParsedSection? {
        val trackers = mutableMapOf<String, String>()
        
        val trackerFields = mapOf(
            "Wounds" to listOf("Wounds", "Раны"),
            "Fate Points" to listOf("Fate Points", "Fate", "Очки судьбы"),
            "Fortune Points" to listOf("Fortune Points", "Fortune"),
            "Resilience" to listOf("Resilience", "Стойкость")
        )
        
        trackerFields.forEach { (key, labels) ->
            labels.forEach { label ->
                val pattern = Regex("$label\\s*[:=]?\\s*([\\d/]+)", RegexOption.IGNORE_CASE)
                pattern.find(fullText)?.let {
                    trackers[key] = it.groupValues[1]
                }
            }
        }
        
        if (trackers.isEmpty()) return null
        
        return ParsedSection(
            sectionType = SectionType.TRACKERS,
            title = "WHRP Trackers",
            content = trackers.entries.joinToString("\n") { "${it.key}: ${it.value}" },
            confidence = 0.75f,
            rawData = trackers.mapValues { it.value as Any }
        )
    }
    
    // Dark Heresy parsing
    private fun parseDarkHeresySections(lines: List<String>, fullText: String): List<ParsedSection> {
        val sections = mutableListOf<ParsedSection>()
        
        extractHeaderSection(lines, fullText)?.let { sections.add(it) }
        extractDarkHeresyAttributesSection(lines, fullText)?.let { sections.add(it) }
        extractDarkHeresySkillsSection(lines, fullText)?.let { sections.add(it) }
        extractDarkHeresyTrackersSection(lines, fullText)?.let { sections.add(it) }
        extractAdvantagesSection(lines, fullText)?.let { sections.add(it) }
        extractFlawsSection(lines, fullText)?.let { sections.add(it) }
        extractNotesSection(lines, fullText)?.let { sections.add(it) }
        
        return sections
    }
    
    private fun extractDarkHeresyAttributesSection(lines: List<String>, fullText: String): ParsedSection? {
        val attributes = mutableMapOf<String, Int>()
        
        val attrNames = listOf(
            "Weapon Skill", "Ballistic Skill", "Strength", "Toughness",
            "Agility", "Intelligence", "Perception", "Willpower", "Fellowship"
        )
        
        attrNames.forEach { attr ->
            val pattern = Regex("$attr\\s*[:=]?\\s*(\\d+)", RegexOption.IGNORE_CASE)
            pattern.findAll(fullText).forEach { match ->
                val value = match.groupValues[1].toIntOrNull()
                if (value != null && value in 1..100) {
                    attributes[attr] = value
                }
            }
        }
        
        if (attributes.isEmpty()) return null
        
        return ParsedSection(
            sectionType = SectionType.ATTRIBUTES,
            title = "Dark Heresy Attributes",
            content = attributes.entries.joinToString("\n") { "${it.key}: ${it.value}" },
            confidence = if (attributes.size >= 6) 0.85f else 0.65f,
            rawData = attributes.mapValues { it.value as Any }
        )
    }
    
    private fun extractDarkHeresySkillsSection(lines: List<String>, fullText: String): ParsedSection? {
        val skills = mutableMapOf<String, Int>()
        
        val skillNames = listOf(
            "Awareness", "Dodge", "Inquiry", "Scrutiny", "Search",
            "Charm", "Command", "Deceive", "Intimidate", "Logic"
        )
        
        skillNames.forEach { skill ->
            val pattern = Regex("$skill\\s*[:=]?\\s*(\\d+)", RegexOption.IGNORE_CASE)
            pattern.findAll(fullText).forEach { match ->
                val value = match.groupValues[1].toIntOrNull()
                if (value != null && value in 0..100) {
                    skills[skill] = value
                }
            }
        }
        
        if (skills.isEmpty()) return null
        
        return ParsedSection(
            sectionType = SectionType.SKILLS,
            title = "Dark Heresy Skills",
            content = skills.entries.joinToString("\n") { "${it.key}: ${it.value}" },
            confidence = if (skills.size >= 5) 0.8f else 0.6f,
            rawData = skills.mapValues { it.value as Any }
        )
    }
    
    private fun extractDarkHeresyTrackersSection(lines: List<String>, fullText: String): ParsedSection? {
        val trackers = mutableMapOf<String, String>()
        
        val trackerFields = mapOf(
            "Wounds" to listOf("Wounds", "Раны"),
            "Fate Points" to listOf("Fate Points", "Fate"),
            "Insanity" to listOf("Insanity", "Безумие"),
            "Corruption" to listOf("Corruption", "Порча")
        )
        
        trackerFields.forEach { (key, labels) ->
            labels.forEach { label ->
                val pattern = Regex("$label\\s*[:=]?\\s*([\\d/]+)", RegexOption.IGNORE_CASE)
                pattern.find(fullText)?.let {
                    trackers[key] = it.groupValues[1]
                }
            }
        }
        
        if (trackers.isEmpty()) return null
        
        return ParsedSection(
            sectionType = SectionType.TRACKERS,
            title = "Dark Heresy Trackers",
            content = trackers.entries.joinToString("\n") { "${it.key}: ${it.value}" },
            confidence = 0.75f,
            rawData = trackers.mapValues { it.value as Any }
        )
    }
    
    // Viedzmin parsing
    private fun parseViedzminSections(lines: List<String>, fullText: String): List<ParsedSection> {
        val sections = mutableListOf<ParsedSection>()
        
        extractHeaderSection(lines, fullText)?.let { sections.add(it) }
        extractViedzminAttributesSection(lines, fullText)?.let { sections.add(it) }
        extractViedzminSkillsSection(lines, fullText)?.let { sections.add(it) }
        extractAdvantagesSection(lines, fullText)?.let { sections.add(it) }
        extractFlawsSection(lines, fullText)?.let { sections.add(it) }
        extractNotesSection(lines, fullText)?.let { sections.add(it) }
        
        return sections
    }
    
    private fun extractViedzminAttributesSection(lines: List<String>, fullText: String): ParsedSection? {
        val attributes = mutableMapOf<String, Int>()
        
        val attrNames = listOf(
            "Интеллект", "Рефлексы", "Ловкость", "Телосложение",
            "Скорость", "Эмпатия", "Ремесло", "Воля"
        )
        
        attrNames.forEach { attr ->
            val pattern = Regex("$attr\\s*[:=]?\\s*(\\d+)", RegexOption.IGNORE_CASE)
            pattern.findAll(fullText).forEach { match ->
                val value = match.groupValues[1].toIntOrNull()
                if (value != null && value in 1..10) {
                    attributes[attr] = value
                }
            }
        }
        
        if (attributes.isEmpty()) return null
        
        return ParsedSection(
            sectionType = SectionType.ATTRIBUTES,
            title = "Viedzmin Attributes",
            content = attributes.entries.joinToString("\n") { "${it.key}: ${it.value}" },
            confidence = if (attributes.size >= 5) 0.85f else 0.65f,
            rawData = attributes.mapValues { it.value as Any }
        )
    }
    
    private fun extractViedzminSkillsSection(lines: List<String>, fullText: String): ParsedSection? {
        val skills = mutableMapOf<String, Int>()
        
        val skillNames = listOf(
            "Внимание", "Выживание", "Дедукция", "Образование",
            "Обман", "Убеждение", "Запугивание", "Атлетика"
        )
        
        skillNames.forEach { skill ->
            val pattern = Regex("$skill\\s*[:=]?\\s*(\\d+)", RegexOption.IGNORE_CASE)
            pattern.findAll(fullText).forEach { match ->
                val value = match.groupValues[1].toIntOrNull()
                if (value != null && value in 0..10) {
                    skills[skill] = value
                }
            }
        }
        
        if (skills.isEmpty()) return null
        
        return ParsedSection(
            sectionType = SectionType.SKILLS,
            title = "Viedzmin Skills",
            content = skills.entries.joinToString("\n") { "${it.key}: ${it.value}" },
            confidence = if (skills.size >= 5) 0.8f else 0.6f,
            rawData = skills.mapValues { it.value as Any }
        )
    }
}
