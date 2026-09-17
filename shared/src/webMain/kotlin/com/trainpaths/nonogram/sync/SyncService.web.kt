@file:OptIn(ExperimentalWasmJsInterop::class)

package com.trainpaths.nonogram.sync

import com.trainpaths.nonogram.AppSDK
import com.trainpaths.nonogram.classes.Difficulty
import com.trainpaths.nonogram.classes.Nonogram
import com.trainpaths.nonogram.classes.PublishStatus
import com.trainpaths.nonogram.classes.toSolutionJson
import com.trainpaths.nonogram.firebase.FirebaseWeb
import com.trainpaths.nonogram.firebase.NonogramDocSnapshot
import com.trainpaths.nonogram.firebase.NonogramQuerySnapshot
import com.trainpaths.nonogram.firebase.collection
import com.trainpaths.nonogram.firebase.doc
import com.trainpaths.nonogram.firebase.getExistsDoc
import com.trainpaths.nonogram.firebase.getNonogramDocs
import com.trainpaths.nonogram.firebase.getProgressDocs
import com.trainpaths.nonogram.firebase.getUserGateDoc
import com.trainpaths.nonogram.firebase.limit as limitTo
import com.trainpaths.nonogram.firebase.orderBy
import com.trainpaths.nonogram.firebase.query
import com.trainpaths.nonogram.firebase.query3
import com.trainpaths.nonogram.firebase.setDoc
import com.trainpaths.nonogram.firebase.setDocMerged
import com.trainpaths.nonogram.firebase.where
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.await
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.toJsNumber
import kotlin.js.toJsString
import kotlin.time.Clock

private const val LOG_TAG = "FirestoreSync(web)"

class FirebaseWebSyncService(private val sdk: AppSDK) : SyncService {

    // Firestore rejects ops until the JS SDK restores its indexedDB session, so gate on it.
    private suspend fun sessionMatches(firebaseUid: String): Boolean {
        val liveUid = FirebaseWeb.awaitSignedInUid()
        if (liveUid != firebaseUid) {
            println("$LOG_TAG: no live session for the requested user")
        }
        return liveUid == firebaseUid
    }

    /**
     * Waits for the same session restore as [sessionMatches] but does not require a match: the
     * public pull must also work signed out, where approved docs are readable without a session.
     */
    private suspend fun awaitSessionSettled() {
        FirebaseWeb.awaitSignedInUid()
    }

    /**
     * The session gate plus the best-effort contract, in one place: no override can forget either.
     * Reports [label] and falls back to [fallback] both when there is no live session and when the
     * call itself fails. Cancellation is rethrown, so a timed-out sync stops instead of walking on.
     */
    private suspend inline fun <T> gated(firebaseUid: String, label: String, fallback: T, block: () -> T): T = try {
        if (!sessionMatches(firebaseUid)) fallback else block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        println("$LOG_TAG: $label: ${e.message}")
        fallback
    }

    private suspend fun fetchProgress(firebaseUid: String): List<RemoteProgress> {
        val snapshot =
            getProgressDocs(collection(FirebaseWeb.requireFirestore(), Paths.progress(firebaseUid)))
                .await()
        val result = mutableListOf<RemoteProgress>()
        snapshot.forEach { docSnapshot ->
            val nonogramId = docSnapshot.id.toLongOrNull()
            if (nonogramId != null) {
                val data = docSnapshot.data()
                result += RemoteProgress(nonogramId, data.boardState, data.updatedAt.toLong())
            }
        }
        return result
    }

    override suspend fun pushProgress(firebaseUid: String, nonogramId: Long, boardState: String?, updatedAt: Long) =
        gated(firebaseUid, "push failed for nonogram $nonogramId", Unit) {
            val reference = doc(FirebaseWeb.requireFirestore(), Paths.progressDoc(firebaseUid, nonogramId))
            setDoc(reference, FirebaseWeb.makeProgressData(boardState, updatedAt)).await()
            Unit
        }

    override suspend fun pullAndMergeAllProgress(firebaseUid: String) =
        gated(firebaseUid, "pull and merge failed", Unit) {
            mergeRemoteProgress(sdk, firebaseUid, fetchProgress(firebaseUid))
        }

    override suspend fun pushNonogram(firebaseUid: String, nonogram: Nonogram, writePublishStatus: Boolean) =
        gated(firebaseUid, "push nonogram ${nonogram.id} failed", Unit) {
            val reference = doc(FirebaseWeb.requireFirestore(), Paths.nonogram(nonogram.id))
            // Merge, so an ordinary save never clobbers a pending or approved publish status.
            setDocMerged(
                reference,
                FirebaseWeb.makeNonogramData(
                    difficulty = nonogram.difficulty.toString(),
                    solutionJson = nonogram.solution.toSolutionJson(),
                    name = nonogram.name,
                    authorUid = firebaseUid,
                    updatedAt = nonogram.updatedAt,
                    publishStatus = if (writePublishStatus) nonogram.publishStatus.name else null,
                ),
                FirebaseWeb.mergeOptions(),
            ).await()
            Unit
        }

    override suspend fun pullPublicNonogramsSince(firebaseUid: String?, since: Long): Long? {
        awaitSessionSettled()
        return try {
            val snapshot = getNonogramDocs(
                query(
                    collection(FirebaseWeb.requireFirestore(), Paths.NONOGRAMS),
                    where(Fields.PUBLISH_STATUS, "==", PublishStatus.APPROVED.name.toJsString()),
                    where(Fields.UPDATED_AT, ">", since.toDouble().toJsNumber()),
                )
            ).await()
            mergeRemoteNonograms(sdk, firebaseUid, since, parseNonograms(snapshot))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            println("$LOG_TAG: pull public nonograms for puzzle list failed: ${e.message}")
            null
        }
    }

    override suspend fun pullOwnedNonograms(firebaseUid: String, since: Long): Long? =
        gated(firebaseUid, "pull owned nonograms for Generator failed", null) {
            val snapshot = getNonogramDocs(
                query(
                    collection(FirebaseWeb.requireFirestore(), Paths.NONOGRAMS),
                    where(Fields.AUTHOR_UID, "==", firebaseUid.toJsString()),
                    where(Fields.UPDATED_AT, ">", since.toDouble().toJsNumber()),
                )
            ).await()
            mergeRemoteNonograms(sdk, firebaseUid, since, parseNonograms(snapshot), pushLocalOnly = since == 0L)
        }

    override suspend fun requestPublish(firebaseUid: String, nonogram: Nonogram): Boolean =
        gated(firebaseUid, "publish request for nonogram ${nonogram.id} rejected", false) {
            setDocMerged(
                doc(FirebaseWeb.requireFirestore(), Paths.nonogram(nonogram.id)),
                FirebaseWeb.makePublishStatusData(PublishStatus.PENDING.name, nonogram.updatedAt),
                FirebaseWeb.mergeOptions(),
            ).await()
            true
        }

    override suspend fun deleteNonogram(firebaseUid: String, nonogramId: Long): Boolean =
        gated(firebaseUid, "delete of nonogram $nonogramId rejected", false) {
            setDocMerged(
                doc(FirebaseWeb.requireFirestore(), Paths.nonogram(nonogramId)),
                FirebaseWeb.makePublishStatusData(
                    publishStatus = PublishStatus.DELETED.name,
                    updatedAt = Clock.System.now().toEpochMilliseconds(),
                    authorUid = firebaseUid,
                ),
                FirebaseWeb.mergeOptions(),
            ).await()
            true
        }

    override suspend fun fetchModerationGate(firebaseUid: String): ModerationGate? =
        gated(firebaseUid, "moderation gate read failed", null) { readModerationGate(firebaseUid) }

    private suspend fun readModerationGate(firebaseUid: String): ModerationGate {
        val snapshot = getUserGateDoc(doc(FirebaseWeb.requireFirestore(), Paths.user(firebaseUid)))
            .await()
        if (!snapshot.exists()) return ModerationGate()
        val data = snapshot.data() ?: return ModerationGate()
        val streak = data.denialStreak?.toInt() ?: 0
        return ModerationGate(streak, data.publishBanned ?: isPublishBanned(streak))
    }

    override suspend fun isAdmin(firebaseUid: String): Boolean =
        gated(firebaseUid, "admin check failed", false) {
            getExistsDoc(doc(FirebaseWeb.requireFirestore(), Paths.admin(firebaseUid)))
                .await()
                .exists()
        }

    override suspend fun pullPendingReviews(firebaseUid: String, limit: Int): List<Nonogram> =
        gated(firebaseUid, "pending review fetch failed", emptyList()) {
            parseNonograms(
                getNonogramDocs(
                    query3(
                        collection(FirebaseWeb.requireFirestore(), Paths.NONOGRAMS),
                        where(Fields.PUBLISH_STATUS, "==", PublishStatus.PENDING.name.toJsString()),
                        orderBy(Fields.UPDATED_AT),
                        limitTo(limit),
                    )
                ).await()
            )
        }

    override suspend fun decideReview(
        firebaseUid: String,
        nonogram: Nonogram,
        approve: Boolean,
        difficulty: Difficulty,
    ): Boolean = gated(firebaseUid, "decision on nonogram ${nonogram.id} failed", false) {
        val firestore = FirebaseWeb.requireFirestore()
        setDocMerged(
            doc(firestore, Paths.nonogram(nonogram.id)),
            FirebaseWeb.makePublishStatusData(
                publishStatus = (if (approve) PublishStatus.APPROVED else PublishStatus.DENIED).name,
                updatedAt = nonogram.updatedAt,
                difficulty = difficulty.name.takeIf { approve },
                writeName = approve,
                name = nonogram.name,
            ),
            FirebaseWeb.mergeOptions(),
        ).await()
        val streak = nextDenialStreak(readModerationGate(nonogram.authorUid).denialStreak, approve)
        setDocMerged(
            doc(firestore, Paths.user(nonogram.authorUid)),
            FirebaseWeb.makeUserGateData(streak, isPublishBanned(streak)),
            FirebaseWeb.mergeOptions(),
        ).await()
        true
    }

    private fun parseNonograms(snapshot: NonogramQuerySnapshot): List<Nonogram> = buildList {
        // The JS forEach callback cannot suspend, so parsing stays synchronous here.
        snapshot.forEach { docSnapshot ->
            val skip = { reason: String ->
                println("$LOG_TAG: skipping malformed nonogram doc ${docSnapshot.id}: $reason")
            }
            try {
                val nonogram = docSnapshot.toDocument().toNonogram(skip)
                if (nonogram != null) add(nonogram)
            } catch (e: Throwable) {
                skip("unreadable fields: ${e.message}")
            }
        }
    }

    private fun NonogramDocSnapshot.toDocument(): NonogramDocument {
        val data = data()
        return NonogramDocument(
            id = id,
            difficulty = data.difficulty,
            solution = data.solution,
            name = data.name,
            authorUid = data.authorUid,
            updatedAt = data.updatedAt?.toLong(),
            publishStatus = data.publishStatus,
        )
    }
}
