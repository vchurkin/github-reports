package com.vchurkin.github.reports.teams

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.Serializable

class TeamsResolver(
    private val client: HttpClient
) {
    suspend fun resolve(organization: String): List<Team> {
        val teams = mutableListOf<Team>()
        var page = 1
        while (page < MAX_PAGES) {
            val teamsPage = client.get("https://api.github.com/orgs/$organization/teams") {
                parameter("per_page", PAGE_SIZE)
                parameter("page", page)
            }.body<List<Team>>()

            if (teamsPage.isEmpty())
                break

            teams.addAll(teamsPage)

            page++
        }
        return teams
    }

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