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
    private val currentUserId: String
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

        fun bind(message: ChatMessage) {
            val isMine = message.senderId == currentUserId
            val context = itemView.context
            messageContainer.gravity = if (isMine) Gravity.END else Gravity.START
            senderNameTextView.isVisible = !isMine
            senderNameTextView.text = message.senderName

            val bubbleColor = if (isMine) {
                ContextCompat.getColor(context, R.color.purple_200)
            } else {
                ContextCompat.getColor(context, android.R.color.white)
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
            
            // Handle text
            messageTextView.text = message.text
            messageTextView.isVisible = message.text.isNotBlank()
            
            val formattedTime = message.timestamp?.toDate()?.let {
                DateFormat.getTimeInstance(DateFormat.SHORT).format(it)
            } ?: ""
            timestampTextView.text = formattedTime
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean =
            oldItem == newItem
    }
}
