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
    suspend fun resolveDaysByOrganization(organization: String, since: LocalDate, until: LocalDate): List<CopilotDayStats> {
        val copilotDays = mutableListOf<CopilotDayStats>()
        var page = 1
        while (page < MAX_PAGES) {
            val copilotDaysPage = try {
                client.get("https://api.github.com/orgs/$organization/copilot/metrics") {
                    parameter("since", since.let { if (it.isBefore(EARLIEST_DATE_BY_ORG)) EARLIEST_DATE_BY_ORG else it })
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

    suspend fun resolve(organization: String, since: LocalDate, until: LocalDate): CopilotStats {
        val copilotDays = resolveDaysByOrganization(organization, since, until)

        var totalActiveUsers = 0
        var totalEngagedUsers = 0
        var daysActive = 0
        copilotDays
            .filter { it.totalActiveUsers > 0 }
            .forEach {
                daysActive++
                totalActiveUsers += it.totalActiveUsers
                totalEngagedUsers += it.totalEngagedUsers
            }

        if (daysActive == 0) {
            return CopilotStats(organization)
        }

        return CopilotStats(
            organization, daysActive,
            dailyActiveUsers = totalActiveUsers.toDouble() / daysActive,
            dailyEngagedUsers = totalEngagedUsers.toDouble() / daysActive
        )
    }

    companion object {
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 1000
        private val EARLIEST_DATE_BY_ORG = LocalDate.parse("2025-08-16")
    }
}

data class CopilotStats(
    val organization: String,
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