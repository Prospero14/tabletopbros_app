package com.fts.ttbros.data.model

data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val teamId: String? = null,
    val teamCode: String? = null,
    val teamSystem: String? = null,
    val role: UserRole = UserRole.PLAYER
)

