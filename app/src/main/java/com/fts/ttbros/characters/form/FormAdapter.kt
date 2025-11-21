package com.fts.ttbros.characters.form

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fts.ttbros.databinding.ItemFormDotsBinding
import com.fts.ttbros.databinding.ItemFormHeaderBinding
import com.fts.ttbros.databinding.ItemFormSectionBinding
import com.fts.ttbros.databinding.ItemFormTextBinding

class FormAdapter(
    private val onValueChanged: (key: String, value: Any) -> Unit
) : ListAdapter<FormItem, RecyclerView.ViewHolder>(FormDiffCallback()) {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_SECTION = 1
        private const val TYPE_TEXT = 2
        private const val TYPE_DOTS = 3
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is FormItem.Header -> TYPE_HEADER
            is FormItem.Section -> TYPE_SECTION
            is FormItem.TextField -> TYPE_TEXT
            is FormItem.DotsField -> TYPE_DOTS
            else -> throw IllegalArgumentException("Unknown item type")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(ItemFormHeaderBinding.inflate(inflater, parent, false))
            TYPE_SECTION -> SectionViewHolder(ItemFormSectionBinding.inflate(inflater, parent, false))
            TYPE_TEXT -> TextViewHolder(ItemFormTextBinding.inflate(inflater, parent, false), onValueChanged)
            TYPE_DOTS -> DotsViewHolder(ItemFormDotsBinding.inflate(inflater, parent, false), onValueChanged)
            else -> throw IllegalArgumentException("Unknown view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderViewHolder -> holder.bind(getItem(position) as FormItem.Header)
            is SectionViewHolder -> holder.bind(getItem(position) as FormItem.Section)
            is TextViewHolder -> holder.bind(getItem(position) as FormItem.TextField)
            is DotsViewHolder -> holder.bind(getItem(position) as FormItem.DotsField)
        }
    }

    class HeaderViewHolder(private val binding: ItemFormHeaderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: FormItem.Header) {
            binding.titleTextView.text = item.title
        }
    }

    class SectionViewHolder(private val binding: ItemFormSectionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: FormItem.Section) {
            binding.titleTextView.text = item.title
        }
    }

    class TextViewHolder(
        private val binding: ItemFormTextBinding,
        private val onValueChanged: (String, Any) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(item: FormItem.TextField) {
            binding.textInputLayout.hint = item.label
            binding.textInputEditText.setText(item.value)
            
            // Remove existing watcher to avoid loops/duplicates if recycled
            // Ideally use a custom TextWatcher that checks for tag/position
            
            binding.textInputEditText.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    onValueChanged(item.key, binding.textInputEditText.text.toString())
                }
            }
        }
    }

    class DotsViewHolder(
        private val binding: ItemFormDotsBinding,
        private val onValueChanged: (String, Any) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(item: FormItem.DotsField) {
            binding.labelTextView.text = item.label
            
            // Simple implementation: 5 RadioButtons or Checkboxes acting as dots
            // For now, let's assume we have a custom view or just 5 ImageViews in the layout
            // This part requires the layout XML to be defined first to know IDs
            
            updateDots(item.value)
            
            binding.dot1.setOnClickListener { updateValue(1, item) }
            binding.dot2.setOnClickListener { updateValue(2, item) }
            binding.dot3.setOnClickListener { updateValue(3, item) }
            binding.dot4.setOnClickListener { updateValue(4, item) }
            binding.dot5.setOnClickListener { updateValue(5, item) }
        }
        
        private fun updateValue(value: Int, item: FormItem.DotsField) {
            // Toggle off if clicking same value? Usually in VTM you can't have 0 dots in attributes, but maybe in skills
            val newValue = if (value == item.value) 0 else value
            updateDots(newValue)
            onValueChanged(item.key, newValue)
        }

        private fun updateDots(value: Int) {
            binding.dot1.isSelected = value >= 1
            binding.dot2.isSelected = value >= 2
            binding.dot3.isSelected = value >= 3
            binding.dot4.isSelected = value >= 4
            binding.dot5.isSelected = value >= 5
        }
    }

    class FormDiffCallback : DiffUtil.ItemCallback<FormItem>() {
        override fun areItemsTheSame(oldItem: FormItem, newItem: FormItem): Boolean {
            return oldItem == newItem // Simplified for data classes
        }

        override fun areContentsTheSame(oldItem: FormItem, newItem: FormItem): Boolean {
            return oldItem == newItem
        }
    }
}
