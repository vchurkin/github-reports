package com.vchurkin.github.reports.contributions

import com.vchurkin.github.reports.Repository
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.Serializable
import java.time.LocalDate

class ContributionsResolver(
    private val client: HttpClient
) {
    suspend fun resolve(repo: Repository, since: LocalDate, until: LocalDate): List<Contributor> {
        return client.get("https://api.github.com/repos/${repo.ownerAsString()}/${repo.name}/contributors") {
            parameter("since", since)
            parameter("until", until)
        }.body()
    }
}

@Serializable
data class Contributor(
    val login: String,
    val contributions: Int
)