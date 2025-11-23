package com.vchurkin.github.reports.contributions

import com.vchurkin.github.reports.repositories.RepositoriesResolver
import com.vchurkin.github.reports.utils.writeCsvLine
import java.io.BufferedWriter
import java.io.File
import java.math.RoundingMode
import java.time.Duration
import java.time.LocalDate

/**
 * Build CSV report with following structure:
 * "Organization","Repository","Author","Contributions"
 */
class ContributionsReporter(
    private val repositoriesResolver: RepositoriesResolver,
    private val contributionsResolver: ContributionsResolver,
    private val outputDir: String
) {
    suspend fun build(query: ContributionsReportQuery) {
        File("$outputDir/contributions.csv").also {
            it.parentFile?.mkdirs()
            it.createNewFile()
        }.bufferedWriter().use { writer ->
            writer.writeCsvHeader()

            val repos = query.organizations.flatMap { org ->
                repositoriesResolver.resolve(org, query.since, query.until)
            }.sortedBy { it.ownerAsString() + "/" + it.name }

            val contributions = contributionsResolver.resolve(repos, query.since, query.until)
            contributions.repos
                .filter { it.value.count() > 0 }
                .entries
                .groupingBy { it.key.ownerAsString() }
                .aggregate { _, acc: ContributionStats?, element, _ -> (acc ?: ContributionStats()).add(element.value) }
                .forEach {
                    writer.writeCsvValues(
                        org = it.key,
                        stats = it.value
                    )
                }

            contributions.repos
                .filter { it.value.count() > 0 }
                .entries
                .sortedByDescending { it.value.count() }
                .forEach {
                    writer.writeCsvValues(
                        org = it.key.ownerAsString(),
                        repo = it.key.name,
                        stats = it.value
                    )
                }

            contributions.authors
                .entries
                .sortedByDescending { it.value.count() }
                .forEach {
                    writer.writeCsvValues(
                        author = it.key,
                        stats = it.value
                    )
                }
        }
    }

    private fun BufferedWriter.writeCsvHeader() {
        this.writeCsvLine("Organization", "Repository", "Author", "Contributions", "Median Lifetime (h)", "Max Lifetime (h)")
    }

    private fun BufferedWriter.writeCsvValues(org: String? = null,
                                              repo: String? = null,
                                              author: String? = null,
                                              stats: ContributionStats) {
        this.writeCsvLine(org, repo, author, stats.count(),
            stats.medianLifetime()!!.toHoursAsDouble().setScale(1),
            stats.maxLifetime()!!.toHoursAsDouble().setScale(1))
    }
}

private fun Duration.toHoursAsDouble(): Double = this.toSeconds().toDouble() / 60 / 60

private fun Double.setScale(n: Int) = this.toBigDecimal().setScale(n, RoundingMode.HALF_UP).toDouble()

data class ContributionsReportQuery(
    val organizations: List<String>,
    val since: LocalDate,
    val until: LocalDate
)