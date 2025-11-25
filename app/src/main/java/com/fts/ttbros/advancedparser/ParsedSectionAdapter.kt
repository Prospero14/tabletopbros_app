package com.fts.ttbros.advancedparser

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fts.ttbros.R
import com.fts.ttbros.data.model.ParsedSection
import com.google.android.material.button.MaterialButton

class ParsedSectionAdapter(
    private val onEditClick: (ParsedSection) -> Unit
) : ListAdapter<ParsedSection, ParsedSectionAdapter.ViewHolder>(DiffCallback()) {

    private val expandedItems = mutableSetOf<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_parsed_section, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val confidenceIndicator: View = itemView.findViewById(R.id.confidenceIndicator)
        private val sectionTitle: TextView = itemView.findViewById(R.id.sectionTitle)
        private val sectionType: TextView = itemView.findViewById(R.id.sectionType)
        private val confidenceScore: TextView = itemView.findViewById(R.id.confidenceScore)
        private val sectionContent: TextView = itemView.findViewById(R.id.sectionContent)
        private val expandButton: MaterialButton = itemView.findViewById(R.id.expandButton)
        private val editButton: ImageButton = itemView.findViewById(R.id.editButton)

        fun bind(section: ParsedSection) {
            sectionTitle.text = section.title
            sectionType.text = "Type: ${section.sectionType.name}"
            
            // Format confidence score
            val confidencePercent = (section.confidence * 100).toInt()
            confidenceScore.text = "$confidencePercent%"
            
            // Set confidence indicator color
            val color = when {
                section.confidence >= 0.8f -> ContextCompat.getColor(itemView.context, android.R.color.holo_green_dark)
                section.confidence >= 0.5f -> ContextCompat.getColor(itemView.context, android.R.color.holo_orange_light)
                else -> ContextCompat.getColor(itemView.context, android.R.color.holo_red_light)
            }
            confidenceIndicator.setBackgroundColor(color)
            
            // Handle content expansion
            val isExpanded = expandedItems.contains(section.id)
            if (isExpanded) {
                sectionContent.maxLines = Int.MAX_VALUE
                expandButton.text = "Показать меньше"
            } else {
                sectionContent.maxLines = 5
                expandButton.text = "Показать больше"
            }
            sectionContent.text = section.content
            
            // Show/hide expand button based on content length
            expandButton.visibility = if (section.content.lines().size > 5) {
                View.VISIBLE
            } else {
                View.GONE
            }
            
            // Expand/collapse click
            expandButton.setOnClickListener {
                if (expandedItems.contains(section.id)) {
                    expandedItems.remove(section.id)
                } else {
                    expandedItems.add(section.id)
                }
                notifyItemChanged(bindingAdapterPosition)
            }
            
            // Edit button
            editButton.setOnClickListener {
                onEditClick(section)
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<ParsedSection>() {
        override fun areItemsTheSame(oldItem: ParsedSection, newItem: ParsedSection): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ParsedSection, newItem: ParsedSection): Boolean {
            return oldItem == newItem
        }
    }
}
