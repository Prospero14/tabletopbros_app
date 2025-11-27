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
import com.fts.ttbros.utils.SnackbarHelper
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
        try {
            val context = context
            if (context == null) {
                android.util.Log.e("ChatFragment", "Context is null in onViewCreated")
                return
            }
            
            emptyView = view.findViewById(R.id.emptyView) ?: run {
                android.util.Log.e("ChatFragment", "emptyView not found")
                return
            }
            messagesRecyclerView = view.findViewById(R.id.messagesRecyclerView) ?: run {
                android.util.Log.e("ChatFragment", "messagesRecyclerView not found")
                return
            }
            messageInputContainer = view.findViewById(R.id.messageInputContainer) ?: run {
                android.util.Log.e("ChatFragment", "messageInputContainer not found")
                return
            }
            messageEditText = view.findViewById(R.id.messageEditText) ?: run {
                android.util.Log.e("ChatFragment", "messageEditText not found")
                return
            }
            sendButton = view.findViewById(R.id.sendButton) ?: run {
                android.util.Log.e("ChatFragment", "sendButton not found")
                return
            }
            menuButton = view.findViewById(R.id.menuButton) ?: run {
                android.util.Log.e("ChatFragment", "menuButton not found")
                return
            }
            pinnedMessageContainer = view.findViewById(R.id.pinnedMessageContainer) ?: run {
                android.util.Log.e("ChatFragment", "pinnedMessageContainer not found")
                return
            }
            pinnedMessageText = view.findViewById(R.id.pinnedMessageText) ?: run {
                android.util.Log.e("ChatFragment", "pinnedMessageText not found")
                return
            }
            
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
            pollsRecyclerView?.layoutManager = LinearLayoutManager(context)
            pollsRecyclerView?.adapter = pollsAdapter

            sendButton.setOnClickListener { sendMessage() }
            setupMenuButton()
            
            // Запускаем observeProfile только после полной инициализации
            observeProfile()
        } catch (e: Exception) {
            android.util.Log.e("ChatFragment", "Error in onViewCreated: ${e.message}", e)
            try {
                SnackbarHelper.showErrorSnackbar(view, "Error initializing chat: ${e.message}")
            } catch (e2: Exception) {
                android.util.Log.e("ChatFragment", "Error showing error snackbar: ${e2.message}", e2)
            }
        }
    }

    private fun observeProfile() {
        if (!isAdded || view == null) {
            android.util.Log.w("ChatFragment", "Fragment not added, cannot observe profile")
            return
        }
        
        try {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    if (!isAdded || view == null) return@launch
                    
                    val profile = userRepository.currentProfile()
                    val currentTeamId = profile?.currentTeamId
                    if (profile == null || currentTeamId.isNullOrBlank()) {
                        if (isAdded && view != null) {
                            view?.let {
                                SnackbarHelper.showErrorSnackbar(it, getString(R.string.error_group_not_found))
                                    ?.setAction(getString(R.string.join_group)) {
                                        try {
                                            val contextForIntent = context
                                            if (contextForIntent != null && isAdded) {
                                                startActivity(Intent(contextForIntent, GroupActivity::class.java))
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("ChatFragment", "Error starting GroupActivity: ${e.message}", e)
                                        }
                                    }
                            }
                        }
                        disableInput()
                        return@launch
                    }
                    userProfile = profile
                    if (isAdded && view != null) {
                        updateInputAvailability(profile)
                        subscribeToMessages(profile)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ChatFragment", "Error observing profile: ${e.message}", e)
                    if (isAdded && view != null) {
                        view?.let {
                            try {
                                SnackbarHelper.showErrorSnackbar(it, getString(R.string.error_loading_profile, e.message ?: ""))
                            } catch (e2: Exception) {
                                android.util.Log.e("ChatFragment", "Error showing error snackbar: ${e2.message}", e2)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatFragment", "Error starting observeProfile coroutine: ${e.message}", e)
            if (isAdded && view != null) {
                view?.let {
                    try {
                        SnackbarHelper.showErrorSnackbar(it, "Error initializing chat: ${e.message}")
                    } catch (e2: Exception) {
                        android.util.Log.e("ChatFragment", "Error showing error snackbar: ${e2.message}", e2)
                    }
                }
            }
        }
    }

    private fun updateInputAvailability(profile: UserProfile) {
        if (!isAdded || view == null) {
            android.util.Log.w("ChatFragment", "Fragment not added or view is null, cannot update input availability")
            return
        }
        
        // Получаем роль из текущей команды
        val currentTeam = profile.teams.find { it.teamId == profile.currentTeamId }
        val userRole = currentTeam?.role ?: profile.role
        
        val canSend = when (chatType) {
            ChatType.ANNOUNCEMENTS -> userRole == UserRole.MASTER
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
        try {
            val teamId = profile.currentTeamId
            if (teamId.isNullOrBlank()) {
                android.util.Log.w("ChatFragment", "Team ID is null or blank, cannot subscribe to messages")
                return
            }
            
            if (!isAdded || view == null) {
                android.util.Log.w("ChatFragment", "Fragment not added, cannot subscribe to messages")
                return
            }
            
            listenerRegistration?.remove()
            listenerRegistration = chatRepository.observeMessages(
                teamId,
                chatType,
                onEvent = { messages: List<ChatMessage> ->
                    try {
                        // Firestore listener вызывается на фоновом потоке, переключаемся на главный
                        if (!isAdded || view == null) return@onEvent
                        
                        viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                            try {
                                // Проверяем, что фрагмент еще прикреплен и view существует
                                if (!isAdded || view == null) return@launch
                        
                        // Handle pinned messages
                        val pinnedMessages = messages.filter { it.isPinned }
                        val latestPinned = pinnedMessages.maxByOrNull { it.pinnedAt ?: 0L }
                        
                        if (latestPinned != null) {
                            try {
                                if (!isAdded || view == null) return@launch
                                
                                pinnedMessageContainer.isVisible = true
                                pinnedMessageText.text = latestPinned.text
                                pinnedMessageContainer.setOnClickListener {
                                    try {
                                        if (!isAdded || view == null) return@setOnClickListener
                                        
                                        val position = messages.indexOfFirst { it.id == latestPinned.id }
                                        if (position != -1 && position < messages.size) {
                                            messagesRecyclerView.post {
                                                try {
                                                    if (isAdded && view != null && position < adapter.itemCount) {
                                                        messagesRecyclerView.scrollToPosition(position)
                                                    }
                                                } catch (e: Exception) {
                                                    android.util.Log.e("ChatFragment", "Error in post scroll to pinned: ${e.message}", e)
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("ChatFragment", "Error scrolling to pinned message: ${e.message}", e)
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("ChatFragment", "Error setting up pinned message: ${e.message}", e)
                                if (isAdded && view != null) {
                                    pinnedMessageContainer.isVisible = false
                                }
                            }
                        } else {
                            if (isAdded && view != null) {
                                pinnedMessageContainer.isVisible = false
                            }
                        }

                        if (!isAdded || view == null) return@launch
                        
                        adapter.submitList(messages) {
                            try {
                                if (!isAdded || view == null) return@submitList
                                
                                if (messages.isNotEmpty()) {
                                    val lastIndex = messages.lastIndex
                                    if (lastIndex >= 0 && lastIndex < messages.size) {
                                        // Используем post для безопасного скроллинга
                                        messagesRecyclerView.post {
                                            try {
                                                if (isAdded && view != null && lastIndex < adapter.itemCount) {
                                                    messagesRecyclerView.scrollToPosition(lastIndex)
                                                }
                                            } catch (e: Exception) {
                                                android.util.Log.e("ChatFragment", "Error in post scroll: ${e.message}", e)
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("ChatFragment", "Error in submitList callback: ${e.message}", e)
                            }
                        }
                        
                        if (isAdded && view != null) {
                            emptyView.isVisible = messages.isEmpty()
                        }
                            } catch (e: Exception) {
                                android.util.Log.e("ChatFragment", "Error in launch block: ${e.message}", e)
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ChatFragment", "Error processing messages: ${e.message}", e)
                    }
                }
            },
            onError = { error: Exception ->
                try {
                    if (!isAdded || view == null) return@onError
                    
                    viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        try {
                            if (isAdded && view != null) {
                                view?.let {
                                    SnackbarHelper.showErrorSnackbar(it, error.localizedMessage ?: getString(R.string.error_unknown))
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("ChatFragment", "Error showing error snackbar: ${e.message}", e)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ChatFragment", "Error in onError callback: ${e.message}", e)
                }
            }
        )
        
        // Subscribe to polls
        subscribeToPolls(teamId)
        } catch (e: Exception) {
            android.util.Log.e("ChatFragment", "Error subscribing to messages: ${e.message}", e)
            if (isAdded && view != null) {
                view?.let {
                    try {
                        SnackbarHelper.showErrorSnackbar(it, "Error connecting to chat: ${e.message}")
                    } catch (e2: Exception) {
                        android.util.Log.e("ChatFragment", "Error showing error snackbar: ${e2.message}", e2)
                    }
                }
            }
        }
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
                        getString(R.string.error_poll_index_required)
                    } else {
                        getString(R.string.error_loading_polls, e.message ?: "")
                    }
                    if (isAdded && view != null) {
                        view?.let { v ->
                            SnackbarHelper.showErrorSnackbar(v, errorMessage)
                        }
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
                val popupMenu = androidx.appcompat.widget.PopupMenu(context, anchorView, android.view.Gravity.TOP)
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
                                    SnackbarHelper.showErrorSnackbar(it, getString(R.string.error_unknown))
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
                    SnackbarHelper.showErrorSnackbar(it, getString(R.string.error_opening_menu, e.message ?: ""))
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
                            SnackbarHelper.showSuccessSnackbar(it, getString(R.string.poll_created))
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ChatFragment", "Error creating poll: ${e.message}", e)
                        view?.let {
                            SnackbarHelper.showErrorSnackbar(it, getString(R.string.error_creating_poll, e.message ?: ""))
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
                        SnackbarHelper.showErrorSnackbar(it, getString(R.string.error_loading_poll, e.message ?: ""))
                    }
                    return@launch
                }
            }
            
            if (poll == null) {
                view?.let {
                    SnackbarHelper.showErrorSnackbar(it, getString(R.string.poll_not_found))
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
                        SnackbarHelper.showErrorSnackbar(it, getString(R.string.poll_not_found))
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
                    SnackbarHelper.showSuccessSnackbar(it, getString(R.string.vote_recorded))
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
                    SnackbarHelper.showErrorSnackbar(it, getString(R.string.error_voting, e.message ?: ""))
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
                    SnackbarHelper.showSuccessSnackbar(it, getString(R.string.message_pinned))
                }
            } catch (e: Exception) {
                view?.let {
                    SnackbarHelper.showErrorSnackbar(it, getString(R.string.error_pinning_message, e.message ?: ""))
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
                    SnackbarHelper.showSuccessSnackbar(it, getString(R.string.message_unpinned))
                }
            } catch (e: Exception) {
                view?.let {
                    SnackbarHelper.showErrorSnackbar(it, getString(R.string.error_unpinning_message, e.message ?: ""))
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
            .setMessage("Добавить этот документ в материалы мастера?")
            .setPositiveButton("Добавить") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val documentRepository = com.fts.ttbros.data.repository.DocumentRepository()
                        val document = documentRepository.getDocument(attachmentId)
                        
                        if (document != null) {
                            documentRepository.copyDocumentToMaterials(
                                document = document,
                                userId = profile.uid,
                                userName = profile.displayName.ifBlank { profile.email },
                                context = context
                            )
                            
                            view?.let {
                                SnackbarHelper.showSuccessSnackbar(it, getString(R.string.material_added_to_master))
                            }
                        } else {
                            view?.let {
                                SnackbarHelper.showErrorSnackbar(it, getString(R.string.document_not_found))
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ChatFragment", "Error adding material: ${e.message}", e)
                        view?.let {
                            SnackbarHelper.showErrorSnackbar(it, getString(R.string.error_adding_material, e.message ?: ""))
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
                    SnackbarHelper.showSuccessSnackbar(it, getString(R.string.poll_pinned))
                }
            } catch (e: Exception) {
                view?.let {
                    SnackbarHelper.showErrorSnackbar(it, getString(R.string.error_pinning_poll, e.message ?: ""))
                }
            }
        }
    }
    
    private fun handleUnpinPoll(pollId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                pollRepository.unpinPoll(pollId)
                view?.let {
                    SnackbarHelper.showSuccessSnackbar(it, getString(R.string.poll_unpinned))
                }
            } catch (e: Exception) {
                view?.let {
                    SnackbarHelper.showErrorSnackbar(it, getString(R.string.error_unpinning_poll, e.message ?: ""))
                }
            }
        }
    }
    
    private fun sendMessage() {
        if (!isAdded || view == null) {
            android.util.Log.w("ChatFragment", "Fragment not added or view is null, cannot send message")
            return
        }
        
        val profile = userProfile ?: run {
            android.util.Log.w("ChatFragment", "User profile is null, cannot send message")
            view?.let {
                SnackbarHelper.showErrorSnackbar(it, getString(R.string.profile_not_loaded))
            }
            return
        }
        
        val text = messageEditText.text?.toString()?.trim().orEmpty()
        if (text.isBlank()) return
        
        val teamId = profile.currentTeamId ?: run {
            android.util.Log.w("ChatFragment", "Team ID is null, cannot send message")
            view?.let {
                SnackbarHelper.showErrorSnackbar(it, getString(R.string.error_team_not_selected))
            }
            return
        }
        
        // Clear input immediately for better UX
        messageEditText.text?.clear()
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (!isAdded || view == null) return@launch
                
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
                android.util.Log.e("ChatFragment", "Error sending message: ${error.message}", error)
                if (isAdded && view != null) {
                    view?.let {
                        SnackbarHelper.showErrorSnackbar(it, error.localizedMessage ?: getString(R.string.error_unknown))
                    }
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
                SnackbarHelper.showErrorSnackbar(it, getString(R.string.profile_not_loaded))
            }
            return
        }
        val teamId = profile.currentTeamId ?: run {
            android.util.Log.w("ChatFragment", "Team ID is null, cannot show dice roll dialog")
            view?.let {
                SnackbarHelper.showErrorSnackbar(it, getString(R.string.error_team_not_selected))
            }
            return
        }

        try {
            // Получаем роль из текущей команды
            val currentTeam = profile.teams.find { it.teamId == profile.currentTeamId }
            val userRole = currentTeam?.role ?: profile.role
            val isMaster = userRole == UserRole.MASTER
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
                    SnackbarHelper.showErrorSnackbar(it, getString(R.string.error_cannot_open_dialog))
                }
                return
            }
            dialog.show(fragmentManager, "DiceRollDialog")
        } catch (e: IllegalStateException) {
            android.util.Log.e("ChatFragment", "IllegalStateException showing dice roll dialog: ${e.message}", e)
            view?.let {
                SnackbarHelper.showErrorSnackbar(it, getString(R.string.error_cannot_open_dialog_retry))
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatFragment", "Error showing dice roll dialog: ${e.message}", e)
            view?.let {
                SnackbarHelper.showErrorSnackbar(it, getString(R.string.error_opening_dialog, e.message ?: ""))
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
                val currentTeam = profile.teams.find { it.teamId == profile.currentTeamId }
                val userRole = currentTeam?.role ?: profile.role
                if (sendOptions.sendToMaster && userRole != UserRole.MASTER) {
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
                        SnackbarHelper.showSuccessSnackbar(it, getString(R.string.dice_roll_sent))
                    }
                }
            } catch (error: Exception) {
                android.util.Log.e("ChatFragment", "Error sending dice roll result: ${error.message}", error)
                view?.let {
                    SnackbarHelper.showErrorSnackbar(it, error.localizedMessage ?: getString(R.string.error_unknown))
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
                    SnackbarHelper.showSuccessSnackbar(it, getString(R.string.character_imported_success))
                }
            } catch (e: Exception) {
                view?.let {
                    SnackbarHelper.showErrorSnackbar(it, getString(R.string.error_importing_character, e.message ?: ""))
                }
            }
        }
    }

    private fun disableInput() {
        if (!isAdded || view == null) return
        messageInputContainer.isVisible = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listenerRegistration?.remove()
        pollsRecyclerView = null
    }
    
    private fun handleMaterialClick(message: ChatMessage) {
        val attachmentId = message.attachmentId ?: return
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val documentRepository = com.fts.ttbros.data.repository.DocumentRepository()
                val document = documentRepository.getDocument(attachmentId)
                
                if (document != null) {
                    // Скачиваем и открываем файл
                    val contextForDocs = context ?: return@launch
                    if (!isAdded) return@launch
                    val docsDir = java.io.File(contextForDocs.filesDir, "documents")
                    docsDir.mkdirs()
                    val file = java.io.File(docsDir, "${document.fileName}")
                    
                    if (file.exists() && file.length() > 0) {
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
                } else {
                    view?.let {
                        SnackbarHelper.showErrorSnackbar(it, getString(R.string.document_not_found))
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatFragment", "Error opening material: ${e.message}", e)
                view?.let {
                    SnackbarHelper.showErrorSnackbar(it, getString(R.string.error_opening_material, e.message ?: ""))
                }
            }
        }
    }
    
    private fun openDocument(file: java.io.File) {
        try {
            val bundle = Bundle().apply {
                putString("filePath", file.absolutePath)
            }
            
            findNavController().navigate(R.id.action_teamChatFragment_to_pdfViewerFragment, bundle)
        } catch (e: Exception) {
            android.util.Log.e("ChatFragment", "Error opening document: ${e.message}", e)
            view?.let {
                SnackbarHelper.showErrorSnackbar(it, getString(R.string.error_opening_file, e.message ?: ""))
            }
        }
    }

    companion object {
        private const val ARG_CHAT_TYPE = "chatType"
    }
}
