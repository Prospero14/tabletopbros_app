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
    private val userRepository = com.fts.ttbros.data.repository.UserRepository()
    private val chatRepository = com.fts.ttbros.chat.data.ChatRepository()
    
    private val adapter = CharactersAdapter(
        onCharacterClick = { character ->
            val bundle = Bundle().apply {
                putString("characterId", character.id)
                putString("system", character.system)
            }
            findNavController().navigate(R.id.action_charactersFragment_to_characterEditorFragment, bundle)
        },
        onShareClick = { character ->
            shareCharacter(character)
        }
    )
    
    private var allCharacters: List<com.fts.ttbros.data.model.Character> = emptyList()

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
        
        setupTabs()
        loadCharacters()
    }
    
    private fun setupTabs() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("All"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(getString(R.string.vampire_masquerade)))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(getString(R.string.dungeons_dragons)))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(getString(R.string.viedzmin_2e)))
        
        binding.tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                filterList()
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })
    }
    
    private fun filterList() {
        val selectedTabPosition = binding.tabLayout.selectedTabPosition
        val filteredList = when (selectedTabPosition) {
            1 -> allCharacters.filter { it.system == "vtm_5e" }
            2 -> allCharacters.filter { it.system == "dnd_5e" }
            3 -> allCharacters.filter { it.system == "viedzmin_2e" }
            else -> allCharacters
        }
        adapter.submitList(filteredList)
        binding.emptyView.isVisible = filteredList.isEmpty()
    }

    private fun loadCharacters() {
        binding.progressBar.isVisible = true
        lifecycleScope.launch {
            try {
                allCharacters = repository.getCharacters()
                filterList()
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Error loading characters: ${e.message}", Snackbar.LENGTH_LONG).show()
            } finally {
                binding.progressBar.isVisible = false
            }
        }
    }

    private fun showSystemSelectionDialog() {
        val systems = arrayOf(
            getString(R.string.vampire_masquerade),
            getString(R.string.dungeons_dragons),
            getString(R.string.viedzmin_2e)
        )
        val systemCodes = arrayOf("vtm_5e", "dnd_5e", "viedzmin_2e")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.select_game_system))
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
    
    private fun shareCharacter(character: com.fts.ttbros.data.model.Character) {
        lifecycleScope.launch {
            val profile = userRepository.currentProfile()
            if (profile == null || profile.teamId.isNullOrBlank()) {
                Snackbar.make(binding.root, "You must join a team to share characters", Snackbar.LENGTH_LONG).show()
                return@launch
            }
            
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Share Character")
                .setMessage("Share '${character.name}' to team chat?")
                .setPositiveButton("Share") { _, _ ->
                    sendCharacterToChat(profile, character)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
    
    private fun sendCharacterToChat(profile: com.fts.ttbros.data.model.UserProfile, character: com.fts.ttbros.data.model.Character) {
        lifecycleScope.launch {
            try {
                chatRepository.sendMessage(
                    profile.teamId!!,
                    com.fts.ttbros.chat.model.ChatType.TEAM,
                    com.fts.ttbros.chat.model.ChatMessage(
                        senderId = profile.uid,
                        senderName = profile.displayName,
                        text = "Shared character: ${character.name}",
                        type = "character",
                        attachmentId = character.id
                    )
                )
                Snackbar.make(binding.root, "Character shared to chat", Snackbar.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Error sharing character: ${e.message}", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
