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
        
        sheetsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
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
                loadingView.isVisible = true
                val sheets = sheetRepository.getUserSheets(userId)
                adapter.submitList(sheets)
                emptyView.isVisible = sheets.isEmpty()
            } catch (e: Exception) {
                android.util.Log.e("CharacterSheetsFragment", "Error loading sheets: ${e.message}", e)
                view?.let {
                    Snackbar.make(it, "Ошибка загрузки листов: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
            } finally {
                loadingView.isVisible = false
            }
        }
    }
    
    private fun uploadAndParsePdf(uri: Uri) {
        val userId = auth.currentUser?.uid ?: return
        val context = context ?: return
        
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
                
                // Create character sheet template (builder)
                val sheet = CharacterSheet(
                    userId = userId,
                    characterName = parsedData["name"] as? String ?: "Безымянный персонаж",
                    system = parsedData["system"] as? String ?: "unknown",
                    pdfUrl = pdfUrl,
                    parsedData = parsedData,
                    attributes = (parsedData["attributes"] as? Map<*, *>)?.mapKeys { it.key.toString() }?.mapValues { (it.value as? Number)?.toInt() ?: 0 } ?: emptyMap(),
                    skills = (parsedData["skills"] as? Map<*, *>)?.mapKeys { it.key.toString() }?.mapValues { (it.value as? Number)?.toInt() ?: 0 } ?: emptyMap(),
                    stats = (parsedData["stats"] as? Map<*, *>)?.mapKeys { it.key.toString() }?.mapValues { it.value ?: "" } ?: emptyMap(),
                    isTemplate = true // Это билдер (шаблон)
                )
                
                // Показать диалог подтверждения создания билдера
                if (isAdded && view != null) {
                    showCreateBuilderConfirmationDialog(sheet)
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
        val errorMessage: String? = null
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
            
            ValidationResult(true)
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
                com.tom_roush.pdfbox.text.PDFTextStripper()
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
            
            document.close()
            document = null
            
            // Basic parsing - extract name and basic info
            // This is a simplified version - you'd want more sophisticated parsing
            val lines = text.lines()
            val parsedData = mutableMapOf<String, Any>()
            
            // Try to find character name (usually on first few lines)
            val nameLine = lines.firstOrNull { it.contains("Name", ignoreCase = true) || it.contains("Имя", ignoreCase = true) }
            parsedData["name"] = nameLine?.substringAfter(":")?.trim()?.substringAfter(" ")?.trim() ?: "Безымянный персонаж"
            
            // Try to detect system
            parsedData["system"] = when {
                text.contains("D&D", ignoreCase = true) || text.contains("Dungeons", ignoreCase = true) -> "dnd_5e"
                text.contains("Vampire", ignoreCase = true) || text.contains("VTM", ignoreCase = true) -> "vtm_5e"
                else -> "unknown"
            }
            
            // Extract attributes (this is very basic - would need format-specific parsing)
            val attributes = mutableMapOf<String, Int>()
            val skills = mutableMapOf<String, Int>()
            val stats = mutableMapOf<String, Any>()
            
            // Basic attribute extraction (this would need to be customized per system)
            lines.forEach { line ->
                // Look for common attribute patterns
                val attributePattern = Regex("(Strength|Dexterity|Constitution|Intelligence|Wisdom|Charisma|Сила|Ловкость|Выносливость|Интеллект|Мудрость|Харизма)\\s*:?\\s*(\\d+)", RegexOption.IGNORE_CASE)
                attributePattern.find(line)?.let { match ->
                    val attrName = match.groupValues[1]
                    val value = match.groupValues[2].toIntOrNull() ?: 0
                    attributes[attrName] = value
                }
            }
            
            parsedData["attributes"] = attributes
            parsedData["skills"] = skills
            parsedData["stats"] = stats
            parsedData["rawText"] = text // Store raw text for reference
            
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
        Toast.makeText(requireContext(), "Открытие листа: ${sheet.characterName}", Toast.LENGTH_SHORT).show()
        // TODO: Navigate to sheet editor fragment
    }
    
    private fun deleteSheet(sheet: CharacterSheet) {
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
    
    private fun showCreateBuilderConfirmationDialog(sheet: CharacterSheet) {
        val context = context ?: return
        if (!isAdded || view == null) {
            android.util.Log.w("CharacterSheetsFragment", "Fragment not attached, cannot show dialog")
            return
        }
        
        try {
            MaterialAlertDialogBuilder(context)
                .setTitle("Создать билдер?")
                .setMessage("PDF успешно распарсен. Хотите создать билдер персонажа из этого листа?\n\nИмя: ${sheet.characterName}\nСистема: ${sheet.system}")
                .setPositiveButton("Создать билдер") { _, _ ->
                    if (isAdded && view != null) {
                        saveBuilder(sheet)
                    }
                }
                .setNegativeButton("Отмена") { _, _ ->
                    if (isAdded && view != null) {
                        view?.let {
                            Snackbar.make(it, "Билдер не создан", Snackbar.LENGTH_SHORT).show()
                        }
                    }
                }
                .setCancelable(false)
                .show()
        } catch (e: Exception) {
            android.util.Log.e("CharacterSheetsFragment", "Error showing dialog: ${e.message}", e)
            view?.let {
                Snackbar.make(it, "Ошибка отображения диалога: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }
    
    private fun saveBuilder(sheet: CharacterSheet) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                sheetRepository.saveSheet(sheet)
                if (isAdded && view != null) {
                    view?.let {
                        Snackbar.make(it, "Билдер создан! Теперь вы можете создать персонажа из него в разделе 'Персонажи' -> 'Мой билдер'", Snackbar.LENGTH_LONG).show()
                    }
                    loadSheets()
                }
            } catch (e: Exception) {
                android.util.Log.e("CharacterSheetsFragment", "Error saving builder: ${e.message}", e)
                if (isAdded && view != null) {
                    view?.let {
                        Snackbar.make(it, "Ошибка сохранения билдера: ${e.message}", Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}

