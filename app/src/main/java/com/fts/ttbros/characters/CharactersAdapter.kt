package com.fts.ttbros.characters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fts.ttbros.data.model.Character
import com.fts.ttbros.databinding.ItemCharacterBinding

class CharactersAdapter(
    private val onCharacterClick: (Character) -> Unit,
    private val onShareClick: (Character) -> Unit
) : ListAdapter<Character, CharactersAdapter.CharacterViewHolder>(CharacterDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CharacterViewHolder {
        val binding = ItemCharacterBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CharacterViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CharacterViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CharacterViewHolder(
        private val binding: ItemCharacterBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onCharacterClick(getItem(position))
                }
            }
        }

        fun bind(character: Character) {
            binding.characterNameTextView.text = character.name.ifBlank { "Unnamed Character" }
            val context = binding.root.context
            binding.systemTextView.text = when(character.system) {
                "vtm_5e" -> context.getString(com.fts.ttbros.R.string.vampire_masquerade)
                "dnd_5e" -> context.getString(com.fts.ttbros.R.string.dungeons_dragons)
                "viedzmin_2e" -> context.getString(com.fts.ttbros.R.string.viedzmin_2e)
                else -> character.system
            }
            binding.clanTextView.text = character.clan.ifBlank { "No Clan/Class" }
            
            binding.shareButton.setOnClickListener {
                onShareClick(character)
            }
        }
    }

    class CharacterDiffCallback : DiffUtil.ItemCallback<Character>() {
        override fun areItemsTheSame(oldItem: Character, newItem: Character): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Character, newItem: Character): Boolean {
            return oldItem == newItem
        }
    }
}
