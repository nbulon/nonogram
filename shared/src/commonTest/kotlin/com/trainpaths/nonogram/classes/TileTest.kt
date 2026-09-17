package com.trainpaths.nonogram.classes

import kotlin.test.Test
import kotlin.test.assertEquals

class TileTest {

    @Test
    fun initialState_isNone() {
        val tile = Tile()
        assertEquals(TileState.NONE, tile.state)
    }

    @Test
    fun click_defaultsToFillAndIsIdempotent() {
        val tile = Tile()
        tile.click()
        assertEquals(TileState.FILLED, tile.state)
        repeat(3) { tile.click() }
        assertEquals(TileState.FILLED, tile.state)
    }

    @Test
    fun click_withExplicitMode_writesThatStateFromAnyStartingState() {
        val targets = mapOf(
            DrawMode.FILL to TileState.FILLED,
            DrawMode.CROSS to TileState.CROSSED,
            DrawMode.ERASE to TileState.NONE,
        )

        for ((mode, target) in targets) {
            for (start in TileState.entries) {
                val tile = Tile().apply { state = start }
                tile.click(mode)
                assertEquals(target, tile.state, "$mode from $start")
            }
        }
    }

    @Test
    fun click_repeatedInSameMode_neverLeavesThatState() {
        for (mode in DrawMode.entries) {
            val tile = Tile()
            repeat(3) { tile.click(mode) }
            assertEquals(mode.target, tile.state, "$mode")
        }
    }

    @Test
    fun mouseDrawMode_primaryTogglesFillAndCross() {
        assertEquals(DrawMode.FILL, mouseDrawMode(TileState.NONE, secondary = false))
        assertEquals(DrawMode.CROSS, mouseDrawMode(TileState.FILLED, secondary = false))
        assertEquals(DrawMode.FILL, mouseDrawMode(TileState.CROSSED, secondary = false))
    }

    @Test
    fun mouseDrawMode_secondaryAlwaysErases() {
        for (start in TileState.entries) {
            assertEquals(DrawMode.ERASE, mouseDrawMode(start, secondary = true), "$start")
        }
    }

    @Test
    fun boardShortcuts_useDistinctTriggers() {
        assertEquals(BoardShortcut.entries.size, BoardShortcut.entries.map { it.trigger }.toSet().size)
    }

    @Test
    fun boardShortcuts_coverBothMouseButtons() {
        assertEquals(
            BoardTrigger.MouseButton.entries.toSet(),
            BoardShortcut.entries.map { it.trigger }.filterIsInstance<BoardTrigger.MouseButton>().toSet(),
        )
    }

    @Test
    fun toSolutionInts_countsOnlyFilled() {
        assertEquals(
            listOf(listOf(1, 0, 0), listOf(0, 1, 0)),
            mixedBoard().toSolutionInts(),
        )
    }

    @Test
    fun toSolutionInts_preservesShapeOfAnEmptyOrRaggedBoard() {
        assertEquals(emptyList<List<Int>>(), emptyList<List<Tile>>().toSolutionInts())
        assertEquals(
            listOf(listOf(0, 0), listOf(0)),
            listOf(List(2) { Tile() }, List(1) { Tile() }).toSolutionInts(),
        )
    }

    @Test
    fun toProgressInts_keepsCrosses() {
        assertEquals(
            listOf(listOf(1, 2, 0), listOf(0, 1, 2)),
            mixedBoard().toProgressInts(),
        )
    }

    @Test
    fun progressInts_roundTripEveryState() {
        val states = TileState.entries.toList()
        val board = listOf(states.map { Tile().apply { state = it } })

        assertEquals(
            listOf(states),
            board.toProgressInts().map { row -> row.map(::progressIntToTileState) },
        )
    }

    @Test
    fun progressIntToTileState_fallsBackToNoneOnAnythingElse() {
        for (value in listOf(0, 3, -1, 42)) {
            assertEquals(TileState.NONE, progressIntToTileState(value), "value $value")
        }
    }

    private fun mixedBoard(): List<List<Tile>> = listOf(
        listOf(TileState.FILLED, TileState.CROSSED, TileState.NONE),
        listOf(TileState.NONE, TileState.FILLED, TileState.CROSSED),
    ).map { row -> row.map { Tile().apply { state = it } } }
}
