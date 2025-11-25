package com.fts.ttbros.data.repository

import com.fts.ttbros.data.model.UserProfile
import com.fts.ttbros.data.model.UserRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UserRepository(
    private val auth: FirebaseAuth = Firebase.auth
) {
    private val yandexDisk = YandexDiskRepository()
    
    // Custom Gson with Timestamp support if needed, or just standard
    // Since UserProfile uses standard types (String, List), default Gson is fine.
    // But if UserProfile has Timestamp, we need adapter.
    // UserProfile seems to have simple types.
    private val gson = Gson()

    suspend fun currentProfile(): UserProfile? {
        val firebaseUser = auth.currentUser ?: return null
        return getProfile(firebaseUser.uid)
    }

    suspend fun ensureProfile(user: FirebaseUser): UserProfile {
        val profile = getProfile(user.uid)
        if (profile != null) {
            return profile
        }

        val newProfile = UserProfile(
            uid = user.uid,
            email = user.email.orEmpty(),
            displayName = user.displayName ?: user.email.orEmpty().substringBefore("@"),
            role = UserRole.PLAYER
        )
        
        saveProfile(newProfile)
        return newProfile
    }

    suspend fun addTeam(teamId: String, teamCode: String, role: UserRole, teamSystem: String? = null, teamName: String = "") {
        val firebaseUser = auth.currentUser ?: return
        val profile = getProfile(firebaseUser.uid) ?: return
        
        // Check if already in team
        if (profile.teams.any { it.teamId == teamId }) return
        
        val newTeamInfo = com.fts.ttbros.data.model.UserTeamInfo(
            teamId = teamId,
            teamCode = teamCode,
            role = role,
            teamSystem = teamSystem ?: "unknown",
            teamName = teamName
        )
        
        val updatedProfile = profile.copy(
            teams = profile.teams + newTeamInfo,
            currentTeamId = teamId // Auto-switch to new team
        )
        
        saveProfile(updatedProfile)
    }
    
    suspend fun updateCurrentTeam(teamId: String) {
        val firebaseUser = auth.currentUser ?: return
        val profile = getProfile(firebaseUser.uid) ?: return
        
        if (profile.currentTeamId == teamId) return
        
        val updatedProfile = profile.copy(currentTeamId = teamId)
        saveProfile(updatedProfile)
    }

    private suspend fun getProfile(userId: String): UserProfile? {
        return withContext(Dispatchers.IO) {
            try {
                val path = "/TTBros/users/$userId/profile.json"
                yandexDisk.readJson(path, UserProfile::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    private suspend fun saveProfile(profile: UserProfile) {
        withContext(Dispatchers.IO) {
            val path = "/TTBros/users/${profile.uid}/profile.json"
            val json = gson.toJson(profile)
            yandexDisk.uploadJson(path, json)
        }
    }
}
