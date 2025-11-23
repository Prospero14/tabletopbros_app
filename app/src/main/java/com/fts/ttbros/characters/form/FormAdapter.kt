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
import com.fts.ttbros.databinding.ItemFormDisciplineBinding
import com.fts.ttbros.databinding.ItemFormButtonBinding

class FormAdapter(
    private val onValueChanged: (key: String, value: Any) -> Unit
) : ListAdapter<FormItem, RecyclerView.ViewHolder>(FormDiffCallback()) {

    var readOnly: Boolean = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_SECTION = 1
        private const val TYPE_TEXT = 2
        private const val TYPE_DOTS = 3
        private const val TYPE_DISCIPLINE = 4
        private const val TYPE_BUTTON = 5
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is FormItem.Header -> TYPE_HEADER
            is FormItem.Section -> TYPE_SECTION
            is FormItem.TextField -> TYPE_TEXT
            is FormItem.DotsField -> TYPE_DOTS
            is FormItem.Discipline -> TYPE_DISCIPLINE
            is FormItem.Button -> TYPE_BUTTON
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
            TYPE_DISCIPLINE -> DisciplineViewHolder(ItemFormDisciplineBinding.inflate(inflater, parent, false), onValueChanged)
            TYPE_BUTTON -> ButtonViewHolder(ItemFormButtonBinding.inflate(inflater, parent, false), onValueChanged)
            else -> throw IllegalArgumentException("Unknown view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderViewHolder -> holder.bind(getItem(position) as FormItem.Header)
            is SectionViewHolder -> holder.bind(getItem(position) as FormItem.Section)
            is TextViewHolder -> holder.bind(getItem(position) as FormItem.TextField, readOnly)
            is DotsViewHolder -> holder.bind(getItem(position) as FormItem.DotsField, readOnly)
            is DisciplineViewHolder -> holder.bind(getItem(position) as FormItem.Discipline, readOnly)
            is ButtonViewHolder -> holder.bind(getItem(position) as FormItem.Button, readOnly)
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
        
        fun bind(item: FormItem.TextField, readOnly: Boolean) {
            binding.textInputLayout.hint = item.label
            binding.textInputEditText.setText(item.value)
            binding.textInputLayout.isEnabled = !readOnly
            
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
        
        fun bind(item: FormItem.DotsField, readOnly: Boolean) {
            binding.labelTextView.text = item.label
            binding.root.alpha = if (readOnly) 0.7f else 1.0f
            
            updateDots(item.value)
            
            if (readOnly) {
                binding.dot1.setOnClickListener(null)
                binding.dot2.setOnClickListener(null)
                binding.dot3.setOnClickListener(null)
                binding.dot4.setOnClickListener(null)
                binding.dot5.setOnClickListener(null)
            } else {
                binding.dot1.setOnClickListener { updateValue(1, item) }
                binding.dot2.setOnClickListener { updateValue(2, item) }
                binding.dot3.setOnClickListener { updateValue(3, item) }
                binding.dot4.setOnClickListener { updateValue(4, item) }
                binding.dot5.setOnClickListener { updateValue(5, item) }
            }
        }
        
        private fun updateValue(value: Int, item: FormItem.DotsField) {
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

    class DisciplineViewHolder(
        private val binding: ItemFormDisciplineBinding,
        private val onValueChanged: (String, Any) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FormItem.Discipline, readOnly: Boolean) {
            binding.nameEditText.setText(item.name)
            binding.nameInputLayout.isEnabled = !readOnly
            binding.root.alpha = if (readOnly) 0.7f else 1.0f

            // Remove old listener to avoid duplicates
            binding.nameEditText.onFocusChangeListener = null
            
            binding.nameEditText.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val text = binding.nameEditText.text?.toString() ?: ""
                    onValueChanged("discipline_name_${item.id}", text)
                }
            }
            
            // Also save on text change when not focused (for immediate updates)
            binding.nameEditText.setOnEditorActionListener { _, _, _ ->
                val text = binding.nameEditText.text?.toString() ?: ""
                onValueChanged("discipline_name_${item.id}", text)
                binding.nameEditText.clearFocus()
                true
            }

            updateDots(item.value)
            
            if (readOnly) {
                binding.dot1.setOnClickListener(null)
                binding.dot2.setOnClickListener(null)
                binding.dot3.setOnClickListener(null)
                binding.dot4.setOnClickListener(null)
                binding.dot5.setOnClickListener(null)
            } else {
                binding.dot1.setOnClickListener { updateValue(1, item) }
                binding.dot2.setOnClickListener { updateValue(2, item) }
                binding.dot3.setOnClickListener { updateValue(3, item) }
                binding.dot4.setOnClickListener { updateValue(4, item) }
                binding.dot5.setOnClickListener { updateValue(5, item) }
            }
        }

        private fun updateValue(value: Int, item: FormItem.Discipline) {
            val newValue = if (value == item.value) 0 else value
            updateDots(newValue)
            onValueChanged("discipline_value_${item.id}", newValue)
        }

        private fun updateDots(value: Int) {
            binding.dot1.isSelected = value >= 1
            binding.dot2.isSelected = value >= 2
            binding.dot3.isSelected = value >= 3
            binding.dot4.isSelected = value >= 4
            binding.dot5.isSelected = value >= 5
        }
    }

    class ButtonViewHolder(
        private val binding: ItemFormButtonBinding,
        private val onValueChanged: (String, Any) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FormItem.Button, readOnly: Boolean) {
            binding.button.text = item.label
            binding.button.isEnabled = !readOnly
            binding.button.visibility = if (readOnly) android.view.View.GONE else android.view.View.VISIBLE
            
            binding.button.setOnClickListener {
                onValueChanged(item.id, "")
            }
        }
    }

    class FormDiffCallback : DiffUtil.ItemCallback<FormItem>() {
        override fun areItemsTheSame(oldItem: FormItem, newItem: FormItem): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: FormItem, newItem: FormItem): Boolean {
            return oldItem == newItem
        }
    }
}
