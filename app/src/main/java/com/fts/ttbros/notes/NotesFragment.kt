package com.fts.ttbros.notes

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.fts.ttbros.R
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

class NotesFragment : Fragment() {

    private lateinit var notesRecyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var addNoteFab: FloatingActionButton
    
    private val notesAdapter = NotesAdapter()
    private val notes = mutableListOf<Note>()
    private var currentPhotoPath: String? = null

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            currentPhotoPath?.let { path ->
                // Photo was taken, path is stored
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_notes, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        notesRecyclerView = view.findViewById(R.id.notesRecyclerView)
        emptyView = view.findViewById(R.id.emptyView)
        addNoteFab = view.findViewById(R.id.addNoteFab)
        
        notesRecyclerView.adapter = notesAdapter
        addNoteFab.setOnClickListener {
            showAddNoteDialog()
        }
        
        updateEmptyView()
    }

    private fun showAddNoteDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_note, null)
        val noteEditText = dialogView.findViewById<EditText>(R.id.noteEditText)
        val photoButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.photoButton)
        val photoPreview = dialogView.findViewById<ImageView>(R.id.photoPreview)

        photoButton.setOnClickListener {
            takePhoto(photoPreview)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Новая заметка")
            .setView(dialogView)
            .setPositiveButton("Сохранить") { _, _ ->
                val text = noteEditText.text.toString()
                if (text.isNotBlank()) {
                    val note = Note(text, currentPhotoPath)
                    notes.add(note)
                    notesAdapter.submitList(notes.toList())
                    currentPhotoPath = null
                    updateEmptyView()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun takePhoto(preview: ImageView) {
        val photoFile = File(requireContext().cacheDir, "note_${System.currentTimeMillis()}.jpg")
        currentPhotoPath = photoFile.absolutePath
        
        val photoUri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            photoFile
        )

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
        }

        takePictureLauncher.launch(intent)
        
        // Show preview after taking photo
        preview.post {
            if (photoFile.exists()) {
                val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                preview.setImageBitmap(bitmap)
                preview.isVisible = true
            }
        }
    }

    private fun updateEmptyView() {
        emptyView.isVisible = notes.isEmpty()
    }

    data class Note(val text: String, val photoPath: String?)

    class NotesAdapter : RecyclerView.Adapter<NotesAdapter.NoteViewHolder>() {
        private var notes: List<Note> = emptyList()

        fun submitList(newNotes: List<Note>) {
            notes = newNotes
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_note, parent, false)
            return NoteViewHolder(view)
        }

        override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
            holder.bind(notes[position])
        }

        override fun getItemCount() = notes.size

        class NoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val textView: TextView = itemView.findViewById(R.id.noteTextView)
            private val imageView: ImageView = itemView.findViewById(R.id.noteImageView)

            fun bind(note: Note) {
                textView.text = note.text
                
                if (note.photoPath != null && File(note.photoPath).exists()) {
                    val bitmap = BitmapFactory.decodeFile(note.photoPath)
                    imageView.setImageBitmap(bitmap)
                    imageView.isVisible = true
                } else {
                    imageView.isVisible = false
                }
            }
        }
    }
}
