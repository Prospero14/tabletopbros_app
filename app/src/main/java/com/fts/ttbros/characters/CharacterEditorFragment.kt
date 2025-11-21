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
import com.fts.ttbros.characters.form.FormAdapter
import com.fts.ttbros.characters.templates.VtmTemplate
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
    
    // Temporary storage for form data changes
    private val formData = mutableMapOf<String, Any>()

    private val adapter = FormAdapter { key, value ->
        formData[key] = value
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
        
        binding.formRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.formRecyclerView.adapter = adapter
        
        binding.saveButton.setOnClickListener {
            saveCharacter()
        }

        loadCharacter()
    }

    private fun loadCharacter() {
        lifecycleScope.launch {
            try {
                if (characterId != null) {
                    // Edit existing character
                    val char = repository.getCharacter(characterId!!)
                    if (char != null) {
                        currentCharacter = char
                        system = char.system
                        formData.putAll(char.data)
                        // Also put top-level fields into formData for the template to use
                        formData["name"] = char.name
                        formData["clan"] = char.clan
                        formData["concept"] = char.concept
                        renderForm()
                    } else {
                        showError("Character not found")
                        findNavController().navigateUp()
                    }
                } else {
                    // New character
                    renderForm()
                }
            } catch (e: Exception) {
                showError("Error loading character: ${e.message}")
            }
        }
    }

    private fun renderForm() {
        val items = when (system) {
            "vtm_5e" -> VtmTemplate.generate(formData)
            "dnd_5e" -> emptyList() // TODO: Implement DnD
            else -> emptyList()
        }
        adapter.submitList(items)
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
