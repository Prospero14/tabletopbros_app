package com.fts.ttbros.chat

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.tasks.await
import java.io.File
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
    private lateinit var attachImageButton: MaterialButton
    
    private val chatRepository = ChatRepository()
    private val userRepository = UserRepository()
    private val auth by lazy { Firebase.auth }
    private val storage = Firebase.storage
    private var listenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null
    private var userProfile: UserProfile? = null
    private lateinit var chatType: ChatType
    private lateinit var adapter: ChatAdapter
    private var currentImageUri: Uri? = null

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
        attachImageButton = view.findViewById(R.id.attachImageButton)
        
        adapter = ChatAdapter(auth.currentUser?.uid.orEmpty())
        val layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        messagesRecyclerView.layoutManager = layoutManager
        messagesRecyclerView.adapter = adapter

        sendButton.setOnClickListener { sendMessage() }
        attachImageButton.setOnClickListener { showImagePicker() }
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

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            currentImageUri?.let { uri ->
                uploadAndSendImage(uri)
            }
        }
    }
    
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                uploadAndSendImage(uri)
            }
        }
    }
    
    private fun showImagePicker() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Отправить изображение")
            .setItems(arrayOf("Сделать фото", "Выбрать из галереи")) { dialog: DialogInterface, which: Int ->
                when (which) {
                    0 -> takePhoto()
                    1 -> pickImageFromGallery()
                }
            }
            .show()
    }
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            launchCamera()
        } else {
            Snackbar.make(requireView(), "Camera permission required", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun takePhoto() {
        if (ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
            return
        }
        launchCamera()
    }

    private fun launchCamera() {
        try {
            val photoFile = File(requireContext().cacheDir, "chat_${System.currentTimeMillis()}.jpg")
            val photoUri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                photoFile
            )
            currentImageUri = photoUri
            
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            takePictureLauncher.launch(intent)
        } catch (e: Exception) {
            Snackbar.make(requireView(), "Ошибка: ${e.message}", Snackbar.LENGTH_SHORT).show()
        }
    }
    
    private fun pickImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }
    
    private fun uploadAndSendImage(imageUri: Uri) {
        val profile = userProfile ?: return
        val teamId = profile.teamId ?: return
        val text = messageEditText.text?.toString()?.trim().orEmpty()
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                sendButton.isEnabled = false
                attachImageButton.isEnabled = false
                
                // Upload to Firebase Storage
                val fileName = "chat_${System.currentTimeMillis()}_${profile.uid}.jpg"
                val storageRef = storage.reference.child("chat_images/$teamId/$fileName")
                
                val uploadTask = storageRef.putFile(imageUri)
                uploadTask.await()
                val downloadUrl = storageRef.downloadUrl.await()
                
                // Send message with image
                chatRepository.sendMessage(
                    teamId,
                    chatType,
                    ChatMessage(
                        senderId = profile.uid,
                        senderName = profile.displayName.ifBlank { profile.email },
                        text = text,
                        imageUrl = downloadUrl.toString()
                    )
                )
                
                messageEditText.text?.clear()
                currentImageUri = null
            } catch (error: Exception) {
                view?.let {
                    Snackbar.make(it, error.localizedMessage ?: getString(R.string.error_unknown), Snackbar.LENGTH_LONG).show()
                }
            } finally {
                sendButton.isEnabled = true
                attachImageButton.isEnabled = true
            }
        }
    }
    
    private fun sendMessage() {
        val profile = userProfile ?: return
        val text = messageEditText.text?.toString()?.trim().orEmpty()
        if (text.isBlank() && currentImageUri == null) return
        messageEditText.text?.clear()
        val teamId = profile.teamId ?: return
        
        if (currentImageUri != null) {
            uploadAndSendImage(currentImageUri!!)
            return
        }
        
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
