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
        val context = context
        val activity = activity
        
        if (context == null || activity == null) {
            // Если контекст недоступен, возвращаем пустой диалог
            // Это не должно произойти, но на всякий случай
            android.util.Log.e("DiceRollDialog", "Context or Activity is null in onCreateDialog")
            return super.onCreateDialog(savedInstanceState)
        }
        
        return try {
            val builder = MaterialAlertDialogBuilder(context)
            val inflater = activity.layoutInflater
            val view = inflater.inflate(com.fts.ttbros.R.layout.dialog_dice_roll, null)

            diceTypeSpinner = view.findViewById(com.fts.ttbros.R.id.diceTypeSpinner)
            quantityEditText = view.findViewById(com.fts.ttbros.R.id.quantityEditText)
            resultTextView = view.findViewById(com.fts.ttbros.R.id.resultTextView)
            rollButton = view.findViewById(com.fts.ttbros.R.id.rollButton)
            sendButton = view.findViewById(com.fts.ttbros.R.id.sendButton)
            sendToTeamCheckbox = view.findViewById(com.fts.ttbros.R.id.sendToTeamCheckbox)
            sendToMasterCheckbox = view.findViewById(com.fts.ttbros.R.id.sendToMasterCheckbox)
            sendToLabelTextView = view.findViewById(com.fts.ttbros.R.id.sendToLabelTextView)
            
            // Проверяем что все view найдены
            if (!::diceTypeSpinner.isInitialized || !::quantityEditText.isInitialized || 
                !::resultTextView.isInitialized || !::rollButton.isInitialized || 
                !::sendButton.isInitialized || !::sendToTeamCheckbox.isInitialized || 
                !::sendToMasterCheckbox.isInitialized || !::sendToLabelTextView.isInitialized) {
                android.util.Log.e("DiceRollDialog", "Failed to find all views")
                return MaterialAlertDialogBuilder(context)
                    .setTitle("Ошибка")
                    .setMessage("Не удалось инициализировать диалог")
                    .setPositiveButton("OK", null)
                    .create()
            }
            
            // Скрываем чекбокс "Мастеру" если пользователь сам мастер
            if (isMaster) {
                sendToMasterCheckbox.visibility = android.view.View.GONE
            }

            setupDiceSpinner()
            setupButtons()

            val dialog = builder
                .setTitle("Бросок кубиков")
                .setView(view)
                .setNegativeButton("Отмена", null)
                .create()

            // Устанавливаем размер диалога
            try {
                dialog.window?.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT
                )
            } catch (e: Exception) {
                android.util.Log.e("DiceRollDialog", "Error setting dialog window size: ${e.message}", e)
            }

            dialog
        } catch (e: Exception) {
            android.util.Log.e("DiceRollDialog", "Error creating dialog: ${e.message}", e)
            MaterialAlertDialogBuilder(context ?: activity ?: return super.onCreateDialog(savedInstanceState))
                .setTitle("Ошибка")
                .setMessage("Не удалось открыть диалог: ${e.message}")
                .setPositiveButton("OK", null)
                .create()
        }
    }

    private fun setupDiceSpinner() {
        try {
            val context = context ?: return
            if (!::diceTypeSpinner.isInitialized) return
            
            val diceTypes = listOf(
                "d2", "d4", "d6", "d8", "d10", "d12", "d20", "d100"
            )
            val adapter = ArrayAdapter(
                context,
                android.R.layout.simple_spinner_item,
                diceTypes
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            diceTypeSpinner.adapter = adapter
        } catch (e: Exception) {
            android.util.Log.e("DiceRollDialog", "Error setting up dice spinner: ${e.message}", e)
        }
    }

    private fun setupButtons() {
        try {
            if (!::rollButton.isInitialized || !::sendButton.isInitialized) return
            
            rollButton.setOnClickListener {
                try {
                    rollDice()
                } catch (e: Exception) {
                    android.util.Log.e("DiceRollDialog", "Error in rollDice: ${e.message}", e)
                    val context = context
                    if (context != null) {
                        android.widget.Toast.makeText(
                            context,
                            "Ошибка броска: ${e.message}",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

            sendButton.setOnClickListener {
                try {
                    if (lastRollResult != null) {
                        val sendOptions = DiceRollSendOptions(
                            sendToTeam = if (::sendToTeamCheckbox.isInitialized) sendToTeamCheckbox.isChecked else false,
                            sendToMaster = if (::sendToMasterCheckbox.isInitialized) sendToMasterCheckbox.isChecked && !isMaster else false
                        )
                        
                        // Проверяем, что хотя бы один чекбокс выбран
                        if (sendOptions.sendToTeam || sendOptions.sendToMaster) {
                            onRollResult(lastRollResult!!, sendOptions)
                            dismiss()
                        } else {
                            // Показываем сообщение, что нужно выбрать хотя бы один чат
                            val context = context
                            if (context != null) {
                                android.widget.Toast.makeText(
                                    context,
                                    "Выберите хотя бы один чат для отправки",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("DiceRollDialog", "Error in sendButton click: ${e.message}", e)
                }
            }

            // Изначально кнопка отправки неактивна и скрыта
            sendButton.isEnabled = false
            sendButton.visibility = android.view.View.GONE
            if (::sendToLabelTextView.isInitialized) {
                sendToLabelTextView.visibility = android.view.View.GONE
            }
            if (::sendToTeamCheckbox.isInitialized) {
                sendToTeamCheckbox.visibility = android.view.View.GONE
            }
            if (::sendToMasterCheckbox.isInitialized) {
                sendToMasterCheckbox.visibility = android.view.View.GONE
            }
        } catch (e: Exception) {
            android.util.Log.e("DiceRollDialog", "Error setting up buttons: ${e.message}", e)
        }
    }

    private fun rollDice() {
        try {
            if (!::diceTypeSpinner.isInitialized || !::quantityEditText.isInitialized || 
                !::resultTextView.isInitialized || !::sendButton.isInitialized) {
                android.util.Log.e("DiceRollDialog", "Views not initialized in rollDice")
                return
            }
            
            val diceType = diceTypeSpinner.selectedItem?.toString() ?: "d6"
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

            val quantityText = quantityEditText.text?.toString()?.trim() ?: "1"
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
            if (::sendToLabelTextView.isInitialized) {
                sendToLabelTextView.visibility = android.view.View.VISIBLE
            }
            if (::sendToTeamCheckbox.isInitialized) {
                sendToTeamCheckbox.visibility = android.view.View.VISIBLE
            }
            if (!isMaster && ::sendToMasterCheckbox.isInitialized) {
                sendToMasterCheckbox.visibility = android.view.View.VISIBLE
            }
        } catch (e: Exception) {
            android.util.Log.e("DiceRollDialog", "Error in rollDice: ${e.message}", e)
            throw e
        }
    }
}

