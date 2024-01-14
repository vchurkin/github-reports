package com.vchurkin.github.reports

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.time.LocalDate

suspend fun main() {
    val envProperties = EnvProperties()
    val githubToken = envProperties.get("GITHUB_TOKEN")!! as String
    val since = LocalDate.parse(envProperties.get("SINCE")!! as String)
    val until = LocalDate.parse(envProperties.get("UNTIL")!! as String)

    val client = createHttpClient(githubToken)
    val repositoriesResolver = RepositoriesResolver(client)
    val contributionsResolver = ContributionsResolver(client)

    try {
        val repos = ORGANIZATION_NAMES.flatMap { org ->
            repositoriesResolver.resolve(org)
                .distinctBy { it.name }
        }.sortedBy { it.ownerAsString() + "/" + it.name }
        println("Repos: ${repos.size}")
        val contributions = contributionsResolver.resolve(repos, since, until)
        contributions.repos
            .filter { it.value > 0 }
            .entries
            .groupingBy { it.key.ownerAsString() }
            .aggregate { _, accumulator: Int?, element, _ -> element.value + (accumulator ?: 0) }
            .forEach { println("Org ${it.key}: ${it.value}") }
        contributions.repos
            .filter { it.value > 0 }
            .entries
            .sortedByDescending { it.value }
            .forEach { println("Repo ${it.key.ownerAsString()}/${it.key.name}: ${it.value}") }
        contributions.authors
            .entries
            .sortedByDescending { it.value }
            .forEach { println("Author ${it.key}: ${it.value}") }
    } catch (e: Exception) {
        println("Error: ${e.message}")
    } finally {
        client.close()
    }
}

private fun createHttpClient(githubToken: String) = HttpClient(CIO) {
    expectSuccess = true
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
        })
    }
    install(Logging) {
        level = LogLevel.INFO
        sanitizeHeader { header -> header == HttpHeaders.Authorization }
    }
    defaultRequest {
        header(HttpHeaders.Authorization, "Bearer $githubToken")
    }
}

