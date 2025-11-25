package com.fts.ttbros.data.repository

import android.content.Context
import com.fts.ttbros.data.model.CharacterSheet
import com.google.firebase.Timestamp
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class CharacterSheetRepository {
    private val yandexDisk = YandexDiskRepository()
    private val gson = Gson()

    suspend fun saveSheet(sheet: CharacterSheet, context: Context): String {
        return withContext(Dispatchers.IO) {
            val resolvedTeamId = sheet.teamId
            val finalTeamId = if (resolvedTeamId.isNotBlank()) resolvedTeamId else "default_team"
            val sheetId = if (sheet.id.isBlank()) UUID.randomUUID().toString() else sheet.id
            val now = Timestamp.now()
            
            val sheetWithTeam = if (sheet.teamId.isBlank()) sheet.copy(teamId = finalTeamId) else sheet
            val sheetWithId = if (sheetWithTeam.id.isBlank()) {
                sheetWithTeam.copy(
                    id = sheetId,
                    createdAt = now,
                    updatedAt = now
                )
            } else {
                sheetWithTeam.copy(updatedAt = now)
            }
            
            // Serialize to JSON
            val json = gson.toJson(sheetWithId)
            val fileName = "${sheetId}.json"
            
            val remotePath = "/TTBros/teams/$finalTeamId/builders/$fileName"
            val metadata = mapOf(
                "userId" to sheetWithId.userId,
                "characterName" to sheetWithId.characterName,
                "system" to sheetWithId.system,
                "type" to "builder_sheet"
            )
            yandexDisk.uploadJson(remotePath, json, metadata)
            sheetId
        }
    }

    suspend fun getUserSheets(userId: String, teamId: String): List<CharacterSheet> {
        return withContext(Dispatchers.IO) {
            try {
                // List files in the builders directory
                val resources = yandexDisk.listResources("/TTBros/teams/$teamId/builders")
                
                // Filter by userId (using metadata if available, or we might need to download?)
                // Ideally we use metadata.
                resources.filter { resource ->
                    // Check metadata first
                    val props = resource.custom_properties
                    if (props != null && props.containsKey("userId")) {
                        props["userId"] == userId
                    } else {
                        // If no metadata, we might need to download and check (expensive)
                        // For now, let's assume if it's in the folder, we check metadata.
                        // If metadata is missing, we skip or include?
                        // Let's include if we can't verify, or maybe skip to be safe.
                        // Better: assume we set metadata on save.
                        false
                    }
                }.mapNotNull { resource ->
                    // We need to download the content to get the full CharacterSheet object
                    // This is N+1 network calls, which is slow.
                    // Optimization: For the list, maybe we only need basic info?
                    // But the app expects CharacterSheet objects.
                    // Let's download for now.
                    try {
                        val jsonUrl = resource.file ?: resource.public_url
                        if (jsonUrl != null) {
                            val json = java.net.URL(jsonUrl).readText()
                            gson.fromJson(json, CharacterSheet::class.java)
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    suspend fun getSheet(sheetId: String, teamId: String): CharacterSheet? {
        return withContext(Dispatchers.IO) {
            try {
                val remotePath = "/TTBros/teams/$teamId/builders/$sheetId.json"
                // We need to get the download URL
                // We can use listResources to find it, or try to construct it?
                // YandexDiskRepository doesn't have a "downloadFile" method that takes a path.
                // But listResources can find it.
                val resources = yandexDisk.listResources("/TTBros/teams/$teamId/builders")
                val resource = resources.find { it.name == "$sheetId.json" }
                
                if (resource != null) {
                    val jsonUrl = resource.file ?: resource.public_url
                    if (jsonUrl != null) {
                        val json = java.net.URL(jsonUrl).readText()
                        gson.fromJson(json, CharacterSheet::class.java)
                    } else {
                        null
                    }
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun deleteSheet(sheetId: String, teamId: String) {
        val remotePath = "/TTBros/teams/$teamId/builders/$sheetId.json"
        yandexDisk.deleteFile(remotePath)
    }

    suspend fun updateSheet(sheet: CharacterSheet, context: Context) {
        saveSheet(sheet, context)
    }
}
