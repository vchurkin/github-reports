package com.vchurkin.github.reports

import com.vchurkin.github.reports.codeowners.CodeownersReportQuery
import com.vchurkin.github.reports.codeowners.CodeownersReporter
import com.vchurkin.github.reports.contributions.ContributionsResolver
import com.vchurkin.github.reports.contributions.ContributionsReportQuery
import com.vchurkin.github.reports.contributions.ContributionsReporter
import com.vchurkin.github.reports.repositories.RepositoriesResolver
import com.vchurkin.github.reports.codeowners.CodeownersResolver
import com.vchurkin.github.reports.copilot.CopilotReportQuery
import com.vchurkin.github.reports.copilot.CopilotReporter
import com.vchurkin.github.reports.copilot.CopilotResolver
import com.vchurkin.github.reports.repositories.RepositoryFileContentResolver
import com.vchurkin.github.reports.teams.TeamsResolver
import com.vchurkin.github.reports.utils.OUTPUT_DIR_DEFAULT
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import java.time.LocalDate

suspend fun main() {
    val envProperties = EnvProperties()

    val githubToken = envProperties.get("GITHUB_TOKEN")!! as String
    val client = createHttpClient(githubToken)

    val outputDir = (envProperties.get("OUTPUT_DIR") as String?)
        ?.takeIf { it.isNotEmpty() }
        ?: OUTPUT_DIR_DEFAULT

    try {
        codeowners(envProperties, client, outputDir)
        contributions(envProperties, client, outputDir)
        copilot(envProperties, client, outputDir)
    } catch (e: Exception) {
        println("Error: ${e.message}")
        e.printStackTrace()
    } finally {
        client.close()
    }
}

private suspend fun contributions(envProperties: EnvProperties, client: HttpClient, outputDir: String) {
    val repositoriesResolver = RepositoriesResolver(client)
    val contributionsResolver = ContributionsResolver(client)
    val contributionsReporter = ContributionsReporter(repositoriesResolver, contributionsResolver, outputDir)

    val since = LocalDate.parse(envProperties.get("SINCE")!! as String)
    val until = LocalDate.parse(envProperties.get("UNTIL")!! as String)
    contributionsReporter.build(ContributionsReportQuery(envProperties.organizations(), since, until))
}

private suspend fun codeowners(envProperties: EnvProperties, client: HttpClient, outputDir: String) {
    val repositoriesResolver = RepositoriesResolver(client)
    val fileContentResolver = RepositoryFileContentResolver(client)
    val codeownersResolver = CodeownersResolver(fileContentResolver)
    val codeownersReporter = CodeownersReporter(repositoriesResolver, codeownersResolver, outputDir)

    codeownersReporter.build(CodeownersReportQuery(envProperties.organizations()))
}

private suspend fun copilot(envProperties: EnvProperties, client: HttpClient, outputDir: String) {
    val teamsResolver = TeamsResolver(client)
    val copilotResolver = CopilotResolver(client, teamsResolver)
    val copilotReporter = CopilotReporter(copilotResolver, outputDir)

    val since = LocalDate.parse(envProperties.get("SINCE")!! as String)
    val until = LocalDate.parse(envProperties.get("UNTIL")!! as String)
    copilotReporter.build(CopilotReportQuery(envProperties.organizations(), since, until))
}

private fun EnvProperties.organizations(): List<String> =
    (this.get("ORGANIZATIONS")!! as String).split(",")

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

