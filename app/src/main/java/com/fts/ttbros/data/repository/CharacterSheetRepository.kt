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

    private val yandexDisk = YandexDiskRepository()

    suspend fun uploadSheet(
        userId: String,
        userName: String,
        characterName: String,
        system: String,
        pdfUri: android.net.Uri,
        context: android.content.Context,
        parsedData: Map<String, Any> = emptyMap(),
        attributes: Map<String, Int> = emptyMap(),
        skills: Map<String, Int> = emptyMap(),
        stats: Map<String, Any> = emptyMap()
    ): CharacterSheet {
        // 1. Upload to Yandex.Disk
        val downloadUrl = yandexDisk.uploadCharacterSheet(userId, pdfUri, context)
        
        // 2. Create Sheet object
        val sheet = CharacterSheet(
            id = UUID.randomUUID().toString(),
            userId = userId,
            userName = userName,
            characterName = characterName,
            system = system,
            pdfUrl = downloadUrl,
            parsedData = parsedData,
            attributes = attributes,
            skills = skills,
            stats = stats,
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )
        
        // 3. Save to Firestore
        db.collection(collection)
            .document(sheet.id)
            .set(sheet)
            .await()
            
        return sheet
    }

    suspend fun updateSheet(sheet: CharacterSheet) {
        val updatedSheet = sheet.copy(updatedAt = Timestamp.now())
        db.collection(collection)
            .document(sheet.id)
            .set(updatedSheet)
            .await()
    }
}

