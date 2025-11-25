package com.fts.ttbros.data.model

data class Team(
    val id: String = "",
    val code: String = "",
    val ownerId: String = "",
    val ownerEmail: String = "",
    val system: String = "",
    val members: List<Member> = emptyList()
)

data class Member(
    val uid: String = "",
    val email: String = "",
    val role: String = "PLAYER", // Stored as String to avoid enum issues in JSON if needed, or use UserRole enum
    val joinedAt: Long = 0
)

