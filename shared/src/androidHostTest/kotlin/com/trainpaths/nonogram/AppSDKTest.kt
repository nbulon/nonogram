package com.trainpaths.nonogram

import com.trainpaths.nonogram.classes.Difficulty
import com.trainpaths.nonogram.classes.Nonogram
import com.trainpaths.nonogram.classes.PublishStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppSDKTest {

    private lateinit var sdk: AppSDK

    @BeforeTest
    fun setUp() {
        sdk = AppSDK(TestDatabaseFactory())
    }

    /**
     * Seeds the fresh per-test DB with [SEED_FIXTURE] and returns the rows. The shipped set is generated
     * from dev Firestore and may be empty between exports, so nothing here leans on its content.
     */
    private suspend fun seed(): List<Nonogram> {
        sdk.seedIfEmpty(SEED_FIXTURE)
        return sdk.getAllNonograms()
    }

    private suspend fun approvedSeedIds(): Set<Long> =
        seed().filter { it.isPublic }.map { it.id }.toSet()

    @Test
    fun seedIfEmpty_doesNotDuplicateOnSecondCall() = runTest {
        val seeded = seed().map { it.id }
        assertTrue(seeded.isNotEmpty())

        assertFalse(sdk.seedIfEmpty(SEED_FIXTURE))

        assertEquals(seeded, sdk.getAllNonograms().map { it.id })
    }

    @Test
    fun addNonogram_assignsRandomJsSafeIdAndTimestamp() = runTest {
        val id1 = sdk.addNonogram("EASY", listOf(listOf(1)))
        val id2 = sdk.addNonogram("EASY", listOf(listOf(1)))
        assertTrue(id1 != id2)
        assertTrue(id1 >= (1L shl 20) && id1 < (1L shl 53))
        assertTrue(sdk.getNonogramById(id1)!!.updatedAt > 0)
    }

    @Test
    fun upsertNonogramFromRemote_insertsThenOverwrites() = runTest {
        val remote = Nonogram(
            id = 42, difficulty = Difficulty.HARD, solution = listOf(listOf(1, 0)),
            name = "Comet", authorUid = "uid-7", updatedAt = 123, publishStatus = PublishStatus.APPROVED,
        )
        sdk.upsertNonogramFromRemote(remote)
        assertEquals(remote, sdk.getNonogramById(42))

        val newer = remote.copy(solution = listOf(listOf(0, 1)), updatedAt = 456)
        sdk.upsertNonogramFromRemote(newer)
        assertEquals(newer, sdk.getNonogramById(42))
        assertEquals(1, sdk.getAllNonograms().size)
    }

    @Test
    fun reassignAuthor_movesOnlyTheMatchingAuthorsPuzzles() = runTest {
        val guestOne = sdk.addNonogram("EASY", listOf(listOf(1)), authorUid = "local:1")
        val guestTwo = sdk.addNonogram("EASY", listOf(listOf(1)), authorUid = "local:1")
        val other = sdk.addNonogram("EASY", listOf(listOf(1)), authorUid = "local:2")

        sdk.reassignAuthor("local:1", "uid-7")

        assertEquals(setOf(guestOne, guestTwo), sdk.getNonogramsByAuthor("uid-7").map { it.id }.toSet())
        assertEquals(listOf(other), sdk.getNonogramsByAuthor("local:2").map { it.id })
        assertTrue(sdk.getNonogramsByAuthor("local:1").isEmpty())
    }

    @Test
    fun deleteNonogram_removesThePuzzleAndEveryUsersProgressOnIt() = runTest {
        val doomed = sdk.addNonogram("EASY", listOf(listOf(1)), authorUid = "uid-7")
        val kept = sdk.addNonogram("EASY", listOf(listOf(0)), authorUid = "uid-7")
        sdk.saveProgressWithTimestamp("uid-7", doomed, "[[1]]", 100)
        sdk.saveProgressWithTimestamp("uid-8", doomed, "[[0]]", 100)
        sdk.saveProgressWithTimestamp("uid-7", kept, "[[0]]", 100)

        sdk.deleteNonogram(doomed)

        assertNull(sdk.getNonogramById(doomed))
        assertNull(sdk.getSingleProgress("uid-7", doomed))
        assertNull(sdk.getSingleProgress("uid-8", doomed))
        assertNotNull(sdk.getNonogramById(kept))
        assertNotNull(sdk.getSingleProgress("uid-7", kept))
    }

    @Test
    fun upsertNonogramFromRemote_roundTripsPublishStatus() = runTest {
        val remote = Nonogram(
            id = 43, difficulty = Difficulty.EASY, solution = listOf(listOf(1)),
            authorUid = "uid-7", updatedAt = 5, publishStatus = PublishStatus.APPROVED,
        )
        sdk.upsertNonogramFromRemote(remote)
        assertEquals(PublishStatus.APPROVED, sdk.getNonogramById(43)!!.publishStatus)
        assertTrue(sdk.getNonogramById(43)!!.isPublic)

        sdk.updateNonogram(43, remote.copy(publishStatus = PublishStatus.UNLISTED))
        assertEquals(PublishStatus.UNLISTED, sdk.getNonogramById(43)!!.publishStatus)
        assertFalse(sdk.getNonogramById(43)!!.isPublic)
    }

    @Test
    fun addAndUpdate_roundTripTheStoredSolverVerdict() = runTest {
        val id = sdk.addNonogram(
            "EASY", listOf(listOf(1)), authorUid = "uid-a", publishStatus = PublishStatus.VALID,
        )
        val added = assertNotNull(sdk.getNonogramById(id))
        assertEquals(PublishStatus.VALID, added.publishStatus)
        assertTrue(added.isKnownValid)
        assertFalse(added.isPublic)

        sdk.updateNonogram(id, added.copy(publishStatus = PublishStatus.NONE))
        assertFalse(assertNotNull(sdk.getNonogramById(id)).isKnownValid)
    }

    @Test
    fun getVisibleNonograms_hidesOtherAuthorsUnapprovedPuzzles() = runTest {
        val approvedSeeds = approvedSeedIds()
        val minePrivate = sdk.addNonogram("EASY", listOf(listOf(1)), authorUid = "uid-a")
        val minePending = sdk.addNonogram(
            "EASY", listOf(listOf(1)), authorUid = "uid-a", publishStatus = PublishStatus.PENDING,
        )
        val theirsApproved = sdk.addNonogram(
            "EASY", listOf(listOf(1)), authorUid = "uid-b", publishStatus = PublishStatus.APPROVED,
        )
        val theirsPrivate = sdk.addNonogram("EASY", listOf(listOf(1)), authorUid = "uid-b")
        // Solvable but never submitted: a verdict is not a publication.
        val theirsValid = sdk.addNonogram(
            "EASY", listOf(listOf(1)), authorUid = "uid-b", publishStatus = PublishStatus.VALID,
        )

        val mine = sdk.getVisibleNonograms("uid-a").map { it.id }.toSet()
        assertTrue(mine.containsAll(setOf(minePrivate, minePending, theirsApproved)))
        assertFalse(theirsPrivate in mine)
        assertFalse(theirsValid in mine)
        // The approved seeds are visible to everyone.
        assertTrue(mine.containsAll(approvedSeeds))

        // A signed-out guest key owns none of them.
        val guest = sdk.getVisibleNonograms("local:9").map { it.id }.toSet()
        assertTrue(guest.containsAll(approvedSeeds + theirsApproved))
        assertFalse(minePrivate in guest)
        assertFalse(minePending in guest)
        assertFalse(theirsPrivate in guest)
    }

    @Test
    fun getVisibleNonograms_blankUidOwnsNothing() = runTest {
        val approvedSeeds = approvedSeedIds()
        val theirs = sdk.addNonogram("EASY", listOf(listOf(1)), authorUid = "uid-a")

        val visible = sdk.getVisibleNonograms("").map { it.id }.toSet()
        assertEquals(approvedSeeds, visible)
        assertFalse(theirs in visible)
    }

    /** The grid every duplicate test shares. */
    private val grid = listOf(listOf(1, 0), listOf(0, 1))

    @Test
    fun hasPublishConflict_seesAnotherAuthorsApprovedPuzzle() = runTest {
        sdk.addNonogram("EASY", grid, authorUid = "uid-b", publishStatus = PublishStatus.APPROVED)
        val mine = sdk.addNonogram("EASY", grid, authorUid = "uid-a")

        assertTrue(sdk.hasPublishConflict(grid, mine, "uid-a"))
    }

    @Test
    fun hasPublishConflict_ignoresUnpublishedAndOtherAuthorsPrivateCopies() = runTest {
        sdk.addNonogram("EASY", grid, authorUid = "uid-b")
        sdk.addNonogram("EASY", grid, authorUid = "uid-b", publishStatus = PublishStatus.DENIED)
        // Another author's request in flight is invisible to us, as it is in Firestore.
        sdk.addNonogram("EASY", grid, authorUid = "uid-b", publishStatus = PublishStatus.PENDING)
        sdk.addNonogram("EASY", grid, authorUid = "uid-b", publishStatus = PublishStatus.UNLISTED)
        sdk.addNonogram("EASY", grid, authorUid = "uid-b", publishStatus = PublishStatus.VALID)
        sdk.addNonogram("EASY", grid, authorUid = "uid-a", publishStatus = PublishStatus.DENIED)
        // My own solvable-but-unsubmitted copy does not block the request either.
        sdk.addNonogram("EASY", grid, authorUid = "uid-a", publishStatus = PublishStatus.VALID)
        val mine = sdk.addNonogram("EASY", grid, authorUid = "uid-a")

        assertFalse(sdk.hasPublishConflict(grid, mine, "uid-a"))
    }

    @Test
    fun hasPublishConflict_seesMyOwnPendingThenUnlistedCopy() = runTest {
        val other = sdk.addNonogram(
            "EASY", grid, authorUid = "uid-a", publishStatus = PublishStatus.PENDING,
        )
        val mine = sdk.addNonogram("EASY", grid, authorUid = "uid-a")
        assertTrue(sdk.hasPublishConflict(grid, mine, "uid-a"))

        val pending = assertNotNull(sdk.getNonogramById(other))
        sdk.updateNonogram(other, pending.copy(publishStatus = PublishStatus.UNLISTED))
        assertTrue(sdk.hasPublishConflict(grid, mine, "uid-a"))
    }

    @Test
    fun hasPublishConflict_excludesThePuzzleItself() = runTest {
        val mine = sdk.addNonogram(
            "EASY", grid, authorUid = "uid-a", publishStatus = PublishStatus.APPROVED,
        )

        assertFalse(sdk.hasPublishConflict(grid, mine, "uid-a"))
    }

    @Test
    fun hasPublishConflict_matchesTheGridExactly() = runTest {
        sdk.addNonogram("EASY", grid, authorUid = "uid-b", publishStatus = PublishStatus.APPROVED)
        val mine = sdk.addNonogram("EASY", listOf(listOf(1)), authorUid = "uid-a")

        assertFalse(sdk.hasPublishConflict(listOf(listOf(0, 1), listOf(1, 0)), mine, "uid-a"))
        // The same pattern padded into a larger grid is a different puzzle.
        val padded = listOf(listOf(1, 0, 0), listOf(0, 1, 0), listOf(0, 0, 0))
        assertFalse(sdk.hasPublishConflict(padded, mine, "uid-a"))

        assertTrue(sdk.hasPublishConflict(grid, mine, "uid-a"))
    }

    @Test
    fun hasPublishConflict_countsTheApprovedSeeds() = runTest {
        val seeded = seed().first { it.isPublic }
        val copy = sdk.addNonogram("EASY", seeded.solution, authorUid = "uid-a")

        assertTrue(sdk.hasPublishConflict(seeded.solution, copy, "uid-a"))
    }

    @Test
    fun addNonogram_and_getAllNonograms_roundTrip() = runTest {
        val solution = listOf(listOf(1, 0), listOf(0, 1))
        val id = sdk.addNonogram("EASY", solution)
        val all = sdk.getAllNonograms()
        assertEquals(1, all.size)
        assertEquals(id, all[0].id)
        assertEquals(Difficulty.EASY, all[0].difficulty)
        assertEquals(solution, all[0].solution)
        assertNull(all[0].name)
    }

    @Test
    fun addAndUpdateNonogram_roundTripsNullableName() = runTest {
        val id = sdk.addNonogram("EASY", listOf(listOf(1)), name = "Tree")
        assertEquals("Tree", sdk.getNonogramById(id)?.name)

        val existing = assertNotNull(sdk.getNonogramById(id))
        sdk.updateNonogram(id, existing.copy(name = null))
        assertNull(sdk.getNonogramById(id)?.name)
    }

    @Test
    fun getNonogramsByDifficulty_filtersCorrectly() = runTest {
        sdk.addNonogram("EASY", listOf(listOf(1)))
        sdk.addNonogram("HARD", listOf(listOf(0)))
        sdk.addNonogram("EASY", listOf(listOf(1, 1)))

        val easy = sdk.getNonogramsByDifficulty("EASY")
        assertEquals(2, easy.size)
        assertTrue(easy.all { it.difficulty == Difficulty.EASY })

        val hard = sdk.getNonogramsByDifficulty("HARD")
        assertEquals(1, hard.size)
    }

    @Test
    fun getNonogramById_returnsCorrectOrNull() = runTest {
        val id = sdk.addNonogram("EASY", listOf(listOf(1)))
        assertNotNull(sdk.getNonogramById(id))
        assertNull(sdk.getNonogramById(9999))
    }

    @Test
    fun upsertUser_and_getUser_roundTrip() = runTest {
        sdk.upsertUser("uid-alice", "Alice")
        val user = sdk.getUser("uid-alice")
        assertNotNull(user)
        assertEquals("Alice", user.name)
    }

    @Test
    fun getUser_returnsNullForAnUnknownUid() = runTest {
        sdk.upsertUser("uid-bob", "Bob")
        assertNull(sdk.getUser("uid-123"))
    }

    @Test
    fun upsertUser_replacesTheDisplayName() = runTest {
        sdk.upsertUser("uid-charlie", "Charlie")
        sdk.upsertUser("uid-charlie", "Charlie Updated")

        val found = sdk.getUser("uid-charlie")
        assertNotNull(found)
        assertEquals("Charlie Updated", found.name)
    }

    @Test
    fun deleteUser_removesOnlyThatRow() = runTest {
        sdk.upsertUser("local:1", "Guest")
        sdk.upsertUser("uid-keep", "Keeper")

        sdk.deleteUser("local:1")

        assertNull(sdk.getUser("local:1"))
        assertNotNull(sdk.getUser("uid-keep"))
    }

    @Test
    fun saveProgress_and_getProgressForUser_roundTrip() = runTest {
        val nonogramId = sdk.addNonogram("EASY", listOf(listOf(1, 0), listOf(0, 1)))
        val board = listOf(listOf(1, 0), listOf(0, 0))

        sdk.saveProgress("uid-dave", nonogramId, board)

        val progress = sdk.getProgressForUser("uid-dave")
        assertEquals(1, progress.size)
        assertEquals(nonogramId, progress[0].nonogram.id)
        assertEquals(board, progress[0].board)
    }

    @Test
    fun saveProgress_keepsCrossedCells() = runTest {
        val nonogramId = sdk.addNonogram("EASY", listOf(listOf(1, 0), listOf(0, 1)))
        val board = listOf(listOf(1, 2), listOf(2, 0))

        sdk.saveProgress("uid-cross", nonogramId, board)

        assertEquals(board, sdk.getProgressForUser("uid-cross").single().board)
    }

    @Test
    fun saveProgressAfterWin_incrementsBeatCount() = runTest {
        val nonogramId = sdk.addNonogram("EASY", listOf(listOf(1)))

        sdk.saveProgress("uid-eve", nonogramId, listOf(listOf(1)))
        sdk.saveProgressAfterWin("uid-eve", nonogramId)

        assertEquals(1, sdk.getProgressForUser("uid-eve")[0].beat)

        sdk.saveProgressAfterWin("uid-eve", nonogramId)
        assertEquals(2, sdk.getProgressForUser("uid-eve")[0].beat)
    }

    @Test
    fun upsertProgress_preservesBeatCount() = runTest {
        val nonogramId = sdk.addNonogram("EASY", listOf(listOf(1)))

        sdk.saveProgress("uid-frank", nonogramId, listOf(listOf(1)))
        sdk.saveProgressAfterWin("uid-frank", nonogramId)
        sdk.saveProgressAfterWin("uid-frank", nonogramId)

        sdk.saveProgress("uid-frank", nonogramId, listOf(listOf(0)))

        val progress = sdk.getProgressForUser("uid-frank")
        assertEquals(2, progress[0].beat)
        assertEquals(listOf(listOf(0)), progress[0].board)
    }

    @Test
    fun mergeProgressInto_movesRowsThatDoNotCollide() = runTest {
        val nonogramId = sdk.addNonogram("EASY", listOf(listOf(1)))
        sdk.saveProgressWithTimestamp("local:1", nonogramId, "[[1]]", 100)

        sdk.mergeProgressInto("local:1", "uid-7")

        val moved = sdk.getSingleProgress("uid-7", nonogramId)
        assertNotNull(moved)
        assertEquals("[[1]]", moved.boardState)
        assertEquals(100, moved.updatedAt)
        assertTrue(sdk.getProgressForUserWithTimestamp("local:1").isEmpty())
    }

    @Test
    fun mergeProgressInto_newerSourceWins() = runTest {
        val nonogramId = sdk.addNonogram("EASY", listOf(listOf(1)))
        sdk.saveProgressWithTimestamp("uid-7", nonogramId, "[[0]]", 100)
        sdk.saveProgressWithTimestamp("local:1", nonogramId, "[[1]]", 200)

        sdk.mergeProgressInto("local:1", "uid-7")

        val merged = sdk.getSingleProgress("uid-7", nonogramId)
        assertNotNull(merged)
        assertEquals("[[1]]", merged.boardState)
        assertEquals(200, merged.updatedAt)
    }

    @Test
    fun mergeProgressInto_olderSourceLoses() = runTest {
        val nonogramId = sdk.addNonogram("EASY", listOf(listOf(1)))
        sdk.saveProgressWithTimestamp("uid-7", nonogramId, "[[0]]", 200)
        sdk.saveProgressWithTimestamp("local:1", nonogramId, "[[1]]", 100)

        sdk.mergeProgressInto("local:1", "uid-7")

        val merged = sdk.getSingleProgress("uid-7", nonogramId)
        assertNotNull(merged)
        assertEquals("[[0]]", merged.boardState)
        assertEquals(200, merged.updatedAt)
        assertTrue(sdk.getProgressForUserWithTimestamp("local:1").isEmpty())
    }

    @Test
    fun mergeProgressInto_keepsTheHigherBeatCount() = runTest {
        val nonogramId = sdk.addNonogram("EASY", listOf(listOf(1)))
        // The account has been beaten twice but has a stale board; the guest has a newer board.
        sdk.saveProgressAfterWin("uid-7", nonogramId)
        sdk.saveProgressAfterWin("uid-7", nonogramId)
        sdk.saveProgressWithTimestamp("uid-7", nonogramId, "[[0]]", 100)
        sdk.saveProgressWithTimestamp("local:1", nonogramId, "[[1]]", 200)

        sdk.mergeProgressInto("local:1", "uid-7")

        val merged = sdk.getProgressForUser("uid-7").single()
        assertEquals(2, merged.beat)
        assertEquals(listOf(listOf(1)), merged.board)
    }

    @Test
    fun saveProgressWithTimestamp_and_getSingleProgress_roundTrip() = runTest {
        val nonogramId = sdk.addNonogram("EASY", listOf(listOf(1)))
        val timestamp = 1700000000000L

        sdk.saveProgressWithTimestamp("uid-grace", nonogramId, "[[1]]", timestamp)

        val single = sdk.getSingleProgress("uid-grace", nonogramId)
        assertNotNull(single)
        assertEquals("[[1]]", single.boardState)
        assertEquals(timestamp, single.updatedAt)
    }

    @Test
    fun getProgressForUserWithTimestamp_returnsAllProgress() = runTest {
        val id1 = sdk.addNonogram("EASY", listOf(listOf(1)))
        val id2 = sdk.addNonogram("HARD", listOf(listOf(0)))

        sdk.saveProgress("uid-heidi", id1, listOf(listOf(1)))
        sdk.saveProgress("uid-heidi", id2, listOf(listOf(0)))

        val progress = sdk.getProgressForUserWithTimestamp("uid-heidi")
        assertEquals(2, progress.size)
    }

    @Test
    fun saveProgress_nullBoard() = runTest {
        val nonogramId = sdk.addNonogram("EASY", listOf(listOf(1)))

        sdk.saveProgress("uid-ivan", nonogramId, null)

        val single = sdk.getSingleProgress("uid-ivan", nonogramId)
        assertNotNull(single)
        assertNull(single.boardState)
    }
}
