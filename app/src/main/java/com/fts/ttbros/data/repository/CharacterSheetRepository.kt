package com.fts.ttbros.data.repository

import com.fts.ttbros.data.model.CharacterSheet
import com.google.firebase.Timestamp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.util.UUID

class CharacterSheetRepository {
    private val auth = Firebase.auth
    private val yandexDisk = YandexDiskRepository()
    
    private val gson: Gson = GsonBuilder()
        .registerTypeAdapter(Timestamp::class.java, object : TypeAdapter<Timestamp>() {
            override fun write(out: JsonWriter, value: Timestamp?) {
                if (value == null) {
                    out.nullValue()
                } else {
                    out.beginObject()
                    out.name("seconds").value(value.seconds)
                    out.name("nanoseconds").value(value.nanoseconds.toLong())
                    out.endObject()
                }
            }

            override fun read(input: JsonReader): Timestamp? {
                var seconds = 0L
                var nanoseconds = 0
                input.beginObject()
                while (input.hasNext()) {
                    when (input.nextName()) {
                        "seconds" -> seconds = input.nextLong()
                        "nanoseconds" -> nanoseconds = input.nextInt()
                        else -> input.skipValue()
                    }
                }
                input.endObject()
                return Timestamp(seconds, nanoseconds)
            }
        })
        .create()

    suspend fun saveSheet(sheet: CharacterSheet): String {
        val sheetId = sheet.id.ifBlank { UUID.randomUUID().toString() }
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
        
        val json = gson.toJson(sheetWithId)
        val fileName = "$sheetId.json"
        
        // Determine path based on whether it's a template or user sheet
        val path = if (sheetWithId.isTemplate) {
            "/TTBros/builders/$fileName"
        } else {
            val userId = sheetWithId.userId.ifBlank { auth.currentUser?.uid ?: "unknown" }
            "/TTBros/character_sheets_data/$userId/$fileName"
        }
        
        yandexDisk.uploadContent(path, json.toByteArray(Charsets.UTF_8))
        
        return sheetId
    }

    suspend fun getUserSheets(userId: String): List<CharacterSheet> {
        val path = "/TTBros/character_sheets_data/$userId"
        val files = yandexDisk.listFiles(path)
        
        return files.mapNotNull { resource ->
            try {
                val url = resource.file ?: resource.public_url
                if (url != null) {
                    val json = yandexDisk.downloadContent(url)
                    gson.fromJson(json, CharacterSheet::class.java)
                } else null
            } catch (e: Exception) {
                android.util.Log.e("CharacterSheetRepo", "Error loading sheet ${resource.name}: ${e.message}")
                null
            }
        }
    }

    suspend fun getSheet(sheetId: String): CharacterSheet? {
        // Try builders first
        try {
            val builderPath = "/TTBros/builders/$sheetId.json"
            // We can't check existence easily without listing or trying to download.
            // Try to download directly? If it fails, try user folder.
            // But downloadContent takes URL, not path.
            // We need to get the file info first.
            
            // Hack: List builders folder and find it.
            val builders = yandexDisk.listFiles("/TTBros/builders")
            val builderFile = builders.find { it.name == "$sheetId.json" }
            
            if (builderFile != null) {
                val url = builderFile.file ?: builderFile.public_url
                if (url != null) {
                    val json = yandexDisk.downloadContent(url)
                    return gson.fromJson(json, CharacterSheet::class.java)
                }
            }
            
            // Try current user folder
            val userId = auth.currentUser?.uid
            if (userId != null) {
                val userPath = "/TTBros/character_sheets_data/$userId"
                val userFiles = yandexDisk.listFiles(userPath)
                val userFile = userFiles.find { it.name == "$sheetId.json" }
                
                if (userFile != null) {
                    val url = userFile.file ?: userFile.public_url
                    if (url != null) {
                        val json = yandexDisk.downloadContent(url)
                        return gson.fromJson(json, CharacterSheet::class.java)
                    }
                }
            }
            
            return null
        } catch (e: Exception) {
            android.util.Log.e("CharacterSheetRepo", "Error getting sheet $sheetId: ${e.message}")
            return null
        }
    }

    suspend fun deleteSheet(sheetId: String) {
        // We don't know if it's a builder or user sheet easily without checking.
        // Assume user sheet first.
        val userId = auth.currentUser?.uid ?: return
        val userPath = "/TTBros/character_sheets_data/$userId/$sheetId.json"
        
        try {
            yandexDisk.deleteFile(userPath)
        } catch (e: Exception) {
            // Try builder path if user is admin? Or just ignore.
            // For now, only delete user sheets.
            android.util.Log.e("CharacterSheetRepo", "Error deleting sheet: ${e.message}")
        }
    }

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
        // 1. Upload PDF to Yandex.Disk
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
        
        // 3. Save to Yandex.Disk (JSON)
        saveSheet(sheet)
            
        return sheet
    }

    suspend fun updateSheet(sheet: CharacterSheet) {
        saveSheet(sheet)
    }
}
