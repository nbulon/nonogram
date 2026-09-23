package com.trainpaths.nonogram.classes

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Size of a tile in content space. Zoom is a transform, not a resize. */
val CELL = 40.dp

val CLUE_CELL = 34.dp

val CLUE_FONT_SIZE = 24.sp
val CLUE_FONT_WEIGHT = FontWeight.Bold

/**
 * Nominal width of the thinnest line on the board — the grid line between two tiles.
 *
 * Every other line is a multiple of it, so the hierarchy hairline < block line < separator is stated
 * once and holds at every zoom. See [lineUnitPx].
 */
val TILE_BORDER = 1.dp

/** Rows and columns per block: the interval at which a heavy grid line is drawn. */
const val BLOCK_SIZE = 5

/** Weight of the heavy grid line drawn every [BLOCK_SIZE] rows and columns, in [lineUnitPx] units. */
const val BLOCK_LINE_UNITS = 2f

/** Weight of the divider between the clue gutters and the playing field, in [lineUnitPx] units. */
const val SEPARATOR_UNITS = 3f

/** Height of a block-index label, as a fraction of [CELL]. Sized off the cell so it scales with the board. */
const val BLOCK_LABEL_FONT_FRACTION = 0.30f

/** Weight of the white outline carrying a block-index label, as a fraction of its font size. */
const val BLOCK_LABEL_OUTLINE_FRACTION = 0.18f

/** Gap between a block-index label and the lines it is tucked against, as a fraction of [CELL]. */
const val BLOCK_LABEL_INSET_FRACTION = 0.12f

/** Below this rendered height the labels are illegible mush, so they are skipped entirely. */
const val BLOCK_LABEL_MIN_DEVICE_PX = 7f

/** No line ever renders thinner than this, or it antialiases into nothing. */
const val LINE_MIN_DEVICE_PX = 1f

/** Extent on each axis of the diagonal struck through a solved clue, as a fraction of [CLUE_CELL] */
const val CLUE_STRIKE_LENGTH_FRACTION = 0.7f

/** Weight of that line, as a fraction of [CLUE_CELL]. */
const val CLUE_STRIKE_WIDTH_FRACTION = 0.07f

/** Content-space width the divider reserves between the clue gutters and the playing field. */
val BOARD_SEPARATOR = TILE_BORDER * SEPARATOR_UNITS

/** Side of the rezoom control, which floats over the board rather than fitting inside the corner. */
val REZOOM_SIDE = 36.dp

/** Its inset from the board's top-left, inside the safe area. */
val REZOOM_EDGE_INSET = 4.dp

/** Zoom ceiling, as a multiple of the larger of [BoardTransformState.fitScale] and 1x. */
private const val MAX_ZOOM_MULTIPLE = 3f

/** Relative slack on the fit comparison, so a scale a rounding error above fit still reads as fitted. */
private const val FIT_EPSILON = 0.001f

/** Multiplicative zoom per scroll-wheel notch. */
const val SCROLL_ZOOM_PER_NOTCH = 1.15f

/** A clue gutter may never occupy more than this fraction of the viewport along its own axis. */
private const val GUTTER_MAX_FRACTION = 0.35f

/**
 * Width of one line unit, in *content* px, at a given layer [scale].
 *
 * A line width that is constant is wrong at some zoom, and wrong in opposite directions depending on
 * the space the constant lives in. Held constant in screen px it is a slab next to a zoomed-out tile
 * and a hairline next to a zoomed-in one. Held constant in content px it scales correctly but falls
 * below one device pixel once `scale < 1 / tileBorderPx` and antialiases away — unevenly, since
 * whether a line lands on a pixel boundary depends on its index.
 *
 * So the width is a function of scale with a floor: proportional above the crossover, exactly
 * [LINE_MIN_DEVICE_PX] below it. A stroke of `w` content px renders at `w * scale` device px, which
 * makes `LINE_MIN_DEVICE_PX / scale` precisely the content width of one device pixel.
 */
fun lineUnitPx(scale: Float, tileBorderPx: Float): Float =
    max(tileBorderPx, LINE_MIN_DEVICE_PX / scale)

data class TileCoord(val row: Int, val col: Int)

/**
 * One drag-paint stroke. Its target is picked once from the starting tile and every tile is visited
 * at most once, so crossing back over part of a stroke never advances those cells again.
 */
internal class TileStroke private constructor(
    private val tiles: List<List<Tile>>,
    private val targetState: TileState,
) {
    private val visited = mutableSetOf<TileCoord>()
    private val edits = mutableListOf<TileEdit>()
    fun edits(): List<TileEdit> = edits.toList()

    /** Paints a batch and reports whether at least one tile actually changed. */
    fun paint(coords: Iterable<TileCoord>): Boolean {
        var changed = false
        for (coord in coords) {
            if (!visited.add(coord)) continue
            val tile = tiles.getOrNull(coord.row)?.getOrNull(coord.col) ?: continue
            if (tile.state != targetState) {
                edits.add(TileEdit(coord.row, coord.col, before = tile.state, after = targetState))
                tile.state = targetState
                changed = true
            }
        }
        return changed
    }

    companion object {
        fun begin(
            tiles: List<List<Tile>>,
            start: TileCoord,
            mode: DrawMode = DrawMode.FILL,
        ): TileStroke? {
            tiles.getOrNull(start.row)?.getOrNull(start.col) ?: return null
            return TileStroke(tiles = tiles, targetState = mode.target)
        }
    }
}

/** Which part of the board a viewport position falls in. Drives gesture routing. */
enum class BoardRegion { GRID, ROW_GUTTER, COL_HEADER, CORNER }

/**
 * Pan/zoom transform for [Board], mapping a content plane larger than the viewport onto it.
 *
 * Content coordinates have their origin at the top-left of the (empty) corner cell, so the clue
 * gutters occupy `[0, gutterWpx] x [0, gutterHpx]` and the tile grid starts at
 * `(gutterWpx, gutterHpx)`. The map to viewport pixels is `v = p * scale + offset`.
 *
 * Nothing exposed here is read during composition — only inside `graphicsLayer {}` lambdas,
 * `drawBehind`, and gesture coroutines — so writes invalidate layers and draws but never trigger
 * recomposition. That is what keeps pan/zoom smooth on a 3600-tile board.
 */
@Stable
class BoardTransformState {

    var viewportW by mutableFloatStateOf(0f); private set
    var viewportH by mutableFloatStateOf(0f); private set
    var cellPx by mutableFloatStateOf(0f); private set
    var gutterWpx by mutableFloatStateOf(0f); private set
    var gutterHpx by mutableFloatStateOf(0f); private set
    var gridWpx by mutableFloatStateOf(0f); private set
    var gridHpx by mutableFloatStateOf(0f); private set

    /** Nominal hairline width. Sets the crossover below which lines stop scaling. See [lineUnitPx]. */
    var tileBorderPx by mutableFloatStateOf(0f); private set

    /** Width the gutter/grid divider reserves in the content plane, so it scales with everything else. */
    var separatorContentPx by mutableFloatStateOf(0f); private set

    private var rows = 0
    private var cols = 0
    private var initialised = false

    /** Set once the user zooms or pans by hand; until then the board re-fits as the gutters grow. */
    private var userAdjusted = false

    /** Region the in-flight drag started in, so crossing a boundary mid-drag doesn't switch targets. */
    private var activeRegion: BoardRegion? = null

    var scale by mutableFloatStateOf(1f); private set
    var offsetX by mutableFloatStateOf(0f); private set
    var offsetY by mutableFloatStateOf(0f); private set

    /** Scroll of each gutter within its own window, in viewport px. Always <= 0. */
    var clueScrollX by mutableFloatStateOf(0f); private set
    var clueScrollY by mutableFloatStateOf(0f); private set

    /**
     * Content-space extent the gutters reserve, capped at the grid's own extent.
     *
     * A clue-dense board wants `gutterWpx` up to `ceil(width / 2) * CLUE_CELL`, which would shove the
     * grid off-screen. Capping at `gridWpx` bounds the gutter to half the content plane, and since
     * the content maps into the viewport at [fitScale], that is at most half the viewport — the
     * requirement — whenever the board is not panned. Clues beyond the cap are reached by scrolling
     * the gutter ([clueScrollX]).
     */
    val visibleGutterWpx: Float get() = min(gutterWpx, gridWpx)
    val visibleGutterHpx: Float get() = min(gutterHpx, gridHpx)

    val contentW: Float get() = visibleGutterWpx + separatorContentPx + gridWpx
    val contentH: Float get() = visibleGutterHpx + separatorContentPx + gridHpx

    /**
     * On-screen width of the divider. Scales with the board like every other line, but never thinner
     * than [SEPARATOR_UNITS] device px — the same floor the grid lines get, so the 1:2:4 hierarchy
     * survives all the way down to fit on a 50x50.
     */
    val separatorScreenPx: Float
        get() = SEPARATOR_UNITS * max(tileBorderPx * scale, LINE_MIN_DEVICE_PX)

    /**
     * On-screen extent of each gutter, *including* the separator that terminates it.
     *
     * Two bounds. It is never wider than the gutter itself, and never narrower than the distance to
     * the grid's actual left edge — the latter is what stops the `GUTTER_MAX_FRACTION` clamp from
     * opening a strip of background between the clues and the grid they label. While the gutter is
     * unpinned, `gridTx - rowGutterTx` *is* the gutter's own width, so the clamp cannot bite. It
     * takes over only once the board is panned far enough that the grid slides underneath, and there
     * narrowing the window reveals grid rather than a gap. The two branches meet continuously.
     */
    val rowGutterWindowW: Float
        get() = min(
            (visibleGutterWpx + separatorContentPx) * scale,
            max(viewportW * GUTTER_MAX_FRACTION, gridTx - rowGutterTx),
        )
    val colHeaderWindowH: Float
        get() = min(
            (visibleGutterHpx + separatorContentPx) * scale,
            max(viewportH * GUTTER_MAX_FRACTION, gridTy - colHeaderTy),
        )

    /**
     * The part of the window the clues may actually paint into: the gutter window less the divider.
     * The divider is *reserved*, not overlaid — at fit on a large board the whole gutter is only a few
     * pixels wide, and a divider drawn on top of it would erase the clues entirely. Below the
     * crossover the drawn width outruns the reserved `separatorContentPx * scale` by at most
     * [SEPARATOR_UNITS] px, which this clip absorbs.
     */
    val rowClueWindowW: Float get() = max(0f, rowGutterWindowW - separatorScreenPx)
    val colClueWindowH: Float get() = max(0f, colHeaderWindowH - separatorScreenPx)

    /** Most-negative scroll: the gutter's far end (the clues nearest the grid) flush with the window. */
    private val minClueScrollX: Float get() = min(0f, rowClueWindowW - gutterWpx * scale)
    private val minClueScrollY: Float get() = min(0f, colClueWindowH - gutterHpx * scale)

    /** Scale at which the whole board — grid, both gutters, and the separator — is exactly inscribed. */
    val fitScale: Float
        get() = if (contentW <= 0f || contentH <= 0f || viewportW <= 0f || viewportH <= 0f) 1f
        else min(viewportW / contentW, viewportH / contentH)

    val minScale: Float get() = fitScale
    val maxScale: Float get() = max(fitScale, 1f) * MAX_ZOOM_MULTIPLE

    val canReset: Boolean get() = scale > fitScale * (1f + FIT_EPSILON)

    /** Viewport placement of the tile grid: after the *capped* gutter and its separator. */
    val gridTx: Float get() = (visibleGutterWpx + separatorContentPx) * scale + offsetX
    val gridTy: Float get() = (visibleGutterHpx + separatorContentPx) * scale + offsetY

    /**
     * Viewport placement of the pinned gutters. `max(0f, ...)` is sticky-header behaviour: the
     * gutter sits at its natural position while its axis is fully visible (otherwise a gap would
     * open between the gutter and the grid it labels), and hard-pins to the edge the moment the
     * content starts to leave the viewport.
     */
    val rowGutterTx: Float get() = max(0f, offsetX)
    val colHeaderTy: Float get() = max(0f, offsetY)

    /**
     * Pushes geometry in from composition. Idempotent; nothing written here is read during
     * composition, so this is safe to call from a composable and — unlike `onSizeChanged` — it
     * produces a correct *first* frame with no fit-scale flash.
     *
     * The first usable geometry fits and centres the board. Later changes (window resize, or a clue
     * gutter growing as the user draws in GenScreen) re-clamp but deliberately preserve the user's
     * zoom.
     */
    fun updateGeometry(
        viewportW: Float,
        viewportH: Float,
        cellPx: Float,
        cluePx: Float,
        tileBorderPx: Float,
        separatorContentPx: Float,
        rows: Int,
        cols: Int,
        maxRowClues: Int,
        maxColClues: Int,
    ) {
        val gw = maxRowClues * cluePx
        val gh = maxColClues * cluePx
        val grW = cols * cellPx
        val grH = rows * cellPx
        if (this.viewportW == viewportW && this.viewportH == viewportH &&
            this.gutterWpx == gw && this.gutterHpx == gh &&
            this.gridWpx == grW && this.gridHpx == grH &&
            this.tileBorderPx == tileBorderPx && this.separatorContentPx == separatorContentPx
        ) return

        this.viewportW = viewportW
        this.viewportH = viewportH
        this.cellPx = cellPx
        this.tileBorderPx = tileBorderPx
        this.separatorContentPx = separatorContentPx
        this.rows = rows
        this.cols = cols
        this.gutterWpx = gw
        this.gutterHpx = gh
        this.gridWpx = grW
        this.gridHpx = grH

        val usable = viewportW > 0f && viewportH > 0f && contentW > 0f && contentH > 0f
        if (!usable) return

        if (!initialised || !userAdjusted) {
            // First layout, or the content grew (a clue gutter widening as the user draws in
            // GenScreen) while the view is still the untouched default. Re-fit rather than let the
            // board silently outgrow the viewport.
            initialised = true
            reset()
        } else {
            apply(scale.coerceIn(minScale, maxScale), offsetX, offsetY)
        }
    }

    /** An axis that fits entirely is force-centred rather than draggable. */
    private fun clampX(x: Float, s: Float): Float {
        val w = contentW * s
        return if (w <= viewportW) (viewportW - w) / 2f else x.coerceIn(viewportW - w, 0f)
    }

    private fun clampY(y: Float, s: Float): Float {
        val h = contentH * s
        return if (h <= viewportH) (viewportH - h) / 2f else y.coerceIn(viewportH - h, 0f)
    }

    private fun apply(s: Float, x: Float, y: Float) {
        scale = s
        offsetX = clampX(x, s)
        offsetY = clampY(y, s)
        // The scroll range depends on scale, so re-clamp whenever the transform moves.
        clueScrollX = clueScrollX.coerceIn(minClueScrollX, 0f)
        clueScrollY = clueScrollY.coerceIn(minClueScrollY, 0f)
    }

    /** Fit and centre, and hand control of re-fitting back to [updateGeometry]. */
    fun reset() {
        userAdjusted = false
        apply(fitScale, 0f, 0f)
        // Show the clues nearest the grid — the ones you read first.
        clueScrollX = minClueScrollX
        clueScrollY = minClueScrollY
    }

    /** Scrolls the row-clue gutter horizontally within its window. */
    fun panClueX(dx: Float) {
        clueScrollX = (clueScrollX + dx).coerceIn(minClueScrollX, 0f)
    }

    /** Scrolls the column-clue header vertically within its window. */
    fun panClueY(dy: Float) {
        clueScrollY = (clueScrollY + dy).coerceIn(minClueScrollY, 0f)
    }

    private fun panBoard(dx: Float, dy: Float) {
        if (dx == 0f && dy == 0f) return
        userAdjusted = true
        apply(scale, offsetX + dx, offsetY + dy)
    }

    /** Zoom about [anchor], keeping the content point under it fixed. */
    fun zoomBy(factor: Float, anchor: Offset) {
        val old = scale
        val new = (old * factor).coerceIn(minScale, maxScale)
        if (new == old) return
        userAdjusted = true
        val k = new / old
        apply(new, anchor.x - (anchor.x - offsetX) * k, anchor.y - (anchor.y - offsetY) * k)
    }

    /**
     * Pan, then zoom about the pinch centroid.
     *
     * A pure drag starting inside a gutter scrolls *that gutter* along its own axis, and pans the
     * board along the other — the gutter tracks the grid on its cross-axis, so this stays coherent.
     * A pinch always transforms the board, wherever it starts.
     */
    fun applyTransformGesture(centroid: Offset, pan: Offset, zoom: Float) {
        val old = scale
        val new = (old * zoom).coerceIn(minScale, maxScale)

        if (new == old) {
            val region = activeRegion ?: regionAt(centroid).also { activeRegion = it }
            when (region) {
                BoardRegion.ROW_GUTTER -> {
                    panClueX(pan.x)
                    panBoard(0f, pan.y)
                }

                BoardRegion.COL_HEADER -> {
                    panClueY(pan.y)
                    panBoard(pan.x, 0f)
                }

                BoardRegion.CORNER -> {
                    panClueX(pan.x)
                    panClueY(pan.y)
                }

                BoardRegion.GRID -> panBoard(pan.x, pan.y)
            }
            return
        }

        // A pinch always transforms the board, and pins the rest of the gesture to doing so.
        activeRegion = BoardRegion.GRID
        userAdjusted = true
        val k = new / old
        val px = offsetX + pan.x
        val py = offsetY + pan.y
        apply(new, centroid.x - (centroid.x - px) * k, centroid.y - (centroid.y - py) * k)
    }

    /** Called when every pointer has lifted, so the next drag re-picks its region. */
    fun endGesture() {
        activeRegion = null
    }

    /** Classifies a viewport position against the *windowed* gutters. */
    fun regionAt(v: Offset): BoardRegion {
        val inRowGutter = v.x < rowGutterTx + rowGutterWindowW
        val inColHeader = v.y < colHeaderTy + colHeaderWindowH
        return when {
            inRowGutter && inColHeader -> BoardRegion.CORNER
            inRowGutter -> BoardRegion.ROW_GUTTER
            inColHeader -> BoardRegion.COL_HEADER
            else -> BoardRegion.GRID
        }
    }

    /** Maps a viewport position to a tile, or null for gutters, corner, or empty space. */
    fun hitTest(v: Offset): TileCoord? {
        val s = scale
        if (s <= 0f || cellPx <= 0f || rows == 0 || cols == 0) return null

        // Reject the frozen chrome, including any tile occluded by a pinned gutter.
        if (regionAt(v) != BoardRegion.GRID) return null

        val gx = (v.x - gridTx) / s
        val gy = (v.y - gridTy) / s
        if (gx < 0f || gy < 0f || gx >= gridWpx || gy >= gridHpx) return null

        val col = floor(gx / cellPx).toInt()
        val row = floor(gy / cellPx).toInt()
        if (row !in 0 until rows || col !in 0 until cols) return null
        return TileCoord(row, col)
    }

    /**
     * Hit-tests a whole pointer segment rather than only its latest sample.
     *
     * Pointer events can be farther apart than one rendered cell during a fast stroke. Sampling at
     * half a rendered cell guarantees ordinary horizontal, vertical, and diagonal strokes cannot
     * jump over an intervening tile.
     */
    internal fun hitTestSegment(from: Offset, to: Offset): List<TileCoord> {
        val sampleDistance = cellPx * scale / 2f
        if (sampleDistance <= 0f || !sampleDistance.isFinite()) return emptyList()

        val delta = to - from
        val steps = max(1, ceil(delta.getDistance() / sampleDistance).toInt())
        return buildList {
            var previous: TileCoord? = null
            for (step in 0..steps) {
                val position = from + delta * (step.toFloat() / steps)
                val coord = hitTest(position)
                if (coord != null && coord != previous) {
                    add(coord)
                    previous = coord
                }
            }
        }
    }
}

/**
 * `awaitFirstDown(requireUnconsumed = true)`, but handing back the event: the mouse button lives on
 * [PointerEvent.buttons], which the change alone does not carry.
 */
private suspend fun AwaitPointerEventScope.awaitFirstDownEvent(): PointerEvent {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Main)
        if (event.changes.all { it.changedToDown() }) return event
    }
}

/**
 * Detects one-pointer drawing without interfering with taps or pinch zoom. A stroke commits only
 * while locked or from the secondary mouse button — an unlocked primary drag is left to the
 * transform detector to pan.
 */
internal suspend fun PointerInputScope.detectBoardDrawGestures(
    state: BoardTransformState,
    tiles: () -> List<List<Tile>>,
    isLocked: () -> Boolean,
    isEditable: () -> Boolean,
    drawMode: (start: TileState, secondary: Boolean) -> DrawMode,
    onTilesChanged: () -> Unit,
    onEdits: (List<TileEdit>) -> Unit = {},
) {
    val slop = viewConfiguration.touchSlop

    awaitPointerEventScope {
        while (true) {
            val downEvent = awaitFirstDownEvent()
            val down = downEvent.changes.first()
            val secondary = downEvent.buttons.isSecondaryPressed
            if (!isLocked() && !secondary) continue

            var previousPosition = down.position
            var committed = false
            var surrenderedToPinch = false
            var stroke: TileStroke? = null

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val tracked = event.changes.firstOrNull { it.id == down.id }

                if (!committed && event.changes.any { it.id != down.id && it.pressed }) {
                    surrenderedToPinch = true
                }

                if (!committed && !surrenderedToPinch && tracked != null &&
                    (tracked.position - down.position).getDistance() > slop
                ) {
                    committed = true
                    if (isEditable()) {
                        val board = tiles()
                        stroke = state.hitTest(down.position)?.let { start ->
                            val startState = board.getOrNull(start.row)?.getOrNull(start.col)?.state
                                ?: return@let null
                            TileStroke.begin(board, start, drawMode(startState, secondary))
                        }
                    }
                }

                if (committed) {
                    // The drawing detector is the innermost Main-pass node. Consuming here prevents
                    // the transform detector from panning the board for this one-pointer gesture.
                    event.changes.forEach { it.consume() }

                    var changed = false
                    if (tracked != null) {
                        stroke?.let {
                            changed = it.paint(state.hitTestSegment(previousPosition, tracked.position))
                        }
                        previousPosition = tracked.position
                    }
                    if (changed) onTilesChanged()
                }

                if (event.changes.none { it.pressed }) {
                    stroke?.let { onEdits(it.edits()) }
                    break
                }
            }
        }
    }
}

/**
 * Tap detector for the board.
 *
 * Unlike `detectTapGestures`, [onTap] fires immediately on UP rather than being deferred by the
 * ~300ms double-tap timeout — filling a tile must not feel laggy.
 *
 * Never consumes, so a sibling `detectTransformGestures` is unaffected. Cancels its pending tap
 * whenever that detector consumes — i.e. once the drag passes touch slop — which is what makes
 * "drag pans, tap toggles" work with no explicit threshold of our own. Reports which mouse button
 * went down, read at the down since the up no longer holds it.
 */
suspend fun PointerInputScope.detectBoardTaps(
    onTap: (position: Offset, secondary: Boolean) -> Unit,
) {
    val slop = viewConfiguration.touchSlop

    awaitPointerEventScope {
        while (true) {
            val downEvent = awaitFirstDownEvent()
            val down = downEvent.changes.first()
            val secondary = downEvent.buttons.isSecondaryPressed
            var cancelled = false
            var up: PointerInputChange? = null

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                if (event.changes.size > 1) cancelled = true // second finger: a pinch, not a tap
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null) {
                    cancelled = true
                    break
                }
                if (change.isConsumed) cancelled = true // the transform detector took over
                if ((change.position - down.position).getDistance() > slop) cancelled = true
                if (!change.pressed) {
                    up = change
                    break
                }
            }

            val u = up
            if (!cancelled && u != null) onTap(u.position, secondary)
        }
    }
}
