package de.pantastix.project.service

import de.pantastix.project.model.api.GitHubRelease
import de.pantastix.project.model.api.UpdateInfo
import de.pantastix.project.platform.getAppVersion
import de.pantastix.project.platform.getPlatform
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import de.pantastix.project.platform.Platform
import kotlinx.serialization.json.Json

object UpdateChecker {
    // Passen Sie dies an Ihr GitHub-Repository an!
    private const val GITHUB_REPO_URL = "https://api.github.com/repos/Pantastix/TCGM/releases/latest"

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun checkForUpdate(): UpdateInfo? {
        val platform = getPlatform()

        val osSuffix = when (platform) {
            Platform.Windows -> ".msi"
            Platform.Mac -> ".dmg"
            Platform.Linux -> ".deb"
            Platform.Android -> ".apk"
            else -> null // Für iOS und WasmJs gibt es keinen In-App-Updater.
        }

        if (osSuffix == null) return null

        return try {
            val currentVersion = getAppVersion()
            println("Current version: $currentVersion")
            val latestRelease = client.get(GITHUB_REPO_URL).body<GitHubRelease>()
            println("Latest release: ${latestRelease.tagName}")
            println(latestRelease)
            val latestVersion = latestRelease.tagName.removePrefix("v")

            println(latestRelease)

            if (isNewerVersion(latestVersion, currentVersion)) {
                val assetUrl = latestRelease.assets.find { it.name.endsWith(osSuffix) }?.downloadUrl
                if (assetUrl != null) {
                    UpdateInfo(version = latestVersion, downloadUrl = assetUrl, platform = platform)
                } else {
                    println("Update gefunden, aber kein passendes Installationspaket ($osSuffix) im Release gefunden.")
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            println("Fehler bei der Update-Prüfung: ${e.message}")
            null
        }
    }

    // Simple Funktion zum Vergleichen von Versionen (z.B. "1.1.0" > "1.0.0")
    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = latest.split('.').map { it.toInt() }
        val currentParts = current.split('.').map { it.toInt() }
        for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
            val latestPart = latestParts.getOrElse(i) { 0 }
            val currentPart = currentParts.getOrElse(i) { 0 }
            if (latestPart > currentPart) return true
            if (latestPart < currentPart) return false
        }
        return false
    }
}