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
                loadingView.isVisible = true
                view?.let {
                    Snackbar.make(it, "Загрузка PDF", Snackbar.LENGTH_SHORT).show()
                }
                
                // Upload PDF to Yandex.Disk
                val pdfUrl = yandexDisk.uploadCharacterSheet(userId, uri, context)
                
                // Проверка что фрагмент все еще прикреплен
                if (!isAdded || view == null) {
                    android.util.Log.w("CharacterSheetsFragment", "Fragment detached during upload")
                    return@launch
                }
                
                view?.let {
                    Snackbar.make(it, "Парсинг PDF...", Snackbar.LENGTH_SHORT).show()
                }
                
                // Parse PDF
                val parsedData = parsePdf(uri, context)
                
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
    
    private suspend fun parsePdf(uri: Uri, context: Context): Map<String, Any> {
        // Basic PDF parsing - this is a simplified version
        // In production, you'd want more sophisticated parsing
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                parsePdfContent(stream)
            } ?: emptyMap<String, Any>()
        } catch (e: Exception) {
            android.util.Log.e("CharacterSheetsFragment", "Error parsing PDF: ${e.message}", e)
            emptyMap<String, Any>()
        }
    }
    
    private fun parsePdfContent(inputStream: InputStream): Map<String, Any> {
        // This is a basic implementation
        // For production, you'd want to use a proper PDF parsing library
        // and extract structured data based on the character sheet format
        return try {
            // Try to read text from PDF using PDFBox
            val document = com.tom_roush.pdfbox.pdmodel.PDDocument.load(inputStream)
            val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
            val text = stripper.getText(document)
            document.close()
            
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
        } catch (e: Exception) {
            android.util.Log.e("CharacterSheetsFragment", "Error parsing PDF content: ${e.message}", e)
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
        if (!isAdded) return
        
        MaterialAlertDialogBuilder(context)
            .setTitle("Создать билдер?")
            .setMessage("PDF успешно распарсен. Хотите создать билдер персонажа из этого листа?\n\nИмя: ${sheet.characterName}\nСистема: ${sheet.system}")
            .setPositiveButton("Создать билдер") { _, _ ->
                saveBuilder(sheet)
            }
            .setNegativeButton("Отмена") { _, _ ->
                view?.let {
                    Snackbar.make(it, "Билдер не создан", Snackbar.LENGTH_SHORT).show()
                }
            }
            .setCancelable(false)
            .show()
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

