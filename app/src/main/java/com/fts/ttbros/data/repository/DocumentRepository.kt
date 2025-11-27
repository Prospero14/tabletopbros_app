package com.fts.ttbros.data.repository

import android.content.Context
import android.net.Uri
import com.fts.ttbros.data.model.Document
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class DocumentRepository {
    private val yandexDisk = YandexDiskRepository()

    fun getDocuments(teamId: String): Flow<List<Document>> = flow {
        // Poll or just emit once. For now, emit once.
        // To support "real-time" feel, we could loop with delay, but let's start simple.
        val path = "/TTBros/teams/$teamId/documents"
        val files = yandexDisk.listFiles(path)
        
        val docs = files.map { resource ->
            val props = resource.custom_properties ?: emptyMap()
            
            // Parse timestamp
            val timestamp = try {
                val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
                val date = format.parse(resource.created)
                if (date != null) Timestamp(date) else Timestamp.now()
            } catch (e: Exception) {
                Timestamp.now()
            }

            Document(
                id = resource.path, // Use path as ID
                teamId = teamId,
                title = (props["title"] as? String) ?: resource.name,
                fileName = resource.name,
                downloadUrl = resource.file ?: resource.public_url ?: "",
                uploadedBy = (props["uploadedBy"] as? String) ?: "",
                uploadedByName = (props["uploadedByName"] as? String) ?: "",
                timestamp = timestamp,
                sizeBytes = resource.size
            )
        }
        emit(docs)
    }

    suspend fun uploadDocument(
        teamId: String,
        uri: Uri,
        title: String,
        fileName: String,
        userId: String,
        userName: String,
        context: Context,
        isMaterial: Boolean = false
    ): Document? {
        try {
            // 1. Upload to Yandex.Disk
            val publicUrl = yandexDisk.uploadFile(
                teamId = teamId,
                fileName = fileName,
                fileUri = uri,
                context = context,
                isMaterial = isMaterial
            )
            
            // 2. Patch metadata
            val remotePath = if (isMaterial) {
                "/TTBros/teams/$teamId/player_materials/$fileName"
            } else {
                "/TTBros/teams/$teamId/documents/$fileName"
            }
            
            val metadata = mapOf(
                "title" to title,
                "uploadedBy" to userId,
                "uploadedByName" to userName,
                "teamId" to teamId
            )
            
            yandexDisk.patchResource(remotePath, metadata)
            
            // 3. Return Document object
            // We need to fetch the resource to get the size and correct timestamp, 
            // or just construct it manually for speed.
            val size = context.contentResolver
                .openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L

            return Document(
                id = remotePath,
                teamId = teamId,
                title = title,
                fileName = fileName,
                downloadUrl = publicUrl,
                uploadedBy = userId,
                uploadedByName = userName,
                timestamp = Timestamp.now(),
                sizeBytes = size
            )
        } catch (e: Exception) {
            android.util.Log.e("DocumentRepository", "Upload error: ${e.message}", e)
            throw e
        }
    }

    suspend fun getDocument(path: String): Document? {
        try {
            // We need to get file info to construct Document object
            // We can use listFiles on parent directory and find the file
            // path is like /TTBros/teams/{teamId}/documents/{fileName}
            
            val parentPath = path.substringBeforeLast('/')
            val fileName = path.substringAfterLast('/')
            
            val files = yandexDisk.listFiles(parentPath)
            val resource = files.find { it.name == fileName } ?: return null
            
            val props = resource.custom_properties ?: emptyMap()
            
            // Parse timestamp
            val timestamp = try {
                val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
                val date = format.parse(resource.created)
                if (date != null) Timestamp(date) else Timestamp.now()
            } catch (e: Exception) {
                Timestamp.now()
            }

            return Document(
                id = resource.path,
                teamId = (props["teamId"] as? String) ?: "",
                title = (props["title"] as? String) ?: resource.name,
                fileName = resource.name,
                downloadUrl = resource.file ?: resource.public_url ?: "",
                uploadedBy = (props["uploadedBy"] as? String) ?: "",
                uploadedByName = (props["uploadedByName"] as? String) ?: "",
                timestamp = timestamp,
                sizeBytes = resource.size
            )
        } catch (e: Exception) {
            android.util.Log.e("DocumentRepository", "Error getting document: ${e.message}", e)
            return null
        }
    }

    suspend fun copyDocumentToMaterials(
        document: Document,
        userId: String,
        userName: String,
        context: Context
    ): Document? {
        try {
            // 1. Download content
            val bytes = yandexDisk.downloadBytes(document.downloadUrl)
            
            // 2. Upload as material
            val fileName = document.fileName
            val teamId = document.teamId
            
            // We need to upload content directly.
            // We can use uploadContent but we need to construct path.
            val remotePath = "/TTBros/teams/$teamId/player_materials/$fileName"
            
            yandexDisk.uploadContent(remotePath, bytes)
            
            // 3. Patch metadata
            val metadata = mapOf(
                "title" to document.title,
                "uploadedBy" to userId,
                "uploadedByName" to userName,
                "teamId" to teamId
            )
            yandexDisk.patchResource(remotePath, metadata)
            
            // 4. Return new Document
            // We need public URL. uploadContent publishes it?
            // uploadContent calls publishFile.
            // We need to get the public URL.
            // We can list files or assume we can get it.
            // Let's list files in materials folder and find it.
            
            val materialsPath = "/TTBros/teams/$teamId/player_materials"
            val files = yandexDisk.listFiles(materialsPath)
            val resource = files.find { it.name == fileName } ?: return null
            
            return Document(
                id = resource.path,
                teamId = teamId,
                title = document.title,
                fileName = fileName,
                downloadUrl = resource.file ?: resource.public_url ?: "",
                uploadedBy = userId,
                uploadedByName = userName,
                timestamp = Timestamp.now(),
                sizeBytes = resource.size
            )
        } catch (e: Exception) {
            android.util.Log.e("DocumentRepository", "Error copying document: ${e.message}", e)
            throw e
        }
    }

    suspend fun uploadCharacterSheetMetadata(
        teamId: String,
        pdfUrl: String,
        characterName: String,
        system: String,
        userId: String,
        userName: String
    ): Document? {
        // This was used to show character sheets in documents list.
        // We can still do this by creating a placeholder file or just uploading the PDF to documents folder?
        // But CharacterSheetRepository handles the actual sheet.
        // If we want it to appear in documents, we should upload it to documents folder.
        // For now, let's just log it, as we are moving to JSON sheets.
        // Or if this is for PDF export, we can implement it.
        // The previous implementation created a Firestore entry.
        // We will skip this for now as we are migrating to JSON sheets which are separate.
        return null
    }

    suspend fun deleteDocument(teamId: String, documentId: String, downloadUrl: String) {
        // documentId is now the path
        try {
            yandexDisk.deleteFile(documentId)
        } catch (e: Exception) {
            android.util.Log.e("DocumentRepository", "Delete error: ${e.message}", e)
            throw e
        }
    }
}
