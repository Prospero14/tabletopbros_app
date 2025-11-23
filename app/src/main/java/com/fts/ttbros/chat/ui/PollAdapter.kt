package com.fts.ttbros.chat.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fts.ttbros.R
import com.fts.ttbros.data.model.Poll
import com.google.android.material.card.MaterialCardView

class PollAdapter(
    private val currentUserId: String,
    private val currentUserName: String,
    private val onVote: (pollId: String, optionId: String) -> Unit,
    private val onPinPoll: ((String) -> Unit)? = null,
    private val onUnpinPoll: ((String) -> Unit)? = null
) : ListAdapter<Poll, PollAdapter.PollViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PollViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_poll, parent, false)
        return PollViewHolder(view)
    }

    override fun onBindViewHolder(holder: PollViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PollViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val pollQuestionTextView: TextView = itemView.findViewById(R.id.pollQuestionTextView)
        private val pollCreatorTextView: TextView = itemView.findViewById(R.id.pollCreatorTextView)
        private val pollOptionsRecyclerView: RecyclerView = itemView.findViewById(R.id.pollOptionsRecyclerView)
        private val pollPinnedIcon: ImageView = itemView.findViewById(R.id.pollPinnedIcon)
        private val pollCard: MaterialCardView = itemView.findViewById(R.id.pollCard)
        private var currentOptionAdapter: PollOptionAdapter? = null

        fun bind(poll: Poll) {
            pollQuestionTextView.text = poll.question
            pollCreatorTextView.text = itemView.context.getString(
                R.string.created_by, poll.createdByName
            )

            // Show pinned indicator
            pollPinnedIcon.isVisible = poll.isPinned

            // Add long click listener for pin/unpin
            pollCard.setOnLongClickListener {
                if (poll.isPinned) {
                    onUnpinPoll?.invoke(poll.id)
                } else {
                    onPinPoll?.invoke(poll.id)
                }
                true
            }

            // Recreate adapter to ensure it has the latest poll data
            val optionAdapter = PollOptionAdapter(
                poll = poll,
                currentUserId = currentUserId,
                onVote = { optionId ->
                    onVote(poll.id, optionId)
                }
            )

            pollOptionsRecyclerView.layoutManager = LinearLayoutManager(itemView.context)
            pollOptionsRecyclerView.adapter = optionAdapter
            optionAdapter.submitList(poll.options)
            currentOptionAdapter = optionAdapter
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<Poll>() {
            override fun areItemsTheSame(oldItem: Poll, newItem: Poll): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Poll, newItem: Poll): Boolean =
                oldItem == newItem
        }
    }
}

