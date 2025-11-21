package com.fts.ttbros.documents

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fts.ttbros.R
import com.fts.ttbros.databinding.FragmentDocumentsBinding
import com.fts.ttbros.databinding.ItemDocumentBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    
    // TODO: Replace these empty strings with direct links to your PDF files (e.g., from Firebase Storage, Google Drive direct link, or any public server)
    private val documents = listOf(
        RuleBook("1", "Vampire: The Masquerade 5e Corebook", "vtm_5e_core.pdf", "https://example.com/vtm_core.pdf"),
        RuleBook("2", "Dungeons & Dragons 5e Player's Handbook", "dnd_5e_phb.pdf", "https://example.com/dnd_phb.pdf"),
        RuleBook("3", "VTM 5e Camarilla", "vtm_5e_camarilla.pdf", "https://example.com/vtm_camarilla.pdf"),
        RuleBook("4", "VTM 5e Anarch", "vtm_5e_anarch.pdf", "https://example.com/vtm_anarch.pdf")
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
        
        checkDownloads()
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
                        Toast.makeText(requireContext(), "Скачано!", Toast.LENGTH_SHORT).show()
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
            
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )
            
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, "application/pdf")
            intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            
            startActivity(intent)
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
            holder.binding.actionImageView.setImageResource(android.R.drawable.ic_menu_view) // Eye icon or similar? using standard for now
            // Or just keep it simple
            holder.binding.actionImageView.visibility = View.GONE // Hide icon if downloaded, or show "Open"
        } else {
            holder.binding.statusTextView.text = "Нажмите для скачивания"
            holder.binding.actionImageView.setImageResource(android.R.drawable.stat_sys_download)
            holder.binding.actionImageView.visibility = View.VISIBLE
        }
        
        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = items.size
}
