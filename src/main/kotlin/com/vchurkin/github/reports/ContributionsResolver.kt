package com.vchurkin.github.reports

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import kotlin.math.min

class ContributionsResolver(
    private val client: HttpClient
) {
    private suspend fun resolveAuthors(repo: Repository, since: LocalDate, until: LocalDate): Map<String, Int> {
        val contributors = ConcurrentHashMap<String, Int>()
        var page = 1
        while (page < MAX_PAGES) {
            val pulls = try {
                client.get("https://api.github.com/repos/${repo.ownerAsString()}/${repo.name}/pulls") {
                    parameter("since", "${since}T00:00:00Z")
                    parameter("until", "${until}T23:59:59Z")
                    parameter("state", "closed")
                    parameter("per_page", PAGE_SIZE)
                    parameter("page", page)
                }.body<List<PullRequest>>()
                    .filter { it.created_at.toLocalDate().isAfter(since) }
            } catch (e: ClientRequestException) {
                if (e.response.status == HttpStatusCode.Conflict)
                    emptyList()
                else
                    throw e
            }

            if (pulls.isEmpty())
                break

            pulls.asSequence()
                .filterNot { it.created_at.toLocalDate().isAfter(until) }
                .filterNot { it.draft }
                .filter { it.merged_at != null  }
                .filter { it.user?.login != null }
                .groupingBy { it.user!!.login!! }
                .eachCount()
                .forEach {
                    contributors.compute(it.key) { _, u -> it.value + (u ?: 0) }
                }

            page++
        }

        return contributors
    }

    suspend fun resolve(repos: List<Repository>, since: LocalDate, until: LocalDate): Contributions {
        if (repos.isEmpty())
            return Contributions()

        val reposQueue = LinkedBlockingQueue(repos)
        val byRepos = ConcurrentHashMap<Repository, Int>()
        val byAuthors = ConcurrentHashMap<String, Int>()

        val parallelism = min(repos.size, MAX_PARALLELISM)
        coroutineScope {
            (1..parallelism).map { _ ->
                async {
                    var repo: Repository? = reposQueue.poll()
                    while (repo != null) {
                        resolveAuthors(repo, since, until)
                            .forEach {
                                byRepos.compute(repo!!) { _, contributions -> it.value + (contributions ?: 0) }
                                byAuthors.compute(it.key) { _, contributions -> it.value + (contributions ?: 0) }
                            }
                        repo = reposQueue.poll()
                    }
                }
            }.awaitAll()
        }
        return Contributions(byRepos, byAuthors)
    }

    private fun Instant.toLocalDate() = this.atZone(ZoneId.systemDefault()).toLocalDate()

    companion object {
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 1000
        const val MAX_PARALLELISM = 5
    }
}

data class Contributions(
    val repos: Map<Repository, Int> = emptyMap(),
    val authors: Map<String, Int> = emptyMap()
)

@Serializable
data class PullRequest(
    val user: User? = null,
    @Serializable(InstantSerializer::class)
    val created_at: Instant,
    @Serializable(InstantSerializer::class)
    val merged_at: Instant? = null,
    val draft: Boolean
)

@Serializable
data class User(
    val login: String? = null
)