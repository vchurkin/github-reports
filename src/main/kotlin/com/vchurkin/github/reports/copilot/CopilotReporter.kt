package com.vchurkin.github.reports.copilot

import com.vchurkin.github.reports.utils.writeCsvLine
import java.io.BufferedWriter
import java.io.File
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

            val copilot = query.organizations.map { copilotResolver.resolve(it, query.since, query.until) }

            copilot.forEach { copilotByOrg ->
                writer.writeCsvValues(
                    org = copilotByOrg.organization,
                    daysActive = copilotByOrg.daysActive,
                    dailyActiveUsers = copilotByOrg.dailyActiveUsers,
                    dailyEngagedUsers = copilotByOrg.dailyEngagedUsers
                )
            }
        }
    }

    private fun BufferedWriter.writeCsvHeader() {
        this.writeCsvLine("Organization", "Days Active", "Daily Active Users", "Daily Engaged Users")
    }

    private fun BufferedWriter.writeCsvValues(org: String? = null,
                                              daysActive: Int? = null,
                                              dailyActiveUsers: Double? = null,
                                              dailyEngagedUsers: Double? = null) {
        this.writeCsvLine(org, daysActive, dailyActiveUsers, dailyEngagedUsers)
    }
}

data class CopilotReportQuery(
    val organizations: List<String>,
    val since: LocalDate,
    val until: LocalDate,
)