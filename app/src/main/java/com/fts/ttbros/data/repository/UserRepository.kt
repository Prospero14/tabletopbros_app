package com.fts.ttbros.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.fts.ttbros.data.model.UserProfile
import com.fts.ttbros.data.model.UserRole
import kotlinx.coroutines.tasks.await

class UserRepository(
    private val auth: FirebaseAuth = Firebase.auth,
    private val firestore: FirebaseFirestore = Firebase.firestore
) {

    private val usersCollection = firestore.collection(COLLECTION_USERS)

    suspend fun currentProfile(): UserProfile? {
        val firebaseUser = auth.currentUser ?: return null
        val snapshot = usersCollection.document(firebaseUser.uid).get().await()
        return if (snapshot.exists()) {
            snapshot.toObject(UserDocument::class.java)?.toDomain()
        } else {
            null
        }
    }

    suspend fun ensureProfile(user: FirebaseUser): UserProfile {
        val docRef = usersCollection.document(user.uid)
        val snapshot = docRef.get().await()
        if (snapshot.exists()) {
            return snapshot.toObject(UserDocument::class.java)?.toDomain()
                ?: UserProfile(uid = user.uid, email = user.email.orEmpty(), displayName = user.displayName.orEmpty())
        }

        val profile = UserProfile(
            uid = user.uid,
            email = user.email.orEmpty(),
            displayName = user.displayName ?: user.email.orEmpty().substringBefore("@"),
            role = UserRole.PLAYER
        )
        docRef.set(UserDocument.from(profile)).await()
        return profile
    }

    suspend fun addTeam(teamId: String, teamCode: String, role: UserRole, teamSystem: String? = null, teamName: String = "") {
        val firebaseUser = auth.currentUser ?: return
        val userRef = usersCollection.document(firebaseUser.uid)
        
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(userRef)
            val currentTeams = snapshot.toObject(UserDocument::class.java)?.teams ?: emptyList()
            
            // Check if already a member
            if (currentTeams.any { it.teamId == teamId }) return@runTransaction
            
            val newTeam = TeamMembershipDocument(
                teamId = teamId,
                teamCode = teamCode,
                teamSystem = teamSystem ?: "",
                role = role.name,
                teamName = teamName
            )
            
            val updatedTeams = currentTeams + newTeam
            
            transaction.update(userRef, mapOf(
                FIELD_TEAMS to updatedTeams,
                FIELD_CURRENT_TEAM_ID to teamId,
                // Update legacy fields for backward compatibility
                FIELD_TEAM_ID to teamId,
                FIELD_TEAM_CODE to teamCode,
                FIELD_TEAM_SYSTEM to teamSystem,
                FIELD_ROLE to role.name
            ))
        }.await()
    }

    suspend fun switchTeam(teamId: String) {
        val firebaseUser = auth.currentUser ?: return
        val userRef = usersCollection.document(firebaseUser.uid)
        
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(userRef)
            val userDoc = snapshot.toObject(UserDocument::class.java) ?: return@runTransaction
            val team = userDoc.teams.find { it.teamId == teamId } ?: return@runTransaction
            
            transaction.update(userRef, mapOf(
                FIELD_CURRENT_TEAM_ID to teamId,
                // Update legacy fields
                FIELD_TEAM_ID to team.teamId,
                FIELD_TEAM_CODE to team.teamCode,
                FIELD_TEAM_SYSTEM to team.teamSystem,
                FIELD_ROLE to team.role
            ))
        }.await()
    }

    // Deprecated: Use addTeam instead
    suspend fun updateTeamInfo(teamId: String, teamCode: String, role: UserRole, teamSystem: String? = null) {
        addTeam(teamId, teamCode, role, teamSystem)
    }

    suspend fun clearTeamInfo() {
        // This might need to be "Leave Team" logic later. For now, just clear legacy fields?
        // Or maybe remove current team?
        // Let's keep it as is for legacy support but it might be weird with multi-team.
        val firebaseUser = auth.currentUser ?: return
        val updates = mapOf(
            FIELD_TEAM_ID to null,
            FIELD_TEAM_CODE to null,
            FIELD_TEAM_SYSTEM to null,
            FIELD_ROLE to UserRole.PLAYER.name,
            FIELD_CURRENT_TEAM_ID to null
        )
        usersCollection.document(firebaseUser.uid).update(updates).await()
    }

    fun signOut() {
        auth.signOut()
    }

    data class UserDocument(
        val uid: String = "",
        val email: String = "",
        val displayName: String = "",
        val teamId: String? = null,
        val teamCode: String? = null,
        val teamSystem: String? = null,
        val role: String = UserRole.PLAYER.name,
        val currentTeamId: String? = null,
        val teams: List<TeamMembershipDocument> = emptyList()
    ) {
        fun toDomain(): UserProfile {
            // Migration logic: If teams is empty but legacy fields exist, create a team entry
            val domainTeams = teams.map { it.toDomain() }.toMutableList()
            if (domainTeams.isEmpty() && !teamId.isNullOrBlank()) {
                domainTeams.add(com.fts.ttbros.data.model.TeamMembership(
                    teamId = teamId,
                    teamCode = teamCode ?: "",
                    teamSystem = teamSystem ?: "",
                    role = UserRole.from(role),
                    teamName = "Legacy Team" // Placeholder
                ))
            }
            
            return UserProfile(
                uid = uid,
                email = email,
                displayName = displayName,
                teamId = teamId,
                teamCode = teamCode,
                teamSystem = teamSystem,
                role = UserRole.from(role),
                currentTeamId = currentTeamId ?: teamId,
                teams = domainTeams
            )
        }

        companion object {
            fun from(profile: UserProfile) = UserDocument(
                uid = profile.uid,
                email = profile.email,
                displayName = profile.displayName,
                teamId = profile.teamId,
                teamCode = profile.teamCode,
                teamSystem = profile.teamSystem,
                role = profile.role.name,
                currentTeamId = profile.currentTeamId,
                teams = profile.teams.map { TeamMembershipDocument.from(it) }
            )
        }
    }
    
    data class TeamMembershipDocument(
        val teamId: String = "",
        val teamCode: String = "",
        val teamSystem: String = "",
        val role: String = UserRole.PLAYER.name,
        val teamName: String = ""
    ) {
        fun toDomain() = com.fts.ttbros.data.model.TeamMembership(
            teamId = teamId,
            teamCode = teamCode,
            teamSystem = teamSystem,
            role = UserRole.from(role),
            teamName = teamName
        )
        
        companion object {
            fun from(domain: com.fts.ttbros.data.model.TeamMembership) = TeamMembershipDocument(
                teamId = domain.teamId,
                teamCode = domain.teamCode,
                teamSystem = domain.teamSystem,
                role = domain.role.name,
                teamName = domain.teamName
            )
        }
    }

    companion object {
        private const val COLLECTION_USERS = "users"
        private const val FIELD_TEAM_ID = "teamId"
        private const val FIELD_TEAM_CODE = "teamCode"
        private const val FIELD_ROLE = "role"
        private const val FIELD_TEAM_SYSTEM = "teamSystem"
        private const val FIELD_TEAMS = "teams"
        private const val FIELD_CURRENT_TEAM_ID = "currentTeamId"
    }
}

