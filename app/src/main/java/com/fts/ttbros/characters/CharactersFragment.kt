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
import com.fts.ttbros.data.model.CharacterSheet
import com.fts.ttbros.data.repository.CharacterRepository
import com.fts.ttbros.data.repository.CharacterSheetRepository
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
    private val sheetRepository = CharacterSheetRepository()
    
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
    private var allBuilders: List<CharacterSheet> = emptyList()
    private val buildersAdapter = BuildersAdapter(
        onBuilderClick = { builder -> createCharacterFromBuilder(builder) },
        onBuilderLongClick = { builder -> showDeleteBuilderDialog(builder) }
    )

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
        loadTeamCharacters()
    }
    
    private fun setupTabs() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val profile = userRepository.currentProfile()
                val currentTeam = profile?.teams?.find { it.teamId == profile.currentTeamId }
                val teamSystem = currentTeam?.teamSystem
                
                // Always add "All" tab
                binding.tabLayout.addTab(binding.tabLayout.newTab().setText("All"))
                
                // Add tab for team's system if it exists
                if (!teamSystem.isNullOrBlank()) {
                    val systemName = when (teamSystem) {
                        "vtm_5e" -> getString(R.string.vampire_masquerade)
                        "dnd_5e" -> getString(R.string.dungeons_dragons)
                        "viedzmin_2e" -> getString(R.string.viedzmin_2e)
                        else -> teamSystem
                    }
                    binding.tabLayout.addTab(binding.tabLayout.newTab().setText(systemName))
                }
                
                // Add "Мой билдер" tab
                binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Мой билдер"))
                
                binding.tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
                    override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                        val tabPosition = tab?.position ?: 0
                        if (tabPosition == binding.tabLayout.tabCount - 1) {
                            // Last tab is "Мой чарник"
                            loadTeamCharacters()
                        } else {
                            filterList(teamSystem)
                        }
                    }
                    override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
                    override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
                })
            } catch (e: Exception) {
                android.util.Log.e("CharactersFragment", "Error setting up tabs: ${e.message}", e)
                // Fallback: just show "All" tab and "Мой чарник"
                binding.tabLayout.addTab(binding.tabLayout.newTab().setText("All"))
                binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Мой чарник"))
                binding.tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
                    override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                        val tabPosition = tab?.position ?: 0
                        if (tabPosition == binding.tabLayout.tabCount - 1) {
                            loadTeamCharacters()
                        } else {
                            filterList(null)
                        }
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
            0 -> allCharacters // "All" tab
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
    
    private fun showTeamCharacters() {
        binding.charactersRecyclerView.adapter = buildersAdapter as RecyclerView.Adapter<RecyclerView.ViewHolder>
        buildersAdapter.submitList(allBuilders)
        binding.emptyView.isVisible = allBuilders.isEmpty()
        binding.emptyView.text = "Нет командных чарников. Загрузите PDF лист персонажа в разделе 'Листы персонажей'"
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
                
                val selectedTabPosition = binding.tabLayout.selectedTabPosition
                if (selectedTabPosition != binding.tabLayout.tabCount - 1) {
                    filterList(teamSystem)
                }
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Error loading characters: ${e.message}", Snackbar.LENGTH_LONG).show()
            } finally {
                binding.progressBar.isVisible = false
            }
        }
    }
    
    private fun loadTeamCharacters() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val userId = Firebase.auth.currentUser?.uid ?: return@launch
                // Загружаем командные чарники (isTemplate = false)
                allBuilders = sheetRepository.getUserSheets(userId).filter { !it.isTemplate }
                
                val selectedTabPosition = binding.tabLayout.selectedTabPosition
                if (selectedTabPosition == binding.tabLayout.tabCount - 1) {
                    showTeamCharacters()
                }
            } catch (e: Exception) {
                android.util.Log.e("CharactersFragment", "Error loading team characters: ${e.message}", e)
            }
        }
    }
    
    private fun createCharacterFromBuilder(builder: CharacterSheet) {
        try {
            val bundle = Bundle().apply {
                putString("characterId", null)
                putString("system", builder.system)
                putString("builderId", builder.id) // Передаем ID билдера
            }
            findNavController().navigate(R.id.action_charactersFragment_to_characterEditorFragment, bundle)
        } catch (e: Exception) {
            android.util.Log.e("CharactersFragment", "Navigation error: ${e.message}", e)
            Snackbar.make(binding.root, "Ошибка открытия редактора персонажа", Snackbar.LENGTH_SHORT).show()
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
            
            if (!system.isNullOrBlank()) {
                openCharacterEditor(system)
            } else {
                showSystemSelectionDialog()
            }
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
    
    private fun showDeleteBuilderDialog(builder: CharacterSheet) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Удалить билдер")
            .setMessage("Вы уверены, что хотите удалить '${builder.characterName}'? Это действие нельзя отменить.")
            .setPositiveButton("Удалить") { _, _ ->
                deleteBuilder(builder)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    private fun deleteBuilder(builder: CharacterSheet) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                sheetRepository.deleteSheet(builder.id)
                Snackbar.make(binding.root, "Билдер удалён", Snackbar.LENGTH_SHORT).show()
                loadTeamCharacters()
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Ошибка удаления: ${e.message}", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/**
 * Адаптер для отображения билдеров персонажей
 */
class BuildersAdapter(
    private val onBuilderClick: (CharacterSheet) -> Unit,
    private val onBuilderLongClick: (CharacterSheet) -> Unit
) : RecyclerView.Adapter<BuildersAdapter.BuilderViewHolder>() {
    
    private var builders: List<CharacterSheet> = emptyList()
    
    fun submitList(newBuilders: List<CharacterSheet>) {
        builders = newBuilders
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BuilderViewHolder {
        val binding = com.fts.ttbros.databinding.ItemCharacterBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return BuilderViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: BuilderViewHolder, position: Int) {
        holder.bind(builders[position])
    }
    
    override fun getItemCount() = builders.size
    
    inner class BuilderViewHolder(
        private val binding: com.fts.ttbros.databinding.ItemCharacterBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onBuilderClick(builders[position])
                }
            }
            binding.root.setOnLongClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onBuilderLongClick(builders[position])
                    true
                } else {
                    false
                }
            }
            // Скрываем кнопку share для билдеров
            binding.shareButton.isVisible = false
        }
        
        fun bind(builder: CharacterSheet) {
            binding.characterNameTextView.text = builder.characterName.ifBlank { "Безымянный билдер" }
            val context = binding.root.context
            binding.systemTextView.text = when(builder.system) {
                "vtm_5e" -> context.getString(com.fts.ttbros.R.string.vampire_masquerade)
                "dnd_5e" -> context.getString(com.fts.ttbros.R.string.dungeons_dragons)
                "viedzmin_2e" -> context.getString(com.fts.ttbros.R.string.viedzmin_2e)
                else -> builder.system
            }
            binding.clanTextView.text = "Билдер • Нажмите для создания персонажа"
        }
    }
}
