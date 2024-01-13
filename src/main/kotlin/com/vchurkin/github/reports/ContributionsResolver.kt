package com.vchurkin.github.reports

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

class ContributionsResolver(
    private val client: HttpClient
) {
    private suspend fun resolveAuthors(repo: Repository, since: LocalDate, until: LocalDate): Map<String, Int> {
        val contributors = ConcurrentHashMap<String, Int>()
        var page = 0
        while (page < MAX_PAGES) {
            val commits = try {
                client.get("https://api.github.com/repos/${repo.ownerAsString()}/${repo.name}/commits") {
                    parameter("since", "${since}T00:00:00Z")
                    parameter("until", "${until}T23:59:59Z")
                    parameter("per_page", PAGE_SIZE)
                    parameter("page", page)
                }.body<List<Commit>>()
            } catch (e: ClientRequestException) {
                emptyList()
            }

            if (commits.isEmpty())
                break

            commits
                .filter { it.author != null }
                .groupingBy { it.author!!.login }
                .fold(0) { accumulator, _ -> accumulator + 1 }
                .forEach {
                    contributors.compute(it.key) { _, u -> it.value + (u ?: 0) }
                }

            page++
        }

        return contributors
    }

    suspend fun resolve(repos: List<Repository>, since: LocalDate, until: LocalDate): Contributions {
        val byRepos = ConcurrentHashMap<String, Int>()
        val byAuthors = ConcurrentHashMap<String, Int>()
        repos.forEach { repo ->
            resolveAuthors(repo, since, until)
                .forEach {
                    byRepos.compute(repo.name) { _, contributions -> it.value + (contributions ?: 0) }
                    byAuthors.compute(it.key) { _, contributions -> it.value + (contributions ?: 0) }
                }
        }
        return Contributions(byRepos, byAuthors)
    }

    companion object {
        const val PAGE_SIZE = 200
        const val MAX_PAGES = 1000
    }
}

data class Contributions(
    val repos: Map<String, Int>,
    val authors: Map<String, Int>
)

@Serializable
data class Commit(
    val author: User?
)

@Serializable
data class User(
    val login: String
)