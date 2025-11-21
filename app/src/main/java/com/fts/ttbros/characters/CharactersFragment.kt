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
import com.fts.ttbros.R
import com.fts.ttbros.data.repository.CharacterRepository
import com.fts.ttbros.databinding.FragmentCharactersBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class CharactersFragment : Fragment() {

    private var _binding: FragmentCharactersBinding? = null
    private val binding get() = _binding!!
    private val repository = CharacterRepository()
    private val adapter = CharactersAdapter { character ->
        val bundle = Bundle().apply {
            putString("characterId", character.id)
            putString("system", character.system)
        }
        findNavController().navigate(R.id.action_charactersFragment_to_characterEditorFragment, bundle)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCharactersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.charactersRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.charactersRecyclerView.adapter = adapter

        binding.addCharacterFab.setOnClickListener {
            showSystemSelectionDialog()
        }

        loadCharacters()
    }

    private fun loadCharacters() {
        binding.progressBar.isVisible = true
        lifecycleScope.launch {
            try {
                val characters = repository.getCharacters()
                adapter.submitList(characters)
                binding.emptyView.isVisible = characters.isEmpty()
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Error loading characters: ${e.message}", Snackbar.LENGTH_LONG).show()
            } finally {
                binding.progressBar.isVisible = false
            }
        }
    }

    private fun showSystemSelectionDialog() {
        val systems = arrayOf("Vampire: The Masquerade 5e", "Dungeons & Dragons 5e")
        val systemCodes = arrayOf("vtm_5e", "dnd_5e")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Выберите систему")
            .setItems(systems) { _, which ->
                val selectedSystem = systemCodes[which]
                val bundle = Bundle().apply {
                    putString("characterId", null)
                    putString("system", selectedSystem)
                }
                findNavController().navigate(R.id.action_charactersFragment_to_characterEditorFragment, bundle)
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
