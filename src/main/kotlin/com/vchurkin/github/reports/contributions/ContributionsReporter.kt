package com.vchurkin.github.reports.contributions

import com.vchurkin.github.reports.repositories.RepositoriesResolver
import com.vchurkin.github.reports.utils.writeCsvLine
import java.io.BufferedWriter
import java.io.File
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
                .filter { it.value > 0 }
                .entries
                .groupingBy { it.key.ownerAsString() }
                .aggregate { _, accumulator: Int?, element, _ -> element.value + (accumulator ?: 0) }
                .forEach {
                    writer.writeCsvValues(
                        org = it.key,
                        contributions = it.value
                    )
                }

            contributions.repos
                .filter { it.value > 0 }
                .entries
                .sortedByDescending { it.value }
                .forEach {
                    writer.writeCsvValues(
                        org = it.key.ownerAsString(),
                        repo = it.key.name,
                        contributions = it.value
                    )
                }

            contributions.authors
                .entries
                .sortedByDescending { it.value }
                .forEach {
                    writer.writeCsvValues(
                        author = it.key,
                        contributions = it.value
                    )
                }
        }
    }

    private fun BufferedWriter.writeCsvHeader() {
        this.writeCsvLine("Organization", "Repository", "Author", "Contributions")
    }

    private fun BufferedWriter.writeCsvValues(org: String? = null,
                                              repo: String? = null,
                                              author: String? = null,
                                              contributions: Int) {
        this.writeCsvLine(org, repo, author, contributions)
    }
}

data class ContributionsReportQuery(
    val organizations: List<String>,
    val since: LocalDate,
    val until: LocalDate
)