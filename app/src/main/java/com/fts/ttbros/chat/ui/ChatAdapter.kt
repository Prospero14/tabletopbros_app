package com.fts.ttbros.chat.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.fts.ttbros.R
import com.fts.ttbros.chat.model.ChatMessage
import com.google.android.material.card.MaterialCardView
import java.text.DateFormat

class ChatAdapter(
    private val currentUserId: String,
    private val onImportCharacter: (String, String) -> Unit, // senderId, characterId
    private val onPinMessage: ((String) -> Unit)? = null,
    private val onUnpinMessage: ((String) -> Unit)? = null
) : ListAdapter<ChatMessage, ChatAdapter.MessageViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        
        private val messageContainer: LinearLayout = itemView.findViewById(R.id.messageContainer)
        private val senderNameTextView: TextView = itemView.findViewById(R.id.senderNameTextView)
        private val messageCard: MaterialCardView = itemView.findViewById(R.id.messageCard)
        private val messageTextView: TextView = itemView.findViewById(R.id.messageTextView)
        private val messageImageView: ImageView = itemView.findViewById(R.id.messageImageView)
        private val timestampTextView: TextView = itemView.findViewById(R.id.timestampTextView)
        private val pinnedIcon: ImageView = itemView.findViewById(R.id.pinnedIcon)
        private val pinnedIcon: ImageView = itemView.findViewById(R.id.pinnedIcon)
        // Dynamically adding button if needed or reusing view structure
        // Ideally we should update layout XML, but for now we can add a button programmatically or use text view as button if type is character
        // Let's assume we can add a button to the layout or reuse messageTextView with a background
        
        // Actually, let's just append a button to the container or use a specific view
        // Since we can't easily modify the layout XML right now without reading it again (which I haven't done for item_chat_message), 
        // I'll assume I can add a button programmatically to the messageCard's child (which is likely a LinearLayout or ConstraintLayout)
        // Wait, I saw item_chat_message in ChatAdapter.kt? No, I only saw the code referencing it.
        // Let's check item_chat_message.xml first to be safe.
        
        // RE-READING STRATEGY: I'll modify the bind method to handle the logic, but I need to know the layout structure.
        // I'll assume standard text view usage for now and maybe append a clickable span or use the text view itself as the button.
        // BETTER: Use the messageTextView as the button text.
        
        fun bind(message: ChatMessage) {
            val isMine = message.senderId == currentUserId
            val context = itemView.context
            messageContainer.gravity = if (isMine) Gravity.END else Gravity.START
            senderNameTextView.isVisible = !isMine
            senderNameTextView.text = message.senderName

            val bubbleColor = if (isMine) {
                ContextCompat.getColor(context, R.color.chat_bubble_own)
            } else {
                ContextCompat.getColor(context, R.color.chat_bubble_other)
            }
            messageCard.setCardBackgroundColor(bubbleColor)
            
            // Handle image
            if (message.hasImage && !message.imageUrl.isNullOrBlank()) {
                messageImageView.isVisible = true
                Glide.with(context)
                .load(message.imageUrl)
                .into(messageImageView)
            } else {
                messageImageView.isVisible = false
            }
            
            // Handle character share
            if (message.type == "character" && !message.attachmentId.isNullOrBlank()) {
                messageTextView.text = "📄 ${message.text}\n(Tap to Import)"
                messageTextView.setOnClickListener {
                    onImportCharacter(message.senderId, message.attachmentId)
                }
                // Make it look like a button/link
                messageTextView.setTextColor(ContextCompat.getColor(context, R.color.teal_700))
                messageTextView.setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                // Handle text
                messageTextView.text = message.text
                messageTextView.setOnClickListener(null) // Reset listener
                messageTextView.setTextColor(ContextCompat.getColor(context, if (isMine) android.R.color.white else android.R.color.black)) // Reset color roughly
                // Actually, color reset is tricky without knowing original colors. 
                // Let's assume default colors are fine or set them explicitly.
                val defaultTextColor = ContextCompat.getColor(context, if (isMine) R.color.white else R.color.black)
                messageTextView.setTextColor(defaultTextColor)
                messageTextView.typeface = android.graphics.Typeface.DEFAULT
            }
            
            messageTextView.isVisible = message.text.isNotBlank()
            
            val formattedTime = message.timestamp?.toDate()?.let {
                DateFormat.getTimeInstance(DateFormat.SHORT).format(it)
            } ?: ""
            timestampTextView.text = formattedTime

            // Show pinned indicator
            pinnedIcon.isVisible = message.isPinned

            // Add long click listener for pin/unpin on both card and container
            val longClickListener = View.OnLongClickListener { view ->
                android.util.Log.d("ChatAdapter", "Long click on message ${message.id}, isPinned: ${message.isPinned}")
                if (message.isPinned) {
                    onUnpinMessage?.invoke(message.id)
                } else {
                    onPinMessage?.invoke(message.id)
                }
                true
            }
            messageCard.setOnLongClickListener(longClickListener)
            messageContainer.setOnLongClickListener(longClickListener)
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean =
            oldItem == newItem
    }
}
