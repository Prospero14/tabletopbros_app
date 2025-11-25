package com.fts.ttbros.charactersheets

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.fts.ttbros.R
import com.fts.ttbros.data.model.CharacterSheet
import com.fts.ttbros.data.repository.CharacterSheetRepository
import kotlinx.coroutines.launch
import java.io.InputStream

class CharacterSheetsFragment : Fragment() {

    private lateinit var sheetsRecyclerView: RecyclerView
    private lateinit var emptyView: View
    private lateinit var addSheetButton: FloatingActionButton
    private lateinit var loadingView: View
    
    private val auth by lazy { Firebase.auth }
    private val yandexDisk = com.fts.ttbros.data.repository.YandexDiskRepository()
    private val sheetRepository = CharacterSheetRepository()
    private val userRepository = com.fts.ttbros.data.repository.UserRepository()
    private val documentRepository = com.fts.ttbros.data.repository.DocumentRepository()
    private lateinit var adapter: CharacterSheetsAdapter
    
    private val pickPdfLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { uploadAndParsePdf(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_character_sheets, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        sheetsRecyclerView = view.findViewById(R.id.sheetsRecyclerView)
        emptyView = view.findViewById(R.id.emptyView)
        addSheetButton = view.findViewById(R.id.addSheetButton)
        loadingView = view.findViewById(R.id.loadingView)
        
        adapter = CharacterSheetsAdapter(
            onSheetClick = { sheet ->
                openSheetEditor(sheet)
            },
            onSheetDelete = { sheet ->
                deleteSheet(sheet)
            }
        )
        
        val context = context ?: return
        sheetsRecyclerView.layoutManager = LinearLayoutManager(context)
        sheetsRecyclerView.adapter = adapter
        
        addSheetButton.setOnClickListener {
            pickPdfLauncher.launch("application/pdf")
        }
        
        loadSheets()
    }
    
    private fun loadSheets() {
        val userId = auth.currentUser?.uid ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (!isAdded || view == null) return@launch
                loadingView.isVisible = true
                val sheets = sheetRepository.getUserSheets(userId)
                if (!isAdded || view == null) return@launch
                adapter.submitList(sheets)
                emptyView.isVisible = sheets.isEmpty()
            } catch (e: Exception) {
                android.util.Log.e("CharacterSheetsFragment", "Error loading sheets: ${e.message}", e)
                if (isAdded && view != null) {
                    view?.let {
                        Snackbar.make(it, "Ошибка загрузки листов: ${e.message}", Snackbar.LENGTH_LONG).show()
                    }
                }
            } finally {
                if (isAdded && view != null) {
                    loadingView.isVisible = false
                }
            }
        }
    }
    
    private fun uploadAndParsePdf(uri: Uri) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            android.util.Log.e("CharacterSheetsFragment", "User not authenticated")
            view?.let {
                Snackbar.make(it, "Ошибка: пользователь не авторизован", Snackbar.LENGTH_LONG).show()
            }
            return
        }
        
        val context = context
        if (context == null) {
            android.util.Log.e("CharacterSheetsFragment", "Context is null")
            return
        }
        
        if (!isAdded || view == null) {
            android.util.Log.w("CharacterSheetsFragment", "Fragment not attached, cannot upload")
            return
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Проверяем что фрагмент все еще прикреплен перед началом операций
                if (!isAdded || view == null) {
                    android.util.Log.w("CharacterSheetsFragment", "Fragment not attached at start of upload")
                    return@launch
                }
                
                loadingView.isVisible = true
                view?.let {
                    Snackbar.make(it, "Проверка PDF...", Snackbar.LENGTH_SHORT).show()
                }
                
                // Сначала проверяем, что это действительно лист персонажа (до загрузки в Yandex.Disk)
                val validationResult = try {
                    validateCharacterSheet(uri, context)
                } catch (e: Exception) {
                    android.util.Log.e("CharacterSheetsFragment", "Error validating PDF: ${e.message}", e)
                    if (isAdded && view != null) {
                        view?.let {
                            Snackbar.make(it, "Ошибка проверки файла: ${e.message}", Snackbar.LENGTH_LONG).show()
                        }
                        loadingView.isVisible = false
                    }
                    return@launch
                }
                if (!validationResult.isValid) {
                    if (isAdded && view != null) {
                        view?.let {
                            Snackbar.make(it, validationResult.errorMessage ?: "Этот файл не является листом персонажа", Snackbar.LENGTH_LONG).show()
                        }
                        loadingView.isVisible = false
                    }
                    return@launch
                }
                
                // Проверка что фрагмент все еще прикреплен
                if (!isAdded || view == null) {
                    android.util.Log.w("CharacterSheetsFragment", "Fragment detached during validation")
                    return@launch
                }
                
                view?.let {
                    Snackbar.make(it, "Загрузка PDF...", Snackbar.LENGTH_SHORT).show()
                }
                
                // Upload PDF to Yandex.Disk (только если валидация прошла)
                val pdfUrl = try {
                    yandexDisk.uploadCharacterSheet(userId, uri, context)
                } catch (e: Exception) {
                    android.util.Log.e("CharacterSheetsFragment", "Error uploading to Yandex.Disk: ${e.message}", e)
                    throw e
                }
                
                // Проверка что фрагмент все еще прикреплен
                if (!isAdded || view == null) {
                    android.util.Log.w("CharacterSheetsFragment", "Fragment detached during upload")
                    return@launch
                }
                
                // Save character sheet metadata to Firestore so it appears in Documents tab
                val userProfile = userRepository.currentProfile()
                val teamId = userProfile?.teamId
                if (teamId != null) {
                    try {
                        documentRepository.uploadCharacterSheetMetadata(
                            teamId = teamId,
                            pdfUrl = pdfUrl,
                            characterName = validationResult.characterName ?: "Unknown",
                            system = validationResult.system ?: "unknown",
                            userId = userId,
                            userName = auth.currentUser?.displayName ?: "Unknown"
                        )
                        android.util.Log.d("CharacterSheetsFragment", "Character sheet metadata saved to Firestore")
                    } catch (e: Exception) {
                        android.util.Log.e("CharacterSheetsFragment", "Error saving metadata: ${e.message}", e)
                        // Don't fail the upload if metadata save fails
                    }
                }
                
                view?.let {
                    Snackbar.make(it, "Парсинг PDF...", Snackbar.LENGTH_SHORT).show()
                }
                
                // Parse PDF
                val parsedData = try {
                    parsePdf(uri, context)
                } catch (e: Exception) {
                    android.util.Log.e("CharacterSheetsFragment", "Error parsing PDF: ${e.message}", e)
                    throw e
                }
                
                // Проверка что фрагмент все еще прикреплен
                if (!isAdded || view == null) {
                    android.util.Log.w("CharacterSheetsFragment", "Fragment detached during parsing")
                    return@launch
                }
                
                // Получаем систему команды для названия
                val profile = userRepository.currentProfile()
                val currentTeam = profile?.teams?.find { it.teamId == profile.currentTeamId }
                val teamSystem = currentTeam?.teamSystem ?: (parsedData["system"] as? String ?: "unknown")
                
                // Формируем название системы для отображения
                val systemName = when (teamSystem) {
                    "vtm_5e" -> "VTM"
                    "dnd_5e" -> "D&D"
                    "viedzmin_2e" -> "Viedzmin"
                    "whrp" -> "WHRP"
                    "wh_darkheresy" -> "Dark Heresy"
                    else -> teamSystem
                }
                
                // Создаем CharacterSheet из загруженного PDF (как было раньше)
                val sheet = CharacterSheet(
                    userId = userId,
                    characterName = "Загруженный лист", // Фиксированное название вместо парсинга
                    system = teamSystem,
                    pdfUrl = pdfUrl,
                    parsedData = parsedData,
                    attributes = (parsedData["attributes"] as? Map<*, *>)?.mapKeys { (it.key as? Any)?.toString() ?: "" }?.mapValues { (it.value as? Number)?.toInt() ?: 0 } ?: emptyMap<String, Int>(),
                    skills = (parsedData["skills"] as? Map<*, *>)?.mapKeys { (it.key as? Any)?.toString() ?: "" }?.mapValues { (it.value as? Number)?.toInt() ?: 0 } ?: emptyMap<String, Int>(),
                    stats = (parsedData["stats"] as? Map<*, *>)?.mapKeys { (it.key as? Any)?.toString() ?: "" }?.mapValues { it.value ?: "" } ?: emptyMap<String, Any>(),
                    isTemplate = false // Это загруженный лист персонажа
                )
                
                // Показать диалог подтверждения создания листа
                // Используем Main диспетчер для показа диалога в UI потоке
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    if (isAdded && view != null && context != null) {
                        showCreateSheetConfirmationDialog(sheet)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("CharacterSheetsFragment", "Error uploading PDF: ${e.message}", e)
                if (isAdded && view != null) {
                    view?.let {
                        Snackbar.make(it, "Ошибка загрузки: ${e.message}", Snackbar.LENGTH_LONG).show()
                    }
                }
            } finally {
                if (isAdded && view != null) {
                    loadingView.isVisible = false
                }
            }
        }
    }
    
    /**
     * Результат валидации листа персонажа
     */
    private data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null,
        val characterName: String? = null,
        val system: String? = null
    )
    
    /**
     * Проверяет, что загруженный PDF является листом персонажа
     * Проверяет наличие характерных ключевых слов и полей
     */
    private suspend fun validateCharacterSheet(uri: Uri, context: Context): ValidationResult {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                validatePdfContent(stream)
            } ?: ValidationResult(false, "Не удалось прочитать файл")
        } catch (e: SecurityException) {
            android.util.Log.e("CharacterSheetsFragment", "SecurityException validating PDF: ${e.message}", e)
            ValidationResult(false, "Нет доступа к файлу. Проверьте разрешения.")
        } catch (e: java.io.FileNotFoundException) {
            android.util.Log.e("CharacterSheetsFragment", "FileNotFoundException validating PDF: ${e.message}", e)
            ValidationResult(false, "Файл не найден")
        } catch (e: Exception) {
            android.util.Log.e("CharacterSheetsFragment", "Error validating PDF: ${e.message}", e)
            ValidationResult(false, "Ошибка проверки файла: ${e.message ?: "Неизвестная ошибка"}")
        }
    }
    
    /**
     * Проверяет содержимое PDF на наличие характерных для листов персонажей элементов
     */
    private fun validatePdfContent(inputStream: InputStream): ValidationResult {
        var document: com.tom_roush.pdfbox.pdmodel.PDDocument? = null
        return try {
            // Читаем текст из PDF
            document = com.tom_roush.pdfbox.pdmodel.PDDocument.load(inputStream)
            val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
            val text = stripper.getText(document).lowercase()
            document.close()
            document = null
            
            // Ключевые слова для проверки листа персонажа
            val requiredKeywords = listOf(
                // Общие для всех систем
                "character", "персонаж", "name", "имя",
                // D&D 5e
                "level", "class", "race", "hp", "hit points", "armor class", "ac",
                "strength", "dexterity", "constitution", "intelligence", "wisdom", "charisma",
                "сила", "ловкость", "выносливость", "интеллект", "мудрость", "харизма",
                // VTM 5e
                "clan", "generation", "disciplines", "humanity", "blood potency",
                "attributes", "skills", "abilities",
                // Общие поля
                "attributes", "skills", "stats", "abilities", "характеристики", "навыки"
            )
            
            // Проверяем наличие хотя бы нескольких ключевых слов
            val foundKeywords = requiredKeywords.count { keyword ->
                text.contains(keyword, ignoreCase = true)
            }
            
            // Минимум 3 ключевых слова должны быть найдены
            if (foundKeywords < 3) {
                return ValidationResult(
                    false,
                    "Файл не похож на лист персонажа. Не найдено достаточно характерных полей (найдено: $foundKeywords из ${requiredKeywords.size})"
                )
            }
            
            // Дополнительная проверка: должны быть найдены атрибуты или характеристики
            val hasAttributes = text.contains("strength", ignoreCase = true) ||
                    text.contains("dexterity", ignoreCase = true) ||
                    text.contains("сила", ignoreCase = true) ||
                    text.contains("ловкость", ignoreCase = true) ||
                    text.contains("attributes", ignoreCase = true) ||
                    text.contains("характеристики", ignoreCase = true)
            
            if (!hasAttributes) {
                return ValidationResult(
                    false,
                    "В файле не найдены характеристики персонажа. Убедитесь, что это лист персонажа."
                )
            }
            
            // Проверка на наличие имени персонажа или похожих полей
            val hasNameField = text.contains("name", ignoreCase = true) ||
                    text.contains("имя", ignoreCase = true) ||
                    text.contains("character name", ignoreCase = true)
            
            if (!hasNameField) {
                return ValidationResult(
                    false,
                    "В файле не найдено поле имени персонажа."
                )
            }
            
            // Extract character name and system from text
            val characterName = extractCharacterName(text)
            val system = detectSystem(text)
            
            ValidationResult(
                isValid = true,
                characterName = characterName,
                system = system
            )
        } catch (e: Exception) {
            android.util.Log.e("CharacterSheetsFragment", "Error validating PDF content: ${e.message}", e)
            try {
                document?.close()
            } catch (closeException: Exception) {
                android.util.Log.e("CharacterSheetsFragment", "Error closing PDF document: ${closeException.message}", closeException)
            }
            ValidationResult(false, "Ошибка при проверке файла: ${e.message}")
        }
    }
    
    /**
     * Extract character name from PDF text
     */
    private fun extractCharacterName(text: String): String {
        // Try to find name after "name:" or "имя:" keywords
        val namePatterns = listOf(
            Regex("name[:\\s]+([^\\n]{1,50})", RegexOption.IGNORE_CASE),
            Regex("имя[:\\s]+([^\\n]{1,50})", RegexOption.IGNORE_CASE),
            Regex("character name[:\\s]+([^\\n]{1,50})", RegexOption.IGNORE_CASE)
        )
        
        for (pattern in namePatterns) {
            val match = pattern.find(text)
            if (match != null && match.groupValues.size > 1) {
                return match.groupValues[1].trim().take(50)
            }
        }
        
        return "Unknown Character"
    }
    
    /**
     * Detect game system from PDF text
     */
    private fun detectSystem(text: String): String {
        return when {
            text.contains("vampire", ignoreCase = true) || 
            text.contains("clan", ignoreCase = true) ||
            text.contains("disciplines", ignoreCase = true) ||
            text.contains("blood potency", ignoreCase = true) -> "vtm_5e"
            
            text.contains("dungeons", ignoreCase = true) ||
            text.contains("dragons", ignoreCase = true) ||
            text.contains("d&d", ignoreCase = true) ||
            text.contains("armor class", ignoreCase = true) -> "dnd_5e"
            
            text.contains("viedzmin", ignoreCase = true) ||
            text.contains("ведьмак", ignoreCase = true) -> "viedzmin_2e"
            
            text.contains("dark heresy", ignoreCase = true) ||
            text.contains("dark heresy", ignoreCase = true) ||
            text.contains("imperium", ignoreCase = true) ||
            text.contains("inquisition", ignoreCase = true) ||
            text.contains("acolyte", ignoreCase = true) ||
            text.contains("throne agent", ignoreCase = true) -> "wh_darkheresy"
            
            text.contains("warhammer fantasy roleplay", ignoreCase = true) ||
            text.contains("whrp", ignoreCase = true) ||
            text.contains("warhammer roleplay", ignoreCase = true) ||
            text.contains("career", ignoreCase = true) && text.contains("advance", ignoreCase = true) ||
            text.contains("fate points", ignoreCase = true) ||
            text.contains("fortune points", ignoreCase = true) -> "whrp"
            
            else -> "unknown"
        }
    }
    
    private suspend fun parsePdf(uri: Uri, context: Context): Map<String, Any> {
        // Basic PDF parsing - this is a simplified version
        // In production, you'd want more sophisticated parsing
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                parsePdfContent(stream)
            } ?: emptyMap<String, Any>()
        } catch (e: SecurityException) {
            android.util.Log.e("CharacterSheetsFragment", "SecurityException parsing PDF: ${e.message}", e)
            emptyMap<String, Any>()
        } catch (e: java.io.FileNotFoundException) {
            android.util.Log.e("CharacterSheetsFragment", "FileNotFoundException parsing PDF: ${e.message}", e)
            emptyMap<String, Any>()
        } catch (e: OutOfMemoryError) {
            android.util.Log.e("CharacterSheetsFragment", "OutOfMemoryError parsing PDF: ${e.message}", e)
            emptyMap<String, Any>()
        } catch (e: Exception) {
            android.util.Log.e("CharacterSheetsFragment", "Error parsing PDF: ${e.message}", e)
            emptyMap<String, Any>()
        }
    }
    
    /**
     * Извлекает атрибуты из текста PDF
     */
    private fun extractAttributes(
        attributeNames: List<String>,
        attributes: MutableMap<String, Int>,
        lines: List<String>,
        fullText: String
    ) {
        attributeNames.forEach { attr ->
            // Паттерн 1: "Attribute: 3" или "Attribute = 3"
            val pattern1 = Regex("$attr\\s*[:=]\\s*(\\d+)", RegexOption.IGNORE_CASE)
            // Паттерн 2: "Attribute 3" (без разделителя)
            val pattern2 = Regex("$attr\\s+(\\d+)", RegexOption.IGNORE_CASE)
            // Паттерн 3: Поиск в полном тексте с контекстом
            val pattern3 = Regex("(?:^|\\n|\\r)\\s*$attr\\s*[:=]?\\s*(\\d+)", RegexOption.IGNORE_CASE)
            
            // Пробуем все паттерны
            listOf(pattern1, pattern2, pattern3).forEach { pattern ->
                pattern.findAll(fullText).forEach { match ->
                    val value = match.groupValues[1].toIntOrNull()
                    if (value != null && value > 0 && value <= 10) {
                        attributes[attr] = value
                        // Также сохраняем нормализованное имя
                        attributes[attr.lowercase().replace(" ", "_")] = value
                    }
                }
            }
        }
    }
    
    /**
     * Извлекает навыки из текста PDF
     */
    private fun extractSkills(
        skillNames: List<String>,
        skills: MutableMap<String, Int>,
        lines: List<String>,
        fullText: String
    ) {
        skillNames.forEach { skill ->
            // Паттерн 1: "Skill: 3" или "Skill = 3"
            val pattern1 = Regex("$skill\\s*[:=]\\s*(\\d+)", RegexOption.IGNORE_CASE)
            // Паттерн 2: "Skill 3" (без разделителя)
            val pattern2 = Regex("$skill\\s+(\\d+)", RegexOption.IGNORE_CASE)
            // Паттерн 3: Поиск в полном тексте
            val pattern3 = Regex("(?:^|\\n|\\r)\\s*$skill\\s*[:=]?\\s*(\\d+)", RegexOption.IGNORE_CASE)
            
            listOf(pattern1, pattern2, pattern3).forEach { pattern ->
                pattern.findAll(fullText).forEach { match ->
                    val value = match.groupValues[1].toIntOrNull()
                    if (value != null && value >= 0 && value <= 5) {
                        skills[skill] = value
                        // Также сохраняем нормализованное имя
                        skills[skill.lowercase().replace(" ", "_")] = value
                    }
                }
            }
        }
    }
    
    /**
     * Извлекает статистики из текста PDF
     */
    private fun extractStats(
        statPatterns: Map<String, List<String>>,
        stats: MutableMap<String, Any>,
        lines: List<String>,
        fullText: String
    ) {
        statPatterns.forEach { (statKey, patterns) ->
            patterns.forEach { pattern ->
                // Паттерн 1: "Stat: value" или "Stat = value"
                val pattern1 = Regex("$pattern\\s*[:=]\\s*([^\\n\\r]+)", RegexOption.IGNORE_CASE)
                // Паттерн 2: "Stat value" (без разделителя)
                val pattern2 = Regex("$pattern\\s+([^\\n\\r]+)", RegexOption.IGNORE_CASE)
                // Паттерн 3: Многострочный поиск
                val pattern3 = Regex("(?:^|\\n|\\r)\\s*$pattern\\s*[:=]?\\s*([^\\n\\r]+)", RegexOption.IGNORE_CASE)
                
                listOf(pattern1, pattern2, pattern3).forEach { regex ->
                    regex.findAll(fullText).forEach { match ->
                        val value = match.groupValues[1].trim()
                        if (value.isNotBlank()) {
                            // Пытаемся определить тип значения
                            val numericValue = value.toIntOrNull()
                            val doubleValue = value.toDoubleOrNull()
                            
                            when {
                                numericValue != null -> {
                                    // Если это число, сохраняем как число
                                    if (stats[statKey] !is String) {
                                        stats[statKey] = numericValue
                                    }
                                }
                                doubleValue != null -> {
                                    stats[statKey] = doubleValue
                                }
                                else -> {
                                    // Текстовое значение
                                    if (stats[statKey] !is Number) {
                                        stats[statKey] = value
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Извлекает специфичные для VTM 5e поля
     */
    private fun extractVtmSpecificFields(stats: MutableMap<String, Any>, lines: List<String>, fullText: String) {
        // Улучшенный парсинг трекеров - поддерживаем разные форматы
        val trackerPatterns = mapOf(
            "health" to listOf("Health", "Здоровье", "Health Tracker"),
            "willpower" to listOf("Willpower", "Сила воли", "Willpower Tracker", "WP"),
            "hunger" to listOf("Hunger", "Голод", "Hunger Tracker"),
            "humanity" to listOf("Humanity", "Человечность", "Humanity Tracker")
        )
        
        trackerPatterns.forEach { (trackerName, labels) ->
            labels.forEach { label ->
                // Формат 1: [X][X][ ][ ][ ] или [✓][✓][ ][ ][ ] или [■][■][ ][ ][ ]
                val pattern1 = Regex("$label\\s*[:=]?\\s*\\[([X✓■\\s]+)\\]", RegexOption.IGNORE_CASE)
                pattern1.findAll(fullText).forEach { match ->
                    val filled = match.groupValues[1].count { it in listOf('X', '✓', '■') }
                    if (filled > 0) {
                        stats["${trackerName}_current"] = filled
                        stats["${trackerName}_max"] = match.groupValues[1].length
                    }
                }
                
                // Формат 2: Health: 5/7 или Health: 5 из 7
                val pattern2 = Regex("$label\\s*[:=]?\\s*(\\d+)\\s*/\\s*(\\d+)", RegexOption.IGNORE_CASE)
                pattern2.findAll(fullText).forEach { match ->
                    val current = match.groupValues[1].toIntOrNull()
                    val max = match.groupValues[2].toIntOrNull()
                    if (current != null) stats["${trackerName}_current"] = current
                    if (max != null) stats["${trackerName}_max"] = max
                }
                
                // Формат 3: Health: [5] или Health: 5
                val pattern3 = Regex("$label\\s*[:=]?\\s*\\[?(\\d+)\\]?", RegexOption.IGNORE_CASE)
                pattern3.findAll(fullText).forEach { match ->
                    val value = match.groupValues[1].toIntOrNull()
                    if (value != null && value <= 10) { // Разумное ограничение для трекеров
                        stats["${trackerName}_value"] = value
                        if (stats["${trackerName}_current"] == null) {
                            stats["${trackerName}_current"] = value
                        }
                    }
                }
                
                // Формат 4: Поиск по строкам - если на строке есть label и числа/квадраты
                lines.forEach { line ->
                    if (line.contains(label, ignoreCase = true) && !line.contains(":", ignoreCase = true)) {
                        // Ищем заполненные квадраты на этой строке
                        val squaresPattern = Regex("\\[([X✓■\\s]+)\\]")
                        squaresPattern.findAll(line).forEach { squareMatch ->
                            val filled = squareMatch.groupValues[1].count { it in listOf('X', '✓', '■') }
                            val total = squareMatch.groupValues[1].length
                            if (filled > 0) {
                                stats["${trackerName}_current"] = filled
                                stats["${trackerName}_max"] = total
                            }
                        }
                    }
                }
            }
        }
        
        // Извлекаем специфичные поля VTM с правильным разделением секций
        val vtmFields = mapOf(
            // Основная информация
            "player" to listOf("Player", "Игрок"),
            "chronicle" to listOf("Chronicle", "Хроника"),
            "chronicle_tenets" to listOf("Chronicle Tenets", "Tenets", "Заповеди хроники"),
            "concept" to listOf("Concept", "Концепция"),
            "predator" to listOf("Predator", "Стиль охоты", "Predator Type"),
            "clan" to listOf("Clan", "Клан"),
            "generation" to listOf("Generation", "Поколение"),
            "sire" to listOf("Sire", "Создатель"),
            
            // Цели
            "ambition" to listOf("Ambition", "Амбиция"),
            "desire" to listOf("Desire", "Желание"),
            
            // Убеждения
            "convictions" to listOf("Convictions", "Conviction", "Убеждения"),
            "touchstones" to listOf("Touchstones", "Touchstone", "Якоря"),
            
            // Личность
            "mortal_identity" to listOf("Mortal Identity", "Mortal Name", "Смертная личность", "Mortal"),
            "mask" to listOf("Mask", "Маска", "Mask Identity"),
            
            // Котерия и домен
            "coterie" to listOf("Coterie", "Котерия", "Coterie Name"),
            "haven" to listOf("Haven", "Убежище", "Haven Location"),
            "domain" to listOf("Domain", "Домен", "Domain Location"),
            
            // Кровь
            "blood_potency" to listOf("Blood Potency", "Мощь крови", "Potency"),
            "blood_resonance" to listOf("Blood Resonance", "Резонанс крови", "Resonance"),
            "blood_surge" to listOf("Blood Surge", "Прилив крови", "Surge"),
            "mend_amount" to listOf("Mend Amount", "Восстановление", "Mend"),
            "power_bonus" to listOf("Power Bonus", "Бонус силы", "Power"),
            "rouse_re_roll" to listOf("Rouse Re-roll", "Повторный бросок пробуждения", "Rouse"),
            "feeding_penalty" to listOf("Feeding Penalty", "Штраф за кормление", "Feeding"),
            
            // Опыт
            "experience_total" to listOf("Experience Total", "Всего опыта", "Total XP"),
            "experience_spent" to listOf("Experience Spent", "Потрачено опыта", "Spent XP"),
            "experience_available" to listOf("Experience Available", "Доступно опыта", "Available XP"),
            
            // Секции с многострочным текстом - извлекаем по секциям
            "advantages" to listOf("Advantages", "Достоинства", "Merits"),
            "flaws" to listOf("Flaws", "Недостатки", "Flaw"),
            "merits" to listOf("Merits", "Достоинства", "Merit"),
            "notes" to listOf("Notes", "Заметки", "Note", "History", "История")
        )
        
        // Извлекаем простые поля (однострочные)
        vtmFields.filter { it.key !in listOf("advantages", "flaws", "merits", "notes", "convictions", "touchstones", "chronicle_tenets", "ambition", "desire") }.forEach { (key, patterns) ->
            patterns.forEach { pattern ->
                val regex = Regex("$pattern\\s*[:=]\\s*([^\\n\\r]{1,200})", RegexOption.IGNORE_CASE)
                regex.findAll(fullText).forEach { match ->
                    val value = match.groupValues[1].trim()
                    if (value.isNotBlank() && !value.matches(Regex("^\\d+$")) && value.length > 1) {
                        // Нормализуем ключ
                        val normalizedKey = key.lowercase().replace(" ", "_")
                        stats[normalizedKey] = value
                        stats[key] = value
                    }
                }
            }
        }
        
        // Извлекаем многострочные секции (достоинства, недостатки, заметки)
        extractMultilineSection(lines, fullText, "advantages", listOf("Advantages", "Достоинства", "Merits"), stats)
        extractMultilineSection(lines, fullText, "flaws", listOf("Flaws", "Недостатки", "Flaw"), stats)
        extractMultilineSection(lines, fullText, "merits", listOf("Merits", "Достоинства", "Merit"), stats)
        extractMultilineSection(lines, fullText, "notes", listOf("Notes", "Заметки", "Note", "History", "История"), stats)
        extractMultilineSection(lines, fullText, "convictions", listOf("Convictions", "Conviction", "Убеждения"), stats)
        extractMultilineSection(lines, fullText, "touchstones", listOf("Touchstones", "Touchstone", "Якоря"), stats)
        extractMultilineSection(lines, fullText, "chronicle_tenets", listOf("Chronicle Tenets", "Tenets", "Заповеди хроники"), stats)
        extractMultilineSection(lines, fullText, "ambition", listOf("Ambition", "Амбиция"), stats)
        extractMultilineSection(lines, fullText, "desire", listOf("Desire", "Желание"), stats)
    }
    
    /**
     * Извлекает многострочную секцию из текста
     */
    private fun extractMultilineSection(lines: List<String>, fullText: String, key: String, labels: List<String>, stats: MutableMap<String, Any>) {
        labels.forEach { label ->
            // Ищем начало секции
            var sectionStart = -1
            lines.forEachIndexed { index, line ->
                val trimmedLine = line.trim()
                // Ищем строку, которая содержит метку и двоеточие/равно
                if (trimmedLine.contains(label, ignoreCase = true) && 
                    (trimmedLine.contains(":") || trimmedLine.contains("=")) &&
                    !trimmedLine.matches(Regex(".*\\d+\\s*[:=].*"))) { // Исключаем строки типа "Health: 5"
                    sectionStart = index
                }
            }
            
            if (sectionStart >= 0) {
                // Извлекаем текст после метки до следующей секции
                val sectionPattern = Regex("$label\\s*[:=]\\s*([^\\n\\r]+)", RegexOption.IGNORE_CASE)
                val match = sectionPattern.find(lines[sectionStart])
                if (match != null) {
                    val startValue = match.groupValues[1].trim()
                    val sectionText = StringBuilder(startValue)
                    
                    // Собираем следующие строки до следующей секции или пустой строки
                    var i = sectionStart + 1
                    // Список меток других секций, которые должны остановить сбор текста
                    val nextSections = listOf(
                        "Advantages", "Достоинства", "Merits", "Достоинства",
                        "Flaws", "Недостатки",
                        "Notes", "Заметки", "History", "История",
                        "Blood Potency", "Мощь крови", "Potency",
                        "Experience", "Опыт", "XP",
                        "Convictions", "Убеждения",
                        "Touchstones", "Якоря",
                        "Chronicle Tenets", "Заповеди хроники",
                        "Ambition", "Амбиция",
                        "Desire", "Желание"
                    )
                    
                    while (i < lines.size && i < sectionStart + 15) { // Увеличиваем до 15 строк
                        val nextLine = lines[i].trim()
                        if (nextLine.isBlank()) {
                            // Если пустая строка после начала секции, продолжаем
                            if (sectionText.isNotEmpty()) {
                                i++
                                continue
                            } else {
                                break
                            }
                        }
                        // Проверяем, не началась ли следующая секция
                        if (nextSections.any { nextLine.contains(it, ignoreCase = true) && 
                            (nextLine.contains(":") || nextLine.contains("=")) &&
                            !nextLine.matches(Regex(".*\\d+\\s*[:=].*")) }) {
                            break
                        }
                        // Добавляем строку, если она не пустая и не является меткой другого поля
                        if (nextLine.length > 2 && !nextLine.matches(Regex("^[А-Яа-яA-Z][^:]*[:=]\\s*\\d+$"))) {
                            sectionText.append("\n").append(nextLine)
                        }
                        i++
                    }
                    
                    val value = sectionText.toString().trim()
                    // Очищаем от возможных остатков других полей в конце
                    val cleaned = value.split(Regex("\\s*(?:Advantages|Достоинства|Flaws|Недостатки|Merits|Notes|Заметки|History|История|Blood Potency|Мощь крови|Experience|Опыт|Convictions|Убеждения|Touchstones|Якоря|Chronicle Tenets|Заповеди хроники|Ambition|Амбиция|Desire|Желание)\\s*[:=]")).firstOrNull()?.trim()
                    
                    if (cleaned != null && cleaned.isNotBlank() && cleaned.length > 2) {
                        stats[key] = cleaned
                        android.util.Log.d("CharacterSheetsFragment", "Extracted $key: ${cleaned.take(100)}...")
                    }
                }
            }
            
            // Также ищем в полном тексте с более широким паттерном (резервный метод)
            if (!stats.containsKey(key)) {
                val widePattern = Regex("$label\\s*[:=]\\s*([^\\n\\r]{10,500})", RegexOption.IGNORE_CASE)
                widePattern.findAll(fullText).forEach { match ->
                    val value = match.groupValues[1].trim()
                    if (value.isNotBlank() && value.length > 2) {
                        // Очищаем от лишних полей - останавливаемся на следующей секции
                        val stopPattern = Regex("(?:Advantages|Достоинства|Flaws|Недостатки|Merits|Notes|Заметки|History|История|Blood Potency|Мощь крови|Experience|Опыт|Convictions|Убеждения|Touchstones|Якоря|Chronicle Tenets|Заповеди хроники|Ambition|Амбиция|Desire|Желание)\\s*[:=]")
                        val cleaned = stopPattern.split(value).firstOrNull()?.trim()
                        if (cleaned != null && cleaned.isNotBlank() && cleaned.length > 2) {
                            stats[key] = cleaned
                            android.util.Log.d("CharacterSheetsFragment", "Extracted $key (wide pattern): ${cleaned.take(100)}...")
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Извлекает специфичные для D&D 5e поля
     */
    private fun extractDndSpecificFields(stats: MutableMap<String, Any>, lines: List<String>, fullText: String) {
        // Извлекаем Saving Throws
        val savingThrows = listOf("Strength", "Dexterity", "Constitution", "Intelligence", "Wisdom", "Charisma")
        savingThrows.forEach { attr ->
            val pattern = Regex("$attr\\s+Saving\\s+Throw\\s*[:=]?\\s*([+-]?\\d+)", RegexOption.IGNORE_CASE)
            pattern.findAll(fullText).forEach { match ->
                val value = match.groupValues[1].toIntOrNull()
                if (value != null) {
                    stats["${attr} Saving Throw"] = value
                }
            }
        }
        
        // Извлекаем Skill Proficiencies
        val skillProficiencies = listOf(
            "Acrobatics", "Animal Handling", "Arcana", "Athletics", "Deception", "History",
            "Insight", "Intimidation", "Investigation", "Medicine", "Nature", "Perception",
            "Performance", "Persuasion", "Religion", "Sleight of Hand", "Stealth", "Survival"
        )
        skillProficiencies.forEach { skill ->
            val pattern = Regex("$skill\\s*[:=]?\\s*([+-]?\\d+)\\s*(?:✓|X|\\*)", RegexOption.IGNORE_CASE)
            pattern.findAll(fullText).forEach { match ->
                val value = match.groupValues[1].toIntOrNull()
                if (value != null) {
                    stats["${skill} Proficiency"] = value
                }
            }
        }
        
        // Извлекаем Ability Scores и Modifiers
        val abilities = listOf("STR", "DEX", "CON", "INT", "WIS", "CHA")
        abilities.forEach { abbr ->
            val pattern = Regex("$abbr\\s*[:=]?\\s*(\\d+)\\s*\\(([+-]?\\d+)\\)", RegexOption.IGNORE_CASE)
            pattern.findAll(fullText).forEach { match ->
                val score = match.groupValues[1].toIntOrNull()
                val modifier = match.groupValues[2].toIntOrNull()
                if (score != null) stats["${abbr} Score"] = score
                if (modifier != null) stats["${abbr} Modifier"] = modifier
            }
        }
    }
    
    /**
     * Извлекает специфичные для WHRP поля
     */
    private fun extractWhrpSpecificFields(stats: MutableMap<String, Any>, lines: List<String>, fullText: String) {
        // Извлекаем Wound Tracker (может быть в формате "[X][X][ ][ ][ ]")
        val woundPattern = Regex("Wounds?\\s*[:=]?\\s*\\[([X\\s]+)\\]", RegexOption.IGNORE_CASE)
        woundPattern.findAll(fullText).forEach { match ->
            val woundLevels = match.groupValues[1].count { it == 'X' }
            if (woundLevels > 0) {
                stats["Wound Levels Filled"] = woundLevels
            }
        }
        
        // Извлекаем Career Advances
        val careerPattern = Regex("Career\\s+Advance\\s*[:=]?\\s*([^\\n\\r]+)", RegexOption.IGNORE_CASE)
        careerPattern.findAll(fullText).forEach { match ->
            val value = match.groupValues[1].trim()
            if (value.isNotBlank()) {
                stats["Career Advances"] = value
            }
        }
        
        // Извлекаем Talents и Traits (могут быть списками)
        val talentPattern = Regex("Talent[s]?\\s*[:=]\\s*([^\\n\\r]{1,500})", RegexOption.IGNORE_CASE)
        talentPattern.findAll(fullText).forEach { match ->
            val value = match.groupValues[1].trim()
            if (value.isNotBlank()) {
                stats["Talents List"] = value
            }
        }
        
        val traitPattern = Regex("Trait[s]?\\s*[:=]\\s*([^\\n\\r]{1,500})", RegexOption.IGNORE_CASE)
        traitPattern.findAll(fullText).forEach { match ->
            val value = match.groupValues[1].trim()
            if (value.isNotBlank()) {
                stats["Traits List"] = value
            }
        }
        
        // Извлекаем Money (может быть в формате "Xgc Xs Xp")
        val moneyPattern = Regex("(?:Money|Gold|Crowns)\\s*[:=]?\\s*(\\d+)\\s*(?:gc|GC|gold|crowns)?\\s*(?:\\s*(\\d+)\\s*(?:s|S|shillings)?)?\\s*(?:\\s*(\\d+)\\s*(?:p|P|pennies)?)?", RegexOption.IGNORE_CASE)
        moneyPattern.findAll(fullText).forEach { match ->
            val gold = match.groupValues[1].toIntOrNull() ?: 0
            val silver = match.groupValues[2].toIntOrNull() ?: 0
            val copper = match.groupValues[3].toIntOrNull() ?: 0
            if (gold > 0 || silver > 0 || copper > 0) {
                stats["Money Gold"] = gold
                stats["Money Silver"] = silver
                stats["Money Copper"] = copper
            }
        }
        
        // Извлекаем Armour по частям тела
        val armourPatterns = mapOf(
            "Armour Head" to listOf("Head", "Helmet", "Голова"),
            "Armour Body" to listOf("Body", "Torso", "Тело"),
            "Armour Arms" to listOf("Arms", "Hands", "Руки"),
            "Armour Legs" to listOf("Legs", "Feet", "Ноги")
        )
        
        armourPatterns.forEach { (key, patterns) ->
            patterns.forEach { pattern ->
                val regex = Regex("(?:Armour|Armor)\\s+$pattern\\s*[:=]?\\s*(\\d+)", RegexOption.IGNORE_CASE)
                regex.findAll(fullText).forEach { match ->
                    val value = match.groupValues[1].toIntOrNull()
                    if (value != null) {
                        stats[key] = value
                    }
                }
            }
        }
    }
    
    /**
     * Извлекает специфичные для WH: Dark Heresy поля
     */
    private fun extractDarkHeresySpecificFields(stats: MutableMap<String, Any>, lines: List<String>, fullText: String) {
        // Извлекаем Wound Tracker
        val woundPattern = Regex("Wounds?\\s*[:=]?\\s*\\[([X\\s]+)\\]", RegexOption.IGNORE_CASE)
        woundPattern.findAll(fullText).forEach { match ->
            val woundLevels = match.groupValues[1].count { it == 'X' }
            if (woundLevels > 0) {
                stats["Wound Levels Filled"] = woundLevels
            }
        }
        
        // Извлекаем Insanity Tracker
        val insanityPattern = Regex("Insanity\\s*[:=]?\\s*\\[([X\\s]+)\\]", RegexOption.IGNORE_CASE)
        insanityPattern.findAll(fullText).forEach { match ->
            val insanityLevels = match.groupValues[1].count { it == 'X' }
            if (insanityLevels > 0) {
                stats["Insanity Levels Filled"] = insanityLevels
            }
        }
        
        // Извлекаем Corruption Tracker
        val corruptionPattern = Regex("Corruption\\s*[:=]?\\s*\\[([X\\s]+)\\]", RegexOption.IGNORE_CASE)
        corruptionPattern.findAll(fullText).forEach { match ->
            val corruptionLevels = match.groupValues[1].count { it == 'X' }
            if (corruptionLevels > 0) {
                stats["Corruption Levels Filled"] = corruptionLevels
            }
        }
        
        // Извлекаем Psychic Powers
        val psychicPattern = Regex("Psychic\\s+Power[s]?\\s*[:=]\\s*([^\\n\\r]{1,500})", RegexOption.IGNORE_CASE)
        psychicPattern.findAll(fullText).forEach { match ->
            val value = match.groupValues[1].trim()
            if (value.isNotBlank()) {
                stats["Psychic Powers List"] = value
            }
        }
        
        // Извлекаем Talents
        val talentPattern = Regex("Talent[s]?\\s*[:=]\\s*([^\\n\\r]{1,500})", RegexOption.IGNORE_CASE)
        talentPattern.findAll(fullText).forEach { match ->
            val value = match.groupValues[1].trim()
            if (value.isNotBlank()) {
                stats["Talents List"] = value
            }
        }
        
        // Извлекаем Traits
        val traitPattern = Regex("Trait[s]?\\s*[:=]\\s*([^\\n\\r]{1,500})", RegexOption.IGNORE_CASE)
        traitPattern.findAll(fullText).forEach { match ->
            val value = match.groupValues[1].trim()
            if (value.isNotBlank()) {
                stats["Traits List"] = value
            }
        }
        
        // Извлекаем Malignancies
        val malignancyPattern = Regex("Malignanc[y|ies]\\s*[:=]\\s*([^\\n\\r]{1,500})", RegexOption.IGNORE_CASE)
        malignancyPattern.findAll(fullText).forEach { match ->
            val value = match.groupValues[1].trim()
            if (value.isNotBlank()) {
                stats["Malignancies List"] = value
            }
        }
        
        // Извлекаем Mutations
        val mutationPattern = Regex("Mutation[s]?\\s*[:=]\\s*([^\\n\\r]{1,500})", RegexOption.IGNORE_CASE)
        mutationPattern.findAll(fullText).forEach { match ->
            val value = match.groupValues[1].trim()
            if (value.isNotBlank()) {
                stats["Mutations List"] = value
            }
        }
        
        // Извлекаем Mental Disorders
        val disorderPattern = Regex("(?:Mental\\s+)?Disorder[s]?\\s*[:=]\\s*([^\\n\\r]{1,500})", RegexOption.IGNORE_CASE)
        disorderPattern.findAll(fullText).forEach { match ->
            val value = match.groupValues[1].trim()
            if (value.isNotBlank()) {
                stats["Mental Disorders List"] = value
            }
        }
        
        // Извлекаем Forbidden Lore
        val forbiddenLorePattern = Regex("Forbidden\\s+Lore\\s*[:=]\\s*([^\\n\\r]{1,500})", RegexOption.IGNORE_CASE)
        forbiddenLorePattern.findAll(fullText).forEach { match ->
            val value = match.groupValues[1].trim()
            if (value.isNotBlank()) {
                stats["Forbidden Lore List"] = value
            }
        }
        
        // Извлекаем Scholastic Lore
        val scholasticLorePattern = Regex("Scholastic\\s+Lore\\s*[:=]\\s*([^\\n\\r]{1,500})", RegexOption.IGNORE_CASE)
        scholasticLorePattern.findAll(fullText).forEach { match ->
            val value = match.groupValues[1].trim()
            if (value.isNotBlank()) {
                stats["Scholastic Lore List"] = value
            }
        }
        
        // Извлекаем Common Lore
        val commonLorePattern = Regex("Common\\s+Lore\\s*[:=]\\s*([^\\n\\r]{1,500})", RegexOption.IGNORE_CASE)
        commonLorePattern.findAll(fullText).forEach { match ->
            val value = match.groupValues[1].trim()
            if (value.isNotBlank()) {
                stats["Common Lore List"] = value
            }
        }
        
        // Извлекаем Thrones (деньги)
        val thronesPattern = Regex("(?:Thrones?|Throne\\s+Gelt)\\s*[:=]?\\s*(\\d+)", RegexOption.IGNORE_CASE)
        thronesPattern.findAll(fullText).forEach { match ->
            val value = match.groupValues[1].toIntOrNull()
            if (value != null) {
                stats["Thrones"] = value
            }
        }
        
        // Извлекаем Armour по частям тела
        val armourPatterns = mapOf(
            "Armour Head" to listOf("Head", "Helmet", "Голова"),
            "Armour Body" to listOf("Body", "Torso", "Тело"),
            "Armour Arms" to listOf("Arms", "Hands", "Руки"),
            "Armour Legs" to listOf("Legs", "Feet", "Ноги")
        )
        
        armourPatterns.forEach { (key, patterns) ->
            patterns.forEach { pattern ->
                val regex = Regex("(?:Armour|Armor)\\s+$pattern\\s*[:=]?\\s*(\\d+)", RegexOption.IGNORE_CASE)
                regex.findAll(fullText).forEach { match ->
                    val value = match.groupValues[1].toIntOrNull()
                    if (value != null) {
                        stats[key] = value
                    }
                }
            }
        }
    }
    
    private fun parsePdfContent(inputStream: InputStream): Map<String, Any> {
        // This is a basic implementation
        // For production, you'd want to use a proper PDF parsing library
        // and extract structured data based on the character sheet format
        var document: com.tom_roush.pdfbox.pdmodel.PDDocument? = null
        return try {
            // Try to read text from PDF using PDFBox
            document = try {
                com.tom_roush.pdfbox.pdmodel.PDDocument.load(inputStream)
            } catch (e: OutOfMemoryError) {
                android.util.Log.e("CharacterSheetsFragment", "OutOfMemoryError loading PDF: ${e.message}", e)
                throw e
            } catch (e: Exception) {
                android.util.Log.e("CharacterSheetsFragment", "Error loading PDF document: ${e.message}", e)
                throw e
            }
            
            val stripper = try {
                val customStripper = com.tom_roush.pdfbox.text.PDFTextStripper()
                // Улучшаем разделение строк - добавляем больше пробелов между словами
                customStripper.setParagraphStart("\n")
                customStripper.setParagraphEnd("\n")
                customStripper.setPageStart("\n")
                customStripper.setPageEnd("\n")
                customStripper.setLineSeparator("\n")
                customStripper.setWordSeparator(" ")
                customStripper.setSuppressDuplicateOverlappingText(true)
                customStripper
            } catch (e: Exception) {
                android.util.Log.e("CharacterSheetsFragment", "Error creating PDFTextStripper: ${e.message}", e)
                document?.close()
                throw e
            }
            
            val text = try {
                stripper.getText(document)
            } catch (e: Exception) {
                android.util.Log.e("CharacterSheetsFragment", "Error extracting text from PDF: ${e.message}", e)
                document?.close()
                throw e
            }
            
            // Дополнительная обработка текста для лучшего разделения строк
            // Заменяем множественные пробелы на один, но сохраняем переносы строк
            val processedText = text
                .replace(Regex("([^\\n])\\s{2,}([^\\n])"), "$1 $2") // Множественные пробелы в одной строке
                .replace(Regex("([A-ZА-Я])([a-zа-я])"), "$1 $2") // Разделяем слипшиеся слова (заглавная+строчная)
                .replace(Regex("([a-zа-я])([A-ZА-Я])"), "$1 $2") // Разделяем слипшиеся слова (строчная+заглавная)
                .replace(Regex("([0-9])([A-ZА-Яa-zа-я])"), "$1 $2") // Разделяем число и букву
                .replace(Regex("([A-ZА-Яa-zа-я])([0-9])"), "$1 $2") // Разделяем букву и число
            
            document.close()
            document = null
            
            android.util.Log.d("CharacterSheetsFragment", "PDF text extracted, length: ${processedText.length} characters")
            
            // ПОЛНЫЙ ПАРСИНГ - извлекаем ВСЕ данные из PDF
            val lines = processedText.lines()
            val parsedData = mutableMapOf<String, Any>()
            
            // 1. Извлекаем имя персонажа
            val nameLine = lines.firstOrNull { it.contains("Name", ignoreCase = true) || it.contains("Имя", ignoreCase = true) }
            parsedData["name"] = nameLine?.substringAfter(":")?.trim()?.substringAfter(" ")?.trim() ?: "Безымянный персонаж"
            
            // 2. Определяем систему
            parsedData["system"] = when {
                processedText.contains("D&D", ignoreCase = true) || processedText.contains("Dungeons", ignoreCase = true) || processedText.contains("DnD", ignoreCase = true) -> "dnd_5e"
                processedText.contains("Vampire", ignoreCase = true) || processedText.contains("VTM", ignoreCase = true) || processedText.contains("VtM", ignoreCase = true) -> "vtm_5e"
                processedText.contains("Viedzmin", ignoreCase = true) || processedText.contains("Ведьмак", ignoreCase = true) -> "viedzmin_2e"
                processedText.contains("dark heresy", ignoreCase = true) ||
                processedText.contains("imperium", ignoreCase = true) ||
                processedText.contains("inquisition", ignoreCase = true) ||
                processedText.contains("acolyte", ignoreCase = true) ||
                processedText.contains("throne agent", ignoreCase = true) -> "wh_darkheresy"
                processedText.contains("warhammer fantasy roleplay", ignoreCase = true) ||
                processedText.contains("whrp", ignoreCase = true) ||
                processedText.contains("warhammer roleplay", ignoreCase = true) ||
                (processedText.contains("career", ignoreCase = true) && processedText.contains("advance", ignoreCase = true)) ||
                processedText.contains("fate points", ignoreCase = true) ||
                processedText.contains("fortune points", ignoreCase = true) -> "whrp"
                else -> "unknown"
            }
            
            // 3. Извлекаем ВСЕ пары ключ-значение (универсальный паттерн)
            val allKeyValuePairs = mutableMapOf<String, Any>()
            val keyValuePattern = Regex("([А-Яа-яA-Za-z\\s]+?)\\s*[:=]\\s*([^\\n]+)", RegexOption.IGNORE_CASE)
            lines.forEach { line ->
                keyValuePattern.findAll(line).forEach { match ->
                    val key = match.groupValues[1].trim()
                    val value = match.groupValues[2].trim()
                    if (key.isNotBlank() && value.isNotBlank() && key.length < 100) {
                        // Нормализуем ключ
                        val normalizedKey = key.lowercase().replace(" ", "_").replace("-", "_")
                        // Пытаемся определить тип значения
                        val processedValue = when {
                            value.toIntOrNull() != null -> value.toInt()
                            value.toDoubleOrNull() != null -> value.toDouble()
                            value.equals("true", ignoreCase = true) || value.equals("да", ignoreCase = true) -> true
                            value.equals("false", ignoreCase = true) || value.equals("нет", ignoreCase = true) -> false
                            else -> value
                        }
                        allKeyValuePairs[normalizedKey] = processedValue
                        // Также сохраняем с оригинальным ключом
                        allKeyValuePairs[key] = processedValue
                    }
                }
            }
            
            // 4. Извлекаем все числовые значения с их контекстом
            val numericValues = mutableMapOf<String, Number>()
            val numericPattern = Regex("([А-Яа-яA-Za-z\\s]+?)\\s*(\\d+)", RegexOption.IGNORE_CASE)
            lines.forEach { line ->
                numericPattern.findAll(line).forEach { match ->
                    val label = match.groupValues[1].trim()
                    val number = match.groupValues[2].toIntOrNull()
                    if (label.isNotBlank() && number != null && label.length < 50) {
                        val normalizedLabel = label.lowercase().replace(" ", "_")
                        numericValues[normalizedLabel] = number
                        numericValues[label] = number
                    }
                }
            }
            
            // 5. Извлекаем атрибуты (специфичные для систем)
            val attributes = mutableMapOf<String, Int>()
            val skills = mutableMapOf<String, Int>()
            val disciplines = mutableMapOf<String, Int>() // VTM дисциплины
            val stats = mutableMapOf<String, Any>()
            
            // Атрибуты D&D 5e (полный список)
            val dndAttributes = listOf(
                "Strength", "Dexterity", "Constitution", "Intelligence", "Wisdom", "Charisma",
                "Сила", "Ловкость", "Выносливость", "Интеллект", "Мудрость", "Харизма",
                "STR", "DEX", "CON", "INT", "WIS", "CHA"
            )
            extractAttributes(dndAttributes, attributes, lines, processedText)
            
            // Атрибуты VTM 5e (ПОЛНЫЙ СПИСОК - все категории)
            // Mental Attributes
            val vtmMentalAttributes = listOf(
                "Intelligence", "Wits", "Resolve",
                "Интеллект", "Сообразительность", "Решимость",
                "INT", "WIT", "RES"
            )
            // Physical Attributes
            val vtmPhysicalAttributes = listOf(
                "Strength", "Dexterity", "Stamina",
                "Сила", "Ловкость", "Выносливость",
                "STR", "DEX", "STA"
            )
            // Social Attributes
            val vtmSocialAttributes = listOf(
                "Presence", "Manipulation", "Composure",
                "Присутствие", "Манипуляция", "Самообладание",
                "PRE", "MAN", "COM"
            )
            extractAttributes(vtmMentalAttributes + vtmPhysicalAttributes + vtmSocialAttributes, attributes, lines, processedText)
            
            // Атрибуты WHRP (Warhammer Fantasy Roleplay)
            val whrpAttributes = listOf(
                "Weapon Skill", "Ballistic Skill", "Strength", "Toughness", "Initiative", "Agility", "Dexterity", "Intelligence", "Willpower", "Fellowship",
                "WS", "BS", "S", "T", "I", "Ag", "Dex", "Int", "WP", "Fel",
                "Навык владения оружием", "Навык стрельбы", "Сила", "Выносливость", "Инициатива", "Ловкость", "Интеллект", "Сила воли", "Общительность",
                "Мощь", "Точность", "Стойкость", "Реакция", "Интеллект", "Воля", "Харизма"
            )
            extractAttributes(whrpAttributes, attributes, lines, processedText)
            
            // Атрибуты WH: Dark Heresy
            val darkHeresyAttributes = listOf(
                "Weapon Skill", "Ballistic Skill", "Strength", "Toughness", "Agility", "Intelligence", "Perception", "Willpower", "Fellowship",
                "WS", "BS", "S", "T", "Ag", "Int", "Per", "WP", "Fel",
                "Навык владения оружием", "Навык стрельбы", "Сила", "Выносливость", "Ловкость", "Интеллект", "Восприятие", "Сила воли", "Общительность",
                "Мощь", "Точность", "Стойкость", "Реакция", "Интеллект", "Восприятие", "Воля", "Харизма"
            )
            extractAttributes(darkHeresyAttributes, attributes, lines, processedText)
            
            // 6. Извлекаем навыки (ПОЛНЫЙ СПИСОК для всех систем)
            // D&D 5e Skills
            val dndSkills = listOf(
                "Acrobatics", "Animal Handling", "Arcana", "Athletics", "Deception", "History",
                "Insight", "Intimidation", "Investigation", "Medicine", "Nature", "Perception",
                "Performance", "Persuasion", "Religion", "Sleight of Hand", "Stealth", "Survival",
                "Акробатика", "Уход за животными", "Магия", "Атлетика", "Обман", "История",
                "Проницательность", "Запугивание", "Расследование", "Медицина", "Природа", "Восприятие",
                "Выступление", "Убеждение", "Религия", "Ловкость рук", "Скрытность", "Выживание"
            )
            
            // VTM 5e Skills - Mental
            val vtmMentalSkills = listOf(
                "Academics", "Awareness", "Finance", "Investigation", "Medicine", "Occult", "Politics", "Science", "Technology",
                "Академические знания", "Внимательность", "Финансы", "Расследование", "Медицина", "Оккультизм", "Политика", "Наука", "Технологии"
            )
            
            // VTM 5e Skills - Physical
            val vtmPhysicalSkills = listOf(
                "Athletics", "Brawl", "Craft", "Drive", "Firearms", "Larceny", "Melee", "Stealth", "Survival",
                "Атлетика", "Рукопашный бой", "Ремесло", "Вождение", "Стрельба", "Воровство", "Холодное оружие", "Скрытность", "Выживание"
            )
            
            // VTM 5e Skills - Social
            val vtmSocialSkills = listOf(
                "Animal Ken", "Etiquette", "Insight", "Intimidation", "Leadership", "Performance", "Persuasion", "Streetwise", "Subterfuge",
                "Общение с животными", "Этикет", "Проницательность", "Запугивание", "Лидерство", "Выступление", "Убеждение", "Уличное чутье", "Скрытность"
            )
            
            // WHRP Skills (Warhammer Fantasy Roleplay)
            val whrpSkills = listOf(
                // Basic Skills
                "Animal Care", "Art", "Athletics", "Bribery", "Charm", "Climb", "Consume Alcohol", "Cool", "Dodge", "Drive", "Endurance", "Entertain", "Gamble", "Gossip", "Haggle", "Heal", "Intimidate", "Intuition", "Language", "Leadership", "Melee", "Navigation", "Outdoor Survival", "Perception", "Ride", "Row", "Stealth", "Swim",
                // Advanced Skills
                "Animal Training", "Artistic", "Channelling", "Charm Animal", "Command", "Commerce", "Evaluate", "Folklore", "Gamble", "Guile", "Haggle", "Heal", "Intimidate", "Intuition", "Language", "Leadership", "Lore", "Melee", "Navigation", "Outdoor Survival", "Perception", "Perform", "Play", "Pray", "Ranged", "Research", "Sail", "Secret Signs", "Set Trap", "Sleight of Hand", "Stealth", "Swim", "Track", "Trade", "Ventriloquism",
                // Русские названия
                "Уход за животными", "Искусство", "Атлетика", "Взятка", "Обаяние", "Лазание", "Употребление алкоголя", "Хладнокровие", "Уклонение", "Вождение", "Выносливость", "Развлечение", "Азартные игры", "Сплетни", "Торговля", "Лечение", "Запугивание", "Интуиция", "Язык", "Лидерство", "Ближний бой", "Навигация", "Выживание", "Восприятие", "Верховая езда", "Гребля", "Скрытность", "Плавание"
            )
            
            // WH: Dark Heresy Skills
            val darkHeresySkills = listOf(
                // Basic Skills
                "Awareness", "Barter", "Carouse", "Charm", "Climb", "Concealment", "Contortionist", "Deceive", "Disguise", "Dodge", "Drive", "Evaluate", "Gamble", "Inquiry", "Interrogation", "Intimidate", "Logic", "Scrutiny", "Search", "Silent Move", "Speak Language", "Swim", "Trade",
                // Advanced Skills
                "Acrobatics", "Awareness", "Barter", "Blather", "Carouse", "Charm", "Chem-Use", "Ciphers", "Climb", "Command", "Commerce", "Common Lore", "Concealment", "Contortionist", "Deceive", "Demolition", "Disguise", "Dodge", "Drive", "Evaluate", "Forbidden Lore", "Gamble", "Inquiry", "Interrogation", "Intimidate", "Literacy", "Logic", "Medicae", "Navigate", "Operate", "Parry", "Performer", "Pilot", "Psyniscience", "Scholastic Lore", "Scrutiny", "Search", "Secret Tongue", "Security", "Shadowing", "Silent Move", "Sleight of Hand", "Speak Language", "Survival", "Swim", "Tech-Use", "Trade", "Tracking", "Wrangling",
                // Русские названия
                "Внимательность", "Торговля", "Пьянство", "Обаяние", "Лазание", "Скрытие", "Акробатика", "Обман", "Маскировка", "Уклонение", "Вождение", "Оценка", "Азартные игры", "Допрос", "Запугивание", "Логика", "Осмотр", "Поиск", "Тихий шаг", "Языки", "Плавание", "Ремесло"
            )
            
            val allSkills = dndSkills + vtmMentalSkills + vtmPhysicalSkills + vtmSocialSkills + whrpSkills + darkHeresySkills
            extractSkills(allSkills, skills, lines, processedText)
            
            // 7. Извлекаем дисциплины VTM 5e
            val vtmDisciplines = listOf(
                "Animalism", "Auspex", "Blood Sorcery", "Celerity", "Dominate", "Fortitude",
                "Obfuscate", "Potence", "Presence", "Protean", "Thin-Blood Alchemy",
                "Animalism", "Ауспекс", "Кровавая магия", "Целерити", "Доминат", "Фортитуд",
                "Обфускат", "Потенс", "Презенс", "Протей", "Алхимия тонкокровых",
                "Animalism", "Auspex", "Blood Sorcery", "Celerity", "Dominate", "Fortitude",
                "Obfuscate", "Potence", "Presence", "Protean", "Thin-Blood Alchemy"
            )
            extractSkills(vtmDisciplines, disciplines, lines, processedText)
            
            // 8. Извлекаем статистики (расширенный список для всех систем)
            val statPatterns = mapOf(
                // D&D 5e
                "HP" to listOf("HP", "Hit Points", "Здоровье", "ХП", "Health", "Hit Points Maximum", "Максимум здоровья"),
                "AC" to listOf("AC", "Armor Class", "Класс защиты", "КЗ", "Armor", "Armor Class (AC)"),
                "Level" to listOf("Level", "Уровень", "Lvl", "Character Level"),
                "Class" to listOf("Class", "Класс", "Character Class"),
                "Race" to listOf("Race", "Раса", "Species"),
                "Background" to listOf("Background", "Предыстория", "Character Background"),
                "Proficiency Bonus" to listOf("Proficiency Bonus", "Бонус мастерства", "Prof Bonus", "PB"),
                "Initiative" to listOf("Initiative", "Инициатива", "Init"),
                "Speed" to listOf("Speed", "Скорость", "Movement", "Movement Speed"),
                "Experience Points" to listOf("Experience Points", "XP", "Опыт", "Experience"),
                
                // VTM 5e
                "Humanity" to listOf("Humanity", "Humanity Rating", "Человечность", "Humanity/Path", "Humanity Path"),
                "Willpower" to listOf("Willpower", "Willpower Rating", "Сила воли", "Willpower Maximum", "Максимум силы воли"),
                "Blood Potency" to listOf("Blood Potency", "Кровная мощь", "Blood Potency Rating", "BP"),
                "Clan" to listOf("Clan", "Клан", "Clan/Bloodline"),
                "Concept" to listOf("Concept", "Концепция", "Character Concept"),
                "Predator" to listOf("Predator", "Стиль охоты", "Predator Type", "Predator Type/Feeding", "Predator Type/Feeding Preference"),
                "Generation" to listOf("Generation", "Поколение", "Gen", "Generation/Blood"),
                "Sire" to listOf("Sire", "Сир", "Sire Name"),
                "Chronicle" to listOf("Chronicle", "Хроника", "Chronicle Name"),
                "Ambition" to listOf("Ambition", "Амбиция", "Character Ambition"),
                "Desire" to listOf("Desire", "Желание", "Character Desire"),
                "Touchstones" to listOf("Touchstones", "Якоря", "Touchstone", "Touchstone Name"),
                "Chronicle Tenets" to listOf("Chronicle Tenets", "Заповеди хроники", "Tenets"),
                "Convictions" to listOf("Convictions", "Убеждения", "Conviction"),
                "Stains" to listOf("Stains", "Пятна", "Stain"),
                "Health" to listOf("Health", "Здоровье", "Health Maximum", "Health Tracker", "Health Levels"),
                "Hunger" to listOf("Hunger", "Голод", "Hunger Rating", "Hunger Tracker"),
                "Resonance" to listOf("Resonance", "Резонанс", "Resonance Type"),
                "Advantages" to listOf("Advantages", "Преимущества", "Advantage"),
                "Flaws" to listOf("Flaws", "Недостатки", "Flaw"),
                "Merits" to listOf("Merits", "Достоинства", "Merit"),
                "Loresheet" to listOf("Loresheet", "Лоршит", "Loresheet Name"),
                "Coterie" to listOf("Coterie", "Котерия", "Coterie Name"),
                "Haven" to listOf("Haven", "Убежище", "Haven Location"),
                "Resources" to listOf("Resources", "Ресурсы", "Resource"),
                "Allies" to listOf("Allies", "Союзники", "Ally"),
                "Contacts" to listOf("Contacts", "Контакты", "Contact"),
                "Fame" to listOf("Fame", "Слава", "Fame Rating"),
                "Herd" to listOf("Herd", "Стадо", "Herd Rating"),
                "Influence" to listOf("Influence", "Влияние", "Influence Rating"),
                "Mask" to listOf("Mask", "Маска", "Mask Identity"),
                "Mortal Identity" to listOf("Mortal Identity", "Смертная личность", "Mortal Name"),
                "Retainers" to listOf("Retainers", "Слуги", "Retainer"),
                "Status" to listOf("Status", "Статус", "Status Rating"),
                "Domain" to listOf("Domain", "Домен", "Domain Location"),
                
                // Viedzmin 2e
                "Profession" to listOf("Profession", "Профессия", "Character Profession"),
                "Race" to listOf("Race", "Раса", "Character Race"),
                "Age" to listOf("Age", "Возраст", "Character Age"),
                "Height" to listOf("Height", "Рост", "Character Height"),
                "Weight" to listOf("Weight", "Вес", "Character Weight"),
                "Hair" to listOf("Hair", "Волосы", "Hair Color"),
                "Eyes" to listOf("Eyes", "Глаза", "Eye Color"),
                
                // WHRP (Warhammer Fantasy Roleplay)
                "Career" to listOf("Career", "Профессия", "Career Path", "Career Class"),
                "Career Level" to listOf("Career Level", "Уровень профессии", "Career Rank", "Level"),
                "Wounds" to listOf("Wounds", "Раны", "Wound Points", "WP", "Wound Maximum", "Максимум ран"),
                "Fate Points" to listOf("Fate Points", "Очки судьбы", "Fate", "FP"),
                "Fortune Points" to listOf("Fortune Points", "Очки удачи", "Fortune", "Fortune"),
                "Resilience" to listOf("Resilience", "Устойчивость", "Resilience Bonus"),
                "Resolve" to listOf("Resolve", "Решимость", "Resolve Points"),
                "Advancement" to listOf("Advancement", "Развитие", "Advancement Points", "AP", "Experience Points", "XP"),
                "Movement" to listOf("Movement", "Движение", "Move", "Movement Rate", "Скорость движения"),
                "Armour Points" to listOf("Armour Points", "Очки брони", "Armour", "AP", "Armour Rating"),
                "Armour Head" to listOf("Armour Head", "Броня головы", "Head Armour"),
                "Armour Body" to listOf("Armour Body", "Броня тела", "Body Armour"),
                "Armour Arms" to listOf("Armour Arms", "Броня рук", "Arms Armour"),
                "Armour Legs" to listOf("Armour Legs", "Броня ног", "Legs Armour"),
                "Weapon" to listOf("Weapon", "Оружие", "Weapons"),
                "Trappings" to listOf("Trappings", "Снаряжение", "Equipment", "Gear"),
                "Money" to listOf("Money", "Деньги", "Gold", "Crowns", "Shillings", "Pennies"),
                "Social Standing" to listOf("Social Standing", "Социальное положение", "Social Class"),
                "Corruption" to listOf("Corruption", "Порча", "Corruption Points"),
                "Mutation" to listOf("Mutation", "Мутация", "Mutations"),
                "Psychology" to listOf("Psychology", "Психология", "Mental Disorders"),
                "Talents" to listOf("Talents", "Таланты", "Talent"),
                "Traits" to listOf("Traits", "Черты", "Trait"),
                "Species" to listOf("Species", "Вид", "Race", "Species Type"),
                "Age" to listOf("Age", "Возраст", "Character Age"),
                "Height" to listOf("Height", "Рост", "Character Height"),
                "Hair" to listOf("Hair", "Волосы", "Hair Color"),
                "Eyes" to listOf("Eyes", "Глаза", "Eye Color"),
                "Distinguishing Marks" to listOf("Distinguishing Marks", "Отличительные черты", "Marks"),
                "Birthplace" to listOf("Birthplace", "Место рождения", "Origin"),
                "Star Sign" to listOf("Star Sign", "Знак зодиака", "Zodiac"),
                "Number of Siblings" to listOf("Number of Siblings", "Количество братьев и сестер", "Siblings"),
                "Motivation" to listOf("Motivation", "Мотивация", "Character Motivation"),
                
                // WH: Dark Heresy
                "Home World" to listOf("Home World", "Родной мир", "Homeworld", "Origin World"),
                "Background" to listOf("Background", "Происхождение", "Character Background", "Origin"),
                "Role" to listOf("Role", "Роль", "Character Role", "Acolyte Role"),
                "Rank" to listOf("Rank", "Ранг", "Character Rank", "Throne Agent Rank"),
                "Divination" to listOf("Divination", "Дивинация", "Divination Quote"),
                "Wounds" to listOf("Wounds", "Раны", "Wound Points", "WP", "Wound Maximum", "Максимум ран"),
                "Fate Points" to listOf("Fate Points", "Очки судьбы", "Fate", "FP"),
                "Insanity Points" to listOf("Insanity Points", "Очки безумия", "Insanity", "IP", "Insanity Rating"),
                "Corruption Points" to listOf("Corruption Points", "Очки порчи", "Corruption", "CP", "Corruption Rating"),
                "Movement" to listOf("Movement", "Движение", "Move", "Movement Rate", "Скорость движения"),
                "Armour Points" to listOf("Armour Points", "Очки брони", "Armour", "AP", "Armour Rating"),
                "Armour Head" to listOf("Armour Head", "Броня головы", "Head Armour"),
                "Armour Body" to listOf("Armour Body", "Броня тела", "Body Armour"),
                "Armour Arms" to listOf("Armour Arms", "Броня рук", "Arms Armour"),
                "Armour Legs" to listOf("Armour Legs", "Броня ног", "Legs Armour"),
                "Weapon" to listOf("Weapon", "Оружие", "Weapons"),
                "Gear" to listOf("Gear", "Снаряжение", "Equipment", "Trappings"),
                "Thrones" to listOf("Thrones", "Троны", "Money", "Throne Gelt"),
                "Experience Points" to listOf("Experience Points", "Очки опыта", "XP", "Experience"),
                "Spent Experience" to listOf("Spent Experience", "Потраченный опыт", "Spent XP"),
                "Available Experience" to listOf("Available Experience", "Доступный опыт", "Available XP"),
                "Talents" to listOf("Talents", "Таланты", "Talent"),
                "Traits" to listOf("Traits", "Черты", "Trait"),
                "Psychic Powers" to listOf("Psychic Powers", "Психические силы", "Psyker Powers", "Psychic Abilities"),
                "Psychic Rating" to listOf("Psychic Rating", "Рейтинг псионика", "PR", "Psy Rating"),
                "Sanctioning" to listOf("Sanctioning", "Санкционирование", "Sanctioned Psyker"),
                "Forbidden Lore" to listOf("Forbidden Lore", "Запретное знание", "Forbidden Knowledge"),
                "Scholastic Lore" to listOf("Scholastic Lore", "Ученое знание", "Scholastic Knowledge"),
                "Common Lore" to listOf("Common Lore", "Общее знание", "Common Knowledge"),
                "Malignancies" to listOf("Malignancies", "Злокачественности", "Malignancy"),
                "Mutations" to listOf("Mutations", "Мутации", "Mutation"),
                "Mental Disorders" to listOf("Mental Disorders", "Психические расстройства", "Disorder", "Psychology"),
                "Age" to listOf("Age", "Возраст", "Character Age"),
                "Build" to listOf("Build", "Телосложение", "Body Type"),
                "Complexion" to listOf("Complexion", "Цвет лица", "Skin Color"),
                "Hair" to listOf("Hair", "Волосы", "Hair Color"),
                "Eyes" to listOf("Eyes", "Глаза", "Eye Color"),
                "Quirk" to listOf("Quirk", "Причуда", "Character Quirk"),
                "Superstition" to listOf("Superstition", "Суеверие", "Character Superstition")
            )
            
            // Извлекаем статистики с улучшенными паттернами
            extractStats(statPatterns, stats, lines, processedText)
            
            // 9. Дополнительный парсинг для VTM 5e - извлекаем все текстовые поля
            if (parsedData["system"] == "vtm_5e") {
                extractVtmSpecificFields(stats, lines, processedText)
            }
            
            // 10. Дополнительный парсинг для D&D 5e
            if (parsedData["system"] == "dnd_5e") {
                extractDndSpecificFields(stats, lines, processedText)
            }
            
            // 11. Дополнительный парсинг для WHRP
            if (parsedData["system"] == "whrp") {
                extractWhrpSpecificFields(stats, lines, processedText)
            }
            
            // 12. Дополнительный парсинг для WH: Dark Heresy
            if (parsedData["system"] == "wh_darkheresy") {
                extractDarkHeresySpecificFields(stats, lines, processedText)
            }
            
            // 8. Извлекаем разделы (заголовки и их содержимое)
            val sections = mutableMapOf<String, MutableList<String>>()
            var currentSection: String? = null
            val sectionPattern = Regex("^([А-Яа-яA-Z][А-Яа-яA-Z\\s]+?)(?:[:]|$)", RegexOption.MULTILINE)
            
            lines.forEachIndexed { index, line ->
                val trimmed = line.trim()
                // Проверяем, является ли строка заголовком раздела
                if (trimmed.isNotEmpty() && 
                    trimmed.length < 50 && 
                    !trimmed.contains(":") && 
                    !trimmed.matches(Regex(".*\\d+.*")) &&
                    trimmed[0].isUpperCase()) {
                    currentSection = trimmed
                    if (!sections.containsKey(currentSection)) {
                        sections[currentSection] = mutableListOf()
                    }
                } else if (currentSection != null && trimmed.isNotEmpty()) {
                    sections[currentSection]?.add(trimmed)
                }
            }
            
            // 11. Сохраняем все извлеченные данные
            parsedData["attributes"] = attributes
            parsedData["skills"] = skills
            parsedData["disciplines"] = disciplines // VTM дисциплины
            parsedData["stats"] = stats
            parsedData["allKeyValuePairs"] = allKeyValuePairs
            parsedData["numericValues"] = numericValues
            parsedData["sections"] = sections.mapValues { it.value.toList() } // Конвертируем в List для сериализации
            parsedData["rawText"] = text
            parsedData["lines"] = lines.toList() // Сохраняем все строки
            
            // 12. Логирование для отладки
            android.util.Log.d("CharacterSheetsFragment", "=== FULL PDF PARSING RESULTS ===")
            android.util.Log.d("CharacterSheetsFragment", "Name: ${parsedData["name"]}")
            android.util.Log.d("CharacterSheetsFragment", "System: ${parsedData["system"]}")
            android.util.Log.d("CharacterSheetsFragment", "Attributes count: ${attributes.size}")
            android.util.Log.d("CharacterSheetsFragment", "Skills count: ${skills.size}")
            android.util.Log.d("CharacterSheetsFragment", "Disciplines count: ${disciplines.size}")
            android.util.Log.d("CharacterSheetsFragment", "Stats count: ${stats.size}")
            android.util.Log.d("CharacterSheetsFragment", "Key-Value pairs count: ${allKeyValuePairs.size}")
            android.util.Log.d("CharacterSheetsFragment", "Numeric values count: ${numericValues.size}")
            android.util.Log.d("CharacterSheetsFragment", "Sections count: ${sections.size}")
            android.util.Log.d("CharacterSheetsFragment", "Total parsed data keys: ${parsedData.keys}")
            android.util.Log.d("CharacterSheetsFragment", "All attributes: $attributes")
            android.util.Log.d("CharacterSheetsFragment", "All skills: $skills")
            android.util.Log.d("CharacterSheetsFragment", "All disciplines: $disciplines")
            android.util.Log.d("CharacterSheetsFragment", "All stats: $stats")
            android.util.Log.d("CharacterSheetsFragment", "Sections: ${sections.keys}")
            
            parsedData
        } catch (e: OutOfMemoryError) {
            android.util.Log.e("CharacterSheetsFragment", "OutOfMemoryError parsing PDF content: ${e.message}", e)
            // Ensure document is closed even if error occurs
            try {
                document?.close()
            } catch (closeException: Exception) {
                android.util.Log.e("CharacterSheetsFragment", "Error closing PDF document: ${closeException.message}", closeException)
            }
            mapOf(
                "name" to "Безымянный персонаж",
                "system" to "unknown",
                "attributes" to emptyMap<String, Int>(),
                "skills" to emptyMap<String, Int>(),
                "stats" to emptyMap<String, Any>()
            )
        } catch (e: Exception) {
            android.util.Log.e("CharacterSheetsFragment", "Error parsing PDF content: ${e.message}", e)
            // Ensure document is closed even if error occurs
            try {
                document?.close()
            } catch (closeException: Exception) {
                android.util.Log.e("CharacterSheetsFragment", "Error closing PDF document: ${closeException.message}", closeException)
            }
            mapOf(
                "name" to "Безымянный персонаж",
                "system" to "unknown",
                "attributes" to emptyMap<String, Int>(),
                "skills" to emptyMap<String, Int>(),
                "stats" to emptyMap<String, Any>()
            )
        }
    }
    
    private fun openSheetEditor(sheet: CharacterSheet) {
        // Navigate to sheet editor
        // For now, just show a toast
        val context = context
        if (context != null && isAdded) {
            Toast.makeText(context, "Открытие листа: ${sheet.characterName}", Toast.LENGTH_SHORT).show()
        }
        // TODO: Navigate to sheet editor fragment
    }
    
    private fun deleteSheet(sheet: CharacterSheet) {
        val context = context ?: return
        MaterialAlertDialogBuilder(context)
            .setTitle("Удалить лист персонажа")
            .setMessage("Вы уверены, что хотите удалить '${sheet.characterName}'? Это действие нельзя отменить.")
            .setPositiveButton("Удалить") { _, _ ->
                performDeleteSheet(sheet)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun performDeleteSheet(sheet: CharacterSheet) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                sheetRepository.deleteSheet(sheet.id)
                loadSheets()
                view?.let {
                    Snackbar.make(it, "Лист удалён", Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                view?.let {
                    Snackbar.make(it, "Ошибка удаления: ${e.message}", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun showCreateSheetConfirmationDialog(sheet: CharacterSheet) {
        val context = context ?: return
        if (!isAdded || view == null) {
            android.util.Log.w("CharacterSheetsFragment", "Fragment not attached, cannot show dialog")
            return
        }
        
        // Проверяем что fragmentManager доступен
        val fragmentManager = parentFragmentManager
        if (fragmentManager.isStateSaved) {
            android.util.Log.w("CharacterSheetsFragment", "FragmentManager state is saved, cannot show dialog")
            view?.let {
                Snackbar.make(it, "Не удалось открыть диалог", Snackbar.LENGTH_SHORT).show()
            }
            return
        }
        
        try {
            MaterialAlertDialogBuilder(context)
                .setTitle("Сохранить лист персонажа?")
                .setMessage("PDF успешно распарсен. Хотите сохранить этот лист персонажа?\n\nНазвание: ${sheet.characterName}\nСистема: ${sheet.system}")
                .setPositiveButton("Сохранить") { _, _ ->
                    if (isAdded && view != null) {
                        saveSheet(sheet)
                    }
                }
                .setNegativeButton("Отмена") { _, _ ->
                    if (isAdded && view != null) {
                        view?.let {
                            Snackbar.make(it, "Лист не сохранён", Snackbar.LENGTH_SHORT).show()
                        }
                    }
                }
                .setCancelable(false)
                .show()
        } catch (e: IllegalStateException) {
            android.util.Log.e("CharacterSheetsFragment", "IllegalStateException showing dialog: ${e.message}", e)
            view?.let {
                Snackbar.make(it, "Не удалось открыть диалог. Попробуйте еще раз.", Snackbar.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("CharacterSheetsFragment", "Error showing dialog: ${e.message}", e)
            view?.let {
                Snackbar.make(it, "Ошибка отображения диалога: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }
    
    private fun saveSheet(sheet: CharacterSheet) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                sheetRepository.saveSheet(sheet)
                if (isAdded && view != null) {
                    view?.let {
                        Snackbar.make(it, "Лист персонажа сохранён! Теперь он доступен в разделе 'Документы' -> 'Загруженные листы персонажей'", Snackbar.LENGTH_LONG).show()
                    }
                    // Возвращаемся назад в DocumentsFragment после сохранения
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        if (isAdded && view != null) {
                            try {
                                findNavController().navigateUp()
                            } catch (e: Exception) {
                                android.util.Log.e("CharacterSheetsFragment", "Navigation error: ${e.message}", e)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("CharacterSheetsFragment", "Error saving sheet: ${e.message}", e)
                if (isAdded && view != null) {
                    view?.let {
                        Snackbar.make(it, "Ошибка сохранения листа: ${e.message}", Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}

