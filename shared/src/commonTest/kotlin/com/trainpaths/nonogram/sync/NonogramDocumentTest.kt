package com.trainpaths.nonogram.sync

import com.trainpaths.nonogram.classes.Difficulty
import com.trainpaths.nonogram.classes.MAX_NONOGRAM_NAME_LENGTH
import com.trainpaths.nonogram.classes.MAX_NONOGRAM_SIDE
import com.trainpaths.nonogram.classes.PublishStatus
import com.trainpaths.nonogram.classes.toSolutionJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NonogramDocumentTest {

    private val skipped = mutableListOf<String>()

    private val grid = List(5) { row -> List(5) { col -> if (row == col) 1 else 0 } }
    private val gridJson = grid.toSolutionJson()

    private fun document(
        id: String = "42",
        difficulty: String? = "HARD",
        solution: String? = gridJson,
        name: String? = "Comet",
        authorUid: String? = "uid-7",
        updatedAt: Long? = 123L,
        publishStatus: String? = "APPROVED",
    ) = NonogramDocument(id, difficulty, solution, name, authorUid, updatedAt, publishStatus)

    private fun NonogramDocument.parse() = toNonogram { skipped += it }

    @Test
    fun mapsEveryField() {
        val nonogram = assertNotNull(document().parse())

        assertEquals(42L, nonogram.id)
        assertEquals(Difficulty.HARD, nonogram.difficulty)
        assertEquals(grid, nonogram.solution)
        assertEquals("Comet", nonogram.name)
        assertEquals("uid-7", nonogram.authorUid)
        assertEquals(123L, nonogram.updatedAt)
        assertEquals(PublishStatus.APPROVED, nonogram.publishStatus)
        assertTrue(skipped.isEmpty())
    }

    @Test
    fun absentOptionalFieldsFallBackInsteadOfSkipping() {
        val nonogram = assertNotNull(
            document(difficulty = null, name = null, authorUid = null, publishStatus = null).parse()
        )

        assertEquals(Difficulty.EASY, nonogram.difficulty)
        assertNull(nonogram.name)
        assertEquals("", nonogram.authorUid)
        assertEquals(PublishStatus.NONE, nonogram.publishStatus)
        assertTrue(skipped.isEmpty())
    }

    @Test
    fun normalizesTheNameOnIngest() {
        val oversized = assertNotNull(document(name = "x".repeat(200)).parse())
        val blank = assertNotNull(document(name = "   ").parse())

        assertEquals(MAX_NONOGRAM_NAME_LENGTH, oversized.name?.length)
        assertNull(blank.name)
        assertTrue(skipped.isEmpty())
    }

    @Test
    fun carriesTheStoredSolverVerdict() {
        val nonogram = assertNotNull(document(publishStatus = "VALID").parse())

        assertEquals(PublishStatus.VALID, nonogram.publishStatus)
        assertTrue(nonogram.isKnownValid)
        assertTrue(skipped.isEmpty())
    }

    @Test
    fun carriesTheDeletedTombstone() {
        val nonogram = assertNotNull(document(publishStatus = "DELETED").parse())

        assertEquals(PublishStatus.DELETED, nonogram.publishStatus)
        assertTrue(skipped.isEmpty())
    }

    @Test
    fun unknownEnumNamesFallBackInsteadOfSkipping() {
        val nonogram = assertNotNull(
            document(difficulty = "IMPOSSIBLE", publishStatus = "RETRACTED").parse()
        )

        assertEquals(Difficulty.EASY, nonogram.difficulty)
        assertEquals(PublishStatus.NONE, nonogram.publishStatus)
    }

    @Test
    fun skipsDocumentsMissingWhatTheMergeNeeds() {
        assertNull(document(id = "not-a-number").parse())
        assertNull(document(solution = null).parse())
        assertNull(document(updatedAt = null).parse())

        assertEquals(3, skipped.size)
    }

    @Test
    fun skipsUnreadableSolution() {
        assertNull(document(solution = "not json").parse())
        assertEquals(1, skipped.size)
    }

    @Test
    fun skipsGridsThatAreNotWellFormed() {
        val ragged = listOf(listOf(1, 0, 0, 0, 0)) + List(4) { listOf(0, 0, 0, 0) }
        val tooBig = List(MAX_NONOGRAM_SIDE + 1) { List(MAX_NONOGRAM_SIDE + 1) { 0 } }

        assertNull(document(solution = ragged.toSolutionJson()).parse())
        assertNull(document(solution = List(4) { List(4) { 0 } }.toSolutionJson()).parse())
        assertNull(document(solution = tooBig.toSolutionJson()).parse())

        assertEquals(3, skipped.size)
    }
}
