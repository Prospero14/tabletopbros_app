package com.fts.ttbros.data.repository

import com.fts.ttbros.data.model.Member
import com.fts.ttbros.data.model.Team
import com.fts.ttbros.data.model.UserRole
import com.fts.ttbros.data.utils.TeamCodeGenerator
import com.google.firebase.auth.FirebaseUser
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class TeamRepository {
    private val yandexDisk = YandexDiskRepository()
    private val gson = Gson()

    suspend fun createTeam(owner: FirebaseUser, system: String): Team {
        return withContext(Dispatchers.IO) {
            val code = TeamCodeGenerator.generate()
            val teamId = UUID.randomUUID().toString()
            
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
            
            // 1. Save team JSON
            val teamPath = "/TTBros/teams/$teamId/team.json"
            val teamJson = gson.toJson(team)
            yandexDisk.uploadJson(teamPath, teamJson)
            
            // 2. Save code mapping
            val codePath = "/TTBros/codes/${code.uppercase()}.json"
            val codeMapping = mapOf("teamId" to teamId)
            val codeJson = gson.toJson(codeMapping)
            yandexDisk.uploadJson(codePath, codeJson)
            
            team
        }
    }

    suspend fun findTeamByCode(code: String): Team? {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Get teamId from code
                val codePath = "/TTBros/codes/${code.uppercase()}.json"
                val codeMapping = yandexDisk.readJson(codePath, Map::class.java)
                val teamId = codeMapping?.get("teamId") as? String ?: return@withContext null
                
                // 2. Get team
                val teamPath = "/TTBros/teams/$teamId/team.json"
                yandexDisk.readJson(teamPath, Team::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun addMember(teamId: String, user: FirebaseUser, role: UserRole) {
        withContext(Dispatchers.IO) {
            val teamPath = "/TTBros/teams/$teamId/team.json"
            val team = yandexDisk.readJson(teamPath, Team::class.java) ?: return@withContext
            
            // Check if already member
            if (team.members.any { it.uid == user.uid }) return@withContext
            
            val newMember = Member(
                uid = user.uid,
                email = user.email.orEmpty(),
                role = role.name,
                joinedAt = System.currentTimeMillis()
            )
            
            val updatedTeam = team.copy(
                members = team.members + newMember
            )
            
            val teamJson = gson.toJson(updatedTeam)
            yandexDisk.uploadJson(teamPath, teamJson)
        }
    }

    suspend fun fetchMembers(teamId: String): List<Member> {
        return withContext(Dispatchers.IO) {
            val teamPath = "/TTBros/teams/$teamId/team.json"
            val team = yandexDisk.readJson(teamPath, Team::class.java)
            team?.members ?: emptyList()
        }
    }
}
