package com.trainpaths.nonogram.classes

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue

/**
 * Which clues of one line the player has *certainly drawn*
 * It only reads what the player has explicitly marked, so it can never hand them a
 * deduction they had not made themselves.
 *
 * Returns a [Long] because a line holds at most `ceil(MAX_NONOGRAM_SIDE / 2)` = 30 clues.
 */
fun solvedClueMask(clues: List<Int>, cells: List<TileState>): Long {
    val k = clues.size
    val n = cells.size
    if (k == 0 || n == 0) return 0L

    val prefix = IntArray(k + 1)
    for (i in 0 until k) prefix[i + 1] = prefix[i] + clues[i]
    fun minLen(a: Int, b: Int): Int = if (b <= a) 0 else prefix[b] - prefix[a] + (b - a) - 1

    var mask = 0L
    var c = 0
    var start = 0
    while (start < n) {
        if (cells[start] != TileState.FILLED) {
            start++
            continue
        }
        var end = start
        while (end < n && cells[end] == TileState.FILLED) end++

        val isSealed = (start == 0 || cells[start - 1] == TileState.CROSSED) &&
                (end == n || cells[end] == TileState.CROSSED)
        val len = end - start

        while (c < k) {
            if (c > 0 && minLen(0, c) > start - 1) {
                c = k
                break
            }
            val matches = if (isSealed) clues[c] == len else clues[c] >= len
            val fitsRight = c + 1 >= k || minLen(c + 1, k) <= n - end - 1
            if (matches && fitsRight) break
            c++
        }
        if (c == k) break

        if (isSealed) mask = mask or (1L shl c)
        c++
        start = end
    }
    return mask
}

/** One row or column of a board: its clues, its tiles, and which of those clues are struck ([solvedClueMask]). */
class ClueLine(val clues: List<Int>, val cells: List<Tile>) {
    /** Derived state, so the gutter's strike drawing and auto-cross share one evaluation per change. */
    val mask: Long by derivedStateOf { solvedClueMask(clues, cells.map { it.state }) }

    val isComplete: Boolean get() = clues.isNotEmpty() && mask == (1L shl clues.size) - 1
}

/** The [ClueLine]s of a whole board. */
class ClueProgress(private val tiles: List<List<Tile>>, rowClues: List<List<Int>>, colClues: List<List<Int>>) {
    val rows: List<ClueLine> = rowClues.mapIndexed { row, clues -> ClueLine(clues, tiles[row]) }
    val cols: List<ClueLine> = colClues.mapIndexed { col, clues -> ClueLine(clues, tiles.map { it[col] }) }

    /**
     * Crosses the empty tiles of every complete line [edits] touched, cascading into the lines those crosses complete.
     * Mutates the tiles and returns its own edits, for the stroke's undo step.
     */
    fun autoCross(edits: List<TileEdit>): List<TileEdit> {
        val pendingRows = ArrayDeque(edits.map { it.row }.distinct())
        val pendingCols = ArrayDeque(edits.map { it.col }.distinct())
        val crosses = mutableListOf<TileEdit>()

        fun cross(row: Int, col: Int): Boolean {
            val tile = tiles[row][col]
            if (tile.state != TileState.NONE) return false
            tile.state = TileState.CROSSED
            crosses += TileEdit(row, col, before = TileState.NONE, after = TileState.CROSSED)
            return true
        }

        while (pendingRows.isNotEmpty() || pendingCols.isNotEmpty()) {
            pendingRows.removeFirstOrNull()?.let { row ->
                if (rows.getOrNull(row)?.isComplete != true) return@let
                for (col in tiles[row].indices) if (cross(row, col)) pendingCols.addLast(col)
            }
            pendingCols.removeFirstOrNull()?.let { col ->
                if (cols.getOrNull(col)?.isComplete != true) return@let
                for (row in tiles.indices) if (cross(row, col)) pendingRows.addLast(row)
            }
        }
        return crosses
    }
}
