package com.fts.ttbros.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.fts.ttbros.data.model.Character
import kotlinx.coroutines.tasks.await

class CharacterRepository(
    private val firestore: FirebaseFirestore = Firebase.firestore,
    private val auth: com.google.firebase.auth.FirebaseAuth = Firebase.auth
) {

    private val usersCollection = firestore.collection("users")

    suspend fun getCharacters(): List<Character> {
        val userId = auth.currentUser?.uid ?: return emptyList()
        val snapshot = usersCollection.document(userId)
            .collection("characters")
            .orderBy("updatedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            doc.toObject(Character::class.java)?.copy(id = doc.id)
        }
    }

    suspend fun getCharacter(characterId: String): Character? {
        val userId = auth.currentUser?.uid ?: return null
        val doc = usersCollection.document(userId)
            .collection("characters")
            .document(characterId)
            .get()
            .await()
        return doc.toObject(Character::class.java)?.copy(id = doc.id)
    }

    suspend fun createCharacter(character: Character): String {
        val userId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")
        val docRef = usersCollection.document(userId).collection("characters").document()
        
        val newCharacter = character.copy(
            id = docRef.id,
            userId = userId,
            createdAt = Timestamp.now(),
            updatedAt = Timestamp.now()
        )
        
        docRef.set(newCharacter).await()
        return docRef.id
    }

    suspend fun updateCharacter(characterId: String, data: Map<String, Any>) {
        val userId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")
        val updates = data.toMutableMap()
        updates["updatedAt"] = FieldValue.serverTimestamp()
        
        usersCollection.document(userId)
            .collection("characters")
            .document(characterId)
            .update(updates)
            .await()
    }

    suspend fun deleteCharacter(characterId: String) {
        val userId = auth.currentUser?.uid ?: return
        usersCollection.document(userId)
            .collection("characters")
            .document(characterId)
            .delete()
            .await()
    }
}
