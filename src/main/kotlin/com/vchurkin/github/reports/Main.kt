package com.vchurkin.github.reports

import com.vchurkin.github.reports.contributions.ContributionsResolver
import io.ktor.client.*
import io.ktor.client.call.*
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


fun main() = runBlocking {
    val envProperties = EnvProperties()
    val githubToken = envProperties.get("GITHUB_TOKEN")!! as String
    val since = LocalDate.parse(envProperties.get("SINCE")!! as String)
    val until = LocalDate.parse(envProperties.get("UNTIL")!! as String)

    val client = createHttpClient(githubToken)
    val repositoriesResolver = RepositoriesResolver(client)
    val contributionsResolver = ContributionsResolver(client)

    try {
        val repositories = repositoriesResolver.resolve(ORGANIZATION_NAMES[0])

        repositories.forEach {
            contributionsResolver.resolve(it, since, until)
                .forEach { println("${it.login}: ${it.contributions}") }
        }
    } catch (e: Exception) {
        println("Error: ${e.message}")
    } finally {
        client.close()
    }
}

private fun createHttpClient(githubToken: String) = HttpClient(CIO) {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
        })
    }
    install(Logging) {
        level = LogLevel.BODY
        sanitizeHeader { header -> header == HttpHeaders.Authorization }
    }
    defaultRequest {
        header(HttpHeaders.Authorization, "Bearer $githubToken")
    }
}

