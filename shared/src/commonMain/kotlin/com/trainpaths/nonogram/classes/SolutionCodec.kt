package com.trainpaths.nonogram.classes

import kotlinx.serialization.json.Json

private val json = Json

/**
 * Encoding for solutions and saved boards. `hasPublishConflict` matches rows on this exact string
 */
internal fun List<List<Int>>.toSolutionJson(): String = json.encodeToString(this)

/**
 * Decode: null when the string is not a grid at all, with the reason handed to [onError].
 * Shape is the caller's business — a stored grid and a grid off the wire are held to different rules.
 */
internal fun String.toSolutionOrNull(onError: (String) -> Unit = {}): List<List<Int>>? =
    try {
        json.decodeFromString<List<List<Int>>>(this)
    } catch (e: Exception) {
        onError(e.message.orEmpty())
        null
    }

/** The seed encoding: one row per line, `0`/`1` per cell. Throws on anything else — seeds are compile-time data. */
internal fun String.toBinaryGrid(): List<List<Int>> =
    lines().map { row ->
        row.map { c ->
            require(c == '0' || c == '1') { "cell '$c' in row \"$row\"" }
            c - '0'
        }
    }
