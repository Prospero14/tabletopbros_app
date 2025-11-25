package com.fts.ttbros.data.repository

import android.content.Context
import android.net.Uri
import com.fts.ttbros.data.model.Document
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.Date

class DocumentRepository {
    private val yandexDisk = YandexDiskRepository()

    fun getDocuments(teamId: String): Flow<List<Document>> = flow {
        // Fetch from all three locations
        val documents = yandexDisk.listResources("/TTBros/teams/$teamId/documents")
        val materials = yandexDisk.listResources("/TTBros/teams/$teamId/player_materials")
        val sheets = yandexDisk.listResources("/TTBros/teams/$teamId/character_sheets")
        
        val allResources = documents + materials + sheets
        
        val docs = allResources.map { resource ->
            val metadata = resource.custom_properties ?: emptyMap()
            Document(
                id = resource.path, // Use path as ID
                teamId = teamId,
                title = metadata["title"] ?: resource.name,
                fileName = resource.name,
                downloadUrl = resource.public_url ?: resource.file ?: "",
                uploadedBy = metadata["uploadedBy"] ?: "",
                uploadedByName = metadata["uploadedByName"] ?: "Unknown",
                timestamp = try {
                    // Parse ISO 8601 date from Yandex (e.g., "2023-10-27T10:00:00+00:00")
                    val date = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US)
                        .parse(resource.created) ?: Date()
                    Timestamp(date)
                } catch (e: Exception) {
                    Timestamp.now()
                },
                sizeBytes = resource.size ?: 0L
            )
        }.sortedByDescending { it.timestamp }
        
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
            val metadata = mapOf(
                "uploadedBy" to userId,
                "uploadedByName" to userName,
                "title" to title,
                "teamId" to teamId
            )
            
            val downloadUrl = yandexDisk.uploadFile(
                teamId = teamId,
                fileName = fileName,
                fileUri = uri,
                context = context,
                isMaterial = isMaterial,
                metadata = metadata
            )
            
            val size = context.contentResolver
                .openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L

            return Document(
                id = "", // Will be filled on reload
                teamId = teamId,
                title = title,
                fileName = fileName,
                downloadUrl = downloadUrl,
                uploadedBy = userId,
                uploadedByName = userName,
                timestamp = Timestamp.now(),
                sizeBytes = size
            )
        } catch (e: Exception) {
            android.util.Log.e("DocumentRepository", "Error uploading document: ${e.message}", e)
            throw e
        }
    }
    
    suspend fun uploadCharacterSheetMetadata(
        teamId: String,
        pdfUrl: String, // Not used directly as we need path to patch
        characterName: String,
        system: String,
        userId: String,
        userName: String,
        fileName: String // Need filename to construct path
    ) {
        // Construct path based on new team-centric structure
        // Note: CharacterSheetsFragment needs to be updated to upload to /teams/{teamId}/character_sheets/
        val remotePath = "/TTBros/teams/$teamId/character_sheets/$fileName"
        
        val metadata = mapOf(
            "uploadedBy" to userId,
            "uploadedByName" to userName,
            "characterName" to characterName,
            "system" to system,
            "title" to characterName
        )
        
        yandexDisk.patchResource(remotePath, metadata)
    }
    
    suspend fun deleteDocument(document: Document) {
        // Document ID is now the path
        if (document.id.isNotEmpty()) {
            yandexDisk.deleteFile(document.id)
        }
    }

    suspend fun copyDocument(
        teamId: String,
        fromPath: String,
        toFileName: String,
        userId: String,
        userName: String,
        title: String
    ): Document? {
        try {
            val toPath = "/TTBros/teams/$teamId/player_materials/$toFileName"
            yandexDisk.copyResource(fromPath, toPath)
            
            // Patch metadata for the new copy
            val metadata = mapOf(
                "uploadedBy" to userId,
                "uploadedByName" to userName,
                "title" to title,
                "teamId" to teamId
            )
            yandexDisk.patchResource(toPath, metadata)
            
            // Return a partial document object (downloadUrl requires publishing or listing, 
            // but for now we just return success)
            // To get full object we would need to call listResources or publishFile, 
            // but let's just assume success and return what we know.
            return Document(
                id = toPath,
                teamId = teamId,
                title = title,
                fileName = toFileName,
                downloadUrl = "", // Will be populated on reload
                uploadedBy = userId,
                uploadedByName = userName,
                timestamp = Timestamp.now(),
                sizeBytes = 0
            )
        } catch (e: Exception) {
            android.util.Log.e("DocumentRepository", "Error copying document: ${e.message}", e)
            throw e
        }
    }
}
