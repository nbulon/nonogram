package com.trainpaths.nonogram.classes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `#` filled, `X` crossed, `.` untouched. */
private fun String.toCells(): List<TileState> = map { char ->
    when (char) {
        '#' -> TileState.FILLED
        'X' -> TileState.CROSSED
        else -> TileState.NONE
    }
}

private data class ClueProgressTestData(
    val desc: String,
    val clues: List<Int>,
    val line: String,
    /** Indices of the clues expected to be struck out. */
    val struck: List<Int>,
)

private val clueProgressData = arrayOf(
    ClueProgressTestData(
        desc = "a full line is sealed by both edges",
        clues = listOf(5),
        line = "#####",
        struck = listOf(0),
    ),
    ClueProgressTestData(
        desc = "one cell short leaves the run open",
        clues = listOf(5),
        line = "####.",
        struck = emptyList(),
    ),
    ClueProgressTestData(
        desc = "a run with blanks around it is not sealed",
        clues = listOf(3),
        line = ".###.",
        struck = emptyList(),
    ),
    ClueProgressTestData(
        desc = "the same run, sealed by crosses",
        clues = listOf(3),
        line = "X###X",
        struck = listOf(0),
    ),
    ClueProgressTestData(
        desc = "a sealed 1 takes the 1, not a 3",
        clues = listOf(3, 1, 3, 2),
        line = "....X#X........",
        struck = listOf(1),
    ),
    ClueProgressTestData(
        desc = "a sealed 1 then a sealed 3 takes the 1 and the second 3",
        clues = listOf(3, 1, 3, 2),
        line = "....X#X###X....",
        struck = listOf(1, 2),
    ),
    ClueProgressTestData(
        desc = "a sealed 3 at the start takes the first 3",
        clues = listOf(3, 1, 3, 2),
        line = "X###X..........",
        struck = listOf(0),
    ),
    ClueProgressTestData(
        // The first 3 cannot be here — 1, 3, 2 no longer fit after it — but the second 3 can:
        // 3 and 1 fit exactly in the five cells to its left.
        desc = "a sealed 3 too far right for the first 3 takes the second",
        clues = listOf(3, 1, 3, 2),
        line = ".....X###X.....",
        struck = listOf(2),
    ),
    ClueProgressTestData(
        desc = "a sealed 3 no clue can reach strikes nothing",
        clues = listOf(3, 1, 3, 2),
        line = "..........X###X",
        struck = emptyList(),
    ),
    ClueProgressTestData(
        desc = "an open run consumes its clue without striking it",
        clues = listOf(3, 1),
        line = "##...X#X",
        struck = listOf(1),
    ),
    ClueProgressTestData(
        desc = "a sealed run matching nothing stops the line",
        clues = listOf(3, 1),
        line = "X##X....",
        struck = emptyList(),
    ),
    ClueProgressTestData(
        desc = "a completed line with untouched gaps strikes nothing",
        clues = listOf(2, 2),
        line = "##.##",
        struck = emptyList(),
    ),
    ClueProgressTestData(
        desc = "the same line with the gap crossed strikes everything",
        clues = listOf(2, 2),
        line = "##X##",
        struck = listOf(0, 1),
    ),
    ClueProgressTestData(
        desc = "an empty line",
        clues = listOf(2, 2),
        line = ".....",
        struck = emptyList(),
    ),
    ClueProgressTestData(
        desc = "a line with no clues",
        clues = emptyList(),
        line = "XXXXX",
        struck = emptyList(),
    ),
)

class ClueProgressTest {
    @Test
    fun solvedClueMask_strikesOnlyCertainlyDrawnClues() {
        clueProgressData.forEach { data ->
            val expected = data.struck.fold(0L) { mask, index -> mask or (1L shl index) }

            assertEquals(
                expected = expected,
                actual = solvedClueMask(data.clues, data.line.toCells()),
                message = "Unexpected mask for ${data.desc}",
            )
        }
    }

    @Test
    fun solvedClueMask_strikesEveryClueOfASolvedLine() {
        val puzzle = Nonogram(
            id = 1,
            difficulty = Difficulty.EASY,
            solution = listOf(
                listOf(1, 1, 0, 1, 0),
                listOf(0, 0, 0, 0, 0),
                listOf(1, 0, 1, 0, 1),
                listOf(0, 1, 1, 1, 0),
                listOf(1, 1, 1, 1, 1),
            ),
        )

        // A player who crosses every blank has sealed every run in the line.
        puzzle.solution.forEachIndexed { row, cells ->
            val clues = puzzle.rowClues[row]
            val marks = cells.map { if (it == 1) TileState.FILLED else TileState.CROSSED }
            val all = (0 until clues.size).fold(0L) { mask, index -> mask or (1L shl index) }

            assertEquals(
                expected = all,
                actual = solvedClueMask(clues, marks),
                message = "Unexpected mask for solved row $row",
            )
        }
    }

    @Test
    fun autoCross_crossesTheRestOfACompletedLine() {
        val tiles = grid("#X...", ".....")
        val edits = ClueProgress(tiles, rowClues = listOf(listOf(1), listOf(1)), colClues = List(5) { listOf(1) })
            .autoCross(touched(0 to 0))

        assertEquals(listOf("#XXXX", "....."), tiles.render())
        assertEquals(listOf(2, 3, 4), edits.map { it.col })
        assertTrue(edits.all { it.row == 0 && it.before == TileState.NONE && it.after == TileState.CROSSED })
    }

    @Test
    fun autoCross_leavesAnOpenLineAlone() {
        val tiles = grid("#....")
        val edits = ClueProgress(tiles, rowClues = listOf(listOf(1)), colClues = List(5) { listOf(1) })
            .autoCross(touched(0 to 0))

        assertTrue(edits.isEmpty())
        assertEquals(listOf("#...."), tiles.render())
    }

    @Test
    fun autoCross_neverTouchesALineWithoutClues() {
        val tiles = grid("...")
        val edits = ClueProgress(tiles, rowClues = listOf(emptyList()), colClues = List(3) { emptyList() })
            .autoCross(touched(0 to 0, 0 to 1, 0 to 2))

        assertTrue(edits.isEmpty())
    }

    @Test
    fun autoCross_cascadesIntoTheLinesItCompletes() {
        // Crossing the rest of row 0 seals column 2's run, which then crosses its own last cell.
        val tiles = grid(
            "#X.",
            "..#",
            "..X",
            "...",
        )
        val edits = ClueProgress(
            tiles,
            rowClues = listOf(listOf(1), listOf(1), emptyList(), emptyList()),
            colClues = listOf(listOf(1), emptyList(), listOf(1)),
        ).autoCross(touched(0 to 0))

        assertEquals(listOf("#XX", "..#", "..X", "..X"), tiles.render())
        assertEquals(listOf(0 to 2, 3 to 2), edits.map { it.row to it.col })
    }

    @Test
    fun clueLine_maskFollowsTheTiles() {
        val tiles = grid("#X...")
        val line = ClueProgress(tiles, rowClues = listOf(listOf(1, 1)), colClues = List(5) { listOf(1) }).rows[0]
        assertEquals(0b01L, line.mask)

        tiles[0][2].state = TileState.FILLED
        tiles[0][3].state = TileState.CROSSED
        assertEquals(0b11L, line.mask)
        assertTrue(line.isComplete)
    }

    /** The stroke that triggers an auto-cross; only the lines it touched matter. */
    private fun touched(vararg cells: Pair<Int, Int>): List<TileEdit> =
        cells.map { (row, col) -> TileEdit(row, col, before = TileState.NONE, after = TileState.FILLED) }

    private fun grid(vararg rows: String): List<List<Tile>> =
        rows.map { row -> row.toCells().map { state -> Tile().apply { this.state = state } } }

    private fun List<List<Tile>>.render(): List<String> = map { row ->
        row.joinToString("") {
            when (it.state) {
                TileState.FILLED -> "#"
                TileState.CROSSED -> "X"
                TileState.NONE -> "."
            }
        }
    }
}
