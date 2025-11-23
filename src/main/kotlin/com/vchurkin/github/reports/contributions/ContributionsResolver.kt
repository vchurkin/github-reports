package com.vchurkin.github.reports.contributions

import com.vchurkin.github.reports.repositories.Repository
import com.vchurkin.github.reports.utils.InstantSerializer
import com.vchurkin.github.reports.utils.toLocalDate
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import kotlin.math.min

class ContributionsResolver(
    private val client: HttpClient
) {
    private suspend fun resolveAuthors(repo: Repository, since: LocalDate, until: LocalDate): Map<String, ContributionStats> {
        val contributors = ConcurrentHashMap<String, ContributionStats>()
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
                    .filter { it.createdAt.toLocalDate().isAfter(since) }
            } catch (e: ClientRequestException) {
                if (e.response.status == HttpStatusCode.Conflict)
                    emptyList()
                else
                    throw e
            }

            if (pulls.isEmpty())
                break

            pulls.asSequence()
                .filterNot { it.createdAt.toLocalDate().isAfter(until) }
                .filterNot { it.draft }
                .filter { it.mergedAt != null }
                .filter { it.user?.login != null }
                .groupingBy { it.user!!.login!! }
                .aggregate { _, acc: ContributionStats?, element, _ ->
                    (acc ?: ContributionStats()).add(PullRequestInfo(element.duration()!!)) }
                .forEach {
                    contributors.compute(it.key) { _, u -> (u ?: ContributionStats()).add(it.value) }
                }

            page++
        }

        return contributors
    }

    suspend fun resolve(repos: List<Repository>, since: LocalDate, until: LocalDate): Contributions {
        if (repos.isEmpty())
            return Contributions()

        val reposQueue = LinkedBlockingQueue(repos)
        val byRepos = ConcurrentHashMap<Repository, ContributionStats>()
        val byAuthors = ConcurrentHashMap<String, ContributionStats>()

        val parallelism = min(repos.size, MAX_PARALLELISM)
        coroutineScope {
            (1..parallelism).map { _ ->
                async {
                    var repo: Repository? = reposQueue.poll()
                    while (repo != null) {
                        resolveAuthors(repo, since, until)
                            .forEach {
                                byRepos.compute(repo!!) { _, u -> (u ?: ContributionStats()).add(it.value) }
                                byAuthors.compute(it.key) { _, u -> (u ?: ContributionStats()).add(it.value) }
                            }
                        repo = reposQueue.poll()
                    }
                }
            }.awaitAll()
        }
        return Contributions(byRepos, byAuthors)
    }

    companion object {
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 1000
        const val MAX_PARALLELISM = 5
    }
}

data class Contributions(
    val repos: Map<Repository, ContributionStats> = emptyMap(),
    val authors: Map<String, ContributionStats> = emptyMap()
)

data class PullRequestInfo(
    val lifetime: Duration,
)

data class ContributionStats(
    val prs: MutableList<PullRequestInfo> = mutableListOf()
) {
    fun count() = prs.size

    fun maxLifetime(): Duration? = prs.maxByOrNull { it.lifetime }?.lifetime

    fun medianLifetime(): Duration? = prs.sortedBy { it.lifetime }.let {
        if (it.isEmpty()) {
            null
        } else if (it.size % 2 == 0) {
            Duration.ofMillis((it[it.size / 2].lifetime.toMillis() + it[(it.size - 1) / 2].lifetime.toMillis()) / 2)
        } else {
            it[it.size / 2].lifetime
        }
    }

    fun add(pr: PullRequestInfo): ContributionStats {
        prs.addLast(pr)
        return this
    }

    fun add(stats: ContributionStats): ContributionStats {
        prs.addAll(stats.prs)
        return this
    }
}

@Serializable
data class PullRequest(
    val number: Long? = null,
    val user: User? = null,
    @Serializable(InstantSerializer::class)
    @SerialName("created_at")
    val createdAt: Instant,
    @Serializable(InstantSerializer::class)
    @SerialName("merged_at")
    val mergedAt: Instant? = null,
    val draft: Boolean
)

fun PullRequest.duration(): Duration? {
    if (this.mergedAt == null) {
        return null;
    }
    return Duration.between(this.createdAt, this.mergedAt)
}

@Serializable
data class User(
    val login: String? = null
)