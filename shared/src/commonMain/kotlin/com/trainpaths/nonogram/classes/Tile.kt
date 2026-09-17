package com.trainpaths.nonogram.classes

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.trainpaths.nonogram.hasMouseAndKeyboard

enum class TileState {
    NONE,
    FILLED,
    CROSSED,
}

enum class DrawMode {
    FILL,
    CROSS,
    ERASE;

    /** The state this mode writes. Constant per mode — an edit never depends on the cell's current state. */
    val target: TileState
        get() = when (this) {
            FILL -> TileState.FILLED
            CROSS -> TileState.CROSSED
            ERASE -> TileState.NONE
        }
}

/**
 * A mouse edit carries its mode in the button: secondary erases, primary toggles fill/cross off the
 * tile it started on. The one edit path that reads a tile's existing state — resolved *before*
 * [DrawMode.target], so a stroke still fixes its target once.
 */
fun mouseDrawMode(start: TileState, secondary: Boolean): DrawMode = when {
    secondary -> DrawMode.ERASE
    start == TileState.FILLED -> DrawMode.CROSS
    else -> DrawMode.FILL
}

/** The one answer to "what does this edit write?": the pencil on touch platforms, the mouse button elsewhere. */
fun resolveDrawMode(pencil: DrawMode, start: TileState, secondary: Boolean): DrawMode =
    if (hasMouseAndKeyboard) mouseDrawMode(start, secondary) else pencil

class Tile {
    private var _state by mutableStateOf(TileState.NONE)

    var wrong by mutableStateOf(false)

    var state: TileState
        get() = _state
        set(value) {
            if (value == _state) return
            _state = value
            wrong = false
        }

    fun click(mode: DrawMode = DrawMode.FILL) {
        state = mode.target
    }
}

fun List<List<Tile>>.toSolutionInts(): List<List<Int>> =
    map { row -> row.map { if (it.state == TileState.FILLED) 1 else 0 } }

fun List<List<Tile>>.toProgressInts(): List<List<Int>> =
    map { row -> row.map { it.state.toProgressInt() } }

fun TileState.toProgressInt(): Int = when (this) {
    TileState.NONE -> 0
    TileState.FILLED -> 1
    TileState.CROSSED -> 2
}

fun progressIntToTileState(value: Int): TileState = when (value) {
    1 -> TileState.FILLED
    2 -> TileState.CROSSED
    else -> TileState.NONE
}

