package com.fts.ttbros.data.repository

import com.fts.ttbros.data.model.Team
import com.fts.ttbros.data.model.TeamMembership
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
        val existing = getProfile(firebaseUser.uid)
        if (existing != null) return existing
        return rebuildProfileFromTeams(firebaseUser)
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
        
        val newTeamInfo = TeamMembership(
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

    suspend fun updateTeamInfo(teamId: String, teamCode: String, role: UserRole, teamSystem: String? = null, teamName: String = "") {
        addTeam(teamId, teamCode, role, teamSystem, teamName)
    }
    
    suspend fun switchTeam(teamId: String) {
        updateCurrentTeam(teamId)
    }
    
    suspend fun updateAvatarUrl(avatarUrl: String?) {
        val firebaseUser = auth.currentUser ?: return
        val profile = getProfile(firebaseUser.uid) ?: return
        val updatedProfile = profile.copy(avatarUrl = avatarUrl)
        saveProfile(updatedProfile)
    }
    
    fun signOut() {
        auth.signOut()
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
                val rawProfile = yandexDisk.readJson(path, UserProfile::class.java)
                rawProfile?.let { normalizeProfile(userId, it) }
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
    private suspend fun normalizeProfile(userId: String, profile: UserProfile): UserProfile {
        var migratedTeams = if (profile.teams.isEmpty() && !profile.teamId.isNullOrBlank()) {
            listOf(
                TeamMembership(
                    teamId = profile.teamId,
                    teamCode = profile.teamCode ?: "",
                    teamSystem = profile.teamSystem ?: "unknown",
                    role = profile.role,
                    teamName = ""
                )
            )
        } else {
            profile.teams
        }
        
        if (migratedTeams.isEmpty()) {
            migratedTeams = loadMembershipsFromTeams(userId)
        }
        
        val fallbackTeamId = profile.currentTeamId
            ?: profile.teamId
            ?: migratedTeams.firstOrNull()?.teamId
        
        val normalized = profile.copy(
            currentTeamId = fallbackTeamId,
            teams = migratedTeams
        )
        
        if (normalized != profile) {
            saveProfile(normalized)
        }
        
        return normalized
    }
    
    private suspend fun rebuildProfileFromTeams(user: FirebaseUser): UserProfile? {
        val memberships = loadMembershipsFromTeams(user.uid)
        val displayName = user.displayName ?: user.email.orEmpty().substringBefore("@")
        val profile = UserProfile(
            uid = user.uid,
            email = user.email.orEmpty(),
            displayName = displayName,
            role = UserRole.PLAYER,
            currentTeamId = memberships.firstOrNull()?.teamId,
            teams = memberships
        )
        saveProfile(profile)
        return profile
    }
    
    private suspend fun loadMembershipsFromTeams(userId: String): List<TeamMembership> {
        return withContext(Dispatchers.IO) {
            val memberships = mutableListOf<TeamMembership>()
            try {
                val teamDirs = yandexDisk.listResources("/TTBros/teams")
                for (resource in teamDirs) {
                    if (resource.type != "dir") continue
                    val teamPath = "${resource.path}/team.json"
                    val team = yandexDisk.readJson(teamPath, Team::class.java) ?: continue
                    val member = team.members.find { it.uid == userId } ?: continue
                    val role = if (member.role.equals(UserRole.MASTER.name, true)) {
                        UserRole.MASTER
                    } else {
                        UserRole.PLAYER
                    }
                    memberships += TeamMembership(
                        teamId = team.id,
                        teamCode = team.code,
                        teamSystem = team.system,
                        role = role,
                        teamName = "Team ${team.code}"
                    )
                }
            } catch (_: Exception) {
                // Ignore and return what we collected
            }
            memberships
        }
    }
}
