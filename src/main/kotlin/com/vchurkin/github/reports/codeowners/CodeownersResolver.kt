package com.vchurkin.github.reports.codeowners

import com.vchurkin.github.reports.repositories.Repository
import com.vchurkin.github.reports.repositories.RepositoryFileContentResolver
import io.ktor.util.*
import java.util.regex.Pattern

class CodeownersResolver(
    private val fileContentResolver: RepositoryFileContentResolver
) {
    suspend fun resolve(repository: Repository): List<String> {
        val content = fileContentResolver.resolve(repository, "CODEOWNERS") ?: return emptyList()
        val lines = content.decodeBase64String().split("\n")
        return lines
            .map { it.trim() }
            .filter { it.startsWith('*') }
            .map { it.substring(1) }
            .flatMap { line ->
                return@flatMap line.split(Pattern.compile("\\s+"))
                    .map { it.trim() }
                    .filter { it.startsWith("@") }
                    .map { it.substring(1) }
            }
    }
}