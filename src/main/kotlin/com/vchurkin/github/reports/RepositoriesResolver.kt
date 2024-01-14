package com.vchurkin.github.reports

import com.vchurkin.github.reports.utils.InstantSerializer
import com.vchurkin.github.reports.utils.toLocalDate
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate

class RepositoriesResolver(
    private val client: HttpClient
) {
    suspend fun resolve(organization: String, since: LocalDate, until: LocalDate): List<Repository> {
        val repos = mutableListOf<Repository>()
        var page = 1
        while (page < MAX_PAGES) {
            val reposPage = client.get("https://api.github.com/orgs/$organization/repos") {
                parameter("per_page", PAGE_SIZE)
                parameter("page", page)
            }.body<List<Repository>>()
                .filterNot { it.createdAt.toLocalDate().isAfter(until) }

            if (reposPage.isEmpty())
                break

            reposPage
                .filterNot { it.pushedAt == null || it.pushedAt.toLocalDate().isBefore(since) }
                .also { repos.addAll(it) }

            page++
        }
        return repos
    }

    companion object {
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 10
    }
}

@Serializable
data class Repository(
    val name: String,
    val owner: Owner,
    @Serializable(InstantSerializer::class)
    @SerialName("created_at")
    val createdAt: Instant,
    @Serializable(InstantSerializer::class)
    @SerialName("pushed_at")
    val pushedAt: Instant? = null
) {
    fun ownerAsString() = owner.login
}

@Serializable
data class Owner(
    val login: String
)