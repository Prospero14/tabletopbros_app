package com.fts.ttbros.data.repository

import android.net.Uri
import com.fts.ttbros.data.model.Document
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class DocumentRepository {
    private val db = FirebaseFirestore.getInstance()
    private val storage = Firebase.storage

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
        userName: String
    ) {
        // 1. Upload to Storage
        val storageRef = storage.reference
            .child("teams/$teamId/documents/${UUID.randomUUID()}_$fileName")
        
        storageRef.putFile(uri).await()
        val downloadUrl = storageRef.downloadUrl.await().toString()
        val metadata = storageRef.metadata.await()
        val size = metadata.sizeBytes

        // 2. Save to Firestore - convert to HashMap to avoid serialization issues
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
        
        db.collection("teams")
            .document(teamId)
            .collection("documents")
            .add(docMap)
            .await()
    }

    suspend fun deleteDocument(teamId: String, documentId: String, downloadUrl: String) {
        // 1. Delete from Firestore
        db.collection("teams")
            .document(teamId)
            .collection("documents")
            .document(documentId)
            .delete()
            .await()

        // 2. Delete from Storage (try/catch as it might fail or file might be missing)
        try {
            val storageRef = Firebase.storage.getReferenceFromUrl(downloadUrl)
            storageRef.delete().await()
        } catch (e: Exception) {
            // Ignore storage delete errors (e.g. file not found)
        }
    }
}
