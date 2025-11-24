package com.vchurkin.github.reports.copilot

import com.vchurkin.github.reports.utils.writeCsvLine
import java.io.BufferedWriter
import java.io.File
import java.math.RoundingMode
import java.time.LocalDate

/**
 * Builds CSV report.
 */
class CopilotReporter(
    private val copilotResolver: CopilotResolver,
    private val outputDir: String
) {
    suspend fun build(query: CopilotReportQuery) {
        File("$outputDir/copilot.csv").also {
            it.parentFile?.mkdirs()
            it.createNewFile()
        }.bufferedWriter().use { writer ->
            writer.writeCsvHeader()

            val copilot = query.organizations.flatMap { copilotResolver.resolve(it, query.since, query.until) }

            copilot.forEach {
                writer.writeCsvValues(
                    it.organization,
                    it.team,
                    it.daysActive,
                    it.dailyActiveUsers,
                    it.dailyEngagedUsers,
                    it.codeCompletion.codeBlocksAccepted,
                    it.codeCompletion.codeLinesAccepted,
                    it.codeReview.prsReviewed
                )
            }
        }
    }

    private fun BufferedWriter.writeCsvHeader() {
        this.writeCsvLine("Organization", "Team",
            "Days Active", "Daily Active Users", "Daily Engaged Users",
            "Code Blocks Accepted", "Code Lines Accepted", "Code Reviews")
    }

    private fun BufferedWriter.writeCsvValues(org: String? = null,
                                              team: String? = null,
                                              daysActive: Int? = null,
                                              dailyActiveUsers: Double? = null,
                                              dailyEngagedUsers: Double? = null,
                                              codeBlocksAccepted: Int? = null,
                                              codeLinesAccepted: Int? = null,
                                              codeReviews: Int? = null) {
        this.writeCsvLine(org, team, daysActive,
            dailyActiveUsers?.toBigDecimal()?.setScale(1, RoundingMode.HALF_UP),
            dailyEngagedUsers?.toBigDecimal()?.setScale(1, RoundingMode.HALF_UP),
            codeBlocksAccepted, codeLinesAccepted, codeReviews)
    }
}

data class CopilotReportQuery(
    val organizations: List<String>,
    val since: LocalDate,
    val until: LocalDate,
)