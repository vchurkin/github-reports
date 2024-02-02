package com.vchurkin.github.reports.codeowners

import com.vchurkin.github.reports.repositories.RepositoriesResolver
import com.vchurkin.github.reports.utils.writeCsvLine
import java.io.BufferedWriter
import java.io.File

/**
 * Builds CSV report with following structure:
 * "Organization","Repository","Codeowner 1","Codeowner 2",...
 */
class CodeownersReporter(
    private val repositoriesResolver: RepositoriesResolver,
    private val codeownersResolver: CodeownersResolver,
    private val outputDir: String
) {
    suspend fun build(query: CodeownersReportQuery) {
        File("$outputDir/codeowners.csv").also {
            it.parentFile?.mkdirs()
            it.createNewFile()
        }.bufferedWriter().use { writer ->
            writer.writeCsvHeader()

            val repos = query.organizations.flatMap { repositoriesResolver.resolve(it) }
                .sortedBy { it.ownerAsString() + "/" + it.name }

            repos.forEach { repo ->
                val codeowners = codeownersResolver.resolve(repo)
                writer.writeCsvValues(
                    org = repo.ownerAsString(),
                    repo = repo.name,
                    codeowners = codeowners
                )
            }
        }
    }

    private fun BufferedWriter.writeCsvHeader() {
        val codeownersHeaders = mutableListOf<String>().also { list ->
            repeat(MAX_CODEOWNERS) {
                list.add("Codeowner ${it + 1}")
            }
        }
        this.writeCsvLine("Organization", "Repository", *codeownersHeaders.toTypedArray())
    }

    private fun <T> List<T?>.toSize(size: Int): List<T?> {
        if (this.size == size) return this
        if (this.size > size) return this.take(size)
        return mutableListOf<T?>().also { newList ->
            newList.addAll(this)
            repeat(size - this.size) {
                newList.add(null)
            }
        }
    }

    private fun BufferedWriter.writeCsvValues(org: String,
                                              repo: String,
                                              codeowners: List<String?>) {
        this.writeCsvLine(org, repo, *codeowners.toSize(MAX_CODEOWNERS).toTypedArray())
    }

    companion object {
        private const val MAX_CODEOWNERS = 5
    }
}

data class CodeownersReportQuery(
    val organizations: List<String>
)