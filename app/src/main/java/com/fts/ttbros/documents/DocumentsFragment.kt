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
import com.fts.ttbros.chat.model.ChatType
import com.fts.ttbros.data.model.Document
import com.fts.ttbros.data.model.UserRole
import com.fts.ttbros.data.repository.DocumentRepository
import com.fts.ttbros.data.repository.UserRepository
import com.fts.ttbros.databinding.FragmentDocumentsBinding
import com.fts.ttbros.databinding.ItemDocumentBinding
import com.fts.ttbros.utils.SnackbarHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
    private var masterMaterialsPrivate: List<Document> = emptyList() // Tab 3a (Master only)
    private var gameMaterialsPublic: List<Document> = emptyList() // Tab 4a (All)
    private var isUploadingMasterMaterial: Boolean = false // Flag for Tab 3a upload
    private var isUploadingGameMaterial: Boolean = false // Flag for Tab 4a upload

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
            onLongClick = { doc -> 
                // Handle long click based on tab and role
                val selectedTab = binding.tabLayout.selectedTabPosition
                
                // Tab 3a (Master Materials Private) - Index depends on role
                // If Master: 0=Docs, 1=Sheets, 2=Master(Private), 3=Game(Public)
                // If Player: 0=Docs, 1=Sheets, 2=Game(Public)
                
                if (isMaster) {
                    when (selectedTab) {
                        2 -> showMasterMaterialMenu(doc) // Tab 3a
                        3 -> showDeleteDialog(doc) // Tab 4a
                        0 -> showDeleteDialog(doc) // Tab 1a
                        else -> {}
                    }
                } else {
                    // Players can't delete or manage materials usually, but let's keep existing logic if any
                }
            }
        )
        
        sheetsAdapter = com.fts.ttbros.charactersheets.CharacterSheetsAdapter(
            onSheetClick = { sheet -> onSheetClicked(sheet) },
            onSheetDelete = { sheet -> showDeleteSheetDialog(sheet) }
        )
        
        val context = context ?: return
        binding.documentsRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.documentsRecyclerView.adapter = adapter
        
        // Hide delete all button as we moved to individual delete
        binding.deleteDocsButton.isVisible = false
        
        binding.addDocumentFab.setOnClickListener {
            val selectedTab = binding.tabLayout.selectedTabPosition
            
            // Reset flags
            isUploadingMasterMaterial = false
            isUploadingGameMaterial = false
            
            if (isMaster) {
                when (selectedTab) {
                    0 -> filePickerLauncher.launch("application/pdf") // Tab 1a: Docs
                    1 -> findNavController().navigate(R.id.action_documentsFragment_to_characterSheetsFragment) // Tab 2a: Sheets
                    2 -> {
                        isUploadingMasterMaterial = true
                        filePickerLauncher.launch("*/*") // Tab 3a: Master Materials (Private)
                    }
                    3 -> {
                        isUploadingGameMaterial = true
                        filePickerLauncher.launch("*/*") // Tab 4a: Game Materials (Public)
                    }
                }
            } else {
                // Player
                when (selectedTab) {
                    1 -> findNavController().navigate(R.id.action_documentsFragment_to_characterSheetsFragment) // Tab 2a: Sheets
                    // Players can't upload to other tabs
                }
            }
        }
        
        // Don't setup tabs here - wait until we know if user is master
        loadData()
    }
    
    private fun setupTabs() {
        binding.tabLayout.removeAllTabs()
        
        // Tab 1a
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Документы"))
        
        // Tab 2a
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Листы персонажей"))
        
        if (isMaster) {
            // Tab 3a (Master only)
            binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Материалы мастера (Личные)"))
            // Tab 4a
            binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Материалы игры (Общие)"))
        } else {
            // Tab 4a (becomes 3rd tab for player)
            binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Материалы игры"))
        }
        
        binding.tabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                updateListForTab(tab?.position ?: 0)
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })
    }
    
    private fun updateListForTab(position: Int) {
        if (isMaster) {
            when (position) {
                0 -> { // Documents
                    binding.documentsRecyclerView.adapter = adapter
                    adapter.submitList(allDocuments)
                    binding.emptyView.isVisible = allDocuments.isEmpty()
                    binding.addDocumentFab.isVisible = true
                }
                1 -> { // Sheets
                    binding.documentsRecyclerView.adapter = sheetsAdapter
                    sheetsAdapter.submitList(allSheets)
                    binding.emptyView.isVisible = allSheets.isEmpty()
                    binding.addDocumentFab.isVisible = true
                }
                2 -> { // Master Materials (Private)
                    binding.documentsRecyclerView.adapter = adapter
                    adapter.submitList(masterMaterialsPrivate)
                    binding.emptyView.isVisible = masterMaterialsPrivate.isEmpty()
                    binding.addDocumentFab.isVisible = true
                }
                3 -> { // Game Materials (Public)
                    binding.documentsRecyclerView.adapter = adapter
                    adapter.submitList(gameMaterialsPublic)
                    binding.emptyView.isVisible = gameMaterialsPublic.isEmpty()
                    binding.addDocumentFab.isVisible = true
                }
            }
        } else {
            when (position) {
                0 -> { // Documents
                    binding.documentsRecyclerView.adapter = adapter
                    adapter.submitList(allDocuments)
                    binding.emptyView.isVisible = allDocuments.isEmpty()
                    binding.addDocumentFab.isVisible = false // Players can't upload docs
                }
                1 -> { // Sheets
                    binding.documentsRecyclerView.adapter = sheetsAdapter
                    sheetsAdapter.submitList(allSheets)
                    binding.emptyView.isVisible = allSheets.isEmpty()
                    binding.addDocumentFab.isVisible = true
                }
                2 -> { // Game Materials (Public)
                    binding.documentsRecyclerView.adapter = adapter
                    adapter.submitList(gameMaterialsPublic)
                    binding.emptyView.isVisible = gameMaterialsPublic.isEmpty()
                    binding.addDocumentFab.isVisible = false // Players can't upload materials
                }
            }
        }
    }
    
    private fun loadData() {
        binding.progressBar.isVisible = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val profile = userRepository.currentProfile()
                val teamIdValue = profile?.currentTeamId
                if (profile == null || teamIdValue.isNullOrBlank()) {
                    binding.emptyView.text = "Join a team to view documents"
                    binding.emptyView.isVisible = true
                    binding.addDocumentFab.isVisible = false
                    binding.progressBar.isVisible = false
                    return@launch
                }
                
                currentTeamId = teamIdValue
                currentUserId = profile.uid
                currentUserName = profile.displayName
                
                // Determine role
                val currentTeam = profile.teams.find { it.teamId == teamIdValue }
                isMaster = currentTeam?.role == UserRole.MASTER
                
                // Setup tabs
                if (binding.tabLayout.tabCount == 0) {
                    setupTabs()
                }
                
                // Update FAB visibility based on initial tab (0)
                binding.addDocumentFab.isVisible = isMaster // Only master can upload to tab 0
                
                val teamId = teamIdValue
                
                try {
                    documentRepository.getDocuments(teamId).collect { docs ->
                        try {
                            // Filter documents based on their path (id)
                            
                            // 1. Documents (Tab 1a) - everything in /documents/
                            allDocuments = docs.filter { doc ->
                                doc.id.contains("/documents/")
                            }
                            
                            // 2. Master Materials Private (Tab 3a) - everything in /master_materials/
                            masterMaterialsPrivate = docs.filter { doc ->
                                doc.id.contains("/master_materials/")
                            }
                            
                            // 3. Game Materials Public (Tab 4a) - everything in /player_materials/
                            gameMaterialsPublic = docs.filter { doc ->
                                doc.id.contains("/player_materials/")
                            }
                            
                            // 4. Sheets (Tab 2a)
                            val teamSystem = currentTeam?.teamSystem
                            val allUserSheets = sheetRepository.getUserSheets(currentUserId)
                            allSheets = if (!teamSystem.isNullOrBlank()) {
                                allUserSheets.filter { it.system == teamSystem }
                            } else {
                                allUserSheets
                            }
                            
                            // Update UI
                            updateListForTab(binding.tabLayout.selectedTabPosition)
                            
                        } catch (e: Exception) {
                            android.util.Log.e("DocumentsFragment", "Error processing documents: ${e.message}", e)
                            if (isAdded && view != null) {
                                SnackbarHelper.showErrorSnackbar(binding.root, getString(R.string.error_processing_documents, e.message ?: ""))
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("DocumentsFragment", "Error loading documents: ${e.message}", e)
                    if (isAdded && view != null) {
                        SnackbarHelper.showErrorSnackbar(binding.root, getString(R.string.error_loading_documents, e.message ?: ""))
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("DocumentsFragment", "Error in loadData: ${e.message}", e)
                if (isAdded && view != null) {
                    SnackbarHelper.showErrorSnackbar(binding.root, getString(R.string.error_unknown))
                }
            } finally {
                binding.progressBar.isVisible = false
            }
        }
    }
    
    private fun showUploadDialog(uri: Uri) {
        val context = context ?: return
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_add_note, null)
        val input = TextInputEditText(context)
        input.hint = "Document Title"
        
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
        try {
            val teamId = currentTeamId
            if (teamId == null) {
                if (isAdded && view != null) {
                    SnackbarHelper.showErrorSnackbar(binding.root, getString(R.string.error_team_not_selected))
                }
                return
            }
        
        val isMasterMat = isUploadingMasterMaterial
        val isGameMat = isUploadingGameMaterial
        
        // Reset flags
        isUploadingMasterMaterial = false
        isUploadingGameMaterial = false
        
        binding.progressBar.isVisible = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val context = context ?: return@launch
                if (!isAdded) return@launch
                
                // Pass flags to repository
                val document = documentRepository.uploadDocument(
                    teamId = teamId, 
                    uri = uri, 
                    title = title, 
                    fileName = fileName, 
                    userId = currentUserId, 
                    userName = currentUserName, 
                    context = context, 
                    isMaterial = isGameMat, // /player_materials/
                    isMasterMaterial = isMasterMat // /master_materials/
                )
                
                if (isAdded && view != null) {
                    SnackbarHelper.showSuccessSnackbar(binding.root, getString(R.string.document_uploaded))
                }
                
            } catch (e: Exception) {
                android.util.Log.e("DocumentsFragment", "Error uploading document: ${e.message}", e)
                if (isAdded && view != null) {
                    SnackbarHelper.showErrorSnackbar(binding.root, getString(R.string.error_uploading, e.message ?: ""))
                }
            } finally {
                if (isAdded && view != null) {
                    binding.progressBar.isVisible = false
                }
            }
        }
        } catch (e: Exception) {
            android.util.Log.e("DocumentsFragment", "Error in uploadDocument: ${e.message}", e)
            if (isAdded && view != null) {
                SnackbarHelper.showErrorSnackbar(binding.root, getString(R.string.error_uploading, e.message ?: ""))
            }
        }
    }
    
    private fun showMasterMaterialMenu(doc: Document) {
        val context = context ?: return
        if (!isAdded) return
        MaterialAlertDialogBuilder(context)
            .setTitle(doc.title)
            .setItems(arrayOf("Опубликовать в материалы игры", "Удалить")) { _, which ->
                when (which) {
                    0 -> publishMaterial(doc)
                    1 -> showDeleteDialog(doc)
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    private fun publishMaterial(doc: Document) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                binding.progressBar.isVisible = true
                
                // 1. Move file
                val newDoc = documentRepository.publishMaterial(doc)
                
                // 2. Send chat message
                if (newDoc != null) {
                     val chatRepository = com.fts.ttbros.chat.data.ChatRepository()
                     val teamId = currentTeamId ?: return@launch
                     
                     val message = com.fts.ttbros.chat.model.ChatMessage(
                        senderId = currentUserId,
                        senderName = currentUserName,
                        text = "Опубликован новый материал: ${newDoc.title}",
                        type = "material",
                        attachmentId = newDoc.id,
                        timestamp = com.google.firebase.Timestamp.now()
                    )
                    chatRepository.sendMessage(teamId, ChatType.TEAM, message)
                    
                    if (isAdded && view != null) {
                        SnackbarHelper.showSuccessSnackbar(binding.root, "Материал опубликован")
                    }
                    
                    // Switch to Game Materials tab (Tab 4a, index 3)
                    binding.tabLayout.getTabAt(3)?.select()
                }
                
            } catch (e: Exception) {
                android.util.Log.e("DocumentsFragment", "Error publishing material: ${e.message}", e)
                if (isAdded && view != null) {
                    SnackbarHelper.showErrorSnackbar(binding.root, "Ошибка публикации: ${e.message}")
                }
            } finally {
                binding.progressBar.isVisible = false
            }
        }
    }
    
    private fun showSendToChatDialog(document: Document) {
        val context = context ?: return
        if (!isAdded || view == null) return
        
        MaterialAlertDialogBuilder(context)
            .setTitle("Отправить в общий чат?")
            .setMessage("Хотите отправить материал '${document.title}' в общий чат?\n\nМатериал сохранён в разделе 'Материалы для игроков' и может быть отправлен позже.")
            .setPositiveButton("Отправить") { _, _ ->
                sendMaterialToChat(document)
            }
            .setNegativeButton("Позже") { _, _ ->
                // Материал уже сохранён в материалах для игроков, просто показываем сообщение
                if (isAdded && view != null) {
                    SnackbarHelper.showWarningSnackbar(binding.root, getString(R.string.material_saved_info))
                }
            }
            .show()
    }
    
    private fun sendMaterialToChat(document: Document) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val chatRepository = com.fts.ttbros.chat.data.ChatRepository()
                val teamId = currentTeamId
                if (teamId == null) {
                    if (isAdded && view != null) {
                        SnackbarHelper.showErrorSnackbar(binding.root, getString(R.string.error_team_not_selected))
                    }
                    return@launch
                }
                
                val message = com.fts.ttbros.chat.model.ChatMessage(
                    senderId = currentUserId,
                    senderName = currentUserName,
                    text = "📎 ${document.title}",
                    type = "material",
                    attachmentId = document.id,
                    timestamp = com.google.firebase.Timestamp.now()
                )
                chatRepository.sendMessage(teamId, ChatType.TEAM, message)
                if (isAdded && view != null) {
                    SnackbarHelper.showSuccessSnackbar(binding.root, getString(R.string.material_sent_to_chat))
                }
            } catch (e: Exception) {
                android.util.Log.e("DocumentsFragment", "Error sending material to chat: ${e.message}", e)
                if (isAdded && view != null) {
                    SnackbarHelper.showErrorSnackbar(binding.root, getString(R.string.error_sending_material_to_chat, e.message ?: ""))
                }
            }
        }
    }
    
    private fun showMaterialMenu(doc: Document) {
        val context = context ?: return
        if (!isAdded) return
        MaterialAlertDialogBuilder(context)
            .setTitle(doc.title)
            .setItems(arrayOf("Отправить в общий чат", "Удалить")) { _, which ->
                when (which) {
                    0 -> sendMaterialToChat(doc)
                    1 -> showDeleteDialog(doc)
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    private fun showDeleteDialog(doc: Document) {
        val context = context ?: return
        if (!isAdded) return
        MaterialAlertDialogBuilder(context)
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
                if (isAdded && view != null) {
                    SnackbarHelper.showSuccessSnackbar(binding.root, getString(R.string.document_deleted))
                }
            } catch (e: Exception) {
                android.util.Log.e("DocumentsFragment", "Error deleting document: ${e.message}", e)
                if (isAdded && view != null) {
                    SnackbarHelper.showErrorSnackbar(binding.root, getString(R.string.delete_failed, e.message ?: ""))
                }
            }
        }
    }
    
    private fun checkDownloads(docs: List<Document>) {
        val context = context ?: return
        if (!isAdded) return
        val docsDir = File(context.filesDir, "documents")
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
        val context = context ?: return
        if (!isAdded) return
        val docsDir = File(context.filesDir, "documents")
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
                        if (isAdded && view != null) {
                            SnackbarHelper.showErrorSnackbar(binding.root, getString(R.string.pdf_not_found))
                        }
                        binding.progressBar.isVisible = false
                        return@launch
                    }
                    
                    val context = context ?: return@launch
                    if (!isAdded) return@launch
                    val docsDir = File(context.filesDir, "documents")
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
                        SnackbarHelper.showErrorSnackbar(binding.root, getString(R.string.error_loading_pdf, e.message ?: ""))
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DocumentsFragment", "Error opening sheet: ${e.message}", e)
            if (isAdded && view != null) {
                SnackbarHelper.showErrorSnackbar(binding.root, getString(R.string.error_opening_sheet, e.message ?: ""))
            }
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
                    SnackbarHelper.showSuccessSnackbar(binding.root, getString(R.string.sheet_deleted))
                    loadData() // Перезагружаем данные
                }
            } catch (e: Exception) {
                android.util.Log.e("DocumentsFragment", "Error deleting sheet: ${e.message}", e)
                if (isAdded && view != null) {
                    SnackbarHelper.showErrorSnackbar(binding.root, getString(R.string.error_deleting_sheet, e.message ?: ""))
                    SnackbarHelper.showErrorSnackbar(binding.root, getString(R.string.error_deleting_sheet, e.message ?: ""))
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
