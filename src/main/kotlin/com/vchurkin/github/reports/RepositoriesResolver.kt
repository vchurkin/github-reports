package com.vchurkin.github.reports

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.Serializable

class RepositoriesResolver(
    private val client: HttpClient
) {
    suspend fun resolve(organization: String): List<Repository> {
        val repos = mutableListOf<Repository>()
        var page = 0
        while (page < MAX_PAGES) {
            val reposPage = client.get("https://api.github.com/orgs/$organization/repos") {
                parameter("per_page", PAGE_SIZE)
                parameter("page", page)
            }.body<List<Repository>>()

            if (reposPage.isEmpty())
                break

            repos.addAll(reposPage)

            page++
        }
        return repos
    }

    companion object {
        const val PAGE_SIZE = 500
        const val MAX_PAGES = 10
    }
}

@Serializable
data class Repository(
    val name: String,
    val owner: Owner
) {
    fun ownerAsString() = owner.login
}

@Serializable
data class Owner(
    val login: String
)