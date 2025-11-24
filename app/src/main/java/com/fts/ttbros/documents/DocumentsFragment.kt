package com.fts.ttbros.documents

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fts.ttbros.R
import com.fts.ttbros.data.model.Document
import com.fts.ttbros.data.model.UserRole
import com.fts.ttbros.data.repository.DocumentRepository
import com.fts.ttbros.data.repository.UserRepository
import com.fts.ttbros.databinding.FragmentDocumentsBinding
import com.fts.ttbros.databinding.ItemDocumentBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

class DocumentsFragment : Fragment() {

    private var _binding: FragmentDocumentsBinding? = null
    private val binding get() = _binding!!
    
    private val documentRepository = DocumentRepository()
    private val userRepository = UserRepository()
    private val sheetRepository = com.fts.ttbros.data.repository.CharacterSheetRepository()
    private lateinit var adapter: DocumentsAdapter
    private lateinit var sheetsAdapter: com.fts.ttbros.charactersheets.CharacterSheetsAdapter
    private var currentTeamId: String? = null
    private var isMaster: Boolean = false
    private var currentUserId: String = ""
    private var currentUserName: String = ""
    private var allDocuments: List<Document> = emptyList()
    private var allSheets: List<com.fts.ttbros.data.model.CharacterSheet> = emptyList()

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { showUploadDialog(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDocumentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        adapter = DocumentsAdapter(
            onClick = { doc -> onDocumentClicked(doc) },
            onLongClick = { doc -> if (isMaster) showDeleteDialog(doc) }
        )
        
        sheetsAdapter = com.fts.ttbros.charactersheets.CharacterSheetsAdapter(
            onSheetClick = { sheet -> onSheetClicked(sheet) },
            onSheetDelete = { sheet -> showDeleteSheetDialog(sheet) }
        )
        
        binding.documentsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.documentsRecyclerView.adapter = adapter
        
        // Hide delete all button as we moved to individual delete
        binding.deleteDocsButton.isVisible = false
        
        binding.addDocumentFab.setOnClickListener {
            val selectedTab = binding.tabLayout.selectedTabPosition
            if (selectedTab == 0) {
                // Загрузка документа
                filePickerLauncher.launch("application/pdf")
            } else {
                // Загрузка листа персонажа - навигация в CharacterSheetsFragment
                try {
                    findNavController().navigate(R.id.action_documentsFragment_to_characterSheetsFragment)
                } catch (e: Exception) {
                    android.util.Log.e("DocumentsFragment", "Navigation error: ${e.message}", e)
                    Snackbar.make(binding.root, "Ошибка открытия загрузки листа", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
        
        setupTabs()
        loadData()
    }
    
    private fun setupTabs() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Документы"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Листы персонажей"))
        
        binding.tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                val tabPosition = tab?.position ?: 0
                if (tabPosition == 0) {
                    // Документы
                    binding.documentsRecyclerView.adapter = adapter
                    adapter.submitList(allDocuments)
                    binding.emptyView.isVisible = allDocuments.isEmpty()
                    binding.addDocumentFab.isVisible = isMaster
                } else {
                    // Листы персонажей
                    binding.documentsRecyclerView.adapter = sheetsAdapter
                    sheetsAdapter.submitList(allSheets)
                    binding.emptyView.isVisible = allSheets.isEmpty()
                    binding.addDocumentFab.isVisible = true // Показываем FAB для загрузки листов
                }
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })
    }
    
    private fun loadData() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val profile = userRepository.currentProfile()
                if (profile == null || profile.teamId.isNullOrBlank()) {
                    binding.emptyView.text = "Join a team to view documents"
                    binding.emptyView.isVisible = true
                    binding.addDocumentFab.isVisible = false
                    return@launch
                }
                
                currentTeamId = profile.teamId
                currentUserId = profile.uid
                currentUserName = profile.displayName
                isMaster = profile.role == UserRole.MASTER
                
                binding.addDocumentFab.isVisible = isMaster
                
                val teamId = profile.teamId
                if (teamId == null) {
                    binding.emptyView.text = "Join a team to view documents"
                    binding.emptyView.isVisible = true
                    return@launch
                }
                
                documentRepository.getDocuments(teamId).collect { docs ->
                    // Фильтруем документы, исключая листы персонажей (они хранятся в character_sheets)
                    allDocuments = docs.filter { doc ->
                        // Исключаем документы из папки character_sheets
                        !doc.downloadUrl.contains("/character_sheets/")
                    }
                    
                    // Загружаем листы персонажей
                    allSheets = sheetRepository.getUserSheets(currentUserId)
                    
                    // Обновляем отображаемый список в зависимости от выбранной вкладки
                    val selectedTab = binding.tabLayout.selectedTabPosition
                    if (selectedTab == 0) {
                        adapter.submitList(allDocuments)
                        binding.emptyView.isVisible = allDocuments.isEmpty()
                        checkDownloads(allDocuments)
                    } else {
                        sheetsAdapter.submitList(allSheets)
                        binding.emptyView.isVisible = allSheets.isEmpty()
                    }
                }
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Error loading documents: ${e.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }
    
    private fun showUploadDialog(uri: Uri) {
        val context = context ?: return
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_add_note, null) // Reusing layout or create new
        // Ideally create a new layout for document upload, but for now let's use a simple input
        val input = TextInputEditText(context)
        input.hint = "Document Title"
        
        // Try to get filename from URI
        var fileName = "document.pdf"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex != -1) {
                fileName = cursor.getString(nameIndex)
                input.setText(fileName.substringBeforeLast("."))
            }
        }

        MaterialAlertDialogBuilder(context)
            .setTitle("Upload Document")
            .setView(input)
            .setPositiveButton("Upload") { _, _ ->
                val title = input.text?.toString()?.trim() ?: fileName
                uploadDocument(uri, title, fileName)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun uploadDocument(uri: Uri, title: String, fileName: String) {
        val teamId = currentTeamId ?: return
        
        binding.progressBar.isVisible = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                documentRepository.uploadDocument(
                    teamId, uri, title, fileName, currentUserId, currentUserName, requireContext()
                )
                Snackbar.make(binding.root, "Document uploaded", Snackbar.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Upload failed: ${e.message}", Snackbar.LENGTH_LONG).show()
            } finally {
                binding.progressBar.isVisible = false
            }
        }
    }
    
    private fun showDeleteDialog(doc: Document) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Document")
            .setMessage("Delete '${doc.title}'?")
            .setPositiveButton("Delete") { _, _ ->
                deleteDocument(doc)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteDocument(doc: Document) {
        val teamId = currentTeamId ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                documentRepository.deleteDocument(teamId, doc.id, doc.downloadUrl)
                Snackbar.make(binding.root, "Document deleted", Snackbar.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Delete failed: ${e.message}", Snackbar.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun checkDownloads(docs: List<Document>) {
        val docsDir = File(requireContext().filesDir, "documents")
        if (!docsDir.exists()) docsDir.mkdirs()
        
        val downloadedIds = mutableSetOf<String>()
        docs.forEach { doc ->
            // Use ID or Filename to check. Using ID is safer for uniqueness, but filename is needed for extension.
            // Let's use id_filename format
            val file = File(docsDir, "${doc.id}_${doc.fileName}")
            if (file.exists()) {
                downloadedIds.add(doc.id)
            }
        }
        adapter.setDownloaded(downloadedIds)
    }
    
    private fun onDocumentClicked(doc: Document) {
        val docsDir = File(requireContext().filesDir, "documents")
        docsDir.mkdirs() // Создать папку если не существует
        val file = File(docsDir, "${doc.id}_${doc.fileName}")
        
        if (file.exists()) {
            openDocument(file)
        } else {
            downloadDocument(doc, file)
        }
    }
    
    private fun downloadDocument(doc: Document, targetFile: File) {
        val context = context ?: return
        Toast.makeText(context, "Downloading ${doc.title}...", Toast.LENGTH_SHORT).show()
        
        if (!isAdded || view == null) {
            android.util.Log.w("DocumentsFragment", "Fragment not attached, cannot download")
            return
        }
        
        binding.progressBar.isVisible = true
        
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    URL(doc.downloadUrl).openStream().use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        if (isAdded && view != null) {
                            binding.progressBar.isVisible = false
                            adapter.markAsDownloaded(doc.id)
                            Toast.makeText(context, "Downloaded", Toast.LENGTH_SHORT).show()
                            openDocument(targetFile)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        if (isAdded && view != null) {
                            binding.progressBar.isVisible = false
                            Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }
    
    private fun openDocument(file: File) {
        val context = context
        if (context == null || !isAdded || view == null) {
            android.util.Log.w("DocumentsFragment", "Fragment not attached, cannot open document")
            return
        }
        
        try {
            // Проверка что файл существует и доступен
            if (!file.exists() || !file.canRead()) {
                android.util.Log.w("DocumentsFragment", "File not accessible: ${file.absolutePath}")
                Toast.makeText(context, "File not accessible", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Проверка что файл не пустой
            if (file.length() == 0L) {
                android.util.Log.w("DocumentsFragment", "File is empty: ${file.absolutePath}")
                Toast.makeText(context, "File is empty", Toast.LENGTH_SHORT).show()
                return
            }
            
            val bundle = Bundle().apply {
                putString("filePath", file.absolutePath)
            }
            
            // Проверка что навигация доступна
            try {
                val navController = findNavController()
                if (navController.currentDestination?.id == R.id.documentsFragment) {
                    navController.navigate(R.id.action_documentsFragment_to_pdfViewerFragment, bundle)
                } else {
                    android.util.Log.w("DocumentsFragment", "Navigation not available, current destination: ${navController.currentDestination?.id}")
                    Toast.makeText(context, "Navigation not available", Toast.LENGTH_SHORT).show()
                }
            } catch (e: IllegalStateException) {
                android.util.Log.e("DocumentsFragment", "Navigation error (IllegalStateException): ${e.message}", e)
                Toast.makeText(context, "Error opening PDF viewer: ${e.message}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("DocumentsFragment", "Navigation error: ${e.message}", e)
                Toast.makeText(context, "Error opening PDF viewer", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            android.util.Log.e("DocumentsFragment", "SecurityException opening document: ${e.message}", e)
            Toast.makeText(context, "No permission to access file", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.util.Log.e("DocumentsFragment", "Error opening document: ${e.message}", e)
            Toast.makeText(context, "Error opening PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onSheetClicked(sheet: com.fts.ttbros.data.model.CharacterSheet) {
        // Открываем PDF листа персонажа
        val context = context ?: return
        if (!isAdded || view == null) {
            android.util.Log.w("DocumentsFragment", "Fragment not attached, cannot open sheet")
            return
        }
        
        try {
            // Скачиваем PDF из Yandex.Disk
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    binding.progressBar.isVisible = true
                    val pdfUrl = sheet.pdfUrl
                    if (pdfUrl.isBlank()) {
                        Snackbar.make(binding.root, "PDF не найден", Snackbar.LENGTH_SHORT).show()
                        binding.progressBar.isVisible = false
                        return@launch
                    }
                    
                    val docsDir = File(requireContext().filesDir, "documents")
                    docsDir.mkdirs()
                    val file = File(docsDir, "${sheet.id}_${sheet.characterName}.pdf")
                    
                    if (file.exists()) {
                        openDocument(file)
                        binding.progressBar.isVisible = false
                    } else {
                        // Скачиваем файл
                        withContext(Dispatchers.IO) {
                            URL(pdfUrl).openStream().use { input ->
                                FileOutputStream(file).use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                        withContext(Dispatchers.Main) {
                            if (isAdded && view != null) {
                                binding.progressBar.isVisible = false
                                openDocument(file)
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("DocumentsFragment", "Error downloading sheet PDF: ${e.message}", e)
                    if (isAdded && view != null) {
                        binding.progressBar.isVisible = false
                        Snackbar.make(binding.root, "Ошибка загрузки PDF: ${e.message}", Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DocumentsFragment", "Error opening sheet: ${e.message}", e)
            Snackbar.make(binding.root, "Ошибка открытия листа: ${e.message}", Snackbar.LENGTH_SHORT).show()
        }
    }
    
    private fun showDeleteSheetDialog(sheet: com.fts.ttbros.data.model.CharacterSheet) {
        val context = context ?: return
        if (!isAdded || view == null) return
        
        MaterialAlertDialogBuilder(context)
            .setTitle("Удалить лист персонажа")
            .setMessage("Вы уверены, что хотите удалить '${sheet.characterName}'? Это действие нельзя отменить.")
            .setPositiveButton("Удалить") { _, _ ->
                deleteSheet(sheet)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    private fun deleteSheet(sheet: com.fts.ttbros.data.model.CharacterSheet) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                sheetRepository.deleteSheet(sheet.id)
                if (isAdded && view != null) {
                    Snackbar.make(binding.root, "Лист удалён", Snackbar.LENGTH_SHORT).show()
                    loadData() // Перезагружаем данные
                }
            } catch (e: Exception) {
                android.util.Log.e("DocumentsFragment", "Error deleting sheet: ${e.message}", e)
                if (isAdded && view != null) {
                    Snackbar.make(binding.root, "Ошибка удаления: ${e.message}", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class DocumentsAdapter(
    private val onClick: (Document) -> Unit,
    private val onLongClick: (Document) -> Unit
) : RecyclerView.Adapter<DocumentsAdapter.ViewHolder>() {

    private var items: List<Document> = emptyList()
    private val downloadedIds = mutableSetOf<String>()

    fun submitList(newItems: List<Document>) {
        items = newItems
        notifyDataSetChanged()
    }
    
    fun setDownloaded(ids: Set<String>) {
        downloadedIds.clear()
        downloadedIds.addAll(ids)
        notifyDataSetChanged()
    }
    
    fun markAsDownloaded(id: String) {
        downloadedIds.add(id)
        notifyDataSetChanged()
    }

    class ViewHolder(val binding: ItemDocumentBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDocumentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.titleTextView.text = item.title
        
        val isDownloaded = downloadedIds.contains(item.id)
        
        if (isDownloaded) {
            holder.binding.statusTextView.text = "Downloaded"
            holder.binding.actionImageView.setImageResource(R.drawable.ic_check_circle)
        } else {
            holder.binding.statusTextView.text = "Tap to download (${formatSize(item.sizeBytes)})"
            holder.binding.actionImageView.setImageResource(android.R.drawable.stat_sys_download)
        }
        
        holder.itemView.setOnClickListener { onClick(item) }
        holder.itemView.setOnLongClickListener { 
            onLongClick(item)
            true 
        }
    }
    
    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return ""
        val kb = bytes / 1024
        if (kb < 1024) return "$kb KB"
        return "${kb / 1024} MB"
    }

    override fun getItemCount() = items.size
}
