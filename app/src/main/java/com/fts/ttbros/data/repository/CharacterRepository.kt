package com.fts.ttbros.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.fts.ttbros.data.model.Character
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.util.Date
import java.util.UUID

class CharacterRepository {

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

    suspend fun getCharacters(): List<Character> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        val path = "/TTBros/users/$userId/characters"
        
        val files = yandexDisk.listFiles(path)
        
        return files.mapNotNull { resource ->
            try {
                // Download content
                // We can use the 'file' property if available, or construct URL
                val url = resource.file ?: resource.public_url
                if (url != null) {
                    val json = yandexDisk.downloadContent(url)
                    gson.fromJson(json, Character::class.java)
                } else {
                    null
                }
            } catch (e: Exception) {
                android.util.Log.e("CharacterRepository", "Error loading character ${resource.name}: ${e.message}")
                null
            }
        }.sortedByDescending { it.updatedAt ?: Timestamp.now() }
    }

    suspend fun getCharacter(characterId: String): Character? {
        val userId = auth.currentUser?.uid ?: return null
        // We need to find the file. Filename is likely {characterId}.json or similar.
        // Let's assume we save it as {characterId}.json
        val path = "/TTBros/users/$userId/characters/$characterId.json"
        
        return try {
            // Get resource to get download link
            // We don't have getResource(path) yet, but we can list and find?
            // Or we can try to publish/get info.
            // But wait, if we know the path, we can try to get the file info.
            // YandexDiskRepository doesn't have a direct "get file info" public method yet except listFiles.
            // But we can use listFiles on the parent folder and find the file.
            // This is inefficient if there are many files, but okay for now.
            
            val parentPath = "/TTBros/users/$userId/characters"
            val files = yandexDisk.listFiles(parentPath)
            val file = files.find { it.name == "$characterId.json" }
            
            if (file != null) {
                val url = file.file ?: file.public_url
                if (url != null) {
                    val json = yandexDisk.downloadContent(url)
                    gson.fromJson(json, Character::class.java)
                } else null
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("CharacterRepository", "Error getting character $characterId: ${e.message}")
            null
        }
    }

    suspend fun createCharacter(character: Character): String {
        val userId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")
        val characterId = UUID.randomUUID().toString()
        
        val newCharacter = character.copy(
            id = characterId,
            userId = userId,
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )
        
        val json = gson.toJson(newCharacter)
        val fileName = "$characterId.json"
        
        // We need to upload this string as a file.
        // YandexDiskRepository takes a Uri.
        // We need to write to a temp file first.
        // But we don't have Context here easily.
        // We should pass Context or use a different approach.
        // Repositories usually shouldn't depend on Context, but YandexDiskRepository does.
        // I'll add Context to the method signature or use a workaround.
        // Existing createCharacter didn't take Context.
        // But YandexDiskRepository needs it for ContentResolver if Uri is content://.
        // If Uri is file://, it might still need it?
        // Wait, YandexDiskRepository.uploadFile takes Context.
        // I should update createCharacter to take Context.
        // This will require updating callers.
        // OR I can add a method to YandexDiskRepository to upload ByteArray/String directly using OkHttp, bypassing Uri/Context.
        
        // Let's add uploadContent(path, content) to YandexDiskRepository.
        // That's cleaner.
        
        // For now, I'll assume I can add uploadContent to YandexDiskRepository.
        // I will do that in a separate step or assume it exists and fix it.
        // I'll assume it exists and add it to YandexDiskRepository next.
        
        yandexDisk.uploadContent(
            path = "/TTBros/users/$userId/characters/$fileName",
            content = json.toByteArray(Charsets.UTF_8)
        )
        
        return characterId
    }

    suspend fun updateCharacter(characterId: String, data: Map<String, Any>) {
        val userId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")
        
        // Load existing
        val existing = getCharacter(characterId) ?: throw IllegalStateException("Character not found")
        
        // Apply updates
        // This is tricky with Map<String, Any> and data class.
        // We need to manually map fields or use reflection/Gson.
        // Since 'data' field in Character is Map, we can update that easily.
        // But top level fields like 'name', 'clan' need to be handled.
        
        var updated = existing
        
        if (data.containsKey("name")) updated = updated.copy(name = data["name"] as String)
        if (data.containsKey("clan")) updated = updated.copy(clan = data["clan"] as String)
        if (data.containsKey("concept")) updated = updated.copy(concept = data["concept"] as String)
        if (data.containsKey("system")) updated = updated.copy(system = data["system"] as String)
        
        if (data.containsKey("data")) {
            @Suppress("UNCHECKED_CAST")
            val newData = data["data"] as Map<String, Any>
            updated = updated.copy(data = newData)
        }
        
        updated = updated.copy(updatedAt = Timestamp.now())
        
        val json = gson.toJson(updated)
        val fileName = "$characterId.json"
        
        yandexDisk.uploadContent(
            path = "/TTBros/users/$userId/characters/$fileName",
            content = json.toByteArray(Charsets.UTF_8)
        )
    }

    suspend fun deleteCharacter(characterId: String) {
        val userId = auth.currentUser?.uid ?: return
        val path = "/TTBros/users/$userId/characters/$characterId.json"
        try {
            yandexDisk.deleteFile(path)
        } catch (e: Exception) {
            android.util.Log.e("CharacterRepository", "Error deleting character: ${e.message}")
        }
    }

    suspend fun copyCharacter(sourceUserId: String, sourceCharacterId: String): String {
        val currentUserId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")
        
        // Get source character
        // We need to construct path manually as getCharacter uses auth.currentUser
        val sourcePath = "/TTBros/users/$sourceUserId/characters"
        val files = yandexDisk.listFiles(sourcePath)
        val file = files.find { it.name == "$sourceCharacterId.json" } ?: throw IllegalStateException("Character not found")
        
        val url = file.file ?: file.public_url ?: throw IllegalStateException("Cannot download character")
        val json = yandexDisk.downloadContent(url)
        val sourceCharacter = gson.fromJson(json, Character::class.java)
        
        // Create new
        val newCharacterId = UUID.randomUUID().toString()
        val newCharacter = sourceCharacter.copy(
            id = newCharacterId,
            userId = currentUserId,
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )
        
        val newJson = gson.toJson(newCharacter)
        val fileName = "$newCharacterId.json"
        
        yandexDisk.uploadContent(
            path = "/TTBros/users/$currentUserId/characters/$fileName",
            content = newJson.toByteArray(Charsets.UTF_8)
        )
        
        return newCharacterId
    }
}
