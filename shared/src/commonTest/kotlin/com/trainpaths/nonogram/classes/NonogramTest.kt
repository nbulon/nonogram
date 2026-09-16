package com.trainpaths.nonogram.classes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private data class NonogramTestData(
    val desc: String,
    val nonogram: Nonogram,
    val expectedRowClues: List<List<Int>>? = null,
    val expectedColClues: List<List<Int>>? = null,
    val verifySolver: Boolean = false,
)

private val nonogramData = arrayOf(
    NonogramTestData(
        desc = "solver sample",
        nonogram = Nonogram(
            id = 1,
            difficulty = Difficulty.EASY,
            solution = listOf(
                listOf(0, 0, 0, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0),
                listOf(0, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0),
                listOf(0, 0, 1, 0, 0, 0, 1, 1, 1, 1, 1, 0, 0, 0),
                listOf(0, 0, 0, 1, 0, 1, 1, 1, 1, 1, 1, 1, 0, 0),
                listOf(0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0),
                listOf(0, 0, 0, 0, 1, 0, 0, 1, 0, 0, 1, 1, 1, 0),
                listOf(0, 0, 0, 0, 0, 1, 1, 0, 1, 1, 0, 1, 1, 0),
                listOf(0, 0, 0, 0, 0, 0, 1, 0, 0, 1, 0, 1, 1, 0),
                listOf(1, 1, 0, 0, 0, 1, 1, 0, 1, 1, 0, 1, 1, 1),
                listOf(1, 1, 1, 0, 1, 0, 0, 1, 0, 0, 0, 1, 1, 1),
                listOf(1, 1, 1, 0, 0, 0, 0, 0, 0, 1, 1, 0, 1, 1),
                listOf(1, 0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 1, 0, 1),
                listOf(0, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1, 1, 0, 1),
                listOf(0, 1, 0, 1, 0, 1, 0, 0, 0, 1, 1, 1, 0, 1),
                listOf(0, 0, 1, 0, 1, 1, 1, 1, 1, 0, 0, 0, 0, 1),
            ),
        ),
        verifySolver = true,
    ),
    NonogramTestData(
        desc = "ritter sample",
        nonogram = Nonogram(
            id = 0,
            difficulty = Difficulty.HARD,
            solution = listOf(
                listOf(0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
                listOf(0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
                listOf(0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
                listOf(0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 1, 1, 1, 0, 0, 0, 0, 0),
                listOf(0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 1, 0, 0, 0, 0, 0),
                listOf(0, 1, 1, 1, 1, 1, 1, 0, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0),
                listOf(0, 1, 1, 1, 1, 1, 0, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 1, 1, 1, 0, 1, 1, 0, 0),
                listOf(0, 1, 1, 1, 1, 1, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 1, 1, 1, 0, 1, 1, 1, 0, 0),
                listOf(1, 1, 1, 1, 1, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1, 1, 0, 1, 0, 0),
                listOf(1, 1, 1, 1, 1, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1, 1, 1, 0, 1, 0, 0),
                listOf(1, 1, 1, 1, 1, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1, 1, 1, 0, 1, 0, 1, 1, 0),
                listOf(0, 1, 1, 1, 1, 0, 0, 1, 1, 1, 0, 0, 0, 0, 1, 1, 1, 0, 1, 0, 1, 0, 1, 1, 0),
                listOf(0, 1, 1, 1, 1, 1, 0, 1, 1, 1, 0, 1, 1, 0, 1, 1, 1, 0, 1, 0, 0, 1, 0, 1, 0),
                listOf(0, 1, 1, 1, 1, 1, 0, 1, 1, 1, 0, 1, 1, 0, 1, 1, 1, 1, 1, 1, 0, 1, 0, 1, 0),
                listOf(0, 1, 1, 1, 1, 1, 0, 1, 1, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 1, 1, 1, 1, 0),
                listOf(0, 0, 1, 1, 1, 1, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0),
                listOf(0, 0, 1, 1, 1, 1, 0, 1, 1, 1, 1, 1, 0, 1, 1, 1, 0, 1, 1, 1, 1, 1, 1, 1, 1),
                listOf(0, 0, 1, 1, 1, 1, 0, 1, 1, 1, 1, 1, 1, 0, 1, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1),
                listOf(0, 0, 1, 1, 1, 0, 0, 1, 1, 1, 1, 1, 1, 0, 0, 1, 1, 0, 1, 0, 0, 1, 0, 1, 1),
                listOf(0, 0, 1, 1, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1, 1, 1, 0, 1, 0, 0, 1, 0),
                listOf(0, 1, 1, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 1, 1, 1, 0, 1, 1, 0),
                listOf(0, 1, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1, 1, 1, 0, 0),
                listOf(0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0),
                listOf(0, 0, 0, 0, 0, 0, 1, 1, 1, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0),
                listOf(0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0),
                listOf(0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 0, 1, 1, 1, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0),
                listOf(0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0),
                listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0),
                listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0),
                listOf(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0),
            ),
        ),
        verifySolver = true,
    ),
    NonogramTestData(
        desc = "all-zero row",
        nonogram = Nonogram(
            id = 2,
            difficulty = Difficulty.EASY,
            solution = listOf(listOf(0, 0, 0)),
        ),
        expectedRowClues = listOf(emptyList()),
    ),
    NonogramTestData(
        desc = "all-filled row",
        nonogram = Nonogram(
            id = 3,
            difficulty = Difficulty.EASY,
            solution = listOf(listOf(1, 1, 1)),
        ),
        expectedRowClues = listOf(listOf(3)),
    ),
    NonogramTestData(
        desc = "multiple filled runs",
        nonogram = Nonogram(
            id = 4,
            difficulty = Difficulty.EASY,
            solution = listOf(listOf(1, 1, 0, 1, 1)),
        ),
        expectedRowClues = listOf(listOf(2, 2)),
    ),
    NonogramTestData(
        desc = "single filled cell",
        nonogram = Nonogram(
            id = 5,
            difficulty = Difficulty.EASY,
            solution = listOf(listOf(1)),
        ),
        expectedRowClues = listOf(listOf(1)),
    ),
    NonogramTestData(
        desc = "single empty cell",
        nonogram = Nonogram(
            id = 6,
            difficulty = Difficulty.EASY,
            solution = listOf(listOf(0)),
        ),
        expectedRowClues = listOf(emptyList()),
    ),
    NonogramTestData(
        desc = "filled run with surrounding empty cells",
        nonogram = Nonogram(
            id = 7,
            difficulty = Difficulty.EASY,
            solution = listOf(listOf(0, 1, 1, 0)),
        ),
        expectedRowClues = listOf(listOf(2)),
    ),
    NonogramTestData(
        desc = "multiple rows",
        nonogram = Nonogram(
            id = 8,
            difficulty = Difficulty.EASY,
            solution = listOf(
                listOf(1, 0, 1),
                listOf(0, 0, 0),
                listOf(1, 1, 1),
            ),
        ),
        expectedRowClues = listOf(listOf(1, 1), emptyList(), listOf(3)),
    ),
    NonogramTestData(
        desc = "column runs",
        nonogram = Nonogram(
            id = 9,
            difficulty = Difficulty.EASY,
            solution = listOf(
                listOf(1, 0),
                listOf(1, 0),
                listOf(0, 1),
            ),
        ),
        expectedColClues = listOf(listOf(2), listOf(1)),
    ),
    NonogramTestData(
        desc = "cross pattern",
        nonogram = Nonogram(
            id = 10,
            difficulty = Difficulty.EASY,
            solution = listOf(
                listOf(0, 0, 1, 0, 0),
                listOf(0, 0, 1, 0, 0),
                listOf(1, 1, 1, 1, 1),
                listOf(0, 0, 1, 0, 0),
                listOf(0, 0, 1, 0, 0),
            ),
        ),
        expectedRowClues = listOf(listOf(1), listOf(1), listOf(5), listOf(1), listOf(1)),
        expectedColClues = listOf(listOf(1), listOf(1), listOf(5), listOf(1), listOf(1)),
    ),
    NonogramTestData(
        desc = "alternating cells",
        nonogram = Nonogram(
            id = 11,
            difficulty = Difficulty.EASY,
            solution = listOf(listOf(1, 0, 1, 0, 1)),
        ),
        expectedRowClues = listOf(listOf(1, 1, 1)),
    ),
    NonogramTestData(
        desc = "empty solution",
        nonogram = Nonogram(
            id = 12,
            difficulty = Difficulty.EASY,
            solution = emptyList(),
        ),
        expectedRowClues = emptyList(),
        expectedColClues = emptyList(),
    ),
)

class NonogramTest {

    @Test
    fun solveNonogram_returnsExpectedSolutions() {
        nonogramData.filter { it.verifySolver }.forEach { data ->
            val solver = Solver(data.nonogram)
            val deduced = solver.solveNonogram()

            assertTrue(
                deduced.none { row -> row.contains(0) },
                "Left a cell undetermined for ${data.desc}",
            )
            assertEquals(
                expected = data.nonogram.solution,
                actual = deduced.map { row -> row.map { if (it == 1) 1 else 0 } },
                message = "Unexpected solution for ${data.desc}",
            )
        }
    }

    /** The Solver reports what it could not pin down as 0, which is what the generator's Check draws. */
    @Test
    fun solveNonogram_leavesAmbiguousCellsUndetermined() {
        val ambiguous = Nonogram(
            id = 13,
            difficulty = Difficulty.EASY,
            solution = listOf(
                listOf(1, 0),
                listOf(0, 1),
            ),
        )

        assertEquals(
            expected = listOf(listOf(0, 0), listOf(0, 0)),
            actual = Solver(ambiguous).solveNonogram(),
        )
    }

    @Test
    fun isValid_isDerivedFromSolverOutput() {
        val valid = nonogramData.first { it.verifySolver }.nonogram
        val invalid = Nonogram(
            id = 13,
            difficulty = Difficulty.EASY,
            solution = listOf(
                listOf(1, 0),
                listOf(0, 1),
            ),
        )

        assertTrue(valid.isValid)
        assertFalse(invalid.isValid)
    }

    @Test
    fun isKnownValid_readsTheStoredStatus() {
        val puzzle = nonogramData.first { it.verifySolver }.nonogram

        for (status in PublishStatus.entries) {
            assertEquals(
                expected = status != PublishStatus.NONE,
                actual = puzzle.copy(publishStatus = status).isKnownValid,
                message = "isKnownValid for $status",
            )
        }
    }

    @Test
    fun clues_areComputedCorrectly() {
        nonogramData.forEach { data ->
            data.expectedRowClues?.let { expected ->
                assertEquals(
                    expected = expected,
                    actual = data.nonogram.rowClues,
                    message = "Unexpected row clues for ${data.desc}",
                )
            }
            data.expectedColClues?.let { expected ->
                assertEquals(
                    expected = expected,
                    actual = data.nonogram.colClues,
                    message = "Unexpected column clues for ${data.desc}",
                )
            }
        }
    }

    @Test
    fun dimensions_matchSolutions() {
        nonogramData.forEach { data ->
            assertEquals(
                expected = data.nonogram.solution.size,
                actual = data.nonogram.height,
                message = "Unexpected height for ${data.desc}",
            )
            assertEquals(
                expected = data.nonogram.solution.firstOrNull()?.size ?: 0,
                actual = data.nonogram.width,
                message = "Unexpected width for ${data.desc}",
            )
        }
    }

    @Test
    fun blankNameNormalizesToNull() {
        assertNull(normalizeNonogramName("   "))
    }

    @Test
    fun nameIsTrimmedAndLineBreaksBecomeSpaces() {
        assertEquals("blue whale", normalizeNonogramName("  blue\nwhale  "))
    }

    @Test
    fun nameIsLimitedToThirtyCharacters() {
        assertEquals("x".repeat(MAX_NONOGRAM_NAME_LENGTH), normalizeNonogramName("x".repeat(31)))
    }

    @Test
    fun ordinaryAndMissingNamesHaveNoProblem() {
        assertNull(nameControl(null))
        assertNull(nameControl("Blue whale"))
        assertNull(nameControl("Ünïcödé-name, 2nd try!"))
    }

    @Test
    fun blockedTermsAreRefusedHoweverTheyAreDisguised() {
        listOf("shit", "SHIT", "sh1t", "s h.i t", "bullshit", "\$hit").forEach { name ->
            assertEquals(NAME_NOT_ALLOWED_MESSAGE, nameControl(name), "'$name' should be refused")
        }
    }

    @Test
    fun innocentWordsContainingShortProfanityPass() {
        listOf("class", "cucumber", "assassin", "Fire truck").forEach { name ->
            assertNull(nameControl(name), "'$name' should be allowed")
        }
    }

    @Test
    fun emojiAndSymbolsAreRefused() {
        listOf("cat \uD83D\uDC31", "sun \u2600", "zero\u200Dwidth", "tab\there").forEach { name ->
            assertEquals(NAME_HAS_SYMBOLS_MESSAGE, nameControl(name), "'$name' should be refused")
        }
    }

    @Test
    fun isOwnedMatchesTheAuthor_butNeverTheUnauthoredBlank() {
        val mine = Nonogram(id = 1, difficulty = Difficulty.EASY, solution = listOf(listOf(1)), authorUid = "uid-7")
        val guests = Nonogram(id = 2, difficulty = Difficulty.EASY, solution = listOf(listOf(1)), authorUid = "local:1")
        val seeded = Nonogram(id = 3, difficulty = Difficulty.EASY, solution = listOf(listOf(1)))

        assertTrue(mine.isOwned("uid-7"))
        assertFalse(mine.isOwned("uid-8"))
        assertFalse(mine.isOwned(null))
        assertTrue(guests.isOwned("local:1"))
        assertFalse(guests.isOwned("local:2"))
        assertFalse(seeded.isOwned(""))
        assertFalse(seeded.isOwned("uid-7"))
    }
}
