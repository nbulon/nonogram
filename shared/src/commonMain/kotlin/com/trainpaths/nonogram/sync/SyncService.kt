package com.trainpaths.nonogram.sync

import com.trainpaths.nonogram.AppSDK
import com.trainpaths.nonogram.auth.AuthRepository
import com.trainpaths.nonogram.classes.Difficulty
import com.trainpaths.nonogram.classes.Nonogram
import com.trainpaths.nonogram.classes.PublishStatus
import kotlin.time.Clock

interface SyncService {
    suspend fun pushProgress(firebaseUid: String, nonogramId: Long, boardState: String?, updatedAt: Long)
    suspend fun pullAndMergeAllProgress(firebaseUid: String)

    /**
     * Writes the puzzle to the shared `nonograms` collection, authored by [firebaseUid].
     */
    suspend fun pushNonogram(firebaseUid: String, nonogram: Nonogram, writePublishStatus: Boolean = false)

    /** Runs for guests too ([firebaseUid] null): approved puzzles are readable unauthenticated. */
    suspend fun pullPublicNonogramsSince(firebaseUid: String?, since: Long): Long?

    /** A full pull (`since == 0`) also pushes the author's local puzzles the remote does not have. */
    suspend fun pullOwnedNonograms(firebaseUid: String, since: Long): Long?

    /** Moves the puzzle to `PENDING`. Returns false when the rules reject it (e.g. banned author). */
    suspend fun requestPublish(firebaseUid: String, nonogram: Nonogram): Boolean

    /**
     * Marks the puzzle `DELETED` — a tombstone, never a hard delete, so the author's other devices
     * drop it on their next owned pull instead of pushing a stale copy back. False when refused.
     */
    suspend fun deleteNonogram(firebaseUid: String, nonogramId: Long): Boolean

    /** Reads the author's denial streak / ban flag; null when the read failed. */
    suspend fun fetchModerationGate(firebaseUid: String): ModerationGate?

    suspend fun isAdmin(firebaseUid: String): Boolean

    /** Admin only: the oldest pending requests, oldest first. */
    suspend fun pullPendingReviews(firebaseUid: String, limit: Int): List<Nonogram>

    /**
     * Admin only: accepts [nonogram] at [difficulty] or denies it, and updates its author's denial
     * streak. Difficulty is the reviewer's call — authors never rate their own puzzles — so it is
     * written only when approving, and so is [Nonogram.name], which carries the reviewer's edit.
     * The document's `updatedAt` is [Nonogram.updatedAt], so the caller can mirror the write locally.
     */
    suspend fun decideReview(
        firebaseUid: String,
        nonogram: Nonogram,
        approve: Boolean,
        difficulty: Difficulty,
    ): Boolean
}

/**
 * Merge policy for pulled nonograms, shared by both platform implementations: a `DELETED`
 * tombstone removes the local row outright, a verdict on a locally `PENDING` puzzle is taken
 * whatever the timestamps say, otherwise remote newer → upsert locally, local newer and locally
 * authored → push back. Returns the newest received `updatedAt` timestamp for the next
 * incremental fetch. A null [firebaseUid] is a guest's unauthenticated public pull: merge in,
 * never push back. [pushLocalOnly] is for the one pull that sees the whole remote set — a full
 * owned pull — and pushes the author's puzzles the remote has never seen (guest-authored ones a
 * sign-in just moved onto the uid).
 */
internal suspend fun SyncService.mergeRemoteNonograms(
    sdk: AppSDK,
    firebaseUid: String?,
    lastSyncedAt: Long,
    remotes: List<Nonogram>,
    pushLocalOnly: Boolean = false,
): Long {
    var newestReceivedAt = lastSyncedAt
    val now = Clock.System.now().toEpochMilliseconds()
    for (remote in remotes) {
        if (remote.updatedAt in (newestReceivedAt + 1)..now) newestReceivedAt = remote.updatedAt
        val local = sdk.getNonogramById(remote.id)
        if (local != null && local.authorUid.isNotEmpty() && local.authorUid != remote.authorUid) {
            continue
        }
        // Delete wins regardless of timestamps, or an offline edit elsewhere would revive the puzzle.
        if (remote.publishStatus == PublishStatus.DELETED) {
            if (local != null) sdk.deleteNonogram(remote.id)
            continue
        }
        
        val decided = local?.publishStatus == PublishStatus.PENDING &&
                remote.publishStatus != PublishStatus.PENDING
        if (local == null || decided || local.updatedAt < remote.updatedAt) {
            sdk.upsertNonogramFromRemote(remote)
        } else if (
            firebaseUid != null &&
            local.updatedAt > remote.updatedAt &&
            local.authorUid == firebaseUid
        ) {
            pushNonogram(firebaseUid, local)
        }
    }
    if (pushLocalOnly && firebaseUid != null) {
        val remoteIds = remotes.mapTo(HashSet()) { it.id }
        for (local in sdk.getNonogramsByAuthor(firebaseUid)) {
            if (local.id !in remoteIds) pushNonogram(firebaseUid, local)
        }
    }
    return newestReceivedAt
}

/**
 * Pulls the approved puzzles changed since this device's last public pull and advances the cursor.
 * False when the pull failed, leaving the cursor untouched so the next attempt retries the range.
 */
internal suspend fun SyncService.syncPublicNonograms(
    authRepository: AuthRepository,
    firebaseUid: String?,
): Boolean {
    val lastSyncedAt = authRepository.getLastPublicNonogramSyncTimestamp()
    val newestReceivedAt = pullPublicNonogramsSince(firebaseUid, lastSyncedAt) ?: return false
    if (newestReceivedAt != lastSyncedAt) {
        authRepository.setLastPublicNonogramSyncTimestamp(newestReceivedAt)
    }
    return true
}
