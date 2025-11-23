package com.fts.ttbros.chat

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import com.fts.ttbros.R
import com.fts.ttbros.data.model.PollOption
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import java.util.UUID

class CreatePollDialog(
    private val context: Context,
    private val onPollCreated: (question: String, options: List<PollOption>, isAnonymous: Boolean) -> Unit
) {

    private val optionInputs = mutableListOf<TextInputEditText>()
    private var optionsContainer: LinearLayout? = null

    fun show() {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_create_poll, null)
        
        val questionInput = view.findViewById<EditText>(R.id.pollQuestionInput)
        val anonymousCheckbox = view.findViewById<CheckBox>(R.id.anonymousPollCheckbox)
        optionsContainer = view.findViewById(R.id.pollOptionsContainer)
        val addOptionButton = view.findViewById<MaterialButton>(R.id.addOptionButton)

        // Add initial 2 options
        addOptionInput()
        addOptionInput()

        addOptionButton.setOnClickListener {
            if (optionInputs.size < 6) {
                addOptionInput()
            } else {
                Snackbar.make(view, R.string.max_options_reached, Snackbar.LENGTH_SHORT).show()
            }
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.create_poll)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val question = questionInput.text.toString().trim()
                val isAnonymous = anonymousCheckbox.isChecked
                
                val options = optionInputs
                    .mapNotNull { it.text?.toString()?.trim() }
                    .filter { it.isNotBlank() }
                    .map { PollOption(id = UUID.randomUUID().toString(), text = it) }
                
                if (question.isNotBlank() && options.size >= 2) {
                    onPollCreated(question, options, isAnonymous)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun addOptionInput() {
        val optionView = LayoutInflater.from(context).inflate(R.layout.item_poll_option_input, optionsContainer, false)
        val optionInput = optionView.findViewById<TextInputEditText>(R.id.pollOptionInput)
        val removeButton = optionView.findViewById<MaterialButton>(R.id.removeOptionButton)

        optionInput.hint = context.getString(R.string.poll_option, optionInputs.size + 1)
        
        removeButton.setOnClickListener {
            if (optionInputs.size > 2) {
                optionsContainer?.removeView(optionView)
                optionInputs.remove(optionInput)
                updateOptionHints()
            }
        }

        // Hide remove button for first 2 options
        if (optionInputs.size < 2) {
            removeButton.visibility = View.GONE
        }

        optionInputs.add(optionInput)
        optionsContainer?.addView(optionView)
    }

    private fun updateOptionHints() {
        optionInputs.forEachIndexed { index, input ->
            input.hint = context.getString(R.string.poll_option, index + 1)
        }
    }
}
