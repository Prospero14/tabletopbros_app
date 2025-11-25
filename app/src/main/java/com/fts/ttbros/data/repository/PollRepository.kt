package com.fts.ttbros.data.repository

import com.fts.ttbros.data.model.Poll
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

class PollRepository(
    private val yandexDiskRepository: YandexDiskRepository = YandexDiskRepository(),
    private val ioScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val gson = Gson()
    private val pollIntervalMs = 5_000L

    suspend fun createPoll(poll: Poll): String {
        val pollId = if (poll.id.isBlank()) UUID.randomUUID().toString() else poll.id
        val pollWithId = poll.copy(id = pollId, createdAt = System.currentTimeMillis())
        val path = pollPath(poll.teamId, pollId)
        val json = gson.toJson(pollWithId)
        yandexDiskRepository.uploadJson(path, json)
        return pollId
    }

    suspend fun getPoll(teamId: String, pollId: String): Poll? {
        val path = pollPath(teamId, pollId)
        return yandexDiskRepository.readJson(path, Poll::class.java)
    }

    fun getChatPolls(teamId: String, chatType: String): Flow<List<Poll>> = callbackFlow {
        val job = ioScope.launch {
            while (isActive) {
                try {
                    val polls = loadPolls(teamId, chatType)
                    trySend(polls)
                } catch (e: Exception) {
                    android.util.Log.e("PollRepository", "Error loading polls: ${e.message}", e)
                    trySend(emptyList())
                }
                delay(pollIntervalMs)
            }
        }
        awaitClose { job.cancel() }
    }

    suspend fun vote(
        teamId: String,
        pollId: String,
        userId: String,
        userName: String,
        optionId: String,
        isAnonymous: Boolean
    ) {
        updatePoll(teamId, pollId) { poll ->
            val updatedVotes = poll.votes.toMutableMap().apply {
                put(userId, optionId)
            }
            val updatedVoterNames = if (!isAnonymous) {
                poll.voterNames.toMutableMap().apply {
                    put(userId, userName)
                }
            } else {
                poll.voterNames
            }
            poll.copy(
                votes = updatedVotes,
                voterNames = updatedVoterNames
            )
        }
    }

    suspend fun deletePoll(teamId: String, pollId: String) {
        val path = pollPath(teamId, pollId)
        yandexDiskRepository.deleteFile(path)
    }

    suspend fun pinPoll(teamId: String, pollId: String, userId: String) {
        updatePoll(teamId, pollId) { poll ->
            poll.copy(
                isPinned = true,
                pinnedBy = userId,
                pinnedAt = System.currentTimeMillis()
            )
        }
    }

    suspend fun unpinPoll(teamId: String, pollId: String) {
        updatePoll(teamId, pollId) { poll ->
            poll.copy(
                isPinned = false,
                pinnedBy = null,
                pinnedAt = null
            )
        }
    }

    private suspend fun loadPolls(teamId: String, chatType: String): List<Poll> {
        val resources = yandexDiskRepository.listResources("/TTBros/teams/$teamId/polls")
        val polls = resources.mapNotNull { resource ->
            try {
                yandexDiskRepository.readJson(resource.path, Poll::class.java)
            } catch (e: Exception) {
                null
            }
        }.filter { it.chatType == chatType }

        return polls.sortedWith(compareBy<Poll>(
            { !it.isPinned },
            { if (it.isPinned) -(it.pinnedAt ?: 0L) else -it.createdAt }
        ))
    }

    private suspend fun updatePoll(teamId: String, pollId: String, transform: (Poll) -> Poll) {
        val path = pollPath(teamId, pollId)
        val poll = yandexDiskRepository.readJson(path, Poll::class.java)
            ?: throw IllegalStateException("Poll not found")
        val updated = transform(poll)
        val json = gson.toJson(updated)
        yandexDiskRepository.uploadJson(path, json)
    }

    private fun pollPath(teamId: String, pollId: String): String {
        return "/TTBros/teams/$teamId/polls/$pollId.json"
    }
}
