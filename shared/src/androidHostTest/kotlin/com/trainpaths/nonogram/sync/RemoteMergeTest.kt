package com.trainpaths.nonogram.sync

import com.trainpaths.nonogram.AppSDK
import com.trainpaths.nonogram.TestDatabaseFactory
import com.trainpaths.nonogram.classes.Difficulty
import com.trainpaths.nonogram.classes.Nonogram
import com.trainpaths.nonogram.classes.PublishStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The progress and nonogram merges as both platform services run them — they supply only the fetch
 * and the single document write, so a [SyncService] that records its pushes is the whole platform half.
 */
class RemoteMergeTest {

    private lateinit var sdk: AppSDK
    private lateinit var service: RecordingSyncService

    private val uid = "uid-7"

    @BeforeTest
    fun setUp() {
        sdk = AppSDK(TestDatabaseFactory())
        service = RecordingSyncService()
    }

    private suspend fun aNonogram(): Long =
        sdk.addNonogram("EASY", listOf(listOf(1, 0), listOf(0, 1)))

    private fun remote(
        id: Long,
        updatedAt: Long,
        authorUid: String = uid,
        publishStatus: PublishStatus = PublishStatus.VALID,
    ) = Nonogram(
        id = id, difficulty = Difficulty.EASY, solution = listOf(listOf(1, 0), listOf(0, 1)),
        name = "Comet", authorUid = authorUid, updatedAt = updatedAt, publishStatus = publishStatus,
    )

    @Test
    fun mergeRemoteProgress_insertsRowsTheDeviceHasNeverSeen() = runTest {
        val id = aNonogram()

        service.mergeRemoteProgress(sdk, uid, listOf(RemoteProgress(id, "[[1,0],[0,0]]", 100)))

        val local = assertNotNull(sdk.getSingleProgress(uid, id))
        assertEquals("[[1,0],[0,0]]", local.boardState)
        assertEquals(100, local.updatedAt)
        assertTrue(service.pushed.isEmpty())
    }

    @Test
    fun mergeRemoteProgress_remoteNewerOverwritesLocal() = runTest {
        val id = aNonogram()
        sdk.saveProgressWithTimestamp(uid, id, "[[0,0],[0,0]]", 100)

        service.mergeRemoteProgress(sdk, uid, listOf(RemoteProgress(id, "[[1,1],[1,1]]", 200)))

        assertEquals("[[1,1],[1,1]]", sdk.getSingleProgress(uid, id)?.boardState)
        assertTrue(service.pushed.isEmpty())
    }

    @Test
    fun mergeRemoteProgress_localNewerIsPushedBackUntouched() = runTest {
        val id = aNonogram()
        sdk.saveProgressWithTimestamp(uid, id, "[[1,1],[1,1]]", 300)

        service.mergeRemoteProgress(sdk, uid, listOf(RemoteProgress(id, "[[0,0],[0,0]]", 200)))

        assertEquals("[[1,1],[1,1]]", sdk.getSingleProgress(uid, id)?.boardState)
        assertEquals(listOf(Push(uid, id, "[[1,1],[1,1]]", 300)), service.pushed)
    }

    @Test
    fun mergeRemoteProgress_equalTimestampsAreLeftAlone() = runTest {
        val id = aNonogram()
        sdk.saveProgressWithTimestamp(uid, id, "[[1,1],[1,1]]", 200)

        service.mergeRemoteProgress(sdk, uid, listOf(RemoteProgress(id, "[[0,0],[0,0]]", 200)))

        assertEquals("[[1,1],[1,1]]", sdk.getSingleProgress(uid, id)?.boardState)
        assertTrue(service.pushed.isEmpty())
    }

    @Test
    fun applyRemoteProgress_overwritesEvenWhenLocalIsNewer() = runTest {
        val id = aNonogram()
        sdk.saveProgressWithTimestamp(uid, id, "[[1,1],[1,1]]", 300)

        applyRemoteProgress(sdk, uid, listOf(RemoteProgress(id, "[[0,0],[0,0]]", 200)))

        val local = assertNotNull(sdk.getSingleProgress(uid, id))
        assertEquals("[[0,0],[0,0]]", local.boardState)
        assertEquals(200, local.updatedAt)
        assertTrue(service.pushed.isEmpty())
    }

    @Test
    fun uploadAllProgress_pushesEveryRowOfThatUserOnly() = runTest {
        val first = sdk.addNonogram("EASY", listOf(listOf(1)))
        val second = sdk.addNonogram("EASY", listOf(listOf(0)))
        sdk.saveProgressWithTimestamp(uid, first, "[[1]]", 100)
        sdk.saveProgressWithTimestamp(uid, second, null, 200)
        sdk.saveProgressWithTimestamp("other-uid", first, "[[0]]", 300)

        service.uploadAllProgress(sdk, uid)

        assertEquals(
            setOf(Push(uid, first, "[[1]]", 100), Push(uid, second, null, 200)),
            service.pushed.toSet(),
        )
    }

    @Test
    fun mergeRemoteNonograms_remoteNewerOverwritesLocal() = runTest {
        sdk.upsertNonogramFromRemote(remote(42, updatedAt = 100).copy(name = "Old"))

        service.mergeRemoteNonograms(sdk, uid, 0, listOf(remote(42, updatedAt = 200)))

        assertEquals("Comet", sdk.getNonogramById(42)?.name)
        assertTrue(service.pushedNonograms.isEmpty())
    }

    @Test
    fun mergeRemoteNonograms_localNewerIsPushedBack() = runTest {
        val local = remote(42, updatedAt = 300).copy(name = "Mine")
        sdk.upsertNonogramFromRemote(local)

        service.mergeRemoteNonograms(sdk, uid, 0, listOf(remote(42, updatedAt = 200)))

        assertEquals("Mine", sdk.getNonogramById(42)?.name)
        assertEquals(listOf(local), service.pushedNonograms)
    }

    @Test
    fun mergeRemoteNonograms_deletedTombstoneRemovesLocalRow() = runTest {
        sdk.upsertNonogramFromRemote(remote(42, updatedAt = 100))
        sdk.saveProgressWithTimestamp(uid, 42, "[[1,0],[0,0]]", 100)

        val cursor = service.mergeRemoteNonograms(
            sdk, uid, 0, listOf(remote(42, updatedAt = 200, publishStatus = PublishStatus.DELETED)),
        )

        assertNull(sdk.getNonogramById(42))
        assertNull(sdk.getSingleProgress(uid, 42))
        assertEquals(200, cursor)
        assertTrue(service.pushedNonograms.isEmpty())
    }

    @Test
    fun mergeRemoteNonograms_deletedTombstoneWinsOverNewerLocal() = runTest {
        sdk.upsertNonogramFromRemote(remote(42, updatedAt = 300))

        service.mergeRemoteNonograms(
            sdk, uid, 0, listOf(remote(42, updatedAt = 200, publishStatus = PublishStatus.DELETED)),
        )

        assertNull(sdk.getNonogramById(42))
        assertTrue(service.pushedNonograms.isEmpty())
    }

    @Test
    fun mergeRemoteNonograms_deletedTombstoneIsNeverStoredLocally() = runTest {
        service.mergeRemoteNonograms(
            sdk, uid, 0, listOf(remote(42, updatedAt = 200, publishStatus = PublishStatus.DELETED)),
        )

        assertNull(sdk.getNonogramById(42))
    }

    @Test
    fun mergeRemoteNonograms_foreignTombstoneLeavesAnotherAuthorsRowAlone() = runTest {
        sdk.upsertNonogramFromRemote(remote(42, updatedAt = 100))

        service.mergeRemoteNonograms(
            sdk, uid, 0,
            listOf(remote(42, updatedAt = 200, authorUid = "uid-8", publishStatus = PublishStatus.DELETED)),
        )

        assertNotNull(sdk.getNonogramById(42))
    }
}

private data class Push(
    val firebaseUid: String,
    val nonogramId: Long,
    val boardState: String?,
    val updatedAt: Long,
)

/** Records the one write each shared merge is allowed to make; everything else is unreachable. */
private class RecordingSyncService : SyncService {

    val pushed = mutableListOf<Push>()
    val pushedNonograms = mutableListOf<Nonogram>()

    override suspend fun pushProgress(firebaseUid: String, nonogramId: Long, boardState: String?, updatedAt: Long) {
        pushed += Push(firebaseUid, nonogramId, boardState, updatedAt)
    }

    override suspend fun pushNonogram(firebaseUid: String, nonogram: Nonogram, writePublishStatus: Boolean) {
        pushedNonograms += nonogram
    }

    override suspend fun hasRemoteProgress(firebaseUid: String) = unused()
    override suspend fun uploadAllLocalProgress(firebaseUid: String) = unused()
    override suspend fun pullAllProgress(firebaseUid: String) = unused()
    override suspend fun pullAndMergeAllProgress(firebaseUid: String) = unused()
    override suspend fun uploadAllLocalNonograms(firebaseUid: String) = unused()
    override suspend fun pullPublicNonogramsSince(firebaseUid: String?, since: Long) = unused()
    override suspend fun pullOwnedNonograms(firebaseUid: String, since: Long) = unused()
    override suspend fun requestPublish(firebaseUid: String, nonogram: Nonogram) = unused()
    override suspend fun deleteNonogram(firebaseUid: String, nonogramId: Long) = unused()
    override suspend fun fetchModerationGate(firebaseUid: String) = unused()
    override suspend fun isAdmin(firebaseUid: String) = unused()
    override suspend fun pullPendingReviews(firebaseUid: String, limit: Int) = unused()
    override suspend fun decideReview(
        firebaseUid: String,
        nonogram: Nonogram,
        approve: Boolean,
        difficulty: Difficulty,
    ) = unused()

    private fun unused(): Nothing = error("not part of either merge")
}
