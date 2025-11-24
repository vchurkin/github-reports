package com.vchurkin.github.reports.teams

import com.vchurkin.github.reports.contributions.User
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.Serializable

class TeamsResolver(
    private val client: HttpClient
) {
    private val teamsCache = mutableMapOf<String, List<Team>>()
    private val teamMembersCache = mutableMapOf<OrganizationTeam, List<String>>()

    suspend fun resolve(organization: String): List<Team> {
        if (teamsCache.containsKey(organization)) {
            return teamsCache[organization]!!
        }

        val teams = mutableListOf<Team>()
        var page = 1
        while (page < MAX_PAGES) {
            val teamsPage = client.get("https://api.github.com/orgs/$organization/teams") {
                parameter("per_page", PAGE_SIZE)
                parameter("page", page)
            }.body<List<Team>>()

            teams.addAll(teamsPage)

            if (teamsPage.size < PAGE_SIZE)
                break

            page++
        }
        teamsCache[organization] = teams
        return teams
    }

    suspend fun resolveTeamMembers(organization: String, team: String): List<String> {
        val cacheKey = OrganizationTeam(organization, team)
        if (teamMembersCache.containsKey(cacheKey)) {
            return teamMembersCache[cacheKey]!!
        }

        val members = mutableListOf<String>()
        var page = 1
        while (page < MAX_PAGES) {
            val membersPage = client.get("https://api.github.com/orgs/$organization/teams/$team/members") {
                parameter("per_page", PAGE_SIZE)
                parameter("page", page)
            }.body<List<User>>()

            members.addAll(membersPage.mapNotNull { it.login })

            if (membersPage.size < PAGE_SIZE)
                break

            page++
        }
        teamMembersCache[cacheKey] = members
        return members
    }

    suspend fun resolveUserTeams(organization: String, login: String): List<String> {
        return resolve(organization).map { it.slug }.mapNotNull { team ->
            val teamMembers = resolveTeamMembers(organization, team)
            return@mapNotNull if (teamMembers.contains(login)) team else null
        }
    }

    data class OrganizationTeam(
        val organization: String,
        val team: String
    )

    companion object {
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 10
    }
}

@Serializable
data class Team(
    val name: String? = null,
    val slug: String
)