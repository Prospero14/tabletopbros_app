package com.fts.ttbros.data.repository

import com.fts.ttbros.data.model.CharacterSheet
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.util.UUID

class CharacterSheetRepository {
    private val db: FirebaseFirestore = Firebase.firestore
    private val collection = "character_sheets"

    suspend fun saveSheet(sheet: CharacterSheet): String {
        val sheetId = if (sheet.id.isBlank()) UUID.randomUUID().toString() else sheet.id
        val now = Timestamp.now()
        val sheetWithId = if (sheet.id.isBlank()) {
            // New sheet
            sheet.copy(
                id = sheetId,
                createdAt = now,
                updatedAt = now
            )
        } else {
            // Update existing
            sheet.copy(updatedAt = now)
        }
        
        db.collection(collection)
            .document(sheetId)
            .set(sheetWithId)
            .await()
        
        return sheetId
    }

    suspend fun getUserSheets(userId: String): List<CharacterSheet> {
        return db.collection(collection)
            .whereEqualTo("userId", userId)
            .get()
            .await()
            .documents
            .mapNotNull { doc ->
                try {
                    doc.toObject(CharacterSheet::class.java)?.copy(id = doc.id)
                } catch (e: Exception) {
                    null
                }
            }
    }

    suspend fun getSheet(sheetId: String): CharacterSheet? {
        return try {
            val doc = db.collection(collection)
                .document(sheetId)
                .get()
                .await()
            
            doc.toObject(CharacterSheet::class.java)?.copy(id = doc.id)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun deleteSheet(sheetId: String) {
        db.collection(collection)
            .document(sheetId)
            .delete()
            .await()
    }

    suspend fun updateSheet(sheet: CharacterSheet) {
        val updatedSheet = sheet.copy(updatedAt = Timestamp.now())
        db.collection(collection)
            .document(sheet.id)
            .set(updatedSheet)
            .await()
    }
}

