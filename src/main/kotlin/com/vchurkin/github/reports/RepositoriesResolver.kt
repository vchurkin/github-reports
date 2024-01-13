package com.vchurkin.github.reports

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.Serializable

class RepositoriesResolver(
    private val client: HttpClient
) {
    suspend fun resolve(organization: String): List<Repository> {
        return client.get("https://api.github.com/orgs/$organization/repos").body()
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