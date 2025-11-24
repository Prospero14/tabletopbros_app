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
import com.fts.ttbros.R
import com.fts.ttbros.characters.form.FormAdapter
import com.fts.ttbros.characters.templates.VtmTemplate
import com.fts.ttbros.characters.templates.ViedzminTemplate
import com.fts.ttbros.characters.templates.DndTemplate
import com.fts.ttbros.data.model.Character
import com.fts.ttbros.data.model.CharacterSheet
import com.fts.ttbros.data.repository.CharacterRepository
import com.fts.ttbros.data.repository.CharacterSheetRepository
import com.fts.ttbros.databinding.FragmentCharacterEditorBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class CharacterEditorFragment : Fragment() {

    private var _binding: FragmentCharacterEditorBinding? = null
    private val binding get() = _binding ?: throw IllegalStateException("Binding is null. Fragment view may have been destroyed.")
    
    private val repository = CharacterRepository()
    private val sheetRepository = CharacterSheetRepository()
    private var characterId: String? = null
    private var system: String? = null
    private var builderId: String? = null
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
        val currentList = (formData["disciplines"] as? List<*>)?.filterIsInstance<Map<String, Any>>()?.toMutableList() ?: mutableListOf()
        
        // Handle both formats: "discipline_name_${id}" and "discipline_value_${id}"
        val parts = key.split("_")
        if (parts.size < 3) return
        
        val type = parts[1] // "name" or "value"
        val id = parts.drop(2).joinToString("_") // Rejoin in case ID has underscores
        
        val index = currentList.indexOfFirst { it["id"] == id }
        if (index != -1) {
            val item = currentList[index].toMutableMap()
            // Convert value to appropriate type
            when (type) {
                "name" -> item["name"] = value.toString()
                "value" -> item["value"] = when (value) {
                    is Number -> value.toInt()
                    is String -> value.toIntOrNull() ?: 0
                    else -> 0
                }
            }
            currentList[index] = item
            formData["disciplines"] = currentList
            android.util.Log.d("CharEditor", "Updated discipline: id=$id, type=$type, value=$value")
            android.util.Log.d("CharEditor", "Current disciplines: $currentList")
        } else {
            android.util.Log.w("CharEditor", "Discipline with id=$id not found in list")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            characterId = it.getString("characterId")
            system = it.getString("system")
            builderId = it.getString("builderId")
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
        
        // Set navigation icon - toolbar will show default back arrow automatically
        // Navigation icon is handled by Navigation component
        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        binding.toolbar.setNavigationOnClickListener {
            try {
                if (!findNavController().popBackStack()) {
                    // If back stack is empty, try to navigate up
                    findNavController().navigateUp()
                }
            } catch (e: Exception) {
                android.util.Log.e("CharacterEditorFragment", "Navigation error: ${e.message}", e)
                activity?.onBackPressed()
            }
        }

        val context = context ?: return
        binding.formRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.formRecyclerView.adapter = adapter
        
        binding.saveButton.setOnClickListener {
            // Force focus clear to ensure last edit is captured
            val currentFocus = activity?.currentFocus
            currentFocus?.clearFocus()
            saveCharacter()
        }
        

        
        // Mode Switch
        val context = context ?: return
        val prefs = context.getSharedPreferences("ttbros_prefs", android.content.Context.MODE_PRIVATE)
        val savedMode = prefs.getBoolean("pref_lock_mode", false)
        val isNew = characterId == null
        val initialMode = if (isNew) true else savedMode
        
        binding.modeSwitch.isChecked = initialMode
        updateMode(initialMode)
        
        binding.modeSwitch.setOnCheckedChangeListener { _, isChecked ->
            updateMode(isChecked)
            prefs.edit().putBoolean("pref_lock_mode", isChecked).apply()
        }
        
        // Language Switch
        binding.langSwitch.isChecked = false 
        updateLanguageLabel(binding.langSwitch.isChecked)
        
        binding.langSwitch.setOnCheckedChangeListener { _, isChecked ->
            updateLanguageLabel(isChecked)
            currentLocale = if (isChecked) java.util.Locale("en") else java.util.Locale("ru")
            renderForm()
        }

        if (characterId != null) {
            loadCharacter()
        } else if (builderId != null) {
            loadBuilderAndCreateCharacter()
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
        // Re-render form to ensure data is consistent (fixes disappearing disciplines)
        renderForm()
    }

    private fun loadCharacter() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val charId = characterId
                if (charId != null) {
                    val char = repository.getCharacter(charId)
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
                        try {
                            if (!findNavController().navigateUp()) {
                                activity?.onBackPressed()
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("CharacterEditorFragment", "Navigation error: ${e.message}", e)
                            activity?.onBackPressed()
                        }
                    }
                }
            } catch (e: Exception) {
                showError("Error loading character: ${e.message}")
            }
        }
    }
    
    private fun loadBuilderAndCreateCharacter() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val bId = builderId
                if (bId != null) {
                    val builder = sheetRepository.getSheet(bId)
                    if (builder != null) {
                        system = builder.system
                        
                        android.util.Log.d("CharacterEditor", "Loading builder: ${builder.characterName}, system: ${builder.system}")
                        android.util.Log.d("CharacterEditor", "Builder attributes: ${builder.attributes}")
                        android.util.Log.d("CharacterEditor", "Builder skills: ${builder.skills}")
                        android.util.Log.d("CharacterEditor", "Builder stats: ${builder.stats}")
                        android.util.Log.d("CharacterEditor", "Builder parsedData keys: ${builder.parsedData.keys}")
                        
                        // Очищаем formData перед заполнением
                        formData.clear()
                        
                        // Заполняем форму данными из билдера
                        formData["name"] = builder.characterName
                        
                        // Копируем атрибуты из билдера (с правильными именами для системы)
                        builder.attributes.forEach { (key, value) ->
                            // Нормализуем имена атрибутов для разных систем
                            val normalizedKey = when {
                                key.contains("Strength", ignoreCase = true) -> "strength"
                                key.contains("Dexterity", ignoreCase = true) -> "dexterity"
                                key.contains("Constitution", ignoreCase = true) -> "constitution"
                                key.contains("Intelligence", ignoreCase = true) -> "intelligence"
                                key.contains("Wisdom", ignoreCase = true) -> "wisdom"
                                key.contains("Charisma", ignoreCase = true) -> "charisma"
                                key.contains("Сила", ignoreCase = true) -> "strength"
                                key.contains("Ловкость", ignoreCase = true) -> "dexterity"
                                key.contains("Выносливость", ignoreCase = true) -> "constitution"
                                key.contains("Интеллект", ignoreCase = true) -> "intelligence"
                                key.contains("Мудрость", ignoreCase = true) -> "wisdom"
                                key.contains("Харизма", ignoreCase = true) -> "charisma"
                                else -> key.lowercase()
                            }
                            formData[normalizedKey] = value
                            android.util.Log.d("CharacterEditor", "Copied attribute: $key -> $normalizedKey = $value")
                        }
                        
                        // Копируем навыки из билдера
                        builder.skills.forEach { (key, value) ->
                            val normalizedKey = key.lowercase().replace(" ", "_")
                            formData["skill_$normalizedKey"] = value
                            android.util.Log.d("CharacterEditor", "Copied skill: $key -> skill_$normalizedKey = $value")
                        }
                        
                        // Копируем другие статистики из билдера
                        builder.stats.forEach { (key, value) ->
                            formData[key.lowercase()] = value
                            android.util.Log.d("CharacterEditor", "Copied stat: $key = $value")
                        }
                        
                        // Копируем parsedData если есть (приоритет над отдельными полями)
                        builder.parsedData.forEach { (key, value) ->
                            try {
                                if (key != "attributes" && key != "skills" && key != "stats" && key != "rawText" && key != "lines") {
                                    formData[key.lowercase()] = value
                                    android.util.Log.d("CharacterEditor", "Copied parsedData: $key = $value")
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("CharacterEditor", "Error copying parsedData key $key: ${e.message}", e)
                            }
                        }
                        
                        android.util.Log.d("CharacterEditor", "Final formData keys: ${formData.keys}")
                        android.util.Log.d("CharacterEditor", "Final formData: $formData")
                        
                        if (isAdded && view != null) {
                            renderForm()
                            Snackbar.make(binding.root, "Персонаж создан из загруженного листа '${builder.characterName}'", Snackbar.LENGTH_LONG).show()
                        }
                    } else {
                        if (isAdded && view != null) {
                            showError("Билдер не найден")
                            try {
                                if (!findNavController().navigateUp()) {
                                    activity?.onBackPressed()
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("CharacterEditorFragment", "Navigation error: ${e.message}", e)
                                activity?.onBackPressed()
                            }
                        }
                    }
                } else {
                    if (isAdded && view != null) {
                        showError("ID билдера не указан")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("CharacterEditorFragment", "Error loading builder: ${e.message}", e)
                if (isAdded && view != null) {
                    showError("Ошибка загрузки билдера: ${e.message}")
                }
            }
        }
    }

    private fun renderForm() {
        val context = context ?: return
        val contextWithLocale = getLocalizedContext(context, currentLocale)
        val items = when (system) {
            "vtm_5e" -> VtmTemplate.generate(formData, contextWithLocale)
            "viedzmin_2e" -> ViedzminTemplate.generate(formData, contextWithLocale)
            "dnd_5e", "dnd" -> DndTemplate.generate(formData, contextWithLocale)
            else -> VtmTemplate.generate(formData, contextWithLocale)
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
        // Map class/profession to clan for display in list
        val clan = when {
            formData.containsKey("clan") -> formData["clan"] as? String ?: ""
            formData.containsKey("class") -> formData["class"] as? String ?: ""
            formData.containsKey("profession") -> formData["profession"] as? String ?: ""
            else -> ""
        }
        val concept = formData["concept"] as? String ?: ""
        
        // Ensure disciplines are properly formatted before saving
        val disciplines = (formData["disciplines"] as? List<*>)?.filterIsInstance<Map<String, Any>>()?.map { disc ->
            mapOf(
                "id" to (disc["id"] as? String ?: java.util.UUID.randomUUID().toString()),
                "name" to (disc["name"] as? String ?: ""),
                "value" to (when (val v = disc["value"]) {
                    is Number -> v.toInt()
                    is String -> v.toIntOrNull() ?: 0
                    else -> 0
                })
            )
        } ?: emptyList()
        formData["disciplines"] = disciplines
        
        lifecycleScope.launch {
            try {
                android.util.Log.d("CharEditor", "Saving character with formData: $formData")
                android.util.Log.d("CharEditor", "Disciplines before save: ${formData["disciplines"]}")
                
                val charId = characterId
                if (charId != null) {
                    // Update
                    repository.updateCharacter(charId, mapOf(
                        "name" to name,
                        "clan" to clan,
                        "concept" to concept,
                        "data" to formData
                    ))
                    showMessage("Персонаж сохранен")
                } else {
                    // Create
                    val newChar = Character(
                        name = name,
                        system = system ?: "unknown",
                        clan = clan,
                        concept = concept,
                        data = formData
                    )
                    val createdId = repository.createCharacter(newChar)
                    characterId = createdId
                    showMessage("Character created")
                }
                // Don't navigate away - let user stay on editor
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
