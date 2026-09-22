package com.trainpaths.nonogram.update

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

private const val LATEST_RELEASE = "https://api.github.com/repos/nbulon/nonogram/releases/latest"
private val TIMEOUT = Duration.ofSeconds(5)
private val json = Json { ignoreUnknownKeys = true }

/** A release newer than the running build, and the installer to hand the user. */
class AvailableUpdate(val latest: String, val current: String, val installerUrl: String)

@Serializable
private class LatestRelease(@SerialName("tag_name") val tagName: String = "")

/** Whether GitHub Releases carries a desktop build newer than this one. */
object UpdateCheck {

    var available: AvailableUpdate? by mutableStateOf(null)
        private set

    private val client by lazy {
        HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
    }

    fun dismiss() {
        available = null
    }

    suspend fun check(currentVersion: String, isProd: Boolean) {
        if (!isProd) return
        val installer = installerUrl() ?: return
        val latest = fetchLatestVersion() ?: return
        if (isNewer(latest, currentVersion)) {
            available = AvailableUpdate(latest, currentVersion, installer)
        }
    }

    /**
     * Bounded by the client's own connect and request timeouts rather than [kotlinx.coroutines.withTimeout]:
     * `send` blocks its thread, so there is no suspension point for a coroutine timeout to act on.
     */
    private suspend fun fetchLatestVersion(): String? = withContext(Dispatchers.IO) {
        try {
            val request = HttpRequest.newBuilder(URI(LATEST_RELEASE))
                .header("Accept", "application/vnd.github+json")
                // The GitHub API answers 403 to a request that sends no User-Agent
                .header("User-Agent", "nonogram-desktop")
                .timeout(TIMEOUT)
                .GET()
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                println("Update: the release check answered ${response.statusCode()}")
                return@withContext null
            }
            parseDesktopTag(json.decodeFromString<LatestRelease>(response.body()).tagName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("Update: checking for a new release failed: ${e.message}")
            null
        }
    }
}
