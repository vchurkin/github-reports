package com.vchurkin.github.reports.copilot

import com.vchurkin.github.reports.teams.TeamsResolver
import com.vchurkin.github.reports.utils.LocalDateSerializer
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.*
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

class CopilotResolver(
    private val client: HttpClient,
    private val teamsResolver: TeamsResolver
) {
    private suspend fun resolveDays(organization: String,
                                    team: String? = null,
                                    since: LocalDate,
                                    until: LocalDate): List<CopilotDayStats> {
        val copilotDays = mutableListOf<CopilotDayStats>()
        var page = 1
        while (page < MAX_PAGES) {
            val copilotDaysPage = try {
                val orgAndTeam = if (team != null) "$organization/team/$team" else organization
                val cappedSince = if (team != null) {
                    if (since.isBefore(EARLIEST_TEAM_STATS)) EARLIEST_TEAM_STATS else since
                } else {
                    if (since.isBefore(EARLIEST_ORG_STATS)) EARLIEST_ORG_STATS else since
                }
                if (!until.isAfter(cappedSince)) {
                    return emptyList()
                }
                client.get("https://api.github.com/orgs/$orgAndTeam/copilot/metrics") {
                    parameter("since", cappedSince)
                    parameter("until", until)
                    parameter("per_page", PAGE_SIZE)
                    parameter("page", page)
                }.body<List<CopilotDayStats>>()
            } catch (e: ClientRequestException) {
                if (e.response.status == HttpStatusCode.UnprocessableEntity) {
                    emptyList()
                } else {
                    throw e
                }
            }

            if (copilotDaysPage.isEmpty())
                break

            copilotDays.addAll(copilotDaysPage)

            page++
        }
        return copilotDays
    }

    private fun List<CopilotDayStats>.summarize(organization: String, team: String? = null): CopilotStats {
        var totalActiveUsers = 0
        var totalEngagedUsers = 0
        var daysActive = 0
        this
            .filter { it.totalActiveUsers > 0 }
            .forEach {
                daysActive++
                totalActiveUsers += it.totalActiveUsers
                totalEngagedUsers += it.totalEngagedUsers
            }

        if (daysActive == 0) {
            return CopilotStats(organization, team)
        }

        return CopilotStats(
            organization,
            team,
            daysActive = daysActive,
            dailyActiveUsers = totalActiveUsers.toDouble() / daysActive,
            dailyEngagedUsers = totalEngagedUsers.toDouble() / daysActive
        )
    }

    suspend fun resolve(organization: String, since: LocalDate, until: LocalDate): List<CopilotStats> {
        val copilotStats = mutableListOf<CopilotStats>()

        val copilotOrgDays = resolveDays(organization, since = since, until = until)
        copilotStats.add(copilotOrgDays.summarize(organization))

        if (!copilotOrgDays.isEmpty()) {
            teamsResolver.resolve(organization).map { team ->
                val copilotTeamDays = resolveDays(organization, team.slug, since, until)
                copilotStats.add(copilotTeamDays.summarize(organization, team.slug))
            }
        }

        return copilotStats
    }

    companion object {
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 1000
        private val EARLIEST_ORG_STATS = LocalDate.parse("2025-08-16")
        private val EARLIEST_TEAM_STATS = LocalDate.parse("2025-10-27")
    }
}

data class CopilotStats(
    val organization: String,
    val team: String?,
    val daysActive: Int = 0,
    val dailyActiveUsers: Double = 0.0,
    val dailyEngagedUsers: Double = 0.0,
)

@Serializable
data class CopilotDayStats(
    @Serializable(LocalDateSerializer::class)
    val date: LocalDate? = null,
    @SerialName("total_active_users")
    val totalActiveUsers: Int,
    @SerialName("total_engaged_users")
    val totalEngagedUsers: Int,
)