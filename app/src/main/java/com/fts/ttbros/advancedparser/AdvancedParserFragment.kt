package com.fts.ttbros.advancedparser

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fts.ttbros.R
import com.fts.ttbros.data.model.ParseResult
import com.fts.ttbros.data.model.ParsedSection
import com.fts.ttbros.parser.AdvancedPdfParser
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class AdvancedParserFragment : Fragment() {

    private lateinit var uploadButton: MaterialButton
    private lateinit var progressLayout: View
    private lateinit var progressText: TextView
    private lateinit var resultInfoCard: View
    private lateinit var characterNameText: TextView
    private lateinit var systemText: TextView
    private lateinit var confidenceText: TextView
    private lateinit var sectionsCountText: TextView
    private lateinit var sectionsLabel: TextView
    private lateinit var sectionsRecyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var createCharacterFab: ExtendedFloatingActionButton

    private val parser = AdvancedPdfParser()
    private var currentParseResult: ParseResult? = null
    private val sections = mutableListOf<ParsedSection>()

    private val adapter = ParsedSectionAdapter { section ->
        showEditDialog(section)
    }

    private val pickPdfLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { parsePdf(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_advanced_parser, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize views
        uploadButton = view.findViewById(R.id.uploadPdfButton)
        progressLayout = view.findViewById(R.id.progressLayout)
        progressText = view.findViewById(R.id.progressText)
        resultInfoCard = view.findViewById(R.id.resultInfoCard)
        characterNameText = view.findViewById(R.id.characterNameText)
        systemText = view.findViewById(R.id.systemText)
        confidenceText = view.findViewById(R.id.confidenceText)
        sectionsCountText = view.findViewById(R.id.sectionsCountText)
        sectionsLabel = view.findViewById(R.id.sectionsLabel)
        sectionsRecyclerView = view.findViewById(R.id.sectionsRecyclerView)
        emptyView = view.findViewById(R.id.emptyView)
        createCharacterFab = view.findViewById(R.id.createCharacterFab)

        // Setup RecyclerView
        val context = context ?: return
        sectionsRecyclerView.layoutManager = LinearLayoutManager(context)
        sectionsRecyclerView.adapter = adapter

        // Setup click listeners
        uploadButton.setOnClickListener {
            pickPdfLauncher.launch("application/pdf")
        }

        createCharacterFab.setOnClickListener {
            createCharacterFromParsedData()
        }
    }

    private val userRepository = com.fts.ttbros.data.repository.UserRepository()
    private val sheetRepository = com.fts.ttbros.data.repository.CharacterSheetRepository()
    private var currentPdfUri: Uri? = null

    private fun parsePdf(uri: Uri) {
        currentPdfUri = uri
        val context = context ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Show progress
                progressLayout.isVisible = true
                progressText.text = "Парсинг PDF..."
                emptyView.isVisible = false
                resultInfoCard.isVisible = false
                sectionsLabel.isVisible = false
                sectionsRecyclerView.isVisible = false
                createCharacterFab.isVisible = false

                // Parse PDF
                val result = parser.parse(uri, context)
                currentParseResult = result

                // Hide progress
                progressLayout.isVisible = false

                if (result.errors.isNotEmpty()) {
                    Snackbar.make(
                        requireView(),
                        "Ошибки при парсинге: ${result.errors.joinToString(", ")}",
                        Snackbar.LENGTH_LONG
                    ).show()
                }

                if (result.sections.isEmpty()) {
                    emptyView.isVisible = true
                    emptyView.text = "Не удалось распознать секции в PDF"
                    return@launch
                }

                // Update UI with results
                displayResults(result)

            } catch (e: Exception) {
                android.util.Log.e("AdvancedParserFragment", "Error parsing PDF: ${e.message}", e)
                progressLayout.isVisible = false
                Snackbar.make(
                    requireView(),
                    "Ошибка: ${e.message}",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun displayResults(result: ParseResult) {
        // Show result info
        resultInfoCard.isVisible = true
        characterNameText.text = "Character: ${result.characterName ?: "Unknown"}"
        systemText.text = "System: ${getSystemDisplayName(result.detectedSystem)}"
        
        val confidencePercent = (result.overallConfidence * 100).toInt()
        confidenceText.text = "Confidence: $confidencePercent%"
        
        sectionsCountText.text = "Sections: ${result.sections.size}"

        // Show sections
        sectionsLabel.isVisible = true
        sectionsRecyclerView.isVisible = true
        sections.clear()
        sections.addAll(result.sections)
        adapter.submitList(result.sections)

        // Show create character button
        createCharacterFab.isVisible = true
    }

    private fun getSystemDisplayName(system: String?): String {
        return when (system) {
            "vtm_5e" -> "Vampire: The Masquerade 5E"
            "dnd_5e" -> "Dungeons & Dragons 5E"
            "whrp" -> "Warhammer Fantasy Roleplay"
            "wh_darkheresy" -> "Warhammer 40k: Dark Heresy"
            else -> system ?: "Unknown"
        }
    }

    private fun showEditDialog(section: ParsedSection) {
        val context = context ?: return

        val editText = TextInputEditText(context).apply {
            setText(section.content)
            minLines = 5
        }

        MaterialAlertDialogBuilder(context)
            .setTitle("Редактировать: ${section.title}")
            .setView(editText)
            .setPositiveButton("Сохранить") { _, _ ->
                val newContent = editText.text.toString()
                updateSection(section, newContent)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun updateSection(section: ParsedSection, newContent: String) {
        val index = sections.indexOfFirst { it.id == section.id }
        if (index >= 0) {
            val updatedSection = section.copy(content = newContent)
            sections[index] = updatedSection
            adapter.submitList(sections.toList())
            Snackbar.make(requireView(), "Секция обновлена", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun createCharacterFromParsedData() {
        val result = currentParseResult ?: return
        val uri = currentPdfUri ?: return
        val context = context ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                progressLayout.isVisible = true
                progressText.text = "Saving character sheet..."
                createCharacterFab.isEnabled = false

                val profile = userRepository.currentProfile()
                if (profile == null) {
                    Snackbar.make(requireView(), "Error: User profile not found", Snackbar.LENGTH_LONG).show()
                    progressLayout.isVisible = false
                    createCharacterFab.isEnabled = true
                    return@launch
                }

                // Extract data for CharacterSheet
                val attributes = mutableMapOf<String, Int>()
                val skills = mutableMapOf<String, Int>()
                val stats = mutableMapOf<String, Any>()
                val parsedData = mutableMapOf<String, Any>()

                result.sections.forEach { section ->
                    when (section.sectionType) {
                        com.fts.ttbros.data.model.SectionType.ATTRIBUTES -> {
                            section.rawData.forEach { (k, v) ->
                                (v as? Number)?.toInt()?.let { attributes[k] = it }
                            }
                        }
                        com.fts.ttbros.data.model.SectionType.SKILLS -> {
                            section.rawData.forEach { (k, v) ->
                                (v as? Number)?.toInt()?.let { skills[k] = it }
                            }
                        }
                        else -> {
                            parsedData[section.title] = section.content
                            section.rawData.forEach { (k, v) ->
                                stats[k] = v
                            }
                        }
                    }
                }

                // Upload sheet using CharacterSheetRepository
                // This will save to /TTBros/character_sheets/$userId/ on Yandex.Disk
                sheetRepository.uploadSheet(
                    userId = profile.uid,
                    userName = profile.displayName,
                    characterName = result.characterName ?: "Parsed Character",
                    system = result.detectedSystem ?: "unknown",
                    pdfUri = uri,
                    context = context,
                    parsedData = parsedData,
                    attributes = attributes,
                    skills = skills,
                    stats = stats
                )

                // Navigate to DocumentsFragment
                findNavController().navigate(R.id.documentsFragment)
                
                Snackbar.make(
                    requireView(),
                    "Character sheet saved. Opening Documents...",
                    Snackbar.LENGTH_SHORT
                ).show()

            } catch (e: Exception) {
                android.util.Log.e("AdvancedParserFragment", "Error saving sheet: ${e.message}", e)
                progressLayout.isVisible = false
                createCharacterFab.isEnabled = true
                Snackbar.make(
                    requireView(),
                    "Error saving sheet: ${e.message}",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }
}
