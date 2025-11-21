package com.fts.ttbros.characters

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.fts.ttbros.MainActivity
import com.fts.ttbros.characters.form.FormAdapter
import com.fts.ttbros.characters.templates.VtmTemplate
import com.fts.ttbros.characters.templates.ViedzminTemplate
import com.fts.ttbros.data.model.Character
import com.fts.ttbros.data.repository.CharacterRepository
import com.fts.ttbros.databinding.FragmentCharacterEditorBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class CharacterEditorFragment : Fragment() {

    private var _binding: FragmentCharacterEditorBinding? = null
    private val binding get() = _binding!!
    
    private val repository = CharacterRepository()
    private var characterId: String? = null
    private var system: String? = null
    private var currentCharacter: Character? = null
    
    // Default to Russian as requested
    private var currentLocale = java.util.Locale("ru") 
    
    // Temporary storage for form data changes
    private val formData = mutableMapOf<String, Any>()

    private val adapter = FormAdapter { key, value ->
        handleFormUpdate(key, value)
    }
    
    private fun handleFormUpdate(key: String, value: Any) {
        if (key == "add_discipline") {
            addDiscipline()
        } else if (key.startsWith("discipline_")) {
            updateDiscipline(key, value)
        } else {
            formData[key] = value
        }
    }

    private fun addDiscipline() {
        val currentList = (formData["disciplines"] as? List<*>)?.filterIsInstance<Map<String, Any>>()?.toMutableList() ?: mutableListOf()
        val newId = java.util.UUID.randomUUID().toString()
        currentList.add(mapOf(
            "id" to newId,
            "name" to "",
            "value" to 0
        ))
        formData["disciplines"] = currentList
        renderForm()
    }

    private fun updateDiscipline(key: String, value: Any) {
        val currentList = (formData["disciplines"] as? List<*>)?.filterIsInstance<Map<String, Any>>()?.toMutableList() ?: return
        
        val parts = key.split("_")
        if (parts.size < 3) return
        val type = parts[1] // "name" or "value"
        val id = parts.drop(2).joinToString("_") // Rejoin in case ID has underscores
        
        val index = currentList.indexOfFirst { it["id"] == id }
        if (index != -1) {
            val item = currentList[index].toMutableMap()
            item[type] = value
            currentList[index] = item
            formData["disciplines"] = currentList
            // No need to re-render for text updates to avoid focus loss
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            characterId = it.getString("characterId")
            system = it.getString("system")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCharacterEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.toolbar.setNavigationOnClickListener {
            (requireActivity() as MainActivity).openDrawer()
        }

        binding.formRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.formRecyclerView.adapter = adapter
        
        binding.saveButton.setOnClickListener {
            saveCharacter()
        }
        
        // Mode Switch
        val isNew = characterId == null
        binding.modeSwitch.isChecked = isNew
        updateMode(isNew)
        
        binding.modeSwitch.setOnCheckedChangeListener { _, isChecked ->
            updateMode(isChecked)
        }
        
        // Language Switch
        // Default is unchecked (RU), checked (ENG)
        binding.langSwitch.isChecked = false 
        updateLanguageLabel(binding.langSwitch.isChecked)
        
        binding.langSwitch.setOnCheckedChangeListener { _, isChecked ->
            updateLanguageLabel(isChecked)
            currentLocale = if (isChecked) java.util.Locale("en") else java.util.Locale("ru")
            renderForm()
        }

        if (characterId != null) {
            loadCharacter()
        } else {
            renderForm()
        }
    }
    
    private fun updateLanguageLabel(isEnglish: Boolean) {
        binding.langSwitch.text = if (isEnglish) "ENG" else "RUS"
    }
    
    private fun updateMode(isEdit: Boolean) {
        adapter.readOnly = !isEdit
        binding.saveButton.isVisible = isEdit
        binding.modeSwitch.text = if (isEdit) "Редактирование" else "Зафиксировано"
    }

    private fun loadCharacter() {
        lifecycleScope.launch {
            try {
                if (characterId != null) {
                    val char = repository.getCharacter(characterId!!)
                    if (char != null) {
                        currentCharacter = char
                        system = char.system
                        formData.putAll(char.data)
                        formData["name"] = char.name
                        formData["clan"] = char.clan
                        formData["concept"] = char.concept
                        renderForm()
                    } else {
                        showError("Character not found")
                        findNavController().navigateUp()
                    }
                }
            } catch (e: Exception) {
                showError("Error loading character: ${e.message}")
            }
        }
    }

    private fun renderForm() {
        val contextWithLocale = getLocalizedContext(requireContext(), currentLocale)
        val items = when (system) {
            "vtm_5e" -> VtmTemplate.generate(formData, contextWithLocale)
            "viedzmin_2e" -> ViedzminTemplate.generate(formData, contextWithLocale)
            "dnd_5e" -> emptyList() // TODO: Implement DnD
            else -> emptyList()
        }
        adapter.submitList(items)
    }
    
    private fun getLocalizedContext(context: android.content.Context, locale: java.util.Locale): android.content.Context {
        val config = android.content.res.Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    private fun saveCharacter() {
        val name = formData["name"] as? String ?: "Unnamed"
        val clan = formData["clan"] as? String ?: ""
        val concept = formData["concept"] as? String ?: ""
        
        lifecycleScope.launch {
            try {
                if (characterId != null) {
                    // Update
                    repository.updateCharacter(characterId!!, mapOf(
                        "name" to name,
                        "clan" to clan,
                        "concept" to concept,
                        "data" to formData
                    ))
                    showMessage("Character updated")
                } else {
                    // Create
                    val newChar = Character(
                        name = name,
                        system = system ?: "unknown",
                        clan = clan,
                        concept = concept,
                        data = formData
                    )
                    repository.createCharacter(newChar)
                    showMessage("Character created")
                }
                findNavController().navigateUp()
            } catch (e: Exception) {
                showError("Error saving: ${e.message}")
            }
        }
    }

    private fun showError(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }
    
    private fun showMessage(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
