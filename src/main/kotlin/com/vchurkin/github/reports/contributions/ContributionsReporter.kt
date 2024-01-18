package com.vchurkin.github.reports.contributions

import com.vchurkin.github.reports.repositories.RepositoriesResolver
import com.vchurkin.github.reports.utils.toLocalDate
import java.io.BufferedWriter
import java.time.LocalDate

class ContributionsReporter(
    private val repositoriesResolver: RepositoriesResolver,
    private val contributionsResolver: ContributionsResolver,
    private val outputWriter: BufferedWriter
) {
    suspend fun calculateContributions(query: ContributionsReportQuery) {
        writeLine("===== CONTRIBUTIONS =====")
        writeLine("$query")
        outputWriter.flush()

        writeLine("===== ORGANIZATIONS =====")
        val repos = query.organizations.flatMap { org ->
            repositoriesResolver.resolve(org, query.since, query.until).also {
                writeLine("Organization: name=$org, repos=${it.size}")
            }
        }.sortedBy { it.ownerAsString() + "/" + it.name }
        outputWriter.flush()

        val contributions = contributionsResolver.resolve(repos, query.since, query.until)
        contributions.repos
            .filter { it.value > 0 }
            .entries
            .groupingBy { it.key.ownerAsString() }
            .aggregate { _, accumulator: Int?, element, _ -> element.value + (accumulator ?: 0) }
            .forEach { writeLine("Organization: name=${it.key}, contributions=${it.value}") }
        outputWriter.flush()

        writeLine("===== REPOSITORIES =====")
        contributions.repos
            .filter { it.value > 0 }
            .entries
            .sortedByDescending { it.value }
            .forEach {
                writeLine("Repo: name=${it.key.ownerAsString()}/${it.key.name}, " +
                        "created=${it.key.createdAt.toLocalDate()}, contributions=${it.value}")
            }
        outputWriter.flush()

        writeLine("===== CONTRIBUTORS =====")
        contributions.authors
            .entries
            .sortedByDescending { it.value }
            .forEach { writeLine("Contributor: name=${it.key}, contributions=${it.value}") }
        outputWriter.flush()
    }

    private fun writeLine(line: String) {
        outputWriter.write(line)
        outputWriter.write("\n")
    }
}

data class ContributionsReportQuery(
    val organizations: List<String>,
    val since: LocalDate,
    val until: LocalDate
)