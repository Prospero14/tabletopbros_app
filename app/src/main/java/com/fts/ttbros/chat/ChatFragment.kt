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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.fts.ttbros.GroupActivity
import com.fts.ttbros.R
import com.fts.ttbros.chat.data.ChatRepository
import com.fts.ttbros.chat.model.ChatMessage
import com.fts.ttbros.chat.model.ChatType
import com.fts.ttbros.chat.ui.ChatAdapter
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
    private lateinit var createPollButton: MaterialButton
    
    private val chatRepository = ChatRepository()
    private val userRepository = UserRepository()
    private val pollRepository = com.fts.ttbros.data.repository.PollRepository()
    private val auth by lazy { Firebase.auth }
    private var listenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null
    private var userProfile: UserProfile? = null
    private lateinit var chatType: ChatType
    private lateinit var adapter: ChatAdapter
    private lateinit var pollsAdapter: com.fts.ttbros.chat.ui.PollAdapter

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
        createPollButton = view.findViewById(R.id.createPollButton)
        
        adapter = ChatAdapter(
            currentUserId = auth.currentUser?.uid.orEmpty(),
            onImportCharacter = { senderId, characterId ->
                importCharacter(senderId, characterId)
            },
            onPinMessage = { messageId ->
                handlePinMessage(messageId)
            },
            onUnpinMessage = { messageId ->
                handleUnpinMessage(messageId)
            },
            onPollClick = { pollId ->
                showPollDetailsDialog(pollId)
            }
        )
        val layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        messagesRecyclerView.layoutManager = layoutManager
        messagesRecyclerView.adapter = adapter

        // Setup polls RecyclerView
        val pollsRecyclerView: RecyclerView = view.findViewById(R.id.pollsRecyclerView)
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
        pollsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        pollsRecyclerView.adapter = pollsAdapter

        sendButton.setOnClickListener { sendMessage() }
        createPollButton.setOnClickListener { showCreatePollDialog() }
        observeProfile()
    }

    private fun observeProfile() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val profile = userRepository.currentProfile()
                if (profile == null || profile.teamId.isNullOrBlank()) {
                    if (isAdded && view != null) {
                        view?.let {
                            Snackbar.make(it, getString(R.string.error_group_not_found), Snackbar.LENGTH_LONG)
                                .setAction(getString(R.string.join_group)) {
                                    try {
                                        startActivity(Intent(requireContext(), GroupActivity::class.java))
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
            createPollButton.isEnabled = canSend
        }
    }

    private fun subscribeToMessages(profile: UserProfile) {
        val teamId = profile.teamId ?: return
        listenerRegistration?.remove()
        listenerRegistration = chatRepository.observeMessages(
            teamId,
            chatType,
            onEvent = { messages ->
                adapter.submitList(messages) {
                    if (messages.isNotEmpty()) {
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
            try {
                pollRepository.getChatPolls(teamId, chatType.key).collect { polls ->
                    if (!isAdded || view == null) return@collect
                    android.util.Log.d("ChatFragment", "Loaded ${polls.size} polls for chatType: ${chatType.key}, teamId: $teamId")
                    pollsAdapter.submitList(polls)
                    // Show/hide polls RecyclerView based on whether there are polls
                    view?.findViewById<RecyclerView>(R.id.pollsRecyclerView)?.isVisible = polls.isNotEmpty()
                }
            } catch (e: Exception) {
                if (!isAdded || view == null) return@launch
                android.util.Log.e("ChatFragment", "Error loading polls: ${e.message}", e)
                val errorMessage = if (e.message?.contains("index") == true || e.message?.contains("Index") == true) {
                    "Ошибка: требуется создать индекс в Firestore для опросов"
                } else {
                    "Ошибка загрузки опросов: ${e.message}"
                }
                view?.let {
                    Snackbar.make(it, errorMessage, Snackbar.LENGTH_LONG).show()
                }
                view?.findViewById<RecyclerView>(R.id.pollsRecyclerView)?.isVisible = false
            }
        }
    }
    
    private fun showCreatePollDialog() {
        if (!isAdded) return
        val profile = userProfile ?: return
        val teamId = profile.teamId ?: return
        
        try {
            CreatePollDialog(requireContext()) { question, options, isAnonymous ->
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

                    if (isAdded && view != null) {
                        Snackbar.make(view, getString(R.string.poll_created), Snackbar.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ChatFragment", "Error creating poll: ${e.message}", e)
                    if (isAdded && view != null) {
                        Snackbar.make(view, "Error creating poll: ${e.message}", Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
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
                    if (isAdded && view != null) {
                        Snackbar.make(view, "Error loading poll: ${e.message}", Snackbar.LENGTH_SHORT).show()
                    }
                    return@launch
                }
            }
            
            if (poll == null) {
                if (isAdded && view != null) {
                    Snackbar.make(view, "Poll not found", Snackbar.LENGTH_SHORT).show()
                }
                return@launch
            }

            if (!isAdded) return@launch
            val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.item_poll, null)
            
            // Setup view
            val questionTextView = dialogView.findViewById<TextView>(R.id.pollQuestionTextView)
            val creatorTextView = dialogView.findViewById<TextView>(R.id.pollCreatorTextView)
            val optionsRecyclerView = dialogView.findViewById<RecyclerView>(R.id.pollOptionsRecyclerView)
            val pinnedIcon = dialogView.findViewById<android.view.View>(R.id.pollPinnedIcon)
            
            questionTextView.text = poll.question
            creatorTextView.text = getString(R.string.created_by, poll.createdByName)
            pinnedIcon.isVisible = poll.isPinned
            
            val optionAdapter = com.fts.ttbros.chat.ui.PollOptionAdapter(
                poll = poll,
                currentUserId = profile.uid,
                onVote = { optionId ->
                    handleVote(poll.id, optionId)
                    // We don't dismiss dialog immediately so user can see result update if real-time
                    // But since this is a dialog, maybe we should dismiss or refresh?
                    // Real-time updates won't happen in this dialog unless we observe the poll.
                    // For simplicity, let's dismiss after vote or just let them close it.
                }
            )
            
            optionsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
            optionsRecyclerView.adapter = optionAdapter
            optionAdapter.submitList(poll.options)

            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }
    
    private fun handleVote(pollId: String, optionId: String) {
        if (!isAdded) return
        val profile = userProfile ?: return
        val view = view ?: return
        
        viewLifecycleOwner.lifecycleScope.launch {
            if (!isAdded || view == null) return@launch
            try {
                // Get poll to check if anonymous
                val polls = pollsAdapter.currentList
                val poll = polls.find { it.id == pollId } ?: run {
                    if (isAdded && view != null) {
                        Snackbar.make(view, "Poll not found", Snackbar.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                
                pollRepository.vote(
                    pollId = pollId,
                    userId = profile.uid,
                    userName = profile.displayName.ifBlank { profile.email },
                    optionId = optionId,
                    isAnonymous = poll.isAnonymous
                )
                if (isAdded && view != null) {
                    Snackbar.make(view, getString(R.string.vote_recorded), Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatFragment", "Error voting: ${e.message}", e)
                if (isAdded && view != null) {
                    Snackbar.make(view, "Error voting: ${e.message}", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun handlePinMessage(messageId: String) {
        val profile = userProfile ?: return
        val teamId = profile.teamId ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                chatRepository.pinMessage(teamId, chatType, messageId, profile.uid)
                view?.let {
                    Snackbar.make(it, R.string.message_pinned, Snackbar.LENGTH_SHORT).show()
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
        val teamId = profile.teamId ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                chatRepository.unpinMessage(teamId, chatType, messageId)
                view?.let {
                    Snackbar.make(it, R.string.message_unpinned, Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                view?.let {
                    Snackbar.make(it, "Error unpinning message: ${e.message}", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun handlePinPoll(pollId: String) {
        val profile = userProfile ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                pollRepository.pinPoll(pollId, profile.uid)
                view?.let {
                    Snackbar.make(it, R.string.poll_pinned, Snackbar.LENGTH_SHORT).show()
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
                    Snackbar.make(it, R.string.poll_unpinned, Snackbar.LENGTH_SHORT).show()
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
        val teamId = profile.teamId ?: return
        
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

    private fun importCharacter(senderId: String, characterId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val characterRepo = com.fts.ttbros.data.repository.CharacterRepository()
                characterRepo.copyCharacter(senderId, characterId)
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
    }

    companion object {
        private const val ARG_CHAT_TYPE = "chatType"
    }
}

