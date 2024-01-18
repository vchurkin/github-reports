package com.vchurkin.github.reports.codeowners

import com.vchurkin.github.reports.repositories.RepositoriesResolver
import java.io.BufferedWriter

class CodeownersReporter(
    private val repositoriesResolver: RepositoriesResolver,
    private val codeownersResolver: CodeownersResolver,
    private val outputWriter: BufferedWriter
) {
    suspend fun calculateCodeowners(query: CodeownersReportQuery) {
        writeLine("===== CODEOWNERS =====")
        writeLine("$query")
        outputWriter.flush()

        writeLine("===== CODEOWNERS =====")
        val repos = query.organizations.flatMap { repositoriesResolver.resolve(it) }
            .sortedBy { it.ownerAsString() + "/" + it.name }
        outputWriter.flush()

        repos.forEach { repo ->
            val codeowners = codeownersResolver.resolve(repo)
            writeLine("Repository: name=${repo.ownerAsString()}/${repo.name}, codeowners=${codeowners}")
        }
        outputWriter.flush()
    }

    private fun writeLine(line: String) {
        outputWriter.write(line)
        outputWriter.write("\n")
    }
}

data class CodeownersReportQuery(
    val organizations: List<String>
)