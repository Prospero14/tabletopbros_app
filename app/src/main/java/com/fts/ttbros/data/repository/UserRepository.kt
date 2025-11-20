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

    suspend fun updateTeamInfo(teamId: String, teamCode: String, role: UserRole, teamSystem: String? = null) {
        val firebaseUser = auth.currentUser ?: return
        val data = mapOf(
            FIELD_TEAM_ID to teamId,
            FIELD_TEAM_CODE to teamCode,
            FIELD_TEAM_SYSTEM to teamSystem,
            FIELD_ROLE to role.name
        )
        usersCollection.document(firebaseUser.uid).update(data).await()
    }

    suspend fun clearTeamInfo() {
        val firebaseUser = auth.currentUser ?: return
        val updates = mapOf(
            FIELD_TEAM_ID to null,
            FIELD_TEAM_CODE to null,
            FIELD_TEAM_SYSTEM to null,
            FIELD_ROLE to UserRole.PLAYER.name
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
        val role: String = UserRole.PLAYER.name
    ) {
        fun toDomain(): UserProfile = UserProfile(
            uid = uid,
            email = email,
            displayName = displayName,
            teamId = teamId,
            teamCode = teamCode,
            teamSystem = teamSystem,
            role = UserRole.from(role)
        )

        companion object {
            fun from(profile: UserProfile) = UserDocument(
                uid = profile.uid,
                email = profile.email,
                displayName = profile.displayName,
                teamId = profile.teamId,
                teamCode = profile.teamCode,
                teamSystem = profile.teamSystem,
                role = profile.role.name
            )
        }
    }

    companion object {
        private const val COLLECTION_USERS = "users"
        private const val FIELD_TEAM_ID = "teamId"
        private const val FIELD_TEAM_CODE = "teamCode"
        private const val FIELD_ROLE = "role"
        private const val FIELD_TEAM_SYSTEM = "teamSystem"
    }
}

