package com.fts.ttbros.calendar

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.EditText
import com.fts.ttbros.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Calendar

class CreateEventDialog(
    private val context: Context,
    private val onEventCreated: (title: String, description: String, dateTime: Long) -> Unit
) {

    private var selectedDateTime: Long = System.currentTimeMillis()
    private var eventToEdit: com.fts.ttbros.data.model.Event? = null

    fun setEventData(event: com.fts.ttbros.data.model.Event) {
        eventToEdit = event
        selectedDateTime = event.dateTime
    }

    fun show() {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_create_event, null)
        
        val titleInput = view.findViewById<EditText>(R.id.eventTitleInput)
        val descriptionInput = view.findViewById<EditText>(R.id.eventDescriptionInput)
        val dateButton = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.selectDateButton)
        val timeButton = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.selectTimeButton)

        // Pre-fill data if editing
        eventToEdit?.let { event ->
            titleInput.setText(event.title)
            descriptionInput.setText(event.description)
        }

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = selectedDateTime
        
        // Update button texts
        dateButton.text = String.format("%02d.%02d.%04d", 
            calendar.get(Calendar.DAY_OF_MONTH), 
            calendar.get(Calendar.MONTH) + 1, 
            calendar.get(Calendar.YEAR))
        timeButton.text = String.format("%02d:%02d", 
            calendar.get(Calendar.HOUR_OF_DAY), 
            calendar.get(Calendar.MINUTE))

        dateButton.setOnClickListener {
            showDatePicker(calendar) { year, month, day ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, day)
                selectedDateTime = calendar.timeInMillis
                dateButton.text = String.format("%02d.%02d.%04d", day, month + 1, year)
            }
        }

        timeButton.setOnClickListener {
            showTimePicker(calendar) { hour, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hour)
                calendar.set(Calendar.MINUTE, minute)
                selectedDateTime = calendar.timeInMillis
                timeButton.text = String.format("%02d:%02d", hour, minute)
            }
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(if (eventToEdit == null) R.string.create_event else R.string.create_event) // Could add "Edit Event" string
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val title = titleInput.text.toString().trim()
                val description = descriptionInput.text.toString().trim()
                
                if (title.isNotBlank()) {
                    onEventCreated(title, description, selectedDateTime)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showDatePicker(calendar: Calendar, onDateSelected: (Int, Int, Int) -> Unit) {
        DatePickerDialog(
            context,
            { _, year, month, day ->
                onDateSelected(year, month, day)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showTimePicker(calendar: Calendar, onTimeSelected: (Int, Int) -> Unit) {
        TimePickerDialog(
            context,
            { _, hour, minute ->
                onTimeSelected(hour, minute)
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
    }
}
