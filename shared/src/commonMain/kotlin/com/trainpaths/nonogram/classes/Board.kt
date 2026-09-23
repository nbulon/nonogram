package com.trainpaths.nonogram.classes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.trainpaths.nonogram.BOARD_SEPARATOR_COLOR
import com.trainpaths.nonogram.icons.expand_content
import com.trainpaths.nonogram.tutorial.TutorialStep
import com.trainpaths.nonogram.tutorial.tutorialAnchor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

@Composable
fun Board(
    nonogram: Nonogram,
    tiles: List<List<Tile>>,
    isLocked: Boolean,
    modifier: Modifier = Modifier,
    isEditable: Boolean = true,
    drawMode: DrawMode = DrawMode.FILL,
    clueProgress: ClueProgress? = null,
    state: BoardTransformState = remember(nonogram.width, nonogram.height) { BoardTransformState() },
    onTilesChanged: () -> Unit = {},
    onEdits: (List<TileEdit>) -> Unit = {},
) {
    // Clues key on the nonogram *object*: GenViewModel builds a new Nonogram on every tap, and the
    // clues must recompute so they update live as the user draws.
    val rowClues = remember(nonogram) { nonogram.rowClues }
    val colClues = remember(nonogram) { nonogram.colClues }
    // maxOfOrNull: a zero-size nonogram yields no clue lines at all, and maxOf would throw.
    val maxRowClues = remember(rowClues) { rowClues.maxOfOrNull { it.size } ?: 1 }
    val maxColClues = remember(colClues) { colClues.maxOfOrNull { it.size } ?: 1 }
    // cacheSize 32: the default of 8 thrashes on a board with more than a handful of block labels.
    val labelMeasurer = rememberTextMeasurer(cacheSize = 32)

    // The transform keys on *dimensions*: in GenScreen the nonogram identity changes on every tap
    // while the size does not, and re-fitting the view mid-drawing would snap the board around. Both
    // GenScreen takes the default; GameScreen hoists it so Check can fit the board as well as mark it.
    val currentTiles = rememberUpdatedState(tiles)
    val currentIsLocked = rememberUpdatedState(isLocked)
    val currentIsEditable = rememberUpdatedState(isEditable)
    val currentDrawMode = rememberUpdatedState(drawMode)
    val currentOnTilesChanged = rememberUpdatedState(onTilesChanged)
    val currentOnEdits = rememberUpdatedState(onEdits)

    // Gutters are sized by the thin CLUE_CELL, not CELL
    val gutterW = CLUE_CELL * maxRowClues
    val gutterH = CLUE_CELL * maxColClues
    val gridW = CELL * nonogram.width
    val gridH = CELL * nonogram.height
    val background = MaterialTheme.colorScheme.background

    // safeContentPadding sits outside the clip, so the safe area is the viewport and the clip rect.
    BoxWithConstraints(modifier.safeContentPadding()) {
        val density = LocalDensity.current
        val cellPx = with(density) { CELL.toPx() }
        val cluePx = with(density) { CLUE_CELL.toPx() }
        val borderPx = with(density) { TILE_BORDER.toPx() }
        val separatorContentPx = with(density) { BOARD_SEPARATOR.toPx() }

        state.updateGeometry(
            viewportW = if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat() else 0f,
            viewportH = if (constraints.hasBoundedHeight) constraints.maxHeight.toFloat() else 0f,
            cellPx = cellPx,
            cluePx = cluePx,
            tileBorderPx = borderPx,
            separatorContentPx = separatorContentPx,
            rows = nonogram.height,
            cols = nonogram.width,
            maxRowClues = maxRowClues,
            maxColClues = maxColClues,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .boardGestures(
                    state = state,
                    isLocked = { currentIsLocked.value },
                    tiles = { currentTiles.value },
                    isEditable = { currentIsEditable.value },
                    drawMode = { start, secondary -> resolveDrawMode(currentDrawMode.value, start, secondary) },
                    onTilesChanged = { currentOnTilesChanged.value() },
                    onEdits = { currentOnEdits.value(it) },
                ),
            contentAlignment = Alignment.TopStart,
        ) {
            // Each region is measured at its unscaled size (requiredSize beats the viewport
            // constraints), placed at (0,0), and moved into place by its own layer matrix.
            Canvas(
                modifier = Modifier
                    .oversized(gridW, gridH)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = state.scale
                        scaleY = state.scale
                        translationX = state.gridTx
                        translationY = state.gridTy
                    },
            ) {
                drawTiles(currentTiles.value, cellPx, borderPx, state.scale)
            }

            // Block-index labels, pinned to the visible field edge
            Spacer(
                modifier = Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawBlockLabels(state, nonogram.width, nonogram.height, cellPx, labelMeasurer)
                    },
            )

            RowClueGutter(
                state = state,
                clues = rowClues,
                lines = clueProgress?.rows,
                slots = maxRowClues,
                gutterW = gutterW,
                gridH = gridH,
            )

            ColClueGutter(
                state = state,
                clues = colClues,
                lines = clueProgress?.cols,
                slots = maxColClues,
                gridW = gridW,
                gutterH = gutterH,
            )

            BoardFrame(state = state, background = background)
        }

        RezoomButton(state = state)
    }
}

/**
 * Every pointer node the board owns The callbacks are read through lambdas rather than captured: a
 * `pointerInput` block outlives the composition that created it, the values have to be fetched
 * when the gesture runs, not when the modifier was built.
 */
private fun Modifier.boardGestures(
    state: BoardTransformState,
    isLocked: () -> Boolean,
    tiles: () -> List<List<Tile>>,
    isEditable: () -> Boolean,
    drawMode: (start: TileState, secondary: Boolean) -> DrawMode,
    onTilesChanged: () -> Unit,
    onEdits: (List<TileEdit>) -> Unit,
): Modifier = this
    // Order matters. Compose dispatches Main innermost-outward, and the last modifier
    // is the innermost, the transform detector sees each event first. Once it passes
    // touch slop it consumes, and the tap detector cancels
    .pointerInput(state) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                if (event.type != PointerEventType.Scroll) continue
                val change = event.changes.firstOrNull() ?: continue
                // Browsers report line/page deltas; clamp so one flick isn't a 100x zoom.
                val notches = change.scrollDelta.y.coerceIn(-3f, 3f)
                if (notches == 0f) continue
                state.zoomBy(SCROLL_ZOOM_PER_NOTCH.pow(-notches), change.position)
                change.consume()
            }
        }
    }
    .pointerInput(state) {
        detectBoardTaps(
            onTap = { position, secondary ->
                if (!isEditable()) return@detectBoardTaps
                val hit = state.hitTest(position) ?: return@detectBoardTaps
                val tile = tiles()
                    .getOrNull(hit.row)?.getOrNull(hit.col) ?: return@detectBoardTaps
                val before = tile.state
                tile.click(drawMode(before, secondary))
                if (tile.state != before) {
                    onEdits(
                        listOf(TileEdit(hit.row, hit.col, before = before, after = tile.state)),
                    )
                }
                onTilesChanged()
            },
        )
    }
    // Final pass, after every other node has had its say: once no pointer is down the
    // gesture is over, so the next drag re-picks whether it moves the board or a gutter.
    .pointerInput(state) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                if (event.changes.none { it.pressed }) state.endGesture()
            }
        }
    }
    .pointerInput(state) {
        detectTransformGestures(panZoomLock = false) { centroid, pan, zoom, _ ->
            state.applyTransformGesture(centroid, pan, zoom)
        }
    }
    // Last means innermost on the Main pass: this detector gets first refusal and consumes a
    // committed stroke before transform sees it. The lock is read per gesture, not keyed on, so
    // toggling it mid-session never restarts the detector.
    .pointerInput(state) {
        detectBoardDrawGestures(
            state = state,
            tiles = tiles,
            isLocked = isLocked,
            isEditable = isEditable,
            drawMode = drawMode,
            onTilesChanged = onTilesChanged,
            onEdits = onEdits,
        )
    }

/**
 * Lays the content out at its true [width] x [height] while reporting a size that fits the incoming
 * constraints.
 *
 * `Box` sizes itself to `max(constraints.minWidth, largestChild.width)` and never coerces back down
 * to `maxWidth`, so a bare `requiredSize` child would drag the parent out to the full content size —
 * defeating `clipToBounds`, displacing the parent under any centring alignment, and shifting the
 * pointer-input coordinate space out from under `hitTest`.
 */
private fun Modifier.oversized(width: Dp, height: Dp): Modifier =
    this.wrapContentSize(Alignment.TopStart, unbounded = true).requiredSize(width, height)

/**
 * The row-clue gutter: a viewport-pinned window (the clip) onto a column of clue lines that scales
 * and pans with the grid rows they label.
 */
@Composable
private fun RowClueGutter(
    state: BoardTransformState,
    clues: List<List<Int>>,
    lines: List<ClueLine>?,
    slots: Int,
    gutterW: Dp,
    gridH: Dp,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithContent {
                val left = state.rowGutterTx
                clipRect(left = left, right = left + state.rowClueWindowW) {
                    this@drawWithContent.drawContent()
                }
            },
    ) {
        Column(
            modifier = Modifier
                .oversized(gutterW, gridH)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = state.scale
                    scaleY = state.scale
                    translationX = state.rowGutterTx + state.clueScrollX
                    translationY = state.gridTy
                },
        ) {
            // One tile tall, so clue lines stay flush with the rows they label.
            clues.forEachIndexed { index, lineClues ->
                RowClueLine(
                    clues = lineClues,
                    line = lines?.getOrNull(index),
                    slots = slots,
                    gutterW = gutterW,
                )
            }
        }
    }
}

/** [RowClueGutter] on the other axis. */
@Composable
private fun ColClueGutter(
    state: BoardTransformState,
    clues: List<List<Int>>,
    lines: List<ClueLine>?,
    slots: Int,
    gridW: Dp,
    gutterH: Dp,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithContent {
                val top = state.colHeaderTy
                clipRect(top = top, bottom = top + state.colClueWindowH) {
                    this@drawWithContent.drawContent()
                }
            },
    ) {
        Row(
            modifier = Modifier
                .oversized(gridW, gutterH)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = state.scale
                    scaleY = state.scale
                    translationX = state.gridTx
                    translationY = state.colHeaderTy + state.clueScrollY
                },
        ) {
            // One tile wide, so clue lines stay flush with the columns they label.
            clues.forEachIndexed { index, lineClues ->
                ColClueLine(
                    clues = lineClues,
                    line = lines?.getOrNull(index),
                    slots = slots,
                    gutterH = gutterH,
                )
            }
        }
    }
}

/**
 * One clue line — a single white block spanning the whole gutter, with the numbers spaced across it.
 * The seam belongs between *lines*, not between individual numbers.
 *
 * Numbers occupy equal weighted slots so they stay column-aligned across lines; short lines are
 * padded with empty slots at the start, which right-aligns row clues against the grid.
 */
@Composable
private fun RowClueLine(clues: List<Int>, line: ClueLine?, slots: Int, gutterW: Dp) {
    Row(
        modifier = Modifier
            .requiredSize(gutterW, CELL)
            .background(Color.White)
            .clueStrikes(clues, line, slots, vertical = false),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(slots - clues.size) { Spacer(Modifier.weight(1f)) }
        for (clue in clues) {
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                ClueText(clue)
            }
        }
    }
}

@Composable
private fun ColClueLine(clues: List<Int>, line: ClueLine?, slots: Int, gutterH: Dp) {
    Column(
        modifier = Modifier
            .requiredSize(CELL, gutterH)
            .background(Color.White)
            .clueStrikes(clues, line, slots, vertical = true),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        repeat(slots - clues.size) { Spacer(Modifier.weight(1f)) }
        for (clue in clues) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                ClueText(clue)
            }
        }
    }
}

/** Strikes through the clues of one line that the player has certainly drawn ([ClueLine.mask]).*/
private fun Modifier.clueStrikes(
    clues: List<Int>,
    line: ClueLine?,
    slots: Int,
    vertical: Boolean,
): Modifier {
    if (line == null || clues.isEmpty()) return this
    return drawWithContent {
        drawContent()
        val mask = line.mask
        if (mask == 0L) return@drawWithContent

        val slotW = if (vertical) size.width else size.width / slots
        val slotH = if (vertical) size.height / slots else size.height
        // The short side is CLUE_CELL in either gutter, so one fraction fits both axes.
        val minor = min(slotW, slotH)
        val half = minor * CLUE_STRIKE_LENGTH_FRACTION / 2f
        val strokeWidth = minor * CLUE_STRIKE_WIDTH_FRACTION
        for (index in clues.indices) {
            if ((mask and (1L shl index)) == 0L) continue
            // Short lines are padded with leading Spacers, so clue i sits in slot slots - size + i.
            val slot = slots - clues.size + index
            val cx = if (vertical) slotW / 2f else (slot + 0.5f) * slotW
            val cy = if (vertical) (slot + 0.5f) * slotH else slotH / 2f
            // Struck off diagonally, bottom-left to top-right.
            drawLine(
                color = Color.DarkGray,
                start = Offset(cx - half, cy + half),
                end = Offset(cx + half, cy - half),
                strokeWidth = strokeWidth,
            )
        }
    }
}

@Composable
private fun ClueText(value: Int) {
    Text(
        text = value.toString(),
        style = MaterialTheme.typography.titleLarge.copy(
            fontSize = CLUE_FONT_SIZE,
            lineHeight = CLUE_FONT_SIZE,
            fontWeight = CLUE_FONT_WEIGHT,
        ),
        color = Color.Black,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

/**
 * Drawn last, over everything. Two jobs:
 *  1. Mask the corner. With both axes panned the row gutter slides under the column header and
 *     would paint row clues into the corner, and vice versa.
 *  2. Draw the left and top edges of the playing field's frame. These are pinned to the gutters, so
 *     they live in viewport px — but their *width* still scales with the board
 *     ([BoardTransformState.separatorScreenPx]), floored so they never thin away.
 *
 * Reads the transform in the draw phase, and contributes no pointer node.
 */
@Composable
private fun BoardFrame(state: BoardTransformState, background: Color) {
    Spacer(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val s = state.scale
                val frame = state.separatorScreenPx
                val gridLeft = state.rowGutterTx + state.rowGutterWindowW
                val gridTop = state.colHeaderTy + state.colHeaderWindowH
                if (gridLeft > 0f && gridTop > 0f) {
                    drawRect(background, Offset.Zero, Size(gridLeft, gridTop))
                }

                val gridRight = (state.gridTx + state.gridWpx * s).coerceAtMost(size.width)
                val gridBottom = (state.gridTy + state.gridHpx * s).coerceAtMost(size.height)

                if (gridBottom > gridTop) {
                    drawRect(
                        color = BOARD_SEPARATOR_COLOR,
                        topLeft = Offset(gridLeft - frame, gridTop - frame),
                        size = Size(frame, gridBottom - gridTop + frame),
                    )
                }
                if (gridRight > gridLeft) {
                    drawRect(
                        color = BOARD_SEPARATOR_COLOR,
                        topLeft = Offset(gridLeft - frame, gridTop - frame),
                        size = Size(gridRight - gridLeft + frame, frame),
                    )
                }
            },
    )
}

/** Fit-to-screen, floating over the board's top-left while the board is zoomed in. */
@Composable
private fun RezoomButton(state: BoardTransformState) {
    val visible by remember(state) { derivedStateOf { state.canReset } }
    if (!visible) return

    Box(
        modifier = Modifier
            .padding(REZOOM_EDGE_INSET)
            .size(REZOOM_SIDE)
            .tutorialAnchor(TutorialStep.BOARD_ZOOM)
            .clip(MaterialTheme.shapes.small)
            .background(REZOOM_SCRIM)
            .clickable { state.reset() }
            .semantics { contentDescription = "Zoom out to fit" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = expand_content,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondary,
            modifier = Modifier.fillMaxSize().padding(REZOOM_ICON_INSET),
        )
    }
}

/** Sheer enough that the grid under the button still reads. */
private val REZOOM_SCRIM = Color.Black.copy(alpha = 0.4f)

private val REZOOM_ICON_INSET = 4.dp

/**
 * Draws the whole grid into one node.
 *
 * Reading `tile.state` (and `tile.wrong`) here registers a *draw-scope* dependency, so filling a tile
 * invalidates the draw of this single node — no recomposition, no relayout. That is far cheaper than
 * the 2500 layout nodes a per-tile Box grid would need merely to exist.
 *
 * [scale] is the layer's scale, and drawing depends on it: this node paints in *content* px and the
 * layer matrix scales the result, so every stroke is a multiple of [lineUnitPx] — proportional to the
 * zoom while it can be, one device pixel once it cannot.
 */
private fun DrawScope.drawTiles(tiles: List<List<Tile>>, cellPx: Float, borderPx: Float, scale: Float) {
    val rows = tiles.size
    val cols = tiles.firstOrNull()?.size ?: 0
    if (rows == 0 || cols == 0 || scale <= 0f) return

    val width = cols * cellPx
    val height = rows * cellPx
    val unit = lineUnitPx(scale, borderPx)
    val thin = unit
    val thick = unit * BLOCK_LINE_UNITS
    val frame = unit * SEPARATOR_UNITS
    val crossInset = cellPx * 0.22f
    val crossStroke = max(cellPx / 14f, LINE_MIN_DEVICE_PX / scale)

    drawRect(Color.White, Offset.Zero, Size(width, height))

    for (row in tiles.indices) {
        val top = row * cellPx
        for (column in tiles[row].indices) {
            val left = column * cellPx
            when (tiles[row][column].state) {
                TileState.FILLED -> drawRect(Color.Black, Offset(left, top), Size(cellPx, cellPx))

                TileState.CROSSED -> {
                    val near = crossInset
                    val far = cellPx - crossInset
                    drawLine(
                        color = Color.Black,
                        start = Offset(left + near, top + near),
                        end = Offset(left + far, top + far),
                        strokeWidth = crossStroke,
                    )
                    drawLine(
                        color = Color.Black,
                        start = Offset(left + far, top + near),
                        end = Offset(left + near, top + far),
                        strokeWidth = crossStroke,
                    )
                }

                TileState.NONE -> {}
            }
        }
    }

    // Gridlines last, over the tiles. Every fifth is heavy with number
    for (column in 1 until cols) {
        val w = if (column % BLOCK_SIZE == 0) thick else thin
        val x = column * cellPx
        drawLine(Color.Gray, Offset(x, 0f), Offset(x, height), strokeWidth = w)
    }
    for (row in 1 until rows) {
        val w = if (row % BLOCK_SIZE == 0) thick else thin
        val y = row * cellPx
        drawLine(Color.Gray, Offset(0f, y), Offset(width, y), strokeWidth = w)
    }

    val wrongStroke = max(cellPx * 0.10f, LINE_MIN_DEVICE_PX / scale)
    val wrongInset = wrongStroke / 2f
    for (row in tiles.indices) {
        val top = row * cellPx
        for (column in tiles[row].indices) {
            if (!tiles[row][column].wrong) continue
            drawRect(
                color = Color.Red,
                topLeft = Offset(column * cellPx + wrongInset, top + wrongInset),
                size = Size(cellPx - wrongStroke, cellPx - wrongStroke),
                style = Stroke(width = wrongStroke),
            )
        }
    }

    // The playing field's right and bottom edges
    drawLine(BOARD_SEPARATOR_COLOR, Offset(width, 0f), Offset(width, height), strokeWidth = frame)
    drawLine(BOARD_SEPARATOR_COLOR, Offset(0f, height), Offset(width, height), strokeWidth = frame)
}

/**
 * Draws the every-[BLOCK_SIZE]th line's index number, pinned to the *visible* right/bottom edge of
 * the field rather than the field's own edge — so the ruler stays legible no matter how far the
 * board is panned, including once the last row/column has scrolled off-screen.
 */
private fun DrawScope.drawBlockLabels(
    state: BoardTransformState,
    cols: Int,
    rows: Int,
    cellPx: Float,
    labelMeasurer: TextMeasurer,
) {
    val s = state.scale
    if (s <= 0f || cols == 0 || rows == 0) return

    val cell = cellPx * s
    val fontPx = cellPx * BLOCK_LABEL_FONT_FRACTION * s
    if (fontPx < BLOCK_LABEL_MIN_DEVICE_PX) return
    val inset = cellPx * BLOCK_LABEL_INSET_FRACTION * s

    val labelStyle = TextStyle(
        color = Color.DarkGray,
        fontSize = fontPx.toSp(),
        lineHeight = fontPx.toSp(),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both,
        ),
    )

    val outline = Stroke(
        width = fontPx * BLOCK_LABEL_OUTLINE_FRACTION,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round,
    )
    fun drawLabel(label: TextLayoutResult, topLeft: Offset) {
        drawText(label, color = Color.White, topLeft = topLeft, drawStyle = outline)
        drawText(label, topLeft = topLeft)
    }

    val anchorX = (state.gridTx + state.gridWpx * s).coerceAtMost(size.width).coerceAtLeast(state.gridTx)
    val anchorY = (state.gridTy + state.gridHpx * s).coerceAtMost(size.height).coerceAtLeast(state.gridTy)

    for (column in 1 until cols) {
        if (column % BLOCK_SIZE != 0) continue
        val x = state.gridTx + column * cell
        if (x < 0f || x > size.width) continue // line itself is off-screen; nothing to label
        val label = labelMeasurer.measure(column.toString(), labelStyle)
        drawLabel(label, Offset(x - inset - label.size.width, anchorY - inset - label.size.height))
    }
    for (row in 1 until rows) {
        if (row % BLOCK_SIZE != 0) continue
        val y = state.gridTy + row * cell
        if (y < 0f || y > size.height) continue
        val label = labelMeasurer.measure(row.toString(), labelStyle)
        drawLabel(label, Offset(anchorX - inset - label.size.width, y - inset - label.size.height))
    }
}
