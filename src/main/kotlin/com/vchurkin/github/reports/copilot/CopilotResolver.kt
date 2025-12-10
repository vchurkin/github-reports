package com.vchurkin.github.reports.copilot

import com.vchurkin.github.reports.contributions.PullRequest
import com.vchurkin.github.reports.teams.TeamsResolver
import com.vchurkin.github.reports.utils.LocalDateSerializer
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.*
import io.ktor.http.HttpStatusCode
import io.ktor.http.ParametersBuilder
import io.ktor.http.URLBuilder
import io.ktor.http.encodeURLParameter
import io.ktor.http.path
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
                    val earliest = LocalDate.now().minusDays(TEAM_STATS_DEPTH_DAYS)
                    if (since.isBefore(earliest)) earliest else since
                } else {
                    val earliest = LocalDate.now().minusDays(ORG_STATS_DEPTH_DAYS)
                    if (since.isBefore(earliest)) earliest else since
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
            .filter { it.activeUsers > 0 }
            .forEach {
                daysActive++
                totalActiveUsers += it.activeUsers
                totalEngagedUsers += it.engagedUsers
            }

        if (daysActive == 0) {
            return CopilotStats(organization, team)
        }

        val codeCompletionStats: CopilotCodeCompletionStats = this
            .flatMap { it.ideCodeCompletions?.editors ?: emptyList() }
            .flatMap { it.models ?: emptyList() }
            .flatMap { it.languages ?: emptyList() }
            .fold(CopilotCodeCompletionStats()) { acc, next ->
                CopilotCodeCompletionStats(
                    acc.codeBlocksAccepted + next.codeBlocksAccepted,
                    acc.codeLinesAccepted + next.codeLinesAccepted
                )
            }

        return CopilotStats(
            organization,
            team,
            daysActive = daysActive,
            dailyActiveUsers = totalActiveUsers.toDouble() / daysActive,
            dailyEngagedUsers = totalEngagedUsers.toDouble() / daysActive,
            codeCompletionStats
        )
    }

    private suspend fun resolveReviewedPulls(organization: String,
                                             since: LocalDate,
                                             until: LocalDate): List<PullRequest> {
        val reviewedPulls = mutableListOf<PullRequest>()
        var page = 1
        while (page < MAX_PAGES) {
            val urlBuilder = URLBuilder().apply {
                protocol = io.ktor.http.URLProtocol.HTTPS
                host = "api.github.com"
                path("search", "issues")
                encodedParameters = ParametersBuilder().apply {
                    val query = "org:$organization is:pr involves:$COPILOT_REVIEWER_BOT_LOGIN created:${since}..${until}"
                    append("q", query.encodeURLParameter(spaceToPlus = false))
                    append("per_page", PAGE_SIZE.toString())
                    append("page", page.toString())
                }
            }
            val pulls = client.get(urlBuilder.buildString()).body<SearchResult<PullRequest>>().items

            if (pulls.isEmpty())
                break

            reviewedPulls.addAll(pulls)

            page++
        }
        return reviewedPulls
    }

    suspend fun resolve(organization: String, since: LocalDate, until: LocalDate): List<CopilotStats> {
        val copilotStats = mutableListOf<CopilotStats>()

        val copilotOrgDays = resolveDays(organization, since = since, until = until)
        copilotStats.add(copilotOrgDays.summarize(organization))

        teamsResolver.resolve(organization).map { team ->
            val copilotTeamDays = if (copilotOrgDays.isEmpty()) emptyList() else
                resolveDays(organization, team.slug, since, until)
            copilotStats.add(copilotTeamDays.summarize(organization, team.slug))
        }

        resolveReviewedPulls(organization, since, until)
            .groupBy { it.user?.login }
            .filter { it.key != null }
            .forEach { (key, _) ->
                teamsResolver.resolveUserTeams(organization, login = key!!)
                    .map { team -> copilotStats.filter { it.team == team }.forEach { stats ->
                        stats.codeReview.prsReviewed++
                    } }
            }

        return copilotStats
    }

    companion object {
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 1000
        const val ORG_STATS_DEPTH_DAYS = 99L
        const val TEAM_STATS_DEPTH_DAYS = 27L
        const val COPILOT_REVIEWER_BOT_LOGIN = "copilot-pull-request-reviewer[bot]"
    }
}

data class CopilotStats(
    val organization: String,
    val team: String?,
    val daysActive: Int = 0,
    val dailyActiveUsers: Double = 0.0,
    val dailyEngagedUsers: Double = 0.0,
    val codeCompletion: CopilotCodeCompletionStats = CopilotCodeCompletionStats(),
    val codeReview: CopilotCodeReviewStats = CopilotCodeReviewStats(),
)

data class CopilotCodeCompletionStats(
    val codeBlocksAccepted: Int = 0,
    val codeLinesAccepted: Int = 0,
)

data class CopilotCodeReviewStats(
    var prsReviewed: Int = 0
)

@Serializable
data class SearchResult<T>(
    @SerialName("total_count")
    val totalCount: Int,
    val items: List<T>
)

@Serializable
data class CopilotDayStats(
    @Serializable(LocalDateSerializer::class)
    val date: LocalDate? = null,
    @SerialName("total_active_users")
    val activeUsers: Int,
    @SerialName("total_engaged_users")
    val engagedUsers: Int,
    @SerialName("copilot_ide_code_completions")
    val ideCodeCompletions: CopilotCodeCompletions? = null
)

@Serializable
data class CopilotCodeCompletions(
    val editors: List<CopilotEditor>? = null
)

@Serializable
data class CopilotEditor(
    val name: String,
    val models: List<CopilotModel>? = null,
)

@Serializable
data class CopilotModel(
    val name: String,
    val languages: List<CopilotLanguage>? = null,
)

@Serializable
data class CopilotLanguage(
    val name: String,
    @SerialName("total_engaged_users")
    val engagedUsers: Int,
    @SerialName("total_code_acceptances")
    val codeBlocksAccepted: Int,
    @SerialName("total_code_lines_accepted")
    val codeLinesAccepted: Int,
)
