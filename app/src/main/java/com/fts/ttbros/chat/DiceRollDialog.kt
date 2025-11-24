package com.fts.ttbros.chat

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.random.Random

data class DiceRollSendOptions(
    val sendToTeam: Boolean,
    val sendToMaster: Boolean
)

class DiceRollDialog(
    private val isMaster: Boolean,
    private val onRollResult: (String, DiceRollSendOptions) -> Unit
) : DialogFragment() {

    private lateinit var diceTypeSpinner: Spinner
    private lateinit var quantityEditText: EditText
    private lateinit var resultTextView: TextView
    private lateinit var rollButton: Button
    private lateinit var sendButton: Button
    private lateinit var sendToTeamCheckbox: MaterialCheckBox
    private lateinit var sendToMasterCheckbox: MaterialCheckBox
    private lateinit var sendToLabelTextView: TextView

    private var lastRollResult: String? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireContext())
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(com.fts.ttbros.R.layout.dialog_dice_roll, null)

        diceTypeSpinner = view.findViewById(com.fts.ttbros.R.id.diceTypeSpinner)
        quantityEditText = view.findViewById(com.fts.ttbros.R.id.quantityEditText)
        resultTextView = view.findViewById(com.fts.ttbros.R.id.resultTextView)
        rollButton = view.findViewById(com.fts.ttbros.R.id.rollButton)
        sendButton = view.findViewById(com.fts.ttbros.R.id.sendButton)
        sendToTeamCheckbox = view.findViewById(com.fts.ttbros.R.id.sendToTeamCheckbox)
        sendToMasterCheckbox = view.findViewById(com.fts.ttbros.R.id.sendToMasterCheckbox)
        sendToLabelTextView = view.findViewById(com.fts.ttbros.R.id.sendToLabelTextView)
        
        // Скрываем чекбокс "Мастеру" если пользователь сам мастер
        if (isMaster) {
            sendToMasterCheckbox.visibility = android.view.View.GONE
        }

        setupDiceSpinner()
        setupButtons()

        val dialog = builder.setView(view)
            .setTitle("Бросок кубиков")
            .setNegativeButton("Отмена") { _, _ -> }
            .create()

        // Устанавливаем размер диалога
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )

        return dialog
    }

    private fun setupDiceSpinner() {
        val diceTypes = listOf(
            "d2", "d4", "d6", "d8", "d10", "d12", "d20", "d100"
        )
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            diceTypes
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        diceTypeSpinner.adapter = adapter
    }

    private fun setupButtons() {
        rollButton.setOnClickListener {
            rollDice()
        }

        sendButton.setOnClickListener {
            if (lastRollResult != null) {
                val sendOptions = DiceRollSendOptions(
                    sendToTeam = sendToTeamCheckbox.isChecked,
                    sendToMaster = sendToMasterCheckbox.isChecked && !isMaster
                )
                
                // Проверяем, что хотя бы один чекбокс выбран
                if (sendOptions.sendToTeam || sendOptions.sendToMaster) {
                    onRollResult(lastRollResult!!, sendOptions)
                    dismiss()
                } else {
                    // Показываем сообщение, что нужно выбрать хотя бы один чат
                    android.widget.Toast.makeText(
                        requireContext(),
                        "Выберите хотя бы один чат для отправки",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        // Изначально кнопка отправки неактивна и скрыта
        sendButton.isEnabled = false
        sendButton.visibility = android.view.View.GONE
        sendToLabelTextView.visibility = android.view.View.GONE
        sendToTeamCheckbox.visibility = android.view.View.GONE
        sendToMasterCheckbox.visibility = android.view.View.GONE
    }

    private fun rollDice() {
        val diceType = diceTypeSpinner.selectedItem.toString()
        val sides = when (diceType) {
            "d2" -> 2
            "d4" -> 4
            "d6" -> 6
            "d8" -> 8
            "d10" -> 10
            "d12" -> 12
            "d20" -> 20
            "d100" -> 100
            else -> 6
        }

        val quantityText = quantityEditText.text.toString().trim()
        val quantity = if (quantityText.isBlank()) {
            1
        } else {
            quantityText.toIntOrNull()?.coerceIn(1, 100) ?: 1
        }

        // Бросаем кубики
        val results = mutableListOf<Int>()
        repeat(quantity) {
            results.add(Random.nextInt(1, sides + 1))
        }

        val total = results.sum()
        val resultText = buildString {
            append("🎲 ")
            append(if (quantity > 1) "$quantity$diceType" else diceType)
            append(": ")
            if (quantity > 1) {
                append("[${results.joinToString(", ")}]")
                append(" = $total")
            } else {
                append("$total")
            }
        }

        resultTextView.text = resultText
        lastRollResult = resultText
        
        // Показываем кнопку отправки и чекбоксы после броска
        sendButton.isEnabled = true
        sendButton.visibility = android.view.View.VISIBLE
        sendToLabelTextView.visibility = android.view.View.VISIBLE
        sendToTeamCheckbox.visibility = android.view.View.VISIBLE
        if (!isMaster) {
            sendToMasterCheckbox.visibility = android.view.View.VISIBLE
        }
    }
}

