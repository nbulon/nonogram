package com.trainpaths.nonogram.tutorial

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TutorialStepTest {

    @Test
    fun picksTheFirstStepInDeclarationOrder() {
        val onScreen = setOf(
            TutorialStep.MENU_SETTINGS,
            TutorialStep.MENU_FILTER,
            TutorialStep.MENU_PLAY,
        )
        assertEquals(TutorialStep.MENU_PLAY, nextStep(seen = emptySet(), registered = onScreen))
    }

    @Test
    fun skipsSeenStepsAndChainsToTheNext() {
        val onScreen = setOf(
            TutorialStep.MENU_PLAY,
            TutorialStep.MENU_FILTER,
            TutorialStep.MENU_SETTINGS,
        )
        assertEquals(
            TutorialStep.MENU_FILTER,
            nextStep(seen = setOf(TutorialStep.MENU_PLAY), registered = onScreen),
        )
    }

    @Test
    fun ignoresStepsThatAreNotOnScreen() {
        assertEquals(
            TutorialStep.SETTINGS_THEME,
            nextStep(seen = emptySet(), registered = setOf(TutorialStep.SETTINGS_THEME)),
        )
    }

    @Test
    fun nothingOnScreen_returnsNull() {
        assertNull(nextStep(seen = emptySet(), registered = emptySet()))
    }

    /** GenScreen's toolbar, left to right, with the top-left wrench last. */
    @Test
    fun genScreenSteps_runInLayoutOrder() {
        val onScreen = setOf(
            TutorialStep.GEN_WRENCH,
            TutorialStep.GEN_CHECK,
            TutorialStep.GEN_SAVE,
            TutorialStep.BOARD_LOCK,
            TutorialStep.BOARD_UNDO,
            TutorialStep.BOARD_DRAW_MODE,
        )

        assertEquals(
            listOf(
                TutorialStep.BOARD_DRAW_MODE,
                TutorialStep.BOARD_UNDO,
                TutorialStep.BOARD_LOCK,
                TutorialStep.GEN_SAVE,
                TutorialStep.GEN_CHECK,
                TutorialStep.GEN_WRENCH,
            ),
            walk(onScreen),
        )
    }

    /** Priority order is what puts a screen's hints in its own top-to-bottom order. */
    @Test
    fun genConfSteps_runInScreenOrder() {
        val onScreen = setOf(
            TutorialStep.GENCONF_DONE,
            TutorialStep.GENCONF_PUBLISH,
            TutorialStep.GENCONF_VALIDITY,
            TutorialStep.GENCONF_SIZE,
            TutorialStep.GENCONF_NAME,
        )
        assertEquals(
            listOf(
                TutorialStep.GENCONF_NAME,
                TutorialStep.GENCONF_SIZE,
                TutorialStep.GENCONF_VALIDITY,
                TutorialStep.GENCONF_PUBLISH,
                TutorialStep.GENCONF_DONE,
            ),
            walk(onScreen),
        )
    }

    @Test
    fun settingsSteps_runInScreenOrder() {
        val onScreen = setOf(
            TutorialStep.SETTINGS_REPLAY,
            TutorialStep.SETTINGS_AUTO_CROSS,
            TutorialStep.SETTINGS_SHOW_NAMES,
            TutorialStep.SETTINGS_THEME,
        )
        assertEquals(
            listOf(
                TutorialStep.SETTINGS_THEME,
                TutorialStep.SETTINGS_SHOW_NAMES,
                TutorialStep.SETTINGS_AUTO_CROSS,
                TutorialStep.SETTINGS_REPLAY,
            ),
            walk(onScreen),
        )
    }

    @Test
    fun everythingSeen_returnsNull() {
        assertNull(
            nextStep(
                seen = TutorialStep.entries.toSet(),
                registered = setOf(TutorialStep.GEN_SAVE),
            )
        )
    }

    /** The steps a user would meet, in order, dismissing each in turn. */
    private fun walk(onScreen: Set<TutorialStep>): List<TutorialStep> {
        val seen = mutableSetOf<TutorialStep>()
        return generateSequence { nextStep(seen, onScreen)?.also { seen += it } }.toList()
    }
}
