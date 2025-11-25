package com.fts.ttbros.data.repository

import com.fts.ttbros.data.model.Character
import com.google.firebase.Timestamp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class CharacterRepository {
    private val auth = Firebase.auth
    private val yandexDisk = YandexDiskRepository()
    private val gson = Gson()

    suspend fun getCharacters(): List<Character> {
        return withContext(Dispatchers.IO) {
            val userId = auth.currentUser?.uid ?: return@withContext emptyList()
            try {
                val path = "/TTBros/users/$userId/characters"
                val resources = yandexDisk.listResources(path)
                
                resources.mapNotNull { resource ->
                    try {
                        val jsonUrl = resource.file ?: resource.public_url
                        if (jsonUrl != null) {
                            val json = java.net.URL(jsonUrl).readText()
                            gson.fromJson(json, Character::class.java)
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        null
                    }
                }.sortedByDescending { it.updatedAt?.seconds ?: 0 }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    suspend fun getCharacter(characterId: String): Character? {
        return withContext(Dispatchers.IO) {
            val userId = auth.currentUser?.uid ?: return@withContext null
            try {
                val path = "/TTBros/users/$userId/characters/$characterId.json"
                // We need to find the file to get the download URL
                // Or list resources in parent folder
                val parentPath = "/TTBros/users/$userId/characters"
                val resources = yandexDisk.listResources(parentPath)
                val resource = resources.find { it.name == "$characterId.json" }
                
                if (resource != null) {
                    val jsonUrl = resource.file ?: resource.public_url
                    if (jsonUrl != null) {
                        val json = java.net.URL(jsonUrl).readText()
                        gson.fromJson(json, Character::class.java)
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

    suspend fun createCharacter(character: Character): String {
        return withContext(Dispatchers.IO) {
            val userId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")
            val characterId = if (character.id.isBlank()) UUID.randomUUID().toString() else character.id
            
            val newCharacter = character.copy(
                id = characterId,
                userId = userId,
                createdAt = Timestamp.now(),
                updatedAt = Timestamp.now()
            )
            
            val json = gson.toJson(newCharacter)
            val path = "/TTBros/users/$userId/characters/$characterId.json"
            
            yandexDisk.uploadJson(path, json)
            
            characterId
        }
    }

    suspend fun updateCharacter(characterId: String, data: Map<String, Any>) {
        withContext(Dispatchers.IO) {
            val userId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")
            
            // First get existing character to merge data
            val existing = getCharacter(characterId) ?: throw IllegalStateException("Character not found")
            
            // Merge data
            // We need to handle nested maps if necessary, but for now top-level merge
            // data contains "name", "clan", "concept", "data" (which is the form data)
            
            val name = data["name"] as? String ?: existing.name
            val clan = data["clan"] as? String ?: existing.clan
            val concept = data["concept"] as? String ?: existing.concept
            val formData = data["data"] as? Map<String, Any> ?: existing.data
            
            val updatedCharacter = existing.copy(
                name = name,
                clan = clan,
                concept = concept,
                data = formData,
                updatedAt = Timestamp.now()
            )
            
            val json = gson.toJson(updatedCharacter)
            val path = "/TTBros/users/$userId/characters/$characterId.json"
            
            yandexDisk.uploadJson(path, json)
        }
    }

    suspend fun deleteCharacter(characterId: String) {
        withContext(Dispatchers.IO) {
            val userId = auth.currentUser?.uid ?: return@withContext
            val path = "/TTBros/users/$userId/characters/$characterId.json"
            yandexDisk.deleteFile(path)
        }
    }

    suspend fun copyCharacter(sourceUserId: String, sourceCharacterId: String): String {
        return withContext(Dispatchers.IO) {
            val currentUserId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")
            
            // Get source character
            // We need to read from source user's folder
            // Assuming we have access (Yandex Disk token is for the app/user?)
            // If the token is for the current user, we can't access other user's private folder unless shared.
            // But here we are using a single OAuth token for the app (service account style) or user's token?
            // The token in YandexDiskRepository is hardcoded "y0__xCbs5y6Axj26Dsg8cirqxXirRUThBMOfP9QPmPxX0_alMiVWA".
            // This looks like a personal token or a service token.
            // If it's a service token that has access to the whole App Folder, then we can read everything.
            // Let's assume we can read.
            
            val sourcePath = "/TTBros/users/$sourceUserId/characters/$sourceCharacterId.json"
            // We need to find the file
            val sourceParentPath = "/TTBros/users/$sourceUserId/characters"
            val resources = yandexDisk.listResources(sourceParentPath)
            val resource = resources.find { it.name == "$sourceCharacterId.json" }
                ?: throw IllegalStateException("Character not found")
                
            val jsonUrl = resource.file ?: resource.public_url ?: throw IllegalStateException("Cannot download source character")
            val json = java.net.URL(jsonUrl).readText()
            val sourceCharacter = gson.fromJson(json, Character::class.java)
            
            // Create new character
            val newId = UUID.randomUUID().toString()
            val newCharacter = sourceCharacter.copy(
                id = newId,
                userId = currentUserId,
                createdAt = Timestamp.now(),
                updatedAt = Timestamp.now()
            )
            
            val newJson = gson.toJson(newCharacter)
            val newPath = "/TTBros/users/$currentUserId/characters/$newId.json"
            
            yandexDisk.uploadJson(newPath, newJson)
            
            newId
        }
    }
}
