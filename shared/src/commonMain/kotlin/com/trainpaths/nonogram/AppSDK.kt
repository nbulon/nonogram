package com.trainpaths.nonogram

import com.trainpaths.nonogram.cache.Database
import com.trainpaths.nonogram.cache.DatabaseFactory
import com.trainpaths.nonogram.cache.NonogramProgress
import com.trainpaths.nonogram.cache.ProgressWithTimestamp
import com.trainpaths.nonogram.cache.SEED_PUZZLES
import com.trainpaths.nonogram.cache.SeedPuzzle
import com.trainpaths.nonogram.cache.User
import com.trainpaths.nonogram.cache.dbDispatcher
import com.trainpaths.nonogram.classes.Nonogram
import com.trainpaths.nonogram.classes.PublishStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AppSDK(private val databaseFactory: DatabaseFactory) {
    private val mutex = Mutex()
    private var database: Database? = null

    private suspend fun db(): Database =
        database ?: mutex.withLock {
            database ?: Database(databaseFactory.createDriver()).also { database = it }
        }

    private suspend fun <T> onDb(block: suspend Database.() -> T): T =
        withContext(dbDispatcher) { db().block() }

    /**
     * Inserts the built-in puzzles into an empty database, and reports whether it wrote. Called once at
     * startup ([AppInitializer]), before anything reads the table.
     *
     * An empty table *is* first launch, so no flag is needed to recognise it — and unlike a flag, the check
     * still holds on web, where OPFS is evictable: a wiped database seeds again instead of coming back with
     * no puzzles at all.
     *
     * [seeds] is a parameter only so tests can pin a fixture set; production always takes the default.
     */
    internal suspend fun seedIfEmpty(seeds: List<SeedPuzzle> = SEED_PUZZLES): Boolean {
        if (onDb { countNonograms() } > 0) return false
        onDb { insertSeeds(seeds) }
        return true
    }

    suspend fun getAllNonograms(): List<Nonogram> =
        onDb { getAllNonograms() }

    suspend fun getNonogramsByDifficulty(difficulty: String): List<Nonogram> =
        onDb { getNonogramsByDifficulty(difficulty) }

    suspend fun getNonogramsByAuthor(authorUid: String): List<Nonogram> =
        onDb { getNonogramsByAuthor(authorUid) }

    /** Approved puzzles plus everything [authorUid] owns — what the menu may list. */
    suspend fun getVisibleNonograms(authorUid: String): List<Nonogram> =
        onDb { getVisibleNonograms(authorUid) }

    suspend fun getNonogramById(id: Long): Nonogram? =
        onDb { getNonogramById(id) }

    /** Whether another puzzle already claims this grid for publication. */
    suspend fun hasPublishConflict(
        solution: List<List<Int>>,
        excludeId: Long,
        authorUid: String,
    ): Boolean = onDb { hasPublishConflict(solution, excludeId, authorUid) }

    suspend fun getRandomNonogram(difficulty: String? = null): Nonogram? =
        onDb { getRandomNonogram(difficulty) }

    suspend fun addNonogram(
        difficulty: String,
        solution: List<List<Int>>,
        authorUid: String = "",
        id: Long? = null,
        name: String? = null,
        publishStatus: PublishStatus = PublishStatus.NONE,
    ): Long =
        onDb { addNonogram(difficulty, solution, authorUid, id, name, publishStatus) }

    suspend fun updateNonogram(
        id: Long,
        nonogram: Nonogram,
    ): Long =
        onDb { updateNonogram(id, nonogram) }

    /** Removes the puzzle together with every progress row on it. */
    suspend fun deleteNonogram(id: Long) =
        onDb { deleteNonogram(id) }

    suspend fun upsertNonogramFromRemote(nonogram: Nonogram) =
        onDb { upsertNonogram(nonogram) }

    /** Moves every puzzle authored under one author key to another, as sign-in does. */
    suspend fun reassignAuthor(fromUid: String, toUid: String) =
        onDb { reassignAuthor(fromUid, toUid) }

    suspend fun upsertUser(uid: String, name: String) =
        onDb { upsertUser(uid, name) }

    suspend fun getUser(uid: String): User? =
        onDb { getUser(uid) }

    suspend fun deleteUser(uid: String) =
        onDb { deleteUser(uid) }

    /** Moves every progress row from one user key to another, newest row winning. */
    suspend fun mergeProgressInto(fromUid: String, toUid: String) =
        onDb { mergeProgressInto(fromUid, toUid) }

    suspend fun saveProgress(userUid: String, nonogramId: Long, board: List<List<Int>>?) =
        onDb { saveProgress(userUid, nonogramId, board) }

    suspend fun saveProgressAfterWin(userUid: String, nonogramId: Long) =
        onDb { saveProgressAfterWin(userUid, nonogramId) }

    suspend fun getProgressForUser(userUid: String): List<NonogramProgress> =
        onDb { getProgressForUser(userUid) }

    suspend fun getProgressForUserWithTimestamp(userUid: String): List<ProgressWithTimestamp> =
        onDb { getProgressForUserWithTimestamp(userUid) }

    suspend fun getSingleProgress(userUid: String, nonogramId: Long): ProgressWithTimestamp? =
        onDb { getSingleProgress(userUid, nonogramId) }

    suspend fun saveProgressWithTimestamp(
        userUid: String,
        nonogramId: Long,
        boardState: String?,
        updatedAt: Long
    ) =
        onDb { saveProgressWithTimestamp(userUid, nonogramId, boardState, updatedAt) }
}
