package com.trainpaths.nonogram.seed

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.readText

/**
 * Field names, mirroring `sync/FirestoreSchema.kt` — that object is `internal` to `:shared`, which this
 * module cannot see. Checked by eye against it, the way the web externals already are.
 */
internal object Fields {
    const val DIFFICULTY = "difficulty"
    const val SOLUTION = "solution"
    const val NAME = "name"
    const val PUBLISH_STATUS = "publishStatus"
}

internal const val NONOGRAMS = "nonograms"
internal const val APPROVED = "APPROVED"

/** A `nonograms/{id}` document, as far as the exporter cares. */
internal data class RemoteDoc(
    val id: String,
    val difficulty: String?,
    val solution: String?,
    val name: String?,
)

internal data class FirebaseEnv(val projectId: String, val apiKey: String)

/**
 * Reads the project id and API key out of the web app's committed `FirebaseWebConfig`, so the exporter
 * cannot drift from the environment the web build talks to. Both values are public client config.
 */
internal fun readEnv(root: Path, env: String): FirebaseEnv {
    val file = root.resolve("webApp/src/$env/kotlin/com/trainpaths/nonogram/FirebaseConfig.web.kt")
    val text = runCatching { file.readText() }
        .getOrElse { error("no FirebaseWebConfig for env '$env' at $file") }
    fun constant(name: String) = Regex("""const val $name\s*=\s*"([^"]+)"""").find(text)?.groupValues?.get(1)
        ?: error("$file has no $name")
    return FirebaseEnv(constant("PROJECT_ID"), constant("API_KEY"))
}

private val json = Json { ignoreUnknownKeys = true }

/**
 * Every approved puzzle, in one unpaged query — the same shape `SyncService.jvm.kt` uses. Filtering on
 * `publishStatus` alone keeps this off the `(publishStatus, updatedAt)` composite index.
 *
 * Approved documents are readable unauthenticated (the console rules say so), so the public API key is all
 * the credential there is.
 */
internal fun fetchApproved(env: FirebaseEnv): List<RemoteDoc> {
    val url = "https://firestore.googleapis.com/v1/projects/${env.projectId}" +
            "/databases/(default)/documents:runQuery?key=${env.apiKey}"
    val body = """
        {"structuredQuery":{
          "from":[{"collectionId":"$NONOGRAMS"}],
          "where":{"fieldFilter":{"field":{"fieldPath":"${Fields.PUBLISH_STATUS}"},
                   "op":"EQUAL","value":{"stringValue":"$APPROVED"}}}}}
    """.trimIndent()

    val request = HttpRequest.newBuilder(URI.create(url))
        .header("Content-Type", "application/json")
        .timeout(Duration.ofSeconds(60))
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build()
    val response = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build()
        .send(request, HttpResponse.BodyHandlers.ofString())
    check(response.statusCode() == 200) {
        "Firestore returned ${response.statusCode()}: ${response.body().take(500)}"
    }

    val results = json.parseToJsonElement(response.body()) as? JsonArray
        ?: error("unexpected response shape: ${response.body().take(200)}")
    return results.mapNotNull { entry ->
        val document = entry.jsonObject["document"]?.jsonObject ?: return@mapNotNull null
        val fields = document["fields"]?.jsonObject ?: JsonObject(emptyMap())
        RemoteDoc(
            id = document.getValue("name").jsonPrimitive.content.substringAfterLast('/'),
            difficulty = fields.stringField(Fields.DIFFICULTY),
            solution = fields.stringField(Fields.SOLUTION),
            name = fields.stringField(Fields.NAME),
        )
    }
}

/** REST wraps every value in its type: `{"stringValue": "…"}`. */
private fun JsonObject.stringField(name: String): String? =
    this[name]?.jsonObject?.get("stringValue")?.jsonPrimitive?.content
