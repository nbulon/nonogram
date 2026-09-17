package com.trainpaths.nonogram.sync

import com.trainpaths.nonogram.AppSDK
import com.trainpaths.nonogram.classes.Difficulty
import com.trainpaths.nonogram.classes.Nonogram
import com.trainpaths.nonogram.classes.PublishStatus
import com.trainpaths.nonogram.classes.toSolutionJson
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.firestore
import kotlinx.coroutines.CancellationException
import kotlin.time.Clock

private const val LOG_TAG = "FirestoreSync"

class FirebaseJvmSyncService(private val sdk: AppSDK) : SyncService {

    private val firestore = Firebase.firestore

    private fun progressCollection(firebaseUid: String) =
        firestore.collection(Paths.USERS).document(firebaseUid).collection(Paths.PROGRESS)

    private fun nonogramsCollection() = firestore.collection(Paths.NONOGRAMS)

    private fun userDocument(firebaseUid: String) =
        firestore.collection(Paths.USERS).document(firebaseUid)

    /**
     * Every Firestore call is best-effort: a failed sync degrades to "not synced yet", never to a
     * crash, so each one reports [label] and falls back to [fallback]. Cancellation is rethrown, so
     * a timed-out sync stops instead of walking on.
     */
    private inline fun <T> logged(label: String, fallback: T, block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        println("$LOG_TAG: $label: ${e.message}")
        fallback
    }

    private suspend fun fetchProgress(firebaseUid: String): List<RemoteProgress> =
        progressCollection(firebaseUid).get().documents.mapNotNull { doc ->
            val nonogramId = doc.id.toLongOrNull() ?: return@mapNotNull null
            RemoteProgress(
                nonogramId = nonogramId,
                boardState = doc.get<String?>(Fields.BOARD_STATE),
                updatedAt = doc.get<Long>(Fields.UPDATED_AT),
            )
        }

    override suspend fun pushProgress(firebaseUid: String, nonogramId: Long, boardState: String?, updatedAt: Long) =
        logged("push failed for nonogram $nonogramId", Unit) {
            progressCollection(firebaseUid)
                .document(nonogramId.toString())
                .set(mapOf(Fields.BOARD_STATE to boardState, Fields.UPDATED_AT to updatedAt))
        }

    override suspend fun hasRemoteProgress(firebaseUid: String): Boolean =
        logged("check remote failed", false) { fetchProgress(firebaseUid).isNotEmpty() }

    override suspend fun uploadAllLocalProgress(firebaseUid: String) =
        logged("upload all failed", Unit) { uploadAllProgress(sdk, firebaseUid) }

    override suspend fun pullAllProgress(firebaseUid: String) =
        logged("pull all failed", Unit) {
            applyRemoteProgress(sdk, firebaseUid, fetchProgress(firebaseUid))
        }

    override suspend fun pullAndMergeAllProgress(firebaseUid: String) =
        logged("pull and merge failed", Unit) {
            mergeRemoteProgress(sdk, firebaseUid, fetchProgress(firebaseUid))
        }

    override suspend fun pushNonogram(firebaseUid: String, nonogram: Nonogram, writePublishStatus: Boolean) =
        logged("push nonogram ${nonogram.id} failed", Unit) {
            val fields = buildMap<String, Any?> {
                put(Fields.DIFFICULTY, nonogram.difficulty.toString())
                put(Fields.SOLUTION, nonogram.solution.toSolutionJson())
                put(Fields.NAME, nonogram.name)
                put(Fields.AUTHOR_UID, firebaseUid)
                put(Fields.UPDATED_AT, nonogram.updatedAt)
                if (writePublishStatus) put(Fields.PUBLISH_STATUS, nonogram.publishStatus.name)
            }
            // Merge, so an ordinary save never clobbers a pending or approved publish status.
            nonogramsCollection().document(nonogram.id.toString()).set(fields, merge = true)
        }

    override suspend fun uploadAllLocalNonograms(firebaseUid: String) =
        logged("upload all nonograms failed", Unit) {
            for (nonogram in sdk.getNonogramsByAuthor(firebaseUid)) {
                pushNonogram(firebaseUid, nonogram)
            }
        }

    override suspend fun pullPublicNonogramsSince(firebaseUid: String?, since: Long): Long? =
        logged("pull public nonograms for puzzle list failed", null) {
            val documents = nonogramsCollection()
                .where { Fields.PUBLISH_STATUS equalTo PublishStatus.APPROVED.name }
                .where { Fields.UPDATED_AT greaterThan since }
                .get().documents
            mergeRemoteNonograms(sdk, firebaseUid, since, parseNonograms(documents))
        }

    override suspend fun pullOwnedNonograms(firebaseUid: String, since: Long): Long? =
        logged("pull owned nonograms for Generator failed", null) {
            val documents = nonogramsCollection()
                .where { Fields.AUTHOR_UID equalTo firebaseUid }
                .where { Fields.UPDATED_AT greaterThan since }
                .get().documents
            mergeRemoteNonograms(sdk, firebaseUid, since, parseNonograms(documents))
        }

    override suspend fun requestPublish(firebaseUid: String, nonogram: Nonogram): Boolean =
        logged("publish request for nonogram ${nonogram.id} rejected", false) {
            nonogramsCollection().document(nonogram.id.toString()).set(
                mapOf(
                    Fields.PUBLISH_STATUS to PublishStatus.PENDING.name,
                    Fields.UPDATED_AT to nonogram.updatedAt,
                ),
                merge = true,
            )
            true
        }

    override suspend fun deleteNonogram(firebaseUid: String, nonogramId: Long): Boolean =
        logged("delete of nonogram $nonogramId rejected", false) {
            nonogramsCollection().document(nonogramId.toString()).set(
                mapOf(
                    Fields.PUBLISH_STATUS to PublishStatus.DELETED.name,
                    Fields.UPDATED_AT to Clock.System.now().toEpochMilliseconds(),
                    Fields.AUTHOR_UID to firebaseUid,
                ),
                merge = true,
            )
            true
        }

    override suspend fun fetchModerationGate(firebaseUid: String): ModerationGate? =
        logged("moderation gate read failed", null) { readModerationGate(firebaseUid) }

    private suspend fun readModerationGate(firebaseUid: String): ModerationGate {
        val snapshot = userDocument(firebaseUid).get()
        if (!snapshot.exists) return ModerationGate()
        val streak = snapshot.get<Long?>(Fields.DENIAL_STREAK)?.toInt() ?: 0
        return ModerationGate(streak, snapshot.get<Boolean?>(Fields.PUBLISH_BANNED) ?: isPublishBanned(streak))
    }

    override suspend fun isAdmin(firebaseUid: String): Boolean =
        logged("admin check failed", false) {
            firestore.collection(Paths.ADMINS).document(firebaseUid).get().exists
        }

    override suspend fun pullPendingReviews(firebaseUid: String, limit: Int): List<Nonogram> =
        logged("pending review fetch failed", emptyList()) {
            parseNonograms(
                nonogramsCollection()
                    .where { Fields.PUBLISH_STATUS equalTo PublishStatus.PENDING.name }
                    .orderBy(Fields.UPDATED_AT)
                    .limit(limit)
                    .get().documents
            )
        }

    override suspend fun decideReview(
        firebaseUid: String,
        nonogram: Nonogram,
        approve: Boolean,
        difficulty: Difficulty,
    ): Boolean = logged("decision on nonogram ${nonogram.id} failed", false) {
        val fields = buildMap<String, Any?> {
            put(
                Fields.PUBLISH_STATUS,
                (if (approve) PublishStatus.APPROVED else PublishStatus.DENIED).name,
            )
            put(Fields.UPDATED_AT, nonogram.updatedAt)
            if (approve) {
                put(Fields.DIFFICULTY, difficulty.name)
                put(Fields.NAME, nonogram.name)
            }
        }
        nonogramsCollection().document(nonogram.id.toString()).set(fields, merge = true)
        val streak = nextDenialStreak(
            current = fetchModerationGate(nonogram.authorUid)?.denialStreak ?: 0,
            approved = approve,
        )
        userDocument(nonogram.authorUid).set(
            mapOf(
                Fields.DENIAL_STREAK to streak.toLong(),
                Fields.PUBLISH_BANNED to isPublishBanned(streak),
            ),
            merge = true,
        )
        true
    }

    private fun parseNonograms(documents: List<DocumentSnapshot>): List<Nonogram> =
        documents.mapNotNull { doc ->
            val skip = { reason: String ->
                println("$LOG_TAG: skipping malformed nonogram doc ${doc.id}: $reason")
            }
            try {
                doc.toDocument().toNonogram(skip)
            } catch (e: Exception) {
                skip("unreadable fields: ${e.message}")
                null
            }
        }

    private fun DocumentSnapshot.toDocument() = NonogramDocument(
        id = id,
        difficulty = get<String?>(Fields.DIFFICULTY),
        solution = get<String?>(Fields.SOLUTION),
        name = get<String?>(Fields.NAME),
        authorUid = get<String?>(Fields.AUTHOR_UID),
        updatedAt = get<Long?>(Fields.UPDATED_AT),
        publishStatus = get<String?>(Fields.PUBLISH_STATUS),
    )
}
