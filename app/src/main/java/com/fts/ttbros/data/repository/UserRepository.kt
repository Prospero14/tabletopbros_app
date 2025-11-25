package com.fts.ttbros.data.repository

import com.fts.ttbros.data.model.TeamMembership
import com.fts.ttbros.data.model.UserProfile
import com.fts.ttbros.data.model.UserRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

class UserRepository(
    private val auth: FirebaseAuth = Firebase.auth,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private val usersCollection get() = firestore.collection("users")

    suspend fun currentProfile(): UserProfile? {
        val firebaseUser = auth.currentUser ?: return null
        return getProfile(firebaseUser.uid)
    }

    suspend fun ensureProfile(user: FirebaseUser): UserProfile {
        val existing = getProfile(user.uid)
        if (existing != null) return existing

        val profile = UserProfile(
            uid = user.uid,
            email = user.email.orEmpty(),
            displayName = user.displayName ?: user.email.orEmpty().substringBefore("@"),
            role = UserRole.PLAYER,
            currentTeamId = null,
            teams = emptyList()
        )
        saveProfile(profile)
        return profile
    }

    suspend fun addTeam(
        teamId: String,
        teamCode: String,
        role: UserRole,
        teamSystem: String? = null,
        teamName: String = ""
    ) {
        val firebaseUser = auth.currentUser ?: return
        val profile = ensureProfile(firebaseUser)

        if (profile.teams.any { it.teamId == teamId }) return

        val newTeam = TeamMembership(
            teamId = teamId,
            teamCode = teamCode,
            teamSystem = teamSystem ?: "unknown",
            role = role,
            teamName = if (teamName.isNotBlank()) teamName else "Team $teamCode"
        )

        val updatedProfile = profile.copy(
            teams = profile.teams + newTeam,
            currentTeamId = teamId
        )
        saveProfile(updatedProfile)
    }

    suspend fun updateTeamInfo(
        teamId: String,
        teamCode: String,
        role: UserRole,
        teamSystem: String? = null,
        teamName: String = ""
    ) {
        addTeam(teamId, teamCode, role, teamSystem, teamName)
    }

    suspend fun switchTeam(teamId: String) {
        val firebaseUser = auth.currentUser ?: return
        val profile = getProfile(firebaseUser.uid) ?: return
        if (profile.currentTeamId == teamId) return
        saveProfile(profile.copy(currentTeamId = teamId))
    }

    suspend fun updateAvatarUrl(avatarUrl: String?) {
        val firebaseUser = auth.currentUser ?: return
        val profile = ensureProfile(firebaseUser)
        saveProfile(profile.copy(avatarUrl = avatarUrl))
    }

    fun signOut() {
        auth.signOut()
    }

    suspend fun updateCurrentTeam(teamId: String) {
        switchTeam(teamId)
    }

    private suspend fun getProfile(userId: String): UserProfile? {
        return try {
            val snapshot = usersCollection.document(userId).get().await()
            if (!snapshot.exists()) null else snapshot.toObject(UserProfile::class.java)?.copy(uid = userId)
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun saveProfile(profile: UserProfile) {
        usersCollection.document(profile.uid).set(profile).await()
    }
}
