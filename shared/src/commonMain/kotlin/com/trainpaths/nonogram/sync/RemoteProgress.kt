package com.trainpaths.nonogram.sync

import com.trainpaths.nonogram.AppSDK

/** A `users/{uid}/progress/{nonogramId}` document; the same three fields on both platforms. */
internal data class RemoteProgress(
    val nonogramId: Long,
    val boardState: String?,
    val updatedAt: Long,
)

/**
 * Merge policy for pulled progress, shared by both platform implementations: remote newer →
 * overwrite locally, local newer → push it back, and a row the remote has never seen → push it.
 * [remotes] is always the whole collection, which is what makes the last rule safe.
 */
internal suspend fun SyncService.mergeRemoteProgress(
    sdk: AppSDK,
    firebaseUid: String,
    remotes: List<RemoteProgress>,
) {
    for ((nonogramId, boardState, updatedAt) in remotes) {
        val local = sdk.getSingleProgress(firebaseUid, nonogramId)
        if (local == null || local.updatedAt < updatedAt) {
            sdk.saveProgressWithTimestamp(firebaseUid, nonogramId, boardState, updatedAt)
        } else if (local.updatedAt > updatedAt) {
            pushProgress(firebaseUid, nonogramId, local.boardState, local.updatedAt)
        }
    }
    val remoteIds = remotes.mapTo(HashSet()) { it.nonogramId }
    for ((nonogramId, boardState, updatedAt) in sdk.getProgressForUserWithTimestamp(firebaseUid)) {
        if (nonogramId !in remoteIds) pushProgress(firebaseUid, nonogramId, boardState, updatedAt)
    }
}
