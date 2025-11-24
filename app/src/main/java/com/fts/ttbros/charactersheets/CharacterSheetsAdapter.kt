package com.fts.ttbros.charactersheets

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fts.ttbros.R
import com.fts.ttbros.data.model.CharacterSheet
import com.google.android.material.card.MaterialCardView

class CharacterSheetsAdapter(
    private val onSheetClick: (CharacterSheet) -> Unit,
    private val onSheetDelete: (CharacterSheet) -> Unit
) : ListAdapter<CharacterSheet, CharacterSheetsAdapter.SheetViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SheetViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_character_sheet, parent, false)
        return SheetViewHolder(view)
    }

    override fun onBindViewHolder(holder: SheetViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SheetViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val sheetCard: MaterialCardView = itemView.findViewById(R.id.sheetCard)
        private val nameTextView: TextView = itemView.findViewById(R.id.sheetNameTextView)
        private val systemTextView: TextView = itemView.findViewById(R.id.sheetSystemTextView)
        private val attributesTextView: TextView = itemView.findViewById(R.id.sheetAttributesTextView)

        fun bind(sheet: CharacterSheet) {
            nameTextView.text = sheet.characterName
            systemTextView.text = when (sheet.system) {
                "dnd_5e" -> "D&D 5e"
                "vtm_5e" -> "Vampire: The Masquerade 5e"
                else -> sheet.system
            }
            
            // Show attributes summary
            val attributesText = if (sheet.attributes.isNotEmpty()) {
                sheet.attributes.entries.take(3).joinToString(", ") { "${it.key}: ${it.value}" }
            } else {
                "Нет данных"
            }
            attributesTextView.text = attributesText
            
            sheetCard.setOnClickListener {
                onSheetClick(sheet)
            }
            
            sheetCard.setOnLongClickListener {
                onSheetDelete(sheet)
                true
            }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<CharacterSheet>() {
            override fun areItemsTheSame(oldItem: CharacterSheet, newItem: CharacterSheet): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: CharacterSheet, newItem: CharacterSheet): Boolean =
                oldItem == newItem
        }
    }
}

