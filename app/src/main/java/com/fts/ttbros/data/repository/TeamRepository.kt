package com.fts.ttbros.data.repository

import com.fts.ttbros.data.model.Member
import com.fts.ttbros.data.model.Team
import com.fts.ttbros.data.model.UserRole
import com.fts.ttbros.data.utils.TeamCodeGenerator
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.UUID

class TeamRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val teamsCollection get() = firestore.collection("teams")
    private val codesCollection get() = firestore.collection("teamCodes")

    suspend fun createTeam(owner: FirebaseUser, system: String): Team {
        val teamId = UUID.randomUUID().toString()
        val code = TeamCodeGenerator.generate()

        val ownerMember = Member(
            uid = owner.uid,
            email = owner.email.orEmpty(),
            role = UserRole.MASTER.name,
            joinedAt = System.currentTimeMillis()
        )

        val team = Team(
            id = teamId,
            code = code,
            ownerId = owner.uid,
            ownerEmail = owner.email.orEmpty(),
            system = system,
            members = listOf(ownerMember)
        )

        teamsCollection.document(teamId).set(team).await()
        codesCollection.document(code.uppercase()).set(mapOf("teamId" to teamId)).await()
        return team
    }

    suspend fun findTeamByCode(code: String): Team? {
        return try {
            val mapping = codesCollection.document(code.uppercase()).get().await()
            val teamId = mapping.getString("teamId") ?: return null
            val snapshot = teamsCollection.document(teamId).get().await()
            snapshot.toObject(Team::class.java)?.copy(id = teamId)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun addMember(teamId: String, user: FirebaseUser, role: UserRole) {
        val teamRef = teamsCollection.document(teamId)
        firestore.runTransaction { txn ->
            val snapshot = txn.get(teamRef)
            val team = snapshot.toObject(Team::class.java) ?: return@runTransaction
            if (team.members.any { it.uid == user.uid }) return@runTransaction
            val updatedTeam = team.copy(
                members = team.members + Member(
                    uid = user.uid,
                    email = user.email.orEmpty(),
                    role = role.name,
                    joinedAt = System.currentTimeMillis()
                )
            )
            txn.set(teamRef, updatedTeam)
        }.await()
    }

    suspend fun fetchMembers(teamId: String): List<Member> {
        return try {
            val snapshot = teamsCollection.document(teamId).get().await()
            snapshot.toObject(Team::class.java)?.members ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
