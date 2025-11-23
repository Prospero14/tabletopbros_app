package com.fts.ttbros.documents

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fts.ttbros.R
import com.fts.ttbros.databinding.FragmentDocumentsBinding
import com.fts.ttbros.databinding.ItemDocumentBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class RuleBook(
    val id: String,
    val title: String,
    val fileName: String,
    val url: String,
    var isDownloaded: Boolean = false
)

class DocumentsFragment : Fragment() {

    private var _binding: FragmentDocumentsBinding? = null
    private val binding get() = _binding!!
    
    // TODO: Replace these empty strings with direct links to your PDF files
    private val documents = listOf(
        RuleBook("1", "Dungeons & Dragons 5e Player's Handbook", "dnd_5e_phb.pdf", "https://drive.google.com/uc?export=download&id=1wR9VH195dnIYlphMr63d5fdsns4o5xSn"),
        RuleBook("2", "Vampire: The Masquerade 5e Corebook", "vtm_5e_core.pdf", "https://drive.google.com/uc?export=download&id=1MUU2c_MSMEQ5yA_JBiIIdBdaQa8sLAX4"),
        // RuleBook("3", "The Witcher TRPG", "witcher_trpg.pdf", "https://drive.google.com/uc?export=download&id=1rSQuKUko9dqBPf7GtqMTtDeadubZZ7di"),
        RuleBook("4", "VTM 5e Character Sheet", "vtm_5e_sheet.pdf", "https://drive.google.com/uc?export=download&id=1LInpHrBwqPO6Cfluj4ZmetARUsMuqNGm"),
        RuleBook("5", "DND 5e Character Sheet", "dnd_5e_sheet.pdf", "https://drive.google.com/uc?export=download&id=1Y18OaGV5pkY0Z7pF7NVmFOGzsPt1Wq2Q"),
        RuleBook("6", "Witcher Map", "witcher_map.pdf", "https://drive.google.com/uc?export=download&id=1a12ynj-atTsgU-rE0cY0QzR8dNKoPXi0"),
        RuleBook("7", "Witcher Character Sheet", "witcher_sheet.pdf", "https://drive.google.com/uc?export=download&id=15FSjt8QnGeWAWQc-hSfa7cAO3zBFk0cR")
    )
    
    private lateinit var adapter: DocumentsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDocumentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        adapter = DocumentsAdapter(documents) { book ->
            onBookClicked(book)
        }
        
        binding.documentsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.documentsRecyclerView.adapter = adapter
        
        binding.deleteDocsButton.setOnClickListener {
            showDeleteConfirmationDialog()
        }
        
        checkDownloads()
    }
    
    private fun showDeleteConfirmationDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Удалить все документы?")
            .setMessage("Вы точно хотите удалить все сохраненные документы?")
            .setPositiveButton("Удалить") { _, _ ->
                deleteAllDocuments()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    private fun deleteAllDocuments() {
        val docsDir = File(requireContext().filesDir, "documents")
        if (docsDir.exists()) {
            docsDir.deleteRecursively()
            docsDir.mkdirs() // Recreate empty directory
        }
        
        documents.forEach { it.isDownloaded = false }
        adapter.notifyDataSetChanged()
        Toast.makeText(requireContext(), "Все документы удалены", Toast.LENGTH_SHORT).show()
    }
    
    private fun checkDownloads() {
        val docsDir = File(requireContext().filesDir, "documents")
        if (!docsDir.exists()) docsDir.mkdirs()
        
        documents.forEach { book ->
            val file = File(docsDir, book.fileName)
            book.isDownloaded = file.exists()
        }
        adapter.notifyDataSetChanged()
    }
    
    private fun onBookClicked(book: RuleBook) {
        if (book.isDownloaded) {
            openDocument(book)
        } else {
            downloadDocument(book)
        }
    }
    
    private fun downloadDocument(book: RuleBook) {
        Toast.makeText(requireContext(), "Скачивание ${book.title}...", Toast.LENGTH_SHORT).show()
        
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val docsDir = File(requireContext().filesDir, "documents")
                    if (!docsDir.exists()) docsDir.mkdirs()
                    val file = File(docsDir, book.fileName)
                    
                    // Real download logic
                    java.net.URL(book.url).openStream().use { input ->
                        java.io.FileOutputStream(file).use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    withContext(Dispatchers.Main) {
                        checkDownloads()
                        Toast.makeText(requireContext(), "Скачано в: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(requireContext(), "Ошибка скачивания: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
    
    private fun openDocument(book: RuleBook) {
        try {
            val docsDir = File(requireContext().filesDir, "documents")
            val file = File(docsDir, book.fileName)
            
            if (file.exists()) {
                val bundle = Bundle().apply {
                    putString("filePath", file.absolutePath)
                }
                findNavController().navigate(R.id.action_documentsFragment_to_pdfViewerFragment, bundle)
            } else {
                Toast.makeText(requireContext(), "Файл не найден", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Не удалось открыть файл: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class DocumentsAdapter(
    private val items: List<RuleBook>,
    private val onClick: (RuleBook) -> Unit
) : RecyclerView.Adapter<DocumentsAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemDocumentBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDocumentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.titleTextView.text = item.title
        
        if (item.isDownloaded) {
            holder.binding.statusTextView.text = "Скачано"
            holder.binding.actionImageView.setImageResource(R.drawable.ic_check_circle)
            holder.binding.actionImageView.visibility = View.VISIBLE
        } else {
            holder.binding.statusTextView.text = "Нажмите для скачивания"
            holder.binding.actionImageView.setImageResource(android.R.drawable.stat_sys_download)
            holder.binding.actionImageView.visibility = View.VISIBLE
        }
        
        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = items.size
}
