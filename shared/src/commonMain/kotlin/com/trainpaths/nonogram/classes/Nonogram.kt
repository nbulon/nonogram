package com.trainpaths.nonogram.classes

import kotlinx.serialization.Serializable

enum class Difficulty(val label: String) {
    EASY("Easy"),
    MEDIUM("Medium"),
    HARD("Hard"),
    HARDCORE("Hardcore"),
}

/**
 * Where a puzzle stands in the publish-review flow; see `docs/publish-moderation.md`.
 *
 * The ordinal is what the `status` column stores, so entries may be appended but never reordered
 * or removed without a migration — which is why [VALID] sits last rather than beside [NONE].
 *
 * [DELETED] is a Firestore-only tombstone: written when the author deletes a puzzle, so a stale
 * copy on another device cannot push it back to life, and removed locally on pull — never stored.
 */
enum class PublishStatus {
    NONE,
    PENDING,
    DENIED,
    UNLISTED,
    APPROVED,
    VALID,
    DELETED,
}

const val MAX_NONOGRAM_NAME_LENGTH = 30
const val UNNAMED_NONOGRAM_TITLE = "???"
const val NAME_HAS_SYMBOLS_MESSAGE = "Emoji and symbols aren't allowed."
const val NAME_NOT_ALLOWED_MESSAGE = "This name isn't allowed."

const val MIN_NONOGRAM_SIDE = 5
const val MAX_NONOGRAM_SIDE = 60

/**
 * Rectangular and non-empty — the shape every clue computation assumes. [Nonogram.colClues]
 * indexes `solution[row][col]` across the *first* row's width, so a ragged grid throws there.
 */
fun List<List<Int>>.isRectangularGrid(): Boolean {
    val width = firstOrNull()?.size ?: return false
    return width > 0 && all { it.size == width }
}

/** [isRectangularGrid], with both sides inside [MIN_NONOGRAM_SIDE]..[MAX_NONOGRAM_SIDE]. */
fun List<List<Int>>.isWellFormedGrid(): Boolean =
    isRectangularGrid() &&
            size in MIN_NONOGRAM_SIDE..MAX_NONOGRAM_SIDE &&
            first().size in MIN_NONOGRAM_SIDE..MAX_NONOGRAM_SIDE

@Serializable
data class Nonogram(
    val id: Long,
    val difficulty: Difficulty,
    val solution: List<List<Int>>,
    val name: String? = null,
    val authorUid: String = "",
    val updatedAt: Long = 0,
    val publishStatus: PublishStatus = PublishStatus.NONE,
) {
    val isPublic: Boolean get() = publishStatus == PublishStatus.APPROVED

    /**
     * The stored Solver verdict — see [PublishStatus]. Every state past [PublishStatus.NONE] was
     * reached by passing the Solver, so the status *is* the verdict; [isValid] recomputes it.
     */
    val isKnownValid: Boolean get() = publishStatus != PublishStatus.NONE

    val height: Int get() = solution.size
    val width: Int get() = solution.firstOrNull()?.size ?: 0
    val longSide: Int get() = maxOf(width, height)

    val rowClues: List<List<Int>> by lazy {
        solution.map { row -> computeLineClues(row) }
    }
    val colClues: List<List<Int>> by lazy {
        (0 until width).map { col ->
            computeLineClues((0 until height).map { row -> solution[row][col] })
        }
    }
    val isValid: Boolean by lazy {
        Solver(this).solveNonogram().map { row -> row.map { if (it == 1) 1 else 0 } } == solution
    }

    /** Seeded puzzles and puzzles authored elsewhere both carry a blank uid, so "" owns nothing. */
    fun isOwned(uid: String?): Boolean = uid != null && authorUid.isNotEmpty() && authorUid == uid
}

private fun computeLineClues(line: List<Int>): List<Int> {
    val clues = mutableListOf<Int>()
    var run = 0
    for (cell in line) {
        if (cell == 1) run++
        else if (run > 0) {
            clues.add(run)
            run = 0
        }
    }
    if (run > 0) clues.add(run)
    return clues
}

fun sanitizeNameInput(value: String): String =
    value
        .replace('\n', ' ')
        .replace('\r', ' ')
        .take(MAX_NONOGRAM_NAME_LENGTH)

/** The stored form: [sanitizeNameInput] plus a trim, with a name left blank stored as null. */
fun normalizeNonogramName(value: String): String? =
    sanitizeNameInput(value.trim()).takeIf { it.isNotEmpty() }

/** Why [name] may not be used, as the message to show, or null when it is fine. A null name is fine. */
fun nameControl(name: String?): String? = when {
    name == null -> null
    name.any { it.isSymbolOrEmoji() } -> NAME_HAS_SYMBOLS_MESSAGE
    containsBlockedTerm(name) -> NAME_NOT_ALLOWED_MESSAGE
    else -> null
}

private fun Char.isSymbolOrEmoji(): Boolean =
    isHighSurrogate() || isLowSurrogate() ||
            code in 0x2600..0x27BF || code in 0x2B00..0x2BFF ||
            code == 0xFE0F || code == 0x200D ||
            category in INVISIBLE_CATEGORIES

private val INVISIBLE_CATEGORIES = setOf(
    CharCategory.CONTROL,
    CharCategory.FORMAT,
    CharCategory.PRIVATE_USE,
    CharCategory.UNASSIGNED,
)
