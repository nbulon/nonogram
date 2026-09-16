@file:OptIn(ExperimentalWasmJsInterop::class)

package com.trainpaths.nonogram.firebase

import com.trainpaths.nonogram.sync.Fields
import kotlinx.coroutines.await
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny

data class WebFirebaseUser(val uid: String, val displayName: String?)

/**
 * Facade over the handwritten Firebase JS SDK bindings. Everything outside this
 * package talks to Firebase only through here
 */
object FirebaseWeb {

    private var auth: Auth? = null
    private var firestore: Firestore? = null

    fun initialize(
        apiKey: String,
        authDomain: String,
        projectId: String,
        messagingSenderId: String,
        appId: String,
    ) {
        val options = JSON.parse(
            buildJsonObject {
                put("apiKey", apiKey)
                put("authDomain", authDomain)
                put("projectId", projectId)
                put("messagingSenderId", messagingSenderId)
                put("appId", appId)
            }.toString()
        )!!
        val app = initializeApp(options)
        auth = getAuth(app)
        firestore = getFirestore(app)
    }

    suspend fun signInWithGoogle(idToken: String?, accessToken: String?): WebFirebaseUser {
        val credential = GoogleAuthProvider.credential(idToken, accessToken)
        val user = signInWithCredential(requireNotNull(auth), credential).await<UserCredential>().user
        return WebFirebaseUser(user.uid, user.displayName)
    }

    suspend fun signOut() {
        val currentAuth = auth ?: return
        signOut(currentAuth).await<JsAny?>()
    }

    /** Waits for the indexedDB session restore; null when signed out. */
    suspend fun awaitSignedInUid(): String? {
        val auth = auth ?: return null
        auth.authStateReady().await()
        return auth.currentUser?.uid
    }

    internal fun requireFirestore(): Firestore =
        requireNotNull(firestore) { "FirebaseWeb.initialize not called" }

    internal fun makeProgressData(boardState: String?, updatedAt: Long): JsAny =
        JSON.parse(
            buildJsonObject {
                put(Fields.BOARD_STATE, boardState)
                put(Fields.UPDATED_AT, updatedAt)
            }.toString()
        )!!

    internal fun makeNonogramData(
        difficulty: String,
        solutionJson: String,
        name: String?,
        authorUid: String,
        updatedAt: Long,
        publishStatus: String?,
    ): JsAny =
        JSON.parse(
            buildJsonObject {
                put(Fields.DIFFICULTY, difficulty)
                put(Fields.SOLUTION, solutionJson)
                put(Fields.NAME, name)
                put(Fields.AUTHOR_UID, authorUid)
                put(Fields.UPDATED_AT, updatedAt)
                if (publishStatus != null) put(Fields.PUBLISH_STATUS, publishStatus)
            }.toString()
        )!!

    internal fun makePublishStatusData(
        publishStatus: String,
        updatedAt: Long,
        difficulty: String? = null,
        authorUid: String? = null,
    ): JsAny =
        JSON.parse(
            buildJsonObject {
                put(Fields.PUBLISH_STATUS, publishStatus)
                put(Fields.UPDATED_AT, updatedAt)
                if (difficulty != null) put(Fields.DIFFICULTY, difficulty)
                if (authorUid != null) put(Fields.AUTHOR_UID, authorUid)
            }.toString()
        )!!

    internal fun makeUserGateData(denialStreak: Int, publishBanned: Boolean): JsAny =
        JSON.parse(
            buildJsonObject {
                put(Fields.DENIAL_STREAK, denialStreak)
                put(Fields.PUBLISH_BANNED, publishBanned)
            }.toString()
        )!!

    internal fun mergeOptions(): JsAny =
        JSON.parse(buildJsonObject { put("merge", true) }.toString())!!
}
