package com.trainpaths.nonogram.cache

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.db.SqlDriver
import com.trainpaths.nonogram.classes.Nonogram
import com.trainpaths.nonogram.classes.PublishStatus
import com.trainpaths.nonogram.classes.isRectangularGrid
import com.trainpaths.nonogram.classes.normalizeNonogramName
import com.trainpaths.nonogram.classes.toSolutionJson
import com.trainpaths.nonogram.classes.toSolutionOrNull
import com.trainpaths.nonogram.util.toDifficulty
import com.trainpaths.nonogram.util.toLong
import com.trainpaths.nonogram.util.toPublishStatus
import kotlin.random.Random
import kotlin.time.Clock

internal class Database(driver: SqlDriver) {
    private val database = NonogramDb(driver)
    private val dbQuery = database.databaseQueries

    // ---------- Nonograms ----------

    internal suspend fun getAllNonograms(): List<Nonogram> =
        dbQuery.selectAllNonograms(::mapNonogram).awaitAsList()

    internal suspend fun getNonogramsByDifficulty(difficulty: String): List<Nonogram> =
        dbQuery.selectNonogramsByDifficulty(difficulty, ::mapNonogram).awaitAsList()

    internal suspend fun getNonogramsByAuthor(authorUid: String): List<Nonogram> =
        dbQuery.selectNonogramsByAuthor(authorUid, ::mapNonogram).awaitAsList()

    /** Approved puzzles plus everything [authorUid] owns — what the menu may list. */
    internal suspend fun getVisibleNonograms(authorUid: String): List<Nonogram> =
        dbQuery.selectVisibleNonograms(PublishStatus.APPROVED.toLong(), authorUid, ::mapNonogram)
            .awaitAsList()

    internal suspend fun getNonogramById(id: Long): Nonogram? =
        dbQuery.selectNonogramById(id, ::mapNonogram).awaitAsOneOrNull()

    /**
     * Whether another puzzle already has this grid spoken for: an approved puzzle from anyone, or
     * [authorUid]'s own unlisted or pending copy. Encodes [solution] the same way the rows were
     * written, so this is an exact grid match, dimensions included.
     */
    internal suspend fun hasPublishConflict(
        solution: List<List<Int>>,
        excludeId: Long,
        authorUid: String,
    ): Boolean =
        dbQuery.selectPublishConflictId(
            solution = solution.toSolutionJson(),
            excludeId = excludeId,
            approvedStatus = PublishStatus.APPROVED.toLong(),
            authorUid = authorUid,
            unlistedStatus = PublishStatus.UNLISTED.toLong(),
            pendingStatus = PublishStatus.PENDING.toLong(),
        ).awaitAsOneOrNull() != null

    internal suspend fun getRandomNonogram(difficulty: String? = null): Nonogram? =
        if (difficulty == null) {
            dbQuery.selectRandomNonogram(::mapNonogram).awaitAsOneOrNull()
        } else {
            dbQuery.selectRandomNonogramByDifficulty(difficulty, ::mapNonogram)
                .awaitAsOneOrNull()
        }

    internal suspend fun countNonograms(): Long =
        dbQuery.countNonograms().awaitAsOne()

    internal suspend fun insertSeeds(seeds: List<SeedPuzzle>) {
        val now = Clock.System.now().toEpochMilliseconds()
        dbQuery.transaction {
            seeds.forEach { seed ->
                dbQuery.insertSeedNonogram(
                    seed.id,
                    seed.difficulty,
                    seed.solution.toSolutionJson(),
                    "",
                    PublishStatus.APPROVED.toLong(),
                    now,
                    seed.name?.let(::normalizeNonogramName),
                )
            }
        }
    }

    internal suspend fun addNonogram(
        difficulty: String,
        solution: List<List<Int>>,
        authorUid: String = "",
        id: Long? = null,
        name: String? = null,
        publishStatus: PublishStatus = PublishStatus.NONE,
    ): Long {
        // Random ids in [2^20, 2^53) stay unique across devices (Firestore doc ids) and JS-double-safe.
        val nonogramId = id ?: Random.nextLong(1L shl 20, 1L shl 53)
        dbQuery.upsertNonogram(
            nonogramId,
            difficulty,
            solution.toSolutionJson(),
            authorUid,
            publishStatus.toLong(),
            Clock.System.now().toEpochMilliseconds(),
            name?.let(::normalizeNonogramName),
        )
        return nonogramId
    }

    internal suspend fun updateNonogram(
        id: Long,
        nonogram: Nonogram,
    ): Long {
        dbQuery.updateNonogram(
            nonogram.difficulty.toString(),
            nonogram.solution.toSolutionJson(),
            nonogram.authorUid,
            nonogram.publishStatus.toLong(),
            Clock.System.now().toEpochMilliseconds(),
            nonogram.name?.let(::normalizeNonogramName),
            id
        )
        return id
    }

    internal suspend fun reassignAuthor(fromUid: String, toUid: String) {
        dbQuery.reassignAuthor(toUid = toUid, fromUid = fromUid)
    }

    internal suspend fun deleteNonogram(id: Long) {
        dbQuery.transaction {
            dbQuery.deleteProgressForNonogram(id)
            dbQuery.deleteNonogram(id)
        }
    }

    internal suspend fun upsertNonogram(nonogram: Nonogram) {
        dbQuery.upsertNonogram(
            nonogram.id,
            nonogram.difficulty.toString(),
            nonogram.solution.toSolutionJson(),
            nonogram.authorUid,
            nonogram.publishStatus.toLong(),
            nonogram.updatedAt,
            nonogram.name?.let(::normalizeNonogramName),
        )
    }
    // ---------- Users ----------

    internal suspend fun upsertUser(uid: String, name: String) {
        dbQuery.upsertUser(uid, name)
    }

    internal suspend fun getUser(uid: String) =
        dbQuery.selectUser(uid).awaitAsOneOrNull()

    internal suspend fun deleteUser(uid: String) {
        dbQuery.deleteUser(uid)
    }

    /** Moves one user's progress onto another, newest row winning; see database.sq. */
    internal suspend fun mergeProgressInto(fromUid: String, toUid: String) {
        dbQuery.transaction {
            dbQuery.mergeProgressInto(toUid = toUid, fromUid = fromUid)
            dbQuery.deleteProgressFor(fromUid)
        }
    }

    internal suspend fun saveProgress(
        userUid: String,
        nonogramId: Long,
        board: List<List<Int>>?
    ) {
        dbQuery.upsertProgress(
            userUid = userUid,
            nonogramId = nonogramId,
            boardState = board?.toSolutionJson(),
            updatedAt = Clock.System.now().toEpochMilliseconds()
        )
    }

    internal suspend fun saveProgressAfterWin(userUid: String, nonogramId: Long) {
        dbQuery.upsertProgressAfterWin(
            userUid = userUid,
            nonogramId = nonogramId,
            updatedAt = Clock.System.now().toEpochMilliseconds()
        )
    }

    internal suspend fun getProgressForUser(userUid: String): List<NonogramProgress> =
        dbQuery.selectProgressForUser(userUid, ::mapProgress).awaitAsList()

    internal suspend fun getProgressForUserWithTimestamp(userUid: String): List<ProgressWithTimestamp> =
        dbQuery.selectProgressForUserWithTimestamp(userUid) { nonogramId, boardState, updatedAt ->
            ProgressWithTimestamp(nonogramId, boardState, updatedAt)
        }.awaitAsList()

    internal suspend fun getSingleProgress(userUid: String, nonogramId: Long): ProgressWithTimestamp? =
        dbQuery.selectSingleProgress(userUid, nonogramId) { boardState, updatedAt ->
            ProgressWithTimestamp(nonogramId, boardState, updatedAt)
        }.awaitAsOneOrNull()

    internal suspend fun saveProgressWithTimestamp(
        userUid: String,
        nonogramId: Long,
        boardState: String?,
        updatedAt: Long
    ) {
        dbQuery.upsertProgress(
            userUid = userUid,
            nonogramId = nonogramId,
            boardState = boardState,
            updatedAt = updatedAt
        )
    }

    /**
     * A row the app cannot make sense of must not take the whole query down with it: one bad
     * `solution` would otherwise make the menu unopenable. Ragged grids are dropped too, since
     * `colClues` indexes across the first row's width.
     */
    private fun decodeSolution(solution: String): List<List<Int>> =
        solution.toSolutionOrNull()?.takeIf { it.isRectangularGrid() } ?: emptyList()

    private fun mapNonogram(
        id: Long,
        difficulty: String,
        solution: String,
        authorUid: String,
        status: Long,
        updatedAt: Long,
        name: String?,
    ): Nonogram = Nonogram(
        id = id,
        difficulty = difficulty.toDifficulty(),
        solution = decodeSolution(solution),
        name = name,
        authorUid = authorUid,
        updatedAt = updatedAt,
        publishStatus = status.toPublishStatus(),
    )

    private fun mapProgress(
        id: Long,
        difficulty: String,
        solution: String,
        authorUid: String,
        status: Long,
        updatedAt: Long,
        name: String?,
        boardState: String?,
        beat: Long
    ): NonogramProgress = NonogramProgress(
        nonogram = Nonogram(
            id = id,
            difficulty = difficulty.toDifficulty(),
            solution = decodeSolution(solution),
            name = name,
            authorUid = authorUid,
            updatedAt = updatedAt,
            publishStatus = status.toPublishStatus(),
        ),
        board = boardState?.toSolutionOrNull(),
        beat = beat
    )
}

data class NonogramProgress(
    val nonogram: Nonogram,
    val board: List<List<Int>>?,
    val beat: Long = 0
)

data class ProgressWithTimestamp(
    val nonogramId: Long,
    val boardState: String?,
    val updatedAt: Long
)
