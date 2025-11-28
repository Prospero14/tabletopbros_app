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
    
    private val notesAdapter = NotesAdapter { note, position ->
        showAddNoteDialog(note, position)
    }
    private val notes = mutableListOf<Note>()
    private var currentPhotoPath: String? = null
    private var currentPhotoPreview: ImageView? = null

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            // Permission granted, proceed with camera
            currentPhotoPreview?.let { launchCamera(it) }
        } else {
            com.google.android.material.snackbar.Snackbar.make(
                requireView(),
                "Camera permission required",
                com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            currentPhotoPath?.let { path ->
                val photoFile = File(path)
                if (photoFile.exists()) {
                    // Photo was taken successfully
                }
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
            showAddNoteDialog(null, -1)
        }
        
        updateEmptyView()
    }

    private fun showAddNoteDialog(existingNote: Note?, position: Int) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_note, null)
        val noteEditText = dialogView.findViewById<EditText>(R.id.noteEditText)
        val photoButton = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.photoButton)
        val photoPreview = dialogView.findViewById<ImageView>(R.id.photoPreview)

        if (existingNote != null) {
            noteEditText.setText(existingNote.text)
            val photoPath = existingNote.photoPath
            currentPhotoPath = photoPath
            if (photoPath != null && File(photoPath).exists()) {
                val bitmap = BitmapFactory.decodeFile(photoPath)
                photoPreview.setImageBitmap(bitmap)
                photoPreview.isVisible = true
            }
        } else {
            currentPhotoPath = null
        }

        photoButton.setOnClickListener {
            takePhoto(photoPreview)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existingNote != null) "Редактировать заметку" else "Новая заметка")
            .setView(dialogView)
            .setPositiveButton("Сохранить") { _, _ ->
                val text = noteEditText.text.toString()
                if (text.isNotBlank()) {
                    val note = Note(text, currentPhotoPath)
                    if (existingNote != null && position != -1) {
                        notes[position] = note
                    } else {
                        notes.add(note)
                    }
                    notesAdapter.submitList(notes.toList())
                    currentPhotoPath = null
                    updateEmptyView()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun takePhoto(preview: ImageView) {
        currentPhotoPreview = preview
        if (androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
            return
        }
        launchCamera(preview)
    }

    private fun launchCamera(preview: ImageView) {
        try {
            val photoFile = File(requireContext().cacheDir, "note_${System.currentTimeMillis()}.jpg")
            currentPhotoPath = photoFile.absolutePath
            
            val photoUri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                photoFile
            )

            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }

            takePictureLauncher.launch(intent)
            
            // Show preview after taking photo with delay
            preview.postDelayed({
                currentPhotoPath?.let { path ->
                    val file = File(path)
                    if (file.exists()) {
                        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                        preview.setImageBitmap(bitmap)
                        preview.isVisible = true
                    }
                }
            }, 1000)
        } catch (e: Exception) {
            android.util.Log.e("NotesFragment", "Error taking photo", e)
            com.google.android.material.snackbar.Snackbar.make(
                requireView(),
                "Ошибка при создании фото: ${e.message}",
                com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    private fun updateEmptyView() {
        emptyView.isVisible = notes.isEmpty()
    }

    data class Note(val text: String, val photoPath: String?)

    class NotesAdapter(private val onNoteClick: (Note, Int) -> Unit) : RecyclerView.Adapter<NotesAdapter.NoteViewHolder>() {
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
            holder.itemView.setOnClickListener {
                onNoteClick(notes[position], position)
            }
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
