package com.fts.ttbros.data.repository

import com.fts.ttbros.data.model.Poll
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class PollRepository {
    private val db = FirebaseFirestore.getInstance()
    private val pollsCollection = db.collection("polls")

    suspend fun createPoll(poll: Poll): String {
        val docRef = pollsCollection.document()
        val pollWithId = poll.copy(id = docRef.id)
        docRef.set(pollWithId).await()
        return docRef.id
    }

    fun getChatPolls(teamId: String, chatType: String): Flow<List<Poll>> = callbackFlow {
        val listener = pollsCollection
            .whereEqualTo("teamId", teamId)
            .whereEqualTo("chatType", chatType)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val polls = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Poll::class.java)
                }?.sortedWith(compareBy<Poll>(
                    { !it.isPinned }, // Pinned first
                    { if (it.isPinned) -(it.pinnedAt ?: 0L) else -it.createdAt } // Most recent first within each group
                )).orEmpty()
                trySend(polls)
            }
        awaitClose { listener.remove() }
    }

    suspend fun vote(pollId: String, userId: String, userName: String, optionId: String, isAnonymous: Boolean) {
        val pollRef = pollsCollection.document(pollId)
        db.runTransaction { transaction ->
            val poll = transaction.get(pollRef).toObject(Poll::class.java)
                ?: throw IllegalStateException("Poll not found")

            val updatedVotes = poll.votes.toMutableMap()
            updatedVotes[userId] = optionId

            val updates = mutableMapOf<String, Any>(
                "votes" to updatedVotes
            )

            if (!isAnonymous) {
                val updatedVoterNames = poll.voterNames.toMutableMap()
                updatedVoterNames[userId] = userName
                updates["voterNames"] = updatedVoterNames
            }

            transaction.update(pollRef, updates)
        }.await()
    }

    suspend fun deletePoll(pollId: String) {
        pollsCollection.document(pollId).delete().await()
    }

    suspend fun pinPoll(pollId: String, userId: String) {
        pollsCollection.document(pollId).update(
            "isPinned" to true,
            "pinnedBy" to userId,
            "pinnedAt" to System.currentTimeMillis()
        ).await()
    }

    suspend fun unpinPoll(pollId: String) {
        val updates = mapOf<String, Any>(
            "isPinned" to false,
            "pinnedBy" to null,
            "pinnedAt" to null
        )
        pollsCollection.document(pollId).update(updates).await()
    }
}
