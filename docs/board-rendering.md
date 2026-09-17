# Board rendering (pan / zoom / draw)

The interactive grid — clues, tiles, pan, zoom, and drawing — is a self-contained engine in
`shared/src/commonMain/.../classes/`:

- **`Board.kt`** — the composable + all drawing.
- **`BoardTransform.kt`** — `BoardTransformState` (the pan/zoom model), gesture detectors, and every layout constant.
- **`Tile.kt`** — `Tile` (a Compose `mutableStateOf` cell), `TileState` (NONE / FILLED / CROSSED)
  and `DrawMode` (which state an edit writes).

Used by both `GameScreen` (playing) and `GenScreen` (drawing), which host `Board` directly — the win check is
`GameScreen`'s own `onTilesChanged` comparison against the solution. This doc exists so I don't have to re-read ~1000
lines to remember how it fits together — the source has the fine-grained *why* in comments; this is the map.

## The core performance idea: draw, don't recompose

A naïve grid is one Box per tile — 3600 layout nodes for a 60×60, re-laid-out on every pan. Instead:

- **All tiles are one `Canvas`** (`drawTiles`). Reading `tile.state` inside the draw lambda registers a *draw-scope*
  dependency, so filling a tile invalidates only that one node's **draw** — no recomposition, no relayout.
- **Pan/zoom is a layer transform, not a resize.** Each region (grid, row gutter, col header) is measured once at its
  true unscaled size (`Modifier.oversized` = `wrapContentSize(unbounded) +
  requiredSize`) and moved by its own `graphicsLayer { scale/translation = state.… }`. Reading the transform *inside*
  the `graphicsLayer`/`drawBehind`/`clipRect` lambda scopes the snapshot read to the layer, so a pan invalidates
  layers/draws but **never triggers recomposition**.

> Consequence: nothing exposed by `BoardTransformState` is read during composition — only inside
> `graphicsLayer{}`, draw lambdas, and gesture coroutines. Keep it that way.

The rezoom button is the one control that reads the transform from *outside* those scopes, and it keeps the
read to a single `derivedStateOf { state.canReset }`. A pan writes only the offsets, which `canReset` never
reads, so a pan does not even re-evaluate it; a zoom re-evaluates to the same `true`. Only the flip
recomposes, and only that one node. Its size and position are constants, so it never re-measures at all —
anything else placed over the board should aim for the same.

## Layout of the `Board` composable

Inside a `BoxWithConstraints(...safeContentPadding())`, `state.updateGeometry(...)` is pushed the viewport + cell/clue
pixel sizes each frame (idempotent; produces a correct *first* frame with no fit-scale flash — unlike `onSizeChanged`).
Then a `clipToBounds` Box stacks, in draw order:

1. **Grid Canvas** — `drawTiles`: white bg, filled cells (full black rects), crossed cells (two lines), then gridlines
   on top (every `BLOCK_SIZE`th line heavy), then the field's right/bottom edge.
2. **Block-label overlay** (`Spacer` + `drawBehind`, `drawBlockLabels`) — the every-`BLOCK_SIZE`th line's index number,
   pinned to the *visible* field edge rather than the field's own edge (see
   "Line metrics" below). Its position in the stack — after the grid Canvas, before the gutters and the frame overlay —
   is what makes a label sliding under a pinned gutter get occluded, for free, by draw order; no extra culling math
   needed.
3. **Row-clue gutter** (`Column` of `RowClueLine`) — clipped to a horizontal window (`rowClueWindowW`), scrolls inside
   it via `clueScrollX`.
4. **Col-clue header** (`Row` of `ColClueLine`) — clipped to a vertical window, scrolls via `clueScrollY`.
5. **Frame overlay** (`Spacer` + `drawBehind`) — masks the corner where gutters would overlap when both axes are panned,
   and paints the field's *left/top* divider edges (the grid Canvas paints the matching right/bottom, so the field ends
   framed on all four sides).

`RezoomButton` is *not* in that stack. It is a sibling of the clipping Box, drawn after it: fixed at
`REZOOM_SIDE` and pinned to the board's top-left, so the board's clip can never cut it and it never
re-measures, and hit-tested ahead of every board gesture node, so pressing it cannot start a pan or a stroke.
It floats over the grid and gutters on a translucent black scrim rather than trying to fit inside the corner —
sizing it to the corner rect was the first design, and at fit on a large board that rect is a few dp across.
It shows only while `state.canReset`.

Clue gutters use the thin `CLUE_CELL` (22.dp) along their own axis, not `CELL` (40.dp), because a row of width W holds
up to `ceil(W/2)` clues — at full cell size the gutter would eat half the screen.

## `BoardTransformState`

Maps a content plane (origin at the top-left of the empty corner cell) to the viewport:
`v = p * scale + offset`. Everything is `mutableFloatStateOf` with `private set`.

- **Fit / re-fit gate.** `fitScale` inscribes the whole board (grid + both gutters + separator).
  `reset()` fits, centres, and clears `userAdjusted`. `updateGeometry` re-fits **only while
  `!userAdjusted`** — so as the clue gutter grows while you draw in GenScreen the board keeps fitting, but once you
  pan/zoom by hand your zoom is preserved. Any pan/zoom sets `userAdjusted = true`.
- **Gutter windowing.** Gutters are "sticky headers": pinned at `max(0f, offset)` and clipped to a window capped at
  `GUTTER_MAX_FRACTION` (0.35) of the viewport; clues past the cap are reached by scrolling the gutter
  (`clueScroll{X,Y}`, always ≤ 0). `visibleGutterWpx` caps the reserved gutter at the grid's own width so it can't shove
  the grid off-screen.
- **`canReset`** — is the board zoomed in past fit? That, and only that, shows the rezoom button. Not
  `userAdjusted`, which a clamp-swallowed pan sets. Offsets need no check of their own: at `fitScale` both
  axes are force-centred, so an offset off its default implies a scale off its default. Gutter scroll is
  excluded even though `reset()` restores it — a pinch already clamped at `fitScale` falls through to
  `applyTransformGesture`'s pan branch, where a centroid over a gutter scrolls it, which would otherwise
  leave the button up on a board that is plainly already fitted.
- **Zoom** is clamped to `[fitScale, max(fitScale,1)*MAX_ZOOM_MULTIPLE]` (3×), anchored so the content point under the
  cursor/centroid stays fixed (`zoomBy`).
- **Hit testing.** `hitTest(v)` → `TileCoord?` (rejects gutters/corner via `regionAt`, and any tile occluded by a pinned
  gutter). `hitTestSegment(from,to)` samples a whole pointer segment at half a rendered cell so a fast stroke can't skip
  a tile.

## Gesture routing — the part that's easy to break

Four stacked `pointerInput` nodes on the gesture Box. Compose dispatches the **Main** pass innermost-outward, so the
*last* modifier sees each event *first*. Order (top → bottom in source):

1. **Scroll-wheel zoom** (Main) — desktop/web; clamps notches so one flick isn't a 100× zoom.
2. **`detectBoardTaps`** — fires `onTap` immediately on UP (no double-tap delay, so tapping a tile isn't laggy). Never
   consumes; **cancels itself** the moment another detector consumes (i.e. once a drag passes touch slop). That
   self-cancel *is* the "tap toggles vs drag pans" threshold — there is no explicit distance check of our own. It
   reports which mouse button went down alongside the position (see "Mouse and keyboard").
3. **End-of-gesture reset** (Final pass) — when no pointer is down, `endGesture()` so the next drag re-picks its region.
4. **`detectTransformGestures`** — pan/zoom. A one-finger drag starting in a gutter scrolls *that gutter* on its own
   axis and pans the board on the other; a pinch always transforms the board and pins the rest of the gesture to that
   (`activeRegion`).
5. **`detectBoardDrawGestures`** — innermost, so it gets first refusal. It takes a gesture only while
   `isLocked() || secondary` (a right-button drag always erases, see below) and otherwise skips to the next down so
   the transform detector pans. Commits a one-pointer stroke only after touch slop (a second finger before that hands
   off to pinch); once committed it consumes every change so the transform detector can't also pan.
   `TileStroke` fixes its target state once from the mode (`mode.target`) and visits each cell at
   most once, so crossing back over a stroke doesn't re-toggle.

Both detectors get their first down from `awaitFirstDownEvent`, a copy of `awaitFirstDown(requireUnconsumed = true)`
that returns the `PointerEvent` — the mouse button lives on `event.buttons`, which the change alone does not carry, and
it has to be read at the down because the up no longer holds it.

**Lock mode** (`isLocked`): `true` → one-finger (left-button) drag *draws*; `false` → it *pans*. Pinch zoom and
tap-to-edit work in both. Toggled from `BottomToolBar` (the lock/unlock button) or the `A` key. `Board` reads it
through a lambda like every other callback, not as a `pointerInput` key, so toggling it mid-session never restarts
the detector.

**Draw mode** (`DrawMode`, picked from the `BottomToolBar`'s drawing group on touch platforms): what an edit *writes*.
`FILL` / `CROSS` / `ERASE` each write that one state and are idempotent, so re-tapping or re-crossing a cell never
undoes it — clearing a cell means selecting `ERASE`. There is no toggle/cycle mode: the earlier `TOGGLE`, which
advanced the cell by `TileState.next()`, was removed along with `TileState.next()` itself. Both mutation paths — the
tap in `Board` and `TileStroke.begin` — resolve through `DrawMode.target`, which is the single answer to "what does
this edit write?" — and it is a plain constant per mode, so `Tile.click` and `TileStroke` never read the cell's
existing state. The one place that does is `resolveDrawMode` (`Tile.kt`), run *before* either: on touch platforms it
returns the pencil unchanged, on mouse platforms it derives the mode from the button and the tile the gesture
started on (`mouseDrawMode`, see "Mouse and keyboard"). The pencil reaches the long-lived gesture coroutines through a
lambda (`drawMode: (start, secondary) -> DrawMode`) read at stroke commit, and is *not* a `pointerInput` key: changing
tools mid-board must not tear down and restart the detectors. It is per-screen composable state, like `isLocked`, and
resets to `FILL`.

## Mouse and keyboard

`hasMouseAndKeyboard` (`PlatformInput.kt`, an `expect val`: `true` on desktop and web, `false` on Android — the two
JVM actuals are separate files because `jvmSharedMain` serves both) switches the board to controls that need a
mouse:

- **The button is the pencil.** `mouseDrawMode(start, secondary)`: the secondary button erases, the primary toggles
  FILLED ↔ CROSSED and fills an empty tile. A stroke still fixes its target once from the tile it started on, so a
  left-drag from an empty cell fills the whole run and from a filled cell crosses it. A right-drag erases whether the
  board is locked or not — right has no panning use, so only the left button obeys the lock. On web,
  `suppressContextMenu()` (`PlatformInput.web.kt`, called from `webApp`'s `main`) keeps the browser menu off the canvas.
- **The pencil group is hidden.** `BottomToolBar` drops the "Pencil" `ToolGroup` and its counts adjust (one fewer
  group and gap). `TutorialStep.BOARD_DRAW_MODE` needs no special case: with no anchor registered the controller
  skips it.
- **Settings → "Keybinds"** opens `dialogs/KeybindsDialog.kt`, one row per `BoardShortcut` — the `icons/Mouse.kt`
  vector or a key cap, then what it does.

**Keys** are not gated: `Modifier.boardShortcuts` (`classes/BoardShortcuts.kt`) works with any keyboard. `BoardShortcut`
is the single table for mouse buttons and keys alike — each entry's `BoardTrigger` is a `MouseButton` (primary
draws, secondary erases) or a `KeyPress` (`A` lock/unlock, `S` check, `D` undo, `F` redo) — read by the handler, the
dialog and the tutorial copy (`keyHint`, which formats a key or a mouse row). The mouse rows only *describe* the bindings; the gesture layer
reads the button itself (`mouseDrawMode`). The modifier goes on each screen's root `Column`, requests focus on entry, and handles plain
`KeyDown` events (any Ctrl/Alt/Meta chord falls through). Key events bubble from the focused node up through its
ancestors, so a bottom-bar button that took focus on a click does not swallow them; a dialog is its own focus root,
so they stay dead while one is open. `history.undo()`/`redo()` are already no-ops on empty stacks; the screen passes
`onCheck = null` while its Check button is disabled.

## The check mark

Both boards have a bottom-bar **Check** button, and they ask different questions of the same mark.
`GameViewModel.checkBoard` marks every tile that contradicts the known solution — `FILLED` where the
solution is 0, `CROSSED` where it is 1; a blank tile is unfinished, not wrong. `GenViewModel.checkBoard`
has no solution to compare against, so it runs the `Solver` on the drawn grid and marks every cell line
logic cannot pin down: `solveNonogram()` reports 0 for a cell it never determined, 1 filled and 2 proven
empty, and a cell is wrong when it came back 0 or disagrees with what is drawn. That is advisory only —
it persists nothing and leaves `publishStatus` alone; it just moves `GenViewModel.validationState`, which
tints the Check icon green or red so a *solvable* puzzle gets an answer too.

Both also call `reset()` on the transform, so the whole board is back on screen to read the marks off,
which is why both screens hoist their `BoardTransformState` rather than letting `Board` remember it.
The mark is a second Compose state on `Tile` (`wrong`), which buys both halves of the behaviour for free:

- `drawTiles` reads `tile.wrong` in the same draw lambda it reads `tile.state`, so marking a tile
  invalidates one node's draw exactly like filling one does. It is painted as a final pass, after the
  gridlines, so the red reads over a black fill.
- `Tile.state` is a setter that clears `wrong` on any *actual* state change. Every mutation path —
  the tap, `TileStroke.paint`, undo/redo, `resetBoard` — writes `state`, so no gesture, history or
  screen code participates in clearing. Re-writing the same state is not an edit and leaves the mark
  standing.

The generator is the one place that clears more than the edited cell: `GenViewModel.updateNonogram`
blanks every `wrong` flag, because one edit can resolve a whole ambiguous region and leave the rest of
the marks describing a grid that no longer exists. GameScreen's marks are independent per-cell truths,
so there the per-tile clear is the right amount.

Nothing about it is persisted or synced, and leaving the puzzle drops it: `loadNonogram` rebuilds the
grid from fresh `Tile`s.

## Struck-out clues

`GameScreen` passes `strikeSolvedClues = true` and every clue the player has **certainly drawn** gets
a dark-grey diagonal struck through it (`solvedClueMask`, `classes/ClueProgress.kt`). `GenScreen`
leaves the flag off: its clues are derived from the tiles being drawn, so all of them would be
struck, always.

The rule is deliberately *not* solver-grade. Asking whether a clue is uniquely placeable given the
marks hands the player deductions they had not made yet — ruinous on a line full of 1s and 2s. It
reads only what the player has explicitly marked:

- A filled run is **sealed** when both of its ends are a `CROSSED` cell or the edge of the line. A
  run with a `NONE` beside it might still grow, so it strikes nothing.
- Runs match the clue list in order, first match wins: a sealed run takes the earliest unconsumed
  clue of the same length, an open run the earliest one at least as long. Open runs never strike,
  but they do consume a clue, so they hold their place in the ordering.
- A match is rejected when it cannot fit — the clues before it need room to its left, the ones after
  it room to its right. When nothing fits, the line stops rather than striking a clue the player
  provably has not placed. On `3 1 3 2` that is what makes a sealed 3 in the middle strike the
  *second* 3: the first one can no longer be followed by `1 3 2`.

The consequence is that a player who never crosses out blanks sees almost nothing struck — a
completed line whose gaps are `NONE` strikes nothing, the same line with them `CROSSED` strikes
everything. That is the price of not doing the player's work for them.

Rendering follows the check mark's lead. `Modifier.clueStrikes` (`Board.kt`) hangs one
`drawWithContent` on each clue *line* — not on each number — and reads that line's tile states inside
it, so an edit invalidates one line's draw and the up-to-30 `ClueText` nodes it holds never
recompose. Undo/redo and `resetBoard` write `Tile.state` as well, so they are covered with no
`onApply` hook. The strike is drawn in the gutter's own content space and rides its layer transform;
it deliberately does not read `state.scale`, which would redraw every clue line on every zoom step.

## Edit history (undo/redo)

`BoardHistory` (`classes/BoardHistory.kt`) is a capped (25-step) undo/redo journal, one instance owned by each of
`GameViewModel` and `GenViewModel`. A "move" is one drag stroke or one tap, recorded as a single `List<TileEdit>` so one
undo reverses the whole gesture, not one cell at a time. Undo/redo write `TileState` back into the *existing* `Tile`
objects (never replace the
`tiles` list), so the single-Canvas draw invalidation above still applies — no recomposition.

`GenViewModel` passes an `onApply` callback (`= { updateNonogram() }`) to its `BoardHistory`, run after every successful
`undo()`/`redo()`. It needs this because `Board` keys clue recomputation on the `nonogram` *object identity*, which only
`updateNonogram()` refreshes — undo/redo otherwise mutate tiles without going through it, leaving clues (and `isDirty`/
`validationState`) stale.
`GameViewModel` doesn't need the hook (its win check re-derives from `tiles` directly on every
`onTilesChanged`), so it leaves `onApply` at its no-op default.

Capture happens at the same two mutation sites as everything else in this doc, but at gesture *granularity*, not the
coarser `onTilesChanged` (which fires many times per drag — once per pointer event batch):

- **Tap** (`Board.kt`) — the tap handler now reads the tile's state before `click()` and emits a single-edit list if it
  changed, via a new `onEdits: (List<TileEdit>) -> Unit` param threaded alongside `onTilesChanged`.
- **Stroke** (`BoardTransform.kt`) — `TileStroke` now records a `TileEdit` (with the prior state)
  every time `paint` actually changes a cell, exposed via `edits()`. `detectBoardDrawGestures` emits the accumulated
  `edits()` exactly **once**, at the same point the inner gesture loop exits (`event.changes.none { it.pressed }`) —
  i.e. gesture end, not per-batch.

`GameScreen` passes `onEdits` straight to `Board`; its win check is otherwise untouched by history. No re-check is needed
after undo/redo: every recorded move's `after` state was already checked the moment that move was first applied (and its
`before` state was checked before that), so undo/redo can only ever revisit board states that have already been through
`checkSolved`.

`BoardHistory` owns the board it journals (set via `reset(tiles)`, called on every load — and in
`GenViewModel` also on `resizeNonogram`, since a resize rebuilds every `Tile` and would otherwise leave the journal
pointing at orphaned objects), so `undo()`/`redo()` take no argument and
`BottomToolBar` can drive either viewmodel's `history` directly — no per-screen wiring beyond passing the instance
through.

## Line metrics

`lineUnitPx(scale, tileBorderPx) = max(tileBorderPx, LINE_MIN_DEVICE_PX / scale)` — a stroke width that scales with zoom
but never drops below one device pixel (or it antialiases into nothing). Every line is a multiple of this unit, giving a
stable **1 : 2 : 4** hierarchy: hairline gridline (`TILE_BORDER`) < block line every `BLOCK_SIZE`=5
(`BLOCK_LINE_UNITS`) < gutter/field separator (`SEPARATOR_UNITS`). The separator width is *reserved* in the content
plane (`separatorContentPx`) so it scales with everything and the clip leaves room for it rather than painting over the
clues.

Each heavy block line also carries its index, so counting blocks off a clue doesn't mean counting lines from the edge by
hand. The number belongs to the *line*, not to the last cell in it: it tracks its line on the cross axis (right-aligned
against a vertical line, sitting above a horizontal one) but pins to the *visible* right/bottom edge of the field on the
other axis (`drawBlockLabels`), not the field's own edge — so it stays legible under any pan, even once the last
row/column has scrolled off screen. That pinning is why the labels are a separate overlay (`Board.kt`, drawn in viewport
px)
rather than living inside `drawTiles`, which paints in content px under the grid's own
`graphicsLayer` and would drag the numbers along with a pan same as the tiles. Font size is a fraction of `CELL`
(`BLOCK_LABEL_FONT_FRACTION`), so it scales with the board like everything else, but a numeral can't be widened the way
a line's stroke can: below `BLOCK_LABEL_MIN_DEVICE_PX` rendered height the labels are dropped outright rather than
floored.
