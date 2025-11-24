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
import androidx.recyclerview.widget.RecyclerView
import com.fts.ttbros.R
import com.fts.ttbros.data.repository.CharacterRepository
import com.fts.ttbros.databinding.FragmentCharactersBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

class CharactersFragment : Fragment() {

    private var _binding: FragmentCharactersBinding? = null
    private val binding get() = _binding ?: throw IllegalStateException("Binding is null. Fragment view may have been destroyed.")
    private val repository = CharacterRepository()
    private val userRepository = com.fts.ttbros.data.repository.UserRepository()
    private val chatRepository = com.fts.ttbros.chat.data.ChatRepository()
    
    private val adapter = CharactersAdapter(
        onCharacterClick = { character ->
            try {
                val bundle = Bundle().apply {
                    putString("characterId", character.id)
                    putString("system", character.system)
                }
                findNavController().navigate(R.id.action_charactersFragment_to_characterEditorFragment, bundle)
            } catch (e: Exception) {
                android.util.Log.e("CharactersFragment", "Navigation error: ${e.message}", e)
                Snackbar.make(binding.root, "Error opening character editor", Snackbar.LENGTH_SHORT).show()
            }
        },
        onShareClick = { character ->
            shareCharacter(character)
        },
        onDeleteClick = { character ->
            showDeleteCharacterDialog(character)
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
            checkSystemAndAddCharacter()
        }
        
        setupTabs()
        loadCharacters()
    }
    
    private fun setupTabs() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val profile = userRepository.currentProfile()
                val currentTeam = profile?.teams?.find { it.teamId == profile.currentTeamId }
                val teamSystem = currentTeam?.teamSystem
                
                // Tab 1: "Все" - все чарники
                binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Все"))
                
                // Tab 2: "[System]" - чарники с фильтром по системе команды
                if (!teamSystem.isNullOrBlank()) {
                    val systemName = when (teamSystem) {
                        "vtm_5e" -> "VTM"
                        "dnd_5e" -> "D&D"
                        "viedzmin_2e" -> "Viedzmin"
                        else -> teamSystem
                    }
                    binding.tabLayout.addTab(binding.tabLayout.newTab().setText(systemName))
                }
                
                binding.tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
                    override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                        filterList(teamSystem)
                    }
                    override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
                    override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
                })
            } catch (e: Exception) {
                android.util.Log.e("CharactersFragment", "Error setting up tabs: ${e.message}", e)
                // Fallback
                binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Все"))
                binding.tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
                    override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                        filterList(null)
                    }
                    override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
                    override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
                })
            }
        }
    }
    
    private fun filterList(teamSystem: String?) {
        val selectedTabPosition = binding.tabLayout.selectedTabPosition
        val filteredList = when (selectedTabPosition) {
            0 -> allCharacters // "Все" tab
            1 -> {
                // Second tab is the team's system
                if (!teamSystem.isNullOrBlank()) {
                    allCharacters.filter { it.system == teamSystem }
                } else {
                    allCharacters
                }
            }
            else -> allCharacters
        }
        binding.charactersRecyclerView.adapter = adapter
        adapter.submitList(filteredList)
        binding.emptyView.isVisible = filteredList.isEmpty()
        binding.emptyView.text = "Нет персонажей. Создайте первого!"
    }
    
    private fun loadCharacters() {
        binding.progressBar.isVisible = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                allCharacters = repository.getCharacters()
                // Get team system for filtering
                val profile = userRepository.currentProfile()
                val currentTeam = profile?.teams?.find { it.teamId == profile.currentTeamId }
                val teamSystem = currentTeam?.teamSystem
                
                filterList(teamSystem)
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Error loading characters: ${e.message}", Snackbar.LENGTH_LONG).show()
            } finally {
                binding.progressBar.isVisible = false
            }
        }
    }
    
    
    private fun checkSystemAndAddCharacter() {
        viewLifecycleOwner.lifecycleScope.launch {
            val profile = userRepository.currentProfile()
            if (profile == null) {
                showSystemSelectionDialog()
                return@launch
            }
            
            val currentTeam = profile.teams.find { it.teamId == profile.currentTeamId }
            val system = currentTeam?.teamSystem
            
            val popup = android.widget.PopupMenu(requireContext(), binding.addCharacterFab)
            popup.menu.add(0, 1, 0, "Классический лист персонажа")
            
            if (!system.isNullOrBlank()) {
                val systemName = when (system) {
                    "vtm_5e" -> "VTM"
                    "dnd_5e" -> "D&D"
                    "viedzmin_2e" -> "Viedzmin"
                    else -> system
                }
                popup.menu.add(0, 2, 1, "Загруженный лист персонажа")
            }
            
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> showSystemSelectionDialog()
                    2 -> {
                        if (system != null) {
                            openCharacterEditor(system)
                        }
                    }
                }
                true
            }
            popup.show()
        }
    }
    
    private fun openCharacterEditor(system: String) {
        try {
            val bundle = Bundle().apply {
                putString("characterId", null)
                putString("system", system)
            }
            findNavController().navigate(R.id.action_charactersFragment_to_characterEditorFragment, bundle)
        } catch (e: Exception) {
            android.util.Log.e("CharactersFragment", "Navigation error: ${e.message}", e)
            Snackbar.make(binding.root, "Error opening character editor", Snackbar.LENGTH_SHORT).show()
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
                try {
                    val bundle = Bundle().apply {
                        putString("characterId", null)
                        putString("system", selectedSystem)
                    }
                    findNavController().navigate(R.id.action_charactersFragment_to_characterEditorFragment, bundle)
                } catch (e: Exception) {
                    android.util.Log.e("CharactersFragment", "Navigation error: ${e.message}", e)
                    Snackbar.make(binding.root, "Error opening character editor", Snackbar.LENGTH_SHORT).show()
                }
            }
            .show()
    }
    
    private fun shareCharacter(character: com.fts.ttbros.data.model.Character) {
        viewLifecycleOwner.lifecycleScope.launch {
            val profile = userRepository.currentProfile()
            if (profile == null || profile.teamId.isNullOrBlank()) {
                Snackbar.make(binding.root, "You must join a team to share characters", Snackbar.LENGTH_LONG).show()
                return@launch
            }
            
            val role = profile.role
            val masterPlayerLabel = if (role == com.fts.ttbros.data.model.UserRole.MASTER) "Player Chat" else "Master Chat"
            
            val options = arrayOf("Team Chat", masterPlayerLabel)
            
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Share '${character.name}' to...")
                .setItems(options) { _, which ->
                    val chatType = when (which) {
                        0 -> com.fts.ttbros.chat.model.ChatType.TEAM
                        1 -> com.fts.ttbros.chat.model.ChatType.MASTER_PLAYER
                        else -> com.fts.ttbros.chat.model.ChatType.TEAM
                    }
                    sendCharacterToChat(profile, character, chatType)
                }
                .show()
        }
    }
    
    private fun sendCharacterToChat(
        profile: com.fts.ttbros.data.model.UserProfile, 
        character: com.fts.ttbros.data.model.Character,
        chatType: com.fts.ttbros.chat.model.ChatType
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                chatRepository.sendMessage(
                    profile.teamId!!,
                    chatType,
                    com.fts.ttbros.chat.model.ChatMessage(
                        senderId = profile.uid,
                        senderName = profile.displayName,
                        text = "Shared character: ${character.name}",
                        type = "character",
                        attachmentId = character.id
                    )
                )
                Snackbar.make(binding.root, "Character shared to ${chatType.name.lowercase().replace("_", " ")}", Snackbar.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Error sharing character: ${e.message}", Snackbar.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showDeleteCharacterDialog(character: com.fts.ttbros.data.model.Character) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Character")
            .setMessage("Are you sure you want to delete '${character.name}'? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteCharacter(character.id)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteCharacter(characterId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                repository.deleteCharacter(characterId)
                Snackbar.make(binding.root, "Character deleted", Snackbar.LENGTH_SHORT).show()
                loadCharacters()
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Error deleting character: ${e.message}", Snackbar.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
