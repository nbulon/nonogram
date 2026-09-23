package com.trainpaths.nonogram.tutorial

import com.trainpaths.nonogram.classes.BoardShortcut
import com.trainpaths.nonogram.classes.BoardTrigger
import com.trainpaths.nonogram.hasMouseAndKeyboard

/**
 * One tutorial hint. Declaration order is priority order: when several steps are on screen at once,
 * the first unseen one in this list is shown, and dismissing it reveals the next. Within a screen
 * that order follows its layout — the toolbar left to right, the app bar last.
 */
enum class TutorialStep(val title: String, val text: String) {
    MENU_PLAY(
        title = "Pick a puzzle",
        text = "Tap a card to start solving it. Your progress is saved as you go.",
    ),
    MENU_FILTER(
        title = "Filter and sort",
        text = "Narrow the list by difficulty or size, choose how it's sorted.",
    ),
    MENU_SWAP_TO_GENERATOR(
        title = "Your own nonograms",
        text = "Switch over to the generator to draw and edit puzzles of your own.",
    ),
    MENU_SETTINGS(
        title = "Settings",
        text = "Preferences like the color-theme live in here.",
    ),

    SETTINGS_THEME(
        title = "Colour theme",
        text = "Pick a color theme you like. Changeable anytime",
    ),
    SETTINGS_SHOW_NAMES(
        title = "Always show names",
        text = "On: puzzle descriptions are visible up front. Off: descriptions stays hidden until you solve it.",
    ),
    SETTINGS_AUTO_CROSS(
        title = "Auto-cross line",
        text = "On: once every clue of a row or column is crossed out, its remaining empty tiles get crossed for you.",
    ),
    SETTINGS_REPLAY(
        title = "Repeat tutorials",
        text = "Lost? This brings all of these tips back from the beginning.",
    ),

    BOARD_AREA(
        title = "The board",
        text = "The numbers along the top and side are the clues." + keyHint(BoardShortcut.DRAW, BoardShortcut.ERASE) +
                " Drag across tiles to draw a run.",
    ),
    BOARD_ZOOM(
        title = "Rezoom",
        text = "Fits the whole board back on screen after you've zoomed or panned.",
    ),
    BOARD_DRAW_MODE(
        title = "Draw mode",
        text = "Pick what a tap or drag writes: fill, cross or erase.",
    ),
    BOARD_UNDO(
        title = "Undo and redo",
        text = "Step back through your strokes. Saves up to 25 steps." +
                keyHint(BoardShortcut.UNDO, BoardShortcut.REDO),
    ),
    BOARD_LOCK(
        title = "Lock the board",
        text = "Locked: dragging draws on the board. Unlocked: dragging pans the board." + keyHint(BoardShortcut.LOCK),
    ),
    BOARD_CHECK(
        title = "Check your work",
        text = "Outlines every tile that contradicts the solution, and fits the board back on screen." +
                keyHint(BoardShortcut.CHECK),
    ),

    GENLIST_NEW(
        title = "Create a nonogram",
        text = "Create a new puzzle. You pick its size and name next.",
    ),
    GENLIST_SCAN(
        title = "Scan an image",
        text = "Turns a picture into a black-and-white grid, then drops it on the drawing board.",
    ),
    GENLIST_EDIT(
        title = "Edit a puzzle",
        text = "Tap one of your puzzles to open it in the editor.",
    ),
    GENLIST_FILTER(
        title = "Filter and sort",
        text = "Narrow your puzzles by status, name or size, or change the order.",
    ),
    GENLIST_SWAP_TO_PUZZLES(
        title = "Back to puzzles",
        text = "Same button in reverse: takes you back to the puzzle list.",
    ),

    GENCONF_NAME(
        title = "Name",
        text = "Optional, up to 30 characters, no emoji. Puzzles without one show as \"???\".",
    ),
    GENCONF_SIZE(
        title = "Grid size",
        text = "Rows and columns. Resizing later keeps whatever you've already drawn.",
    ),
    GENCONF_VALIDITY(
        title = "Validity",
        text = "Green means the puzzle is solvable. Only valid puzzles can be published.",
    ),
    GENCONF_PUBLISH(
        title = "Publishing",
        text = "Send a puzzle for review. Once approved, it will be published.",
    ),
    GENCONF_DONE(
        title = "Off you go",
        text = "This takes you to the drawing board and saves your changes when editing.",
    ),

    GEN_SAVE(
        title = "Save",
        text = "Saves without leaving the board. It lights up whenever there's something to save.",
    ),
    GEN_CHECK(
        title = "Check solvability",
        text = "Outlines the cells the solver can't work out. Green means the puzzle is uniquely solvable." +
                keyHint(BoardShortcut.CHECK),
    ),
    GEN_WRENCH(
        title = "Puzzle settings",
        text = "Reopen the name, size and publishing options for this puzzle.",
    );
}

/**
 * The step to show right now: the highest-priority one that is both unseen and currently on screen.
 */
fun nextStep(seen: Set<TutorialStep>, registered: Set<TutorialStep>): TutorialStep? =
    TutorialStep.entries.firstOrNull { it !in seen && it in registered }

/** " Press D / F." for keys, " Left-click: toggle fill / cross, right-click: toggle erase / cross." for mouse buttons — only where a mouse and keyboard are the norm. */
private fun keyHint(vararg shortcuts: BoardShortcut): String = when {
    !hasMouseAndKeyboard -> ""
    shortcuts.first().trigger is BoardTrigger.MouseButton ->
        " " + shortcuts.joinToString(", ") { "${it.trigger.label}: ${it.label}".lowercase() }
            .replaceFirstChar { it.uppercase() } + "."

    else -> " Press " + shortcuts.joinToString(" / ") { it.trigger.label } + "."
}
