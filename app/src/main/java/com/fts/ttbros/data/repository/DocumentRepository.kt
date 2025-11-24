package com.fts.ttbros.data.repository

import android.content.Context
import android.net.Uri
import com.fts.ttbros.data.model.Document
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class DocumentRepository {
    private val db = FirebaseFirestore.getInstance()
    private val yandexDisk = YandexDiskRepository()

    fun getDocuments(teamId: String): Flow<List<Document>> = callbackFlow {
        val listener = db.collection("teams")
            .document(teamId)
            .collection("documents")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val docs = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Document::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(docs)
            }
        awaitClose { listener.remove() }
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
            android.util.Log.d("DocumentRepository", "Starting upload to Yandex.Disk")
            
            // 1. Upload to Yandex.Disk and get public URL
            android.util.Log.d("DocumentRepository", "Uploading file: $fileName, isMaterial: $isMaterial")
            val downloadUrl = yandexDisk.uploadFile(
                teamId = teamId,
                fileName = fileName,
                fileUri = uri,
                context = context,
                isMaterial = isMaterial
            )
            
            android.util.Log.d("DocumentRepository", "File uploaded, URL: $downloadUrl")
            android.util.Log.d("DocumentRepository", "URL contains /player_materials/: ${downloadUrl.contains("/player_materials/")}")
            
            // 2. Get file size
            val size = context.contentResolver
                .openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
            
            android.util.Log.d("DocumentRepository", "File size: $size bytes")

            // 3. Save to Firestore
            val docMap = hashMapOf(
                "teamId" to teamId,
                "title" to title,
                "fileName" to fileName,
                "downloadUrl" to downloadUrl,
                "uploadedBy" to userId,
                "uploadedByName" to userName,
                "timestamp" to Timestamp.now(),
                "sizeBytes" to size
            )
            
            val docRef = db.collection("teams")
                .document(teamId)
                .collection("documents")
                .add(docMap)
                .await()
                
            android.util.Log.d("DocumentRepository", "Document metadata saved to Firestore")
            
            // 4. Return Document object
            return Document(
                id = docRef.id,
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
            android.util.Log.e("DocumentRepository", "Upload error: ${e.message}", e)
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
        try {
            val fileName = "character_sheet_${characterName}_${System.currentTimeMillis()}.pdf"
            
            val docMap = hashMapOf(
                "teamId" to teamId,
                "title" to "Character Sheet: $characterName",
                "fileName" to fileName,
                "downloadUrl" to pdfUrl,
                "uploadedBy" to userId,
                "uploadedByName" to userName,
                "timestamp" to Timestamp.now(),
                "sizeBytes" to 0L, // Unknown size for already uploaded files
                "system" to system
            )
            
            val docRef = db.collection("teams")
                .document(teamId)
                .collection("documents")
                .add(docMap)
                .await()
                
            android.util.Log.d("DocumentRepository", "Character sheet metadata saved to Firestore")
            
            return Document(
                id = docRef.id,
                teamId = teamId,
                title = "Character Sheet: $characterName",
                fileName = fileName,
                downloadUrl = pdfUrl,
                uploadedBy = userId,
                uploadedByName = userName,
                timestamp = Timestamp.now(),
                sizeBytes = 0L
            )
        } catch (e: Exception) {
            android.util.Log.e("DocumentRepository", "Error saving character sheet metadata: ${e.message}", e)
            throw e
        }
    }

    suspend fun deleteDocument(teamId: String, documentId: String, downloadUrl: String) {
        // 1. Delete from Firestore
        db.collection("teams")
            .document(teamId)
            .collection("documents")
            .document(documentId)
            .delete()
            .await()

        // 2. Delete from Yandex.Disk
        try {
            // Extract path from public URL or construct it
            // For now, we'll skip deletion from Yandex.Disk as we need to track the path
            // TODO: Store remote path in Firestore for proper deletion
            android.util.Log.w("DocumentRepository", "Yandex.Disk file deletion not implemented yet")
        } catch (e: Exception) {
            android.util.Log.e("DocumentRepository", "Yandex.Disk delete error: ${e.message}", e)
        }
    }
}
