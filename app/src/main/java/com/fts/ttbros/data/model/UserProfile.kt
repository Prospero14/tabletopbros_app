package com.fts.ttbros.data.model

data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val avatarUrl: String? = null, // URL аватарки на Яндекс.Диске
    val teamId: String? = null, // Deprecated: Use currentTeamId or teams list
    val teamCode: String? = null, // Deprecated
    val teamSystem: String? = null, // Deprecated
    val role: UserRole = UserRole.PLAYER, // Deprecated
    
    val currentTeamId: String? = null,
    val teams: List<TeamMembership> = emptyList()
)

data class TeamMembership(
    val teamId: String = "",
    val teamCode: String = "",
    val teamSystem: String = "",
    val role: UserRole = UserRole.PLAYER,
    val teamName: String = ""
)

