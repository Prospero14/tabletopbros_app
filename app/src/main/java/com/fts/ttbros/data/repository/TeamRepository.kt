package com.fts.ttbros.data.repository

import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.fts.ttbros.data.model.Team
import com.fts.ttbros.data.model.UserRole
import com.fts.ttbros.data.utils.TeamCodeGenerator
import kotlinx.coroutines.tasks.await

class TeamRepository(
    private val firestore: FirebaseFirestore = Firebase.firestore
) {

    private val teamsCollection = firestore.collection(COLLECTION_TEAMS)

    suspend fun createTeam(owner: FirebaseUser): Team {
        val code = TeamCodeGenerator.generate()
        val docRef = teamsCollection.document()
        val payload = mapOf(
            FIELD_CODE to code,
            FIELD_OWNER_ID to owner.uid,
            FIELD_OWNER_EMAIL to owner.email,
            FIELD_CREATED_AT to FieldValue.serverTimestamp()
        )
        docRef.set(payload).await()
        addMemberDocument(docRef.id, owner.uid, owner.email.orEmpty(), UserRole.MASTER)
        return Team(
            id = docRef.id,
            code = code,
            ownerId = owner.uid,
            ownerEmail = owner.email.orEmpty()
        )
    }

    suspend fun findTeamByCode(code: String): Team? {
        val snapshot = teamsCollection.whereEqualTo(FIELD_CODE, code.uppercase()).limit(1).get().await()
        val document = snapshot.documents.firstOrNull() ?: return null
        return Team(
            id = document.id,
            code = document.getString(FIELD_CODE).orEmpty(),
            ownerId = document.getString(FIELD_OWNER_ID).orEmpty(),
            ownerEmail = document.getString(FIELD_OWNER_EMAIL).orEmpty()
        )
    }

    suspend fun addMember(teamId: String, user: FirebaseUser, role: UserRole) {
        addMemberDocument(teamId, user.uid, user.email.orEmpty(), role)
    }

    suspend fun fetchMembers(teamId: String): List<Member> {
        val snapshot = teamsCollection
            .document(teamId)
            .collection(COLLECTION_MEMBERS)
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            val uid = doc.getString(FIELD_MEMBER_UID) ?: return@mapNotNull null
            val email = doc.getString(FIELD_MEMBER_EMAIL).orEmpty()
            val role = UserRole.from(doc.getString(FIELD_ROLE))
            Member(uid = uid, email = email, role = role)
        }
    }

    private suspend fun addMemberDocument(teamId: String, uid: String, email: String, role: UserRole) {
        val docRef = teamsCollection
            .document(teamId)
            .collection(COLLECTION_MEMBERS)
            .document(uid)

        val payload = mapOf(
            FIELD_MEMBER_UID to uid,
            FIELD_MEMBER_EMAIL to email,
            FIELD_ROLE to role.name,
            FIELD_JOINED_AT to FieldValue.serverTimestamp()
        )
        docRef.set(payload).await()
    }

    data class Member(
        val uid: String,
        val email: String,
        val role: UserRole
    )

    companion object {
        private const val COLLECTION_TEAMS = "teams"
        private const val COLLECTION_MEMBERS = "members"
        private const val FIELD_CODE = "code"
        private const val FIELD_OWNER_ID = "ownerId"
        private const val FIELD_OWNER_EMAIL = "ownerEmail"
        private const val FIELD_CREATED_AT = "createdAt"
        private const val FIELD_MEMBER_UID = "uid"
        private const val FIELD_MEMBER_EMAIL = "email"
        private const val FIELD_JOINED_AT = "joinedAt"
        private const val FIELD_ROLE = "role"
    }
}

