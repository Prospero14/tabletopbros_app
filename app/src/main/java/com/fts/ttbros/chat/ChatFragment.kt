package com.fts.ttbros.chat

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import com.fts.ttbros.GroupActivity
import com.fts.ttbros.R
import com.fts.ttbros.chat.data.ChatRepository
import com.fts.ttbros.chat.model.ChatMessage
import com.fts.ttbros.chat.model.ChatType
import com.fts.ttbros.chat.ui.ChatAdapter
import com.fts.ttbros.data.model.Poll
import com.fts.ttbros.data.model.UserProfile
import com.fts.ttbros.data.model.UserRole
import com.fts.ttbros.data.repository.UserRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

class ChatFragment : Fragment() {

    private lateinit var emptyView: TextView
    private lateinit var messagesRecyclerView: RecyclerView
    private lateinit var messageInputContainer: LinearLayout
    private lateinit var messageEditText: TextInputEditText
    private lateinit var sendButton: MaterialButton
    private lateinit var menuButton: MaterialButton
    private lateinit var pinnedMessageContainer: androidx.cardview.widget.CardView
    private lateinit var pinnedMessageText: TextView
    
    private val chatRepository = ChatRepository()
    private val userRepository = UserRepository()
    private val pollRepository = com.fts.ttbros.data.repository.PollRepository()
    private val auth by lazy { Firebase.auth }
    private var listenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null
    private var userProfile: UserProfile? = null
    private lateinit var chatType: ChatType
    private lateinit var adapter: ChatAdapter
    private lateinit var pollsAdapter: com.fts.ttbros.chat.ui.PollAdapter
    private var pollsRecyclerView: RecyclerView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val typeArg = arguments?.getString(ARG_CHAT_TYPE)
            android.util.Log.d("ChatFragment", "onCreate: typeArg=$typeArg")
            chatType = ChatType.from(typeArg)
            android.util.Log.d("ChatFragment", "onCreate: chatType=$chatType")
        } catch (e: Exception) {
            android.util.Log.e("ChatFragment", "Error in onCreate: ${e.message}", e)
            chatType = ChatType.TEAM // Fallback
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        android.util.Log.d("ChatFragment", "onCreateView")
        return try {
            inflater.inflate(R.layout.fragment_chat, container, false)
        } catch (e: Exception) {
            android.util.Log.e("ChatFragment", "Error inflating layout: ${e.message}", e)
            throw RuntimeException("Failed to inflate ChatFragment layout", e)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        emptyView = view.findViewById(R.id.emptyView)
        messagesRecyclerView = view.findViewById(R.id.messagesRecyclerView)
        messageInputContainer = view.findViewById(R.id.messageInputContainer)
        messageEditText = view.findViewById(R.id.messageEditText)
        sendButton = view.findViewById(R.id.sendButton)
        menuButton = view.findViewById(R.id.menuButton)
        pinnedMessageContainer = view.findViewById(R.id.pinnedMessageContainer)
        pinnedMessageText = view.findViewById(R.id.pinnedMessageText)
        
        adapter = ChatAdapter(
            currentUserId = auth.currentUser?.uid.orEmpty(),
            onImportCharacter = { senderId, characterId, messageId ->
                importCharacter(senderId, characterId, messageId)
            },
            onPinMessage = { messageId ->
                handlePinMessage(messageId)
            },
            onUnpinMessage = { messageId ->
                handleUnpinMessage(messageId)
            },
            onPollClick = { pollId ->
                showPollDetailsDialog(pollId)
            },
            onAddMaterial = { message ->
                handleAddMaterial(message)
            },
            onMaterialClick = { message ->
                handleMaterialClick(message)
            }
        )
        val context = context ?: return
        val layoutManager = LinearLayoutManager(context).apply {
            stackFromEnd = true
        }
        messagesRecyclerView.layoutManager = layoutManager
        messagesRecyclerView.adapter = adapter

        // Setup polls RecyclerView
        pollsRecyclerView = view.findViewById(R.id.pollsRecyclerView)
        pollsAdapter = com.fts.ttbros.chat.ui.PollAdapter(
            currentUserId = auth.currentUser?.uid.orEmpty(),
            currentUserName = auth.currentUser?.displayName ?: auth.currentUser?.email ?: "",
            onVote = { pollId, optionId ->
                handleVote(pollId, optionId)
            },
            onPinPoll = { pollId ->
                handlePinPoll(pollId)
            },
            onUnpinPoll = { pollId ->
                handleUnpinPoll(pollId)
            }
        )
        val contextForPolls = context
        if (contextForPolls != null) {
            pollsRecyclerView?.layoutManager = LinearLayoutManager(contextForPolls)
        }
        pollsRecyclerView?.adapter = pollsAdapter

        sendButton.setOnClickListener { sendMessage() }
        setupMenuButton()
        observeProfile()
    }

    private fun observeProfile() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val profile = userRepository.currentProfile()
                val currentTeamId = profile?.currentTeamId
                if (profile == null || currentTeamId.isNullOrBlank()) {
                    if (isAdded && view != null) {
                        view?.let {
                            Snackbar.make(it, getString(R.string.error_group_not_found), Snackbar.LENGTH_LONG)
                                .setAction(getString(R.string.join_group)) {
                                    try {
                                        val contextForIntent = context
                                        if (contextForIntent != null && isAdded) {
                                            startActivity(Intent(contextForIntent, GroupActivity::class.java))
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("ChatFragment", "Error starting GroupActivity: ${e.message}", e)
                                    }
                                }
                                .show()
                        }
                    }
                    disableInput()
                    return@launch
                }
                userProfile = profile
                if (isAdded) {
                    updateInputAvailability(profile)
                    subscribeToMessages(profile)
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatFragment", "Error observing profile: ${e.message}", e)
                if (isAdded && view != null) {
                    view?.let {
                        Snackbar.make(it, "Ошибка загрузки профиля: ${e.message}", Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun updateInputAvailability(profile: UserProfile) {
        val canSend = when (chatType) {
            ChatType.ANNOUNCEMENTS -> profile.role == UserRole.MASTER
            else -> true
        }
        
        if (!canSend && chatType == ChatType.ANNOUNCEMENTS) {
            messageInputContainer.isVisible = false
        } else {
            messageInputContainer.isVisible = true
            sendButton.isEnabled = canSend
            messageEditText.isEnabled = canSend
            menuButton.isEnabled = canSend
        }
    }



    private fun subscribeToMessages(profile: UserProfile) {
        val teamId = profile.currentTeamId ?: return
        listenerRegistration?.remove()
        listenerRegistration = chatRepository.observeMessages(
            teamId,
            chatType,
            onEvent = { messages ->
                // Handle pinned messages
                val pinnedMessages = messages.filter { it.isPinned }
                val latestPinned = pinnedMessages.maxByOrNull { it.pinnedAt ?: 0L }
                
                if (latestPinned != null) {
                    pinnedMessageContainer.isVisible = true
                    pinnedMessageText.text = latestPinned.text
                    pinnedMessageContainer.setOnClickListener {
                        val position = messages.indexOfFirst { it.id == latestPinned.id }
                        if (position != -1) {
                            messagesRecyclerView.scrollToPosition(position)
                        }
                    }
                } else {
                    pinnedMessageContainer.isVisible = false
                }

                adapter.submitList(messages) {
                    if (messages.isNotEmpty()) {
                        // Only scroll to bottom if we are already near bottom or it's initial load
                        // For now, simple behavior: scroll to bottom on new messages
                        // But we need to be careful not to disrupt scrolling if user is reading up
                        // simple implementation:
                        messagesRecyclerView.scrollToPosition(messages.lastIndex)
                    }
                }
                emptyView.isVisible = messages.isEmpty()
            },
            onError = { error ->
                view?.let {
                    Snackbar.make(it, error.localizedMessage ?: getString(R.string.error_unknown), Snackbar.LENGTH_LONG).show()
                }
            }
        )
        
        // Subscribe to polls
        subscribeToPolls(teamId)
    }
    
    private fun subscribeToPolls(teamId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    pollRepository.getChatPolls(teamId, chatType.key).collect { polls ->
                        // Check if fragment is still attached and view exists before updating UI
                        if (!isAdded || view == null || !viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                            return@collect
                        }
                        android.util.Log.d("ChatFragment", "Loaded ${polls.size} polls for chatType: ${chatType.key}, teamId: $teamId")
                        pollsAdapter.submitList(polls)
                        // Show/hide polls RecyclerView based on whether there are polls
                        pollsRecyclerView?.isVisible = polls.isNotEmpty()
                    }
                } catch (e: Exception) {
                    // Only show error if fragment is still attached and view exists
                    if (!isAdded || view == null || !viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                        android.util.Log.w("ChatFragment", "Error loading polls but fragment not attached: ${e.message}")
                        return@repeatOnLifecycle
                    }
                    android.util.Log.e("ChatFragment", "Error loading polls: ${e.message}", e)
                    val errorMessage = if (e.message?.contains("index") == true || e.message?.contains("Index") == true) {
                        "Ошибка: требуется создать индекс в Firestore для опросов"
                    } else {
                        "Ошибка загрузки опросов: ${e.message}"
                    }
                    view?.let {
                        Snackbar.make(it, errorMessage, Snackbar.LENGTH_LONG).show()
                    }
                    pollsRecyclerView?.isVisible = false
                }
            }
        }
    }
    
    private fun setupMenuButton() {
        menuButton.setOnClickListener { anchorView ->
            if (!isAdded || view == null) {
                android.util.Log.w("ChatFragment", "Fragment not attached, cannot show menu")
                return@setOnClickListener
            }
            
            try {
                val context = context ?: return@setOnClickListener
                if (!isAdded) return@setOnClickListener
                val popupMenu = android.widget.PopupMenu(context, anchorView, android.view.Gravity.TOP)
                popupMenu.menuInflater.inflate(com.fts.ttbros.R.menu.chat_actions_menu, popupMenu.menu)
                
                popupMenu.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        com.fts.ttbros.R.id.action_create_poll -> {
                            showCreatePollDialog()
                            true
                        }
                        com.fts.ttbros.R.id.action_dice_roll -> {
                            try {
                                showDiceRollDialog()
                            } catch (e: Exception) {
                                android.util.Log.e("ChatFragment", "Error on dice roll menu click: ${e.message}", e)
                                this.view?.let {
                                    Snackbar.make(it, "Ошибка: ${e.message}", Snackbar.LENGTH_SHORT).show()
                                }
                            }
                            true
                        }
                        else -> false
                    }
                }
                
                popupMenu.setOnDismissListener {
                    // Меню закрыто
                }
                
                popupMenu.show()
            } catch (e: Exception) {
                android.util.Log.e("ChatFragment", "Error showing menu: ${e.message}", e)
                this.view?.let {
                    Snackbar.make(it, "Ошибка открытия меню: ${e.message}", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun showCreatePollDialog() {
        if (!isAdded) {
            android.util.Log.w("ChatFragment", "Fragment not added, cannot show poll dialog")
            return
        }
        val profile = userProfile ?: run {
            android.util.Log.w("ChatFragment", "User profile is null, cannot show poll dialog")
            return
        }
        val teamId = profile.currentTeamId ?: run {
            android.util.Log.w("ChatFragment", "Team ID is null, cannot show poll dialog")
            return
        }
        
        android.util.Log.d("ChatFragment", "Showing create poll dialog")
        try {
            val context = context ?: return
            if (!isAdded) return
            CreatePollDialog(context) { question, options, isAnonymous ->
                android.util.Log.d("ChatFragment", "Poll dialog callback called: question='$question', options=${options.size}, isAnonymous=$isAnonymous")
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val poll = com.fts.ttbros.data.model.Poll(
                            teamId = teamId,
                            chatType = chatType.key,
                            question = question,
                            options = options,
                            isAnonymous = isAnonymous,
                            createdBy = profile.uid,
                            createdByName = profile.displayName.ifBlank { profile.email }
                        )
                        val pollId = pollRepository.createPoll(poll)
                        android.util.Log.d("ChatFragment", "Poll created with ID: $pollId, chatType: ${chatType.key}, teamId: $teamId")
                        
                        // Send poll message to chat
                        chatRepository.sendMessage(
                            teamId,
                            chatType,
                            ChatMessage(
                                senderId = profile.uid,
                                senderName = profile.displayName.ifBlank { profile.email },
                                text = question,
                                type = "poll",
                                attachmentId = pollId
                            )
                        )

                        view?.let {
                            Snackbar.make(it, getString(R.string.poll_created), Snackbar.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ChatFragment", "Error creating poll: ${e.message}", e)
                        view?.let {
                            Snackbar.make(it, "Error creating poll: ${e.message}", Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }.show()
        } catch (e: Exception) {
            android.util.Log.e("ChatFragment", "Error showing create poll dialog: ${e.message}", e)
        }
    }

    private fun showPollDetailsDialog(pollId: String) {
        if (!isAdded) return
        val profile = userProfile ?: return
        
        viewLifecycleOwner.lifecycleScope.launch {
            if (!isAdded || view == null) return@launch
            // Try to find in current list first
            var poll = pollsAdapter.currentList.find { it.id == pollId }
            
            if (poll == null) {
                // Fetch from repo
                try {
                    poll = pollRepository.getPoll(pollId)
                } catch (e: Exception) {
                    android.util.Log.e("ChatFragment", "Error loading poll: ${e.message}", e)
                    view?.let {
                        Snackbar.make(it, "Error loading poll: ${e.message}", Snackbar.LENGTH_SHORT).show()
                    }
                    return@launch
                }
            }
            
            if (poll == null) {
                view?.let {
                    Snackbar.make(it, "Poll not found", Snackbar.LENGTH_SHORT).show()
                }
                return@launch
            }

            if (!isAdded) return@launch
            val context = context ?: return@launch
            val dialogView = LayoutInflater.from(context).inflate(R.layout.item_poll, null)
            
            // Setup view - poll is guaranteed to be non-null here (checked above)
            val questionTextView = dialogView.findViewById<TextView>(R.id.pollQuestionTextView)
            val creatorTextView = dialogView.findViewById<TextView>(R.id.pollCreatorTextView)
            val optionsRecyclerView = dialogView.findViewById<RecyclerView>(R.id.pollOptionsRecyclerView)
            val pinnedIcon = dialogView.findViewById<android.view.View>(R.id.pollPinnedIcon)
            
            // poll is guaranteed to be non-null here (checked above)
            val nonNullPoll = poll ?: return@launch
            questionTextView.text = nonNullPoll.question
            creatorTextView.text = getString(R.string.created_by, nonNullPoll.createdByName)
            pinnedIcon.isVisible = nonNullPoll.isPinned
            
            var currentPoll: Poll = nonNullPoll
            var isUpdating = false
            
            fun updateAdapter() {
                // Check if fragment is still attached and view exists
                if (isUpdating || !isAdded || view == null || !viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    return
                }
                isUpdating = true
                try {
                    // Create local copy to avoid smart cast issues in closure
                    val pollCopy: Poll = currentPoll
                    val newAdapter = com.fts.ttbros.chat.ui.PollOptionAdapter(
                        poll = pollCopy,
                        currentUserId = profile.uid,
                        onVote = { optionId ->
                            // Check if fragment is still attached before processing vote
                            if (!isAdded || view == null || !viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                                return@PollOptionAdapter
                            }
                            // Use local copy inside closure to avoid smart cast issues
                            val pollToUpdate: Poll = currentPoll
                            android.util.Log.d("ChatFragment", "Vote clicked in dialog for poll: ${pollToUpdate.id}, option: $optionId")
                            // Optimistically update poll in dialog immediately
                            val updatedVotes = pollToUpdate.votes.toMutableMap().apply {
                                put(profile.uid, optionId)
                            }
                            val updatedVoterNames = if (!pollToUpdate.isAnonymous) {
                                pollToUpdate.voterNames.toMutableMap().apply {
                                    put(profile.uid, profile.displayName.ifBlank { profile.email })
                                }
                            } else {
                                pollToUpdate.voterNames
                            }
                            currentPoll = pollToUpdate.copy(
                                votes = updatedVotes,
                                voterNames = updatedVoterNames
                            )
                            // Update adapter with new poll data to show vote immediately
                            updateAdapter()
                            // Also update in main list
                            handleVote(currentPoll.id, optionId)
                        }
                    )
                    optionsRecyclerView.adapter = newAdapter
                    newAdapter.submitList(pollCopy.options)
                } finally {
                    isUpdating = false
                }
            }
            
            optionsRecyclerView.layoutManager = LinearLayoutManager(context)
            updateAdapter()

            com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }
    
    private fun handleVote(pollId: String, optionId: String) {
        if (!isAdded) return
        val profile = userProfile ?: return
        val view = view ?: return
        
        // Optimistically update UI immediately
        val currentPolls = pollsAdapter.currentList.toMutableList()
        val pollIndex = currentPolls.indexOfFirst { it.id == pollId }
        var originalPoll: Poll? = null
        
        if (pollIndex != -1) {
            originalPoll = currentPolls[pollIndex]
            // Create updated poll with new vote
            val updatedVotes = originalPoll.votes.toMutableMap().apply {
                put(profile.uid, optionId)
            }
            val updatedVoterNames = if (!originalPoll.isAnonymous) {
                originalPoll.voterNames.toMutableMap().apply {
                    put(profile.uid, profile.displayName.ifBlank { profile.email })
                }
            } else {
                originalPoll.voterNames
            }
            
            val updatedPoll = originalPoll.copy(
                votes = updatedVotes,
                voterNames = updatedVoterNames
            )
            
            currentPolls[pollIndex] = updatedPoll
            pollsAdapter.submitList(currentPolls)
            android.util.Log.d("ChatFragment", "Optimistically updated poll UI - vote immediately visible")
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            if (!isAdded || view == null) return@launch
            try {
                // Try to find poll in current list first
                var poll = pollsAdapter.currentList.find { it.id == pollId }
                
                // If not found, try to fetch from repository
                if (poll == null) {
                    android.util.Log.d("ChatFragment", "Poll not in list, fetching from repository: $pollId")
                    poll = pollRepository.getPoll(pollId)
                }
                
                if (poll == null) {
                    android.util.Log.e("ChatFragment", "Poll not found: $pollId")
                    // Revert optimistic update
                    if (pollIndex != -1 && originalPoll != null) {
                        val revertedPolls = pollsAdapter.currentList.toMutableList()
                        revertedPolls[pollIndex] = originalPoll
                        pollsAdapter.submitList(revertedPolls)
                    }
                    view?.let {
                        Snackbar.make(it, "Poll not found", Snackbar.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                
                android.util.Log.d("ChatFragment", "Voting for poll: $pollId, option: $optionId, isAnonymous: ${poll.isAnonymous}")
                
                pollRepository.vote(
                    pollId = pollId,
                    userId = profile.uid,
                    userName = profile.displayName.ifBlank { profile.email },
                    optionId = optionId,
                    isAnonymous = poll.isAnonymous
                )
                // UI already updated optimistically, just show confirmation
                view?.let {
                    Snackbar.make(it, getString(R.string.vote_recorded), Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatFragment", "Error voting: ${e.message}", e)
                // Revert optimistic update on error
                if (pollIndex != -1 && originalPoll != null) {
                    val revertedPolls = pollsAdapter.currentList.toMutableList()
                    revertedPolls[pollIndex] = originalPoll
                    pollsAdapter.submitList(revertedPolls)
                    android.util.Log.d("ChatFragment", "Reverted optimistic update due to error")
                }
                view?.let {
                    Snackbar.make(it, "Error voting: ${e.message}", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun handlePinMessage(messageId: String) {
        val profile = userProfile ?: return
        val teamId = profile.currentTeamId ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                chatRepository.pinMessage(teamId, chatType, messageId, profile.uid)
                view?.let {
                    Snackbar.make(it, getString(R.string.message_pinned), Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                view?.let {
                    Snackbar.make(it, "Error pinning message: ${e.message}", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun handleUnpinMessage(messageId: String) {
        val profile = userProfile ?: return
        val teamId = profile.currentTeamId ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                chatRepository.unpinMessage(teamId, chatType, messageId)
                view?.let {
                    Snackbar.make(it, getString(R.string.message_unpinned), Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                view?.let {
                    Snackbar.make(it, "Error unpinning message: ${e.message}", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun handleAddMaterial(message: ChatMessage) {
        val profile = userProfile ?: return
        val teamId = profile.currentTeamId ?: return
        val attachmentId = message.attachmentId ?: return
        
        // Показываем диалог подтверждения
        val context = context ?: return
        if (!isAdded || view == null) return
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
            .setTitle("Добавить материал?")
            .setMessage("Хотите добавить этот материал в раздел 'Материалы от мастера'?")
            .setPositiveButton("Добавить") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        // Получаем документ по attachmentId из Firestore
                        val documentRepository = com.fts.ttbros.data.repository.DocumentRepository()
                        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        val docSnapshot = firestore.collection("teams")
                            .document(teamId)
                            .collection("documents")
                            .document(attachmentId)
                            .get()
                            .await()
                        
                        if (docSnapshot.exists()) {
                            val targetDocument = docSnapshot.toObject(com.fts.ttbros.data.model.Document::class.java)
                                ?.copy(id = docSnapshot.id)
                            
                            if (targetDocument != null) {
                                // Скачиваем файл и загружаем заново как материал для текущего пользователя
                                val downloadUrl = targetDocument.downloadUrl
                                val fileName = targetDocument.fileName
                                val title = targetDocument.title
                                
                                // Скачиваем файл во временный файл
                                val contextForFile = context ?: return@launch
                                if (!isAdded) return@launch
                                val tempFile = java.io.File(contextForFile.cacheDir, "temp_material_${System.currentTimeMillis()}.pdf")
                                java.net.URL(downloadUrl).openStream().use { input ->
                                    java.io.FileOutputStream(tempFile).use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                
                                // Загружаем как материал
                                val uri = android.net.Uri.fromFile(tempFile)
                                documentRepository.uploadDocument(
                                    teamId = teamId,
                                    uri = uri,
                                    title = title,
                                    fileName = fileName,
                                    userId = profile.uid,
                                    userName = profile.displayName,
                                    context = contextForFile,
                                    isMaterial = true
                                )
                                
                                // Удаляем временный файл
                                tempFile.delete()
                                
                                view?.let {
                                    Snackbar.make(it, "Материал добавлен в 'Материалы от мастера'", Snackbar.LENGTH_SHORT).show()
                                }
                            } else {
                                view?.let {
                                    Snackbar.make(it, "Документ не найден", Snackbar.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            view?.let {
                                Snackbar.make(it, "Документ не найден", Snackbar.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ChatFragment", "Error adding material: ${e.message}", e)
                        view?.let {
                            Snackbar.make(it, "Ошибка добавления материала: ${e.message}", Snackbar.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    private fun handlePinPoll(pollId: String) {
        val profile = userProfile ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                pollRepository.pinPoll(pollId, profile.uid)
                view?.let {
                    Snackbar.make(it, getString(R.string.poll_pinned), Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                view?.let {
                    Snackbar.make(it, "Error pinning poll: ${e.message}", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun handleUnpinPoll(pollId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                pollRepository.unpinPoll(pollId)
                view?.let {
                    Snackbar.make(it, getString(R.string.poll_unpinned), Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                view?.let {
                    Snackbar.make(it, "Error unpinning poll: ${e.message}", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun sendMessage() {
        val profile = userProfile ?: return
        val text = messageEditText.text?.toString()?.trim().orEmpty()
        if (text.isBlank()) return
        messageEditText.text?.clear()
        val teamId = profile.currentTeamId ?: return
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                chatRepository.sendMessage(
                    teamId,
                    chatType,
                    ChatMessage(
                        senderId = profile.uid,
                        senderName = profile.displayName.ifBlank { profile.email },
                        text = text
                    )
                )
            } catch (error: Exception) {
                view?.let {
                    Snackbar.make(it, error.localizedMessage ?: getString(R.string.error_unknown), Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showDiceRollDialog() {
        if (!isAdded || view == null) {
            android.util.Log.w("ChatFragment", "Fragment not added or view is null, cannot show dice roll dialog")
            return
        }
        val profile = userProfile ?: run {
            android.util.Log.w("ChatFragment", "User profile is null, cannot show dice roll dialog")
            view?.let {
                Snackbar.make(it, "Профиль пользователя не загружен", Snackbar.LENGTH_SHORT).show()
            }
            return
        }
        val teamId = profile.currentTeamId ?: run {
            android.util.Log.w("ChatFragment", "Team ID is null, cannot show dice roll dialog")
            view?.let {
                Snackbar.make(it, "Команда не выбрана", Snackbar.LENGTH_SHORT).show()
            }
            return
        }

        try {
            val isMaster = profile.role == UserRole.MASTER
            val dialog = DiceRollDialog(isMaster) { rollResult, sendOptions ->
                if (isAdded && view != null) {
                    sendDiceRollResult(rollResult, sendOptions, teamId, profile)
                }
            }
            // Проверяем что fragmentManager доступен
            val fragmentManager = parentFragmentManager
            if (fragmentManager.isStateSaved) {
                android.util.Log.w("ChatFragment", "FragmentManager state is saved, cannot show dialog")
                view?.let {
                    Snackbar.make(it, "Не удалось открыть диалог", Snackbar.LENGTH_SHORT).show()
                }
                return
            }
            dialog.show(fragmentManager, "DiceRollDialog")
        } catch (e: IllegalStateException) {
            android.util.Log.e("ChatFragment", "IllegalStateException showing dice roll dialog: ${e.message}", e)
            view?.let {
                Snackbar.make(it, "Не удалось открыть диалог. Попробуйте еще раз.", Snackbar.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatFragment", "Error showing dice roll dialog: ${e.message}", e)
            view?.let {
                Snackbar.make(it, "Ошибка открытия диалога: ${e.message}", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendDiceRollResult(
        rollResult: String,
        sendOptions: DiceRollSendOptions,
        teamId: String,
        profile: UserProfile
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                var sentCount = 0
                
                // Отправляем в общий чат (TEAM), если выбрано
                if (sendOptions.sendToTeam) {
                    chatRepository.sendMessage(
                        teamId,
                        ChatType.TEAM,
                        ChatMessage(
                            senderId = profile.uid,
                            senderName = profile.displayName.ifBlank { profile.email },
                            text = rollResult
                        )
                    )
                    sentCount++
                }

                // Отправляем мастеру (MASTER_PLAYER), если выбрано
                if (sendOptions.sendToMaster && profile.role != UserRole.MASTER) {
                    chatRepository.sendMessage(
                        teamId,
                        ChatType.MASTER_PLAYER,
                        ChatMessage(
                            senderId = profile.uid,
                            senderName = profile.displayName.ifBlank { profile.email },
                            text = rollResult
                        )
                    )
                    sentCount++
                }

                if (sentCount > 0) {
                    view?.let {
                        Snackbar.make(it, "Результат броска отправлен", Snackbar.LENGTH_SHORT).show()
                    }
                }
            } catch (error: Exception) {
                android.util.Log.e("ChatFragment", "Error sending dice roll result: ${error.message}", error)
                view?.let {
                    Snackbar.make(it, error.localizedMessage ?: getString(R.string.error_unknown), Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun importCharacter(senderId: String, characterId: String, messageId: String) {
        val profile = userProfile ?: return
        val teamId = profile.currentTeamId ?: return
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val characterRepo = com.fts.ttbros.data.repository.CharacterRepository()
                characterRepo.copyCharacter(senderId, characterId)
                
                // Mark as imported
                chatRepository.markAsImported(teamId, chatType, messageId, profile.uid)
                
                view?.let {
                    Snackbar.make(it, "Character imported successfully!", Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                view?.let {
                    Snackbar.make(it, "Error importing character: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun disableInput() {
        messageInputContainer.isVisible = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listenerRegistration?.remove()
        pollsRecyclerView = null
    }
    
    private fun handleMaterialClick(message: ChatMessage) {
        val profile = userProfile ?: return
        val teamId = profile.currentTeamId ?: return
        val attachmentId = message.attachmentId ?: return
        
        // Показываем диалог с опциями: открыть или добавить в материалы
        val context = context ?: return
        if (!isAdded || view == null) return
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
            .setTitle("Материал")
            .setItems(arrayOf("Открыть", "Добавить в материалы")) { _, which ->
                when (which) {
                    0 -> {
                        // Открыть материал
                        openMaterial(teamId, attachmentId)
                    }
                    1 -> {
                        // Добавить в материалы
                        handleAddMaterial(message)
                    }
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    private fun openMaterial(teamId: String, documentId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val docSnapshot = firestore.collection("teams")
                    .document(teamId)
                    .collection("documents")
                    .document(documentId)
                    .get()
                    .await()
                
                if (docSnapshot.exists()) {
                    val document = docSnapshot.toObject(com.fts.ttbros.data.model.Document::class.java)
                        ?.copy(id = docSnapshot.id)
                    
                    if (document != null) {
                        // Скачиваем и открываем файл
                        val contextForDocs = context ?: return@launch
                        if (!isAdded) return@launch
                        val docsDir = java.io.File(contextForDocs.filesDir, "documents")
                        docsDir.mkdirs()
                        val file = java.io.File(docsDir, "${document.id}_${document.fileName}")
                        
                        if (file.exists()) {
                            openDocument(file)
                        } else {
                            // Скачиваем файл
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                java.net.URL(document.downloadUrl).openStream().use { input ->
                                    java.io.FileOutputStream(file).use { output ->
                                        input.copyTo(output)
                                    }
                                }
                            }
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                if (isAdded && view != null) {
                                    openDocument(file)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatFragment", "Error opening material: ${e.message}", e)
                view?.let {
                    Snackbar.make(it, "Ошибка открытия материала: ${e.message}", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun openDocument(file: java.io.File) {
        val context = context ?: return
        if (!isAdded || view == null) return
        
        try {
            if (!file.exists() || !file.canRead() || file.length() == 0L) {
                view?.let {
                    Snackbar.make(it, "Файл недоступен", Snackbar.LENGTH_SHORT).show()
                }
                return
            }
            
            val bundle = Bundle().apply {
                putString("filePath", file.absolutePath)
            }
            
            findNavController().navigate(R.id.action_teamChatFragment_to_pdfViewerFragment, bundle)
        } catch (e: Exception) {
            android.util.Log.e("ChatFragment", "Error opening document: ${e.message}", e)
            view?.let {
                Snackbar.make(it, "Ошибка открытия файла: ${e.message}", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val ARG_CHAT_TYPE = "chatType"
    }
}

