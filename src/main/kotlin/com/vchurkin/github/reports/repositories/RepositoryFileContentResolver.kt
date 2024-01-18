package com.vchurkin.github.reports.repositories

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.Serializable

class RepositoryFileContentResolver(
    private val client: HttpClient
) {
    suspend fun resolve(repository: Repository, filePath: String): String? {
        return try {
            client.get("https://api.github.com/repos/${repository.ownerAsString()}/${repository.name}/contents/$filePath")
                .body<RepositoryFileContent>()
                .content
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.NotFound)
                null
            else
                throw e
        }
    }
}

@Serializable
data class RepositoryFileContent(
    val content: String? = null
)