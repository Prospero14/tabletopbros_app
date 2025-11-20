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

class ChatFragment : Fragment() {

    private lateinit var emptyView: TextView
    private lateinit var messagesRecyclerView: RecyclerView
    private lateinit var messageInputContainer: LinearLayout
    private lateinit var messageEditText: TextInputEditText
    private lateinit var sendButton: MaterialButton
    
    private val chatRepository = ChatRepository()
    private val userRepository = UserRepository()
    private val auth by lazy { Firebase.auth }
    private var listenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null
    private var userProfile: UserProfile? = null
    private lateinit var chatType: ChatType
    private lateinit var adapter: ChatAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chatType = ChatType.from(arguments?.getString(ARG_CHAT_TYPE))
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        emptyView = view.findViewById(R.id.emptyView)
        messagesRecyclerView = view.findViewById(R.id.messagesRecyclerView)
        messageInputContainer = view.findViewById(R.id.messageInputContainer)
        messageEditText = view.findViewById(R.id.messageEditText)
        sendButton = view.findViewById(R.id.sendButton)
        
        adapter = ChatAdapter(auth.currentUser?.uid.orEmpty())
        val layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        messagesRecyclerView.layoutManager = layoutManager
        messagesRecyclerView.adapter = adapter

        sendButton.setOnClickListener { sendMessage() }
        observeProfile()
    }

    private fun observeProfile() {
        viewLifecycleOwner.lifecycleScope.launch {
            val profile = userRepository.currentProfile()
            if (profile == null || profile.teamId.isNullOrBlank()) {
                view?.let {
                    Snackbar.make(it, R.string.error_group_not_found, Snackbar.LENGTH_LONG)
                        .setAction(R.string.join_group) {
                            startActivity(Intent(requireContext(), GroupActivity::class.java))
                        }
                        .show()
                }
                disableInput()
                return@launch
            }
            userProfile = profile
            updateInputAvailability(profile)
            subscribeToMessages(profile)
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
