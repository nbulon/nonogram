package com.trainpaths.nonogram

import com.trainpaths.nonogram.cache.SeedPuzzle

/**
 * Stand-in for the shipped seeds. `SEED_PUZZLES` is generated from dev Firestore and may be empty between
 * exports, so tests that need built-in puzzles to exist pin their own instead of leaning on the build's.
 */
internal val SEED_FIXTURE: List<SeedPuzzle> = listOf(
    SeedPuzzle(
        id = 4_215_003_001L,
        name = "Plus",
        difficulty = "EASY",
        rows = """
            00100
            00100
            11111
            00100
            00100
        """.trimIndent(),
    ),
    SeedPuzzle(
        id = 4_215_003_002L,
        name = null,
        difficulty = "MEDIUM",
        rows = """
            11111
            10000
            11100
            10000
            11111
        """.trimIndent(),
    ),
)
