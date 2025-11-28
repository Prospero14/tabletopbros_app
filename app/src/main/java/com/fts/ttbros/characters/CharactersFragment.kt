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
    private val sheetRepository = com.fts.ttbros.data.repository.CharacterSheetRepository()
    
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

        val context = context ?: return
        binding.charactersRecyclerView.layoutManager = LinearLayoutManager(context)
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
                // Clear existing tabs
                binding.tabLayout.removeAllTabs()
                
                // Add single tab "Листы персонажей"
                binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Листы персонажей"))
                
                // Disable tab indicator or make it static since there's only one tab
                // We don't need a listener anymore as there's only one tab
            } catch (e: Exception) {
                android.util.Log.e("CharactersFragment", "Error setting up tabs: ${e.message}", e)
            }
        }
    }
    
    private fun filterList(teamSystem: String?) {
        // Always filter by team system if available
        val filteredList = if (!teamSystem.isNullOrBlank()) {
            allCharacters.filter { it.system == teamSystem }
        } else {
            allCharacters
        }
        
        binding.charactersRecyclerView.adapter = adapter
        adapter.submitList(filteredList)
        binding.emptyView.isVisible = filteredList.isEmpty()
        
        if (filteredList.isEmpty()) {
            if (!teamSystem.isNullOrBlank()) {
                binding.emptyView.text = "Нет персонажей системы $teamSystem. Создайте первого!"
            } else {
                binding.emptyView.text = "Нет персонажей. Создайте первого!"
            }
        }
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
        // Отключаем кнопку сразу для предотвращения множественных нажатий
        binding.addCharacterFab.isEnabled = false
        
        // Показываем меню сразу, без сетевых запросов
        try {
            val context = context ?: run {
                binding.addCharacterFab.isEnabled = true
                return
            }
            
            val profile = userRepository.currentProfile()
            val currentTeam = profile?.teams?.find { it.teamId == profile.currentTeamId }
            val teamSystem = currentTeam?.teamSystem
            
            // Показываем меню сразу с обоими пунктами
            val popup = android.widget.PopupMenu(context, binding.addCharacterFab)
            popup.menu.add(0, 1, 0, "Классический лист персонажа")
            popup.menu.add(0, 2, 1, "Загруженный лист персонажа") // Всегда показываем этот пункт
            
            popup.setOnMenuItemClickListener { item ->
                try {
                    when (item.itemId) {
                        1 -> {
                            if (isAdded && view != null) {
                                showSystemSelectionDialog(teamSystem)
                            }
                        }
                        2 -> {
                            // Показываем диалог выбора билдера с загрузкой
                            if (isAdded && view != null) {
                                showBuilderSelectionDialog()
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("CharactersFragment", "Error in menu item click: ${e.message}", e)
                    if (isAdded && view != null) {
                        Snackbar.make(binding.root, "Ошибка: ${e.message}", Snackbar.LENGTH_SHORT).show()
                    }
                }
                true
            }
            
            popup.setOnDismissListener {
                // Включаем кнопку обратно после закрытия меню
                binding.addCharacterFab.isEnabled = true
            }
            
            popup.show()
        } catch (e: Exception) {
            android.util.Log.e("CharactersFragment", "Error showing popup: ${e.message}", e)
            binding.addCharacterFab.isEnabled = true
            if (isAdded && view != null) {
                Snackbar.make(binding.root, "Ошибка открытия меню: ${e.message}", Snackbar.LENGTH_SHORT).show()
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
    
    private fun showBuilderSelectionDialog() {
        // Show loading indicator
        val loadingSnackbar = Snackbar.make(binding.root, "Загрузка листов...", Snackbar.LENGTH_INDEFINITE)
        loadingSnackbar.show()
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (!isAdded || view == null) {
                    loadingSnackbar.dismiss()
                    return@launch
                }
                
                val userId = Firebase.auth.currentUser?.uid
                if (userId == null) {
                    loadingSnackbar.dismiss()
                    Snackbar.make(binding.root, "Пользователь не авторизован", Snackbar.LENGTH_SHORT).show()
                    return@launch
                }
                
                val sheets = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    sheetRepository.getUserSheets(userId)
                }
                
                loadingSnackbar.dismiss()
                
                if (sheets.isEmpty()) {
                    if (isAdded && view != null) {
                        Snackbar.make(binding.root, "Нет загруженных листов персонажей", Snackbar.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                
                // Создаем список названий билдеров
                val builderNames = sheets.map { it.characterName ?: "Безымянный лист" }
                
                // Проверяем контекст перед показом диалога
                val context = context ?: return@launch
                if (!isAdded) return@launch
                
                // Показываем диалог выбора
                MaterialAlertDialogBuilder(context)
                    .setTitle("Выберите загруженный лист персонажа")
                    .setItems(builderNames.toTypedArray()) { _, which ->
                        try {
                            if (which >= 0 && which < sheets.size) {
                                val selectedSheet = sheets[which]
                                if (selectedSheet.id.isNotBlank()) {
                                    openCharacterEditorFromBuilder(selectedSheet.id)
                                } else {
                                    if (isAdded && view != null) {
                                        Snackbar.make(binding.root, "Ошибка: неверный лист персонажа", Snackbar.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("CharactersFragment", "Error selecting builder: ${e.message}", e)
                            if (isAdded && view != null) {
                                Snackbar.make(binding.root, "Ошибка выбора: ${e.message}", Snackbar.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .setNegativeButton("Отмена", null)
                    .show()
            } catch (e: Exception) {
                loadingSnackbar.dismiss()
                android.util.Log.e("CharactersFragment", "Error showing builder selection: ${e.message}", e)
                if (isAdded && view != null) {
                    Snackbar.make(binding.root, "Ошибка загрузки билдеров: ${e.message}", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun openCharacterEditorFromBuilder(builderId: String) {
        try {
            val bundle = Bundle().apply {
                putString("characterId", null)
                putString("system", null) // Система будет определена из билдера
                putString("builderId", builderId)
            }
            findNavController().navigate(R.id.action_charactersFragment_to_characterEditorFragment, bundle)
        } catch (e: Exception) {
            android.util.Log.e("CharactersFragment", "Navigation error: ${e.message}", e)
            Snackbar.make(binding.root, "Ошибка открытия редактора персонажа", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun showSystemSelectionDialog(teamSystem: String?) {
        try {
            if (!isAdded || view == null) return
            
            val context = context ?: return
            
            // Фильтруем системы: если есть teamSystem, показываем только её
            val availableSystems = if (!teamSystem.isNullOrBlank()) {
                // Показываем только систему команды
                when (teamSystem) {
                    "vtm_5e" -> arrayOf(getString(R.string.vampire_masquerade))
                    "dnd_5e" -> arrayOf(getString(R.string.dungeons_dragons))
                    "viedzmin_2e" -> arrayOf(getString(R.string.viedzmin_2e))
                    "whrp" -> arrayOf("Warhammer Fantasy Roleplay")
                    "wh_darkheresy" -> arrayOf("Warhammer 40k: Dark Heresy")
                    else -> arrayOf(
                        getString(R.string.vampire_masquerade),
                        getString(R.string.dungeons_dragons),
                        getString(R.string.viedzmin_2e),
                        "Warhammer Fantasy Roleplay",
                        "Warhammer 40k: Dark Heresy"
                    )
                }
            } else {
                // Если системы нет, показываем все
                arrayOf(
                    getString(R.string.vampire_masquerade),
                    getString(R.string.dungeons_dragons),
                    getString(R.string.viedzmin_2e),
                    "Warhammer Fantasy Roleplay",
                    "Warhammer 40k: Dark Heresy"
                )
            }
            
            val systems = availableSystems
            // Маппинг систем должен соответствовать отфильтрованному списку
            val systemCodes = if (!teamSystem.isNullOrBlank()) {
                when (teamSystem) {
                    "vtm_5e" -> arrayOf("vtm_5e")
                    "dnd_5e" -> arrayOf("dnd_5e")
                    "viedzmin_2e" -> arrayOf("viedzmin_2e")
                    "whrp" -> arrayOf("whrp")
                    "wh_darkheresy" -> arrayOf("wh_darkheresy")
                    else -> arrayOf("vtm_5e", "dnd_5e", "viedzmin_2e", "whrp", "wh_darkheresy")
                }
            } else {
                arrayOf("vtm_5e", "dnd_5e", "viedzmin_2e", "whrp", "wh_darkheresy")
            }

            if (systems.isEmpty() || systemCodes.isEmpty() || systems.size != systemCodes.size) {
                android.util.Log.e("CharactersFragment", "Systems arrays mismatch: systems=${systems.size}, codes=${systemCodes.size}")
                Snackbar.make(binding.root, "Ошибка: неверная конфигурация систем", Snackbar.LENGTH_SHORT).show()
                return
            }

            MaterialAlertDialogBuilder(context)
                .setTitle(getString(R.string.select_game_system))
                .setItems(systems) { _, which ->
                    try {
                        if (which >= 0 && which < systemCodes.size) {
                            val selectedSystem = systemCodes[which]
                            val bundle = Bundle().apply {
                                putString("characterId", null)
                                putString("system", selectedSystem)
                            }
                            findNavController().navigate(R.id.action_charactersFragment_to_characterEditorFragment, bundle)
                        } else {
                            android.util.Log.e("CharactersFragment", "Invalid system index: $which")
                            if (isAdded && view != null) {
                                Snackbar.make(binding.root, "Ошибка выбора системы", Snackbar.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("CharactersFragment", "Navigation error: ${e.message}", e)
                        if (isAdded && view != null) {
                            Snackbar.make(binding.root, "Ошибка открытия редактора: ${e.message}", Snackbar.LENGTH_SHORT).show()
                        }
                    }
                }
                .show()
        } catch (e: Exception) {
            android.util.Log.e("CharactersFragment", "Error showing system selection: ${e.message}", e)
            if (isAdded && view != null) {
                Snackbar.make(binding.root, "Ошибка: ${e.message}", Snackbar.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun shareCharacter(character: com.fts.ttbros.data.model.Character) {
        viewLifecycleOwner.lifecycleScope.launch {
            val profile = userRepository.currentProfile()
            val currentTeamId = profile?.currentTeamId
            if (profile == null || currentTeamId.isNullOrBlank()) {
                Snackbar.make(binding.root, "You must join a team to share characters", Snackbar.LENGTH_LONG).show()
                return@launch
            }
            
            // Определяем роль из текущей команды
            val currentTeam = profile.teams.find { it.teamId == currentTeamId }
            val role = currentTeam?.role ?: profile.role
            val masterPlayerLabel = if (role == com.fts.ttbros.data.model.UserRole.MASTER) "Player Chat" else "Master Chat"
            
            val options = arrayOf("Team Chat", masterPlayerLabel)
            
            val context = context ?: return@launch
            if (!isAdded) return@launch
            MaterialAlertDialogBuilder(context)
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
                // teamId is already validated in shareCharacter, but add safety check
                val teamId = profile.currentTeamId
                if (teamId.isNullOrBlank()) {
                    Snackbar.make(binding.root, "No team selected", Snackbar.LENGTH_LONG).show()
                    return@launch
                }
                
                chatRepository.sendMessage(
                    teamId,
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
