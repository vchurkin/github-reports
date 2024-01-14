package com.vchurkin.github.reports

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate

suspend fun main() {
    val envProperties = EnvProperties()
    val githubToken = envProperties.get("GITHUB_TOKEN")!! as String
    val organizations = (envProperties.get("ORGANIZATIONS")!! as String).split(",")
    val since = LocalDate.parse(envProperties.get("SINCE")!! as String)
    val until = LocalDate.parse(envProperties.get("UNTIL")!! as String)

    val outputFile = File("build/report.txt").also {
        it.parentFile?.mkdirs()
        it.createNewFile()
    }
    val outputWriter = outputFile.bufferedWriter()

    val client = createHttpClient(githubToken)
    val repositoriesResolver = RepositoriesResolver(client)
    val contributionsResolver = ContributionsResolver(client)
    val reporter = Reporter(repositoriesResolver, contributionsResolver, outputWriter)

    try {
        reporter.calculateContributions(ReportQuery(organizations, since, until))

        println(outputFile.readText())
    } catch (e: Exception) {
        println("Error: ${e.message}")
        e.printStackTrace()
    } finally {
        client.close()
        outputWriter.close()
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

