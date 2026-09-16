package com.trainpaths.nonogram.filter

import com.trainpaths.nonogram.classes.CardStatus
import com.trainpaths.nonogram.classes.Difficulty
import com.trainpaths.nonogram.classes.cardStatus

object NonogramFilters {

    const val PERSONAL = "Personal"

    val DIFFICULTY = FilterAttribute(
        label = "Difficulty",
        options = Difficulty.entries.map { difficulty ->
            FilterOption(difficulty.label) { it.difficulty == difficulty }
        },
        ascending = compareBy { it.difficulty.ordinal },
    )

    val STATUS = FilterAttribute(
        label = "Status",
        options = CardStatus.entries.map { status ->
            FilterOption(status.label) { it.cardStatus == status }
        },
        ascending = compareBy { it.cardStatus.ordinal },
    )

    /** The generator list's entries: one user's own puzzles, so ownership is not a question there. */
    val GENERATOR: List<FilterEntry> = listOf(STATUS)

    fun forUser(authorUid: String?): List<FilterEntry> = listOf(
        DIFFICULTY,
        FilterToggle(PERSONAL) { it.isOwned(authorUid) },
    )
}
