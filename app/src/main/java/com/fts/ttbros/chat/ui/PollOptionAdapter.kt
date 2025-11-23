package com.fts.ttbros.chat.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fts.ttbros.R
import com.fts.ttbros.data.model.Poll
import com.fts.ttbros.data.model.PollOption

class PollOptionAdapter(
    private val poll: Poll,
    private val currentUserId: String,
    private val onVote: (optionId: String) -> Unit
) : ListAdapter<PollOption, PollOptionAdapter.OptionViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OptionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_poll_option, parent, false)
        return OptionViewHolder(view)
    }

    override fun onBindViewHolder(holder: OptionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class OptionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val optionTextTextView: TextView = itemView.findViewById(R.id.optionTextTextView)
        private val optionProgressBar: ProgressBar = itemView.findViewById(R.id.optionProgressBar)
        private val optionVoteCountTextView: TextView = itemView.findViewById(R.id.optionVoteCountTextView)
        private val optionVotersTextView: TextView = itemView.findViewById(R.id.optionVotersTextView)
        private val optionCheckBox: com.google.android.material.checkbox.MaterialCheckBox = 
            itemView.findViewById(R.id.optionCheckBox)
        private val optionContainer: LinearLayout = itemView.findViewById(R.id.optionContainer)
        private val cardView: com.google.android.material.card.MaterialCardView = 
            itemView as com.google.android.material.card.MaterialCardView

        fun bind(option: PollOption) {
            optionTextTextView.text = option.text

            // Calculate votes for this option
            val votesForOption = poll.votes.values.count { it == option.id }
            val totalVotes = poll.votes.size
            val percentage = if (totalVotes > 0) {
                (votesForOption * 100) / totalVotes
            } else {
                0
            }

            // Animate progress bar smoothly
            android.animation.ObjectAnimator.ofInt(optionProgressBar, "progress", optionProgressBar.progress, percentage)
                .setDuration(300)
                .start()
            
            // Show percentage and vote count
            val percentageText = if (totalVotes > 0) {
                "$percentage%"
            } else {
                "0%"
            }
            optionVoteCountTextView.text = "$percentageText • ${itemView.context.getString(
                R.string.poll_votes_count, votesForOption, totalVotes
            )}"

            // Check if current user voted for this option
            val userVote = poll.votes[currentUserId]
            val isSelected = userVote == option.id
            optionCheckBox.isChecked = isSelected

            // Visual feedback for selected option
            if (isSelected) {
                // Highlight selected option
                cardView.strokeColor = itemView.context.getColor(R.color.purple_500)
                cardView.strokeWidth = 2
                cardView.cardElevation = 4f
                optionTextTextView.setTextColor(itemView.context.getColor(R.color.purple_500))
                optionTextTextView.setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                // Normal state
                cardView.strokeColor = itemView.context.getColor(android.R.color.transparent)
                cardView.strokeWidth = 0
                cardView.cardElevation = 2f
                optionTextTextView.setTextColor(itemView.context.getColor(android.R.color.black))
                optionTextTextView.setTypeface(null, android.graphics.Typeface.NORMAL)
            }

            // Show voters for non-anonymous polls
            if (!poll.isAnonymous && votesForOption > 0) {
                val voters = poll.votes
                    .filter { it.value == option.id }
                    .mapNotNull { poll.voterNames[it.key] }
                    .take(3) // Show max 3 names
                
                if (voters.isNotEmpty()) {
                    val votersText = if (voters.size == votesForOption) {
                        "👤 ${voters.joinToString(", ")}"
                    } else {
                        "👤 ${voters.joinToString(", ")} +${votesForOption - voters.size}"
                    }
                    optionVotersTextView.text = votersText
                    optionVotersTextView.isVisible = true
                } else {
                    optionVotersTextView.isVisible = false
                }
            } else {
                optionVotersTextView.isVisible = false
            }

            // Handle click to vote
            optionContainer.setOnClickListener {
                android.util.Log.d("PollOptionAdapter", "Option clicked: ${option.id}, userVote: $userVote")
                if (userVote != option.id) {
                    // Add visual feedback
                    itemView.animate()
                        .scaleX(0.95f)
                        .scaleY(0.95f)
                        .setDuration(100)
                        .withEndAction {
                            itemView.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(100)
                                .start()
                        }
                        .start()
                    onVote(option.id)
                } else {
                    android.util.Log.d("PollOptionAdapter", "User already voted for this option")
                }
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<PollOption>() {
            override fun areItemsTheSame(oldItem: PollOption, newItem: PollOption): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: PollOption, newItem: PollOption): Boolean =
                oldItem == newItem
        }
    }
}

