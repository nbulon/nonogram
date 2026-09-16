package com.trainpaths.nonogram.auth

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth

actual suspend fun firebaseSignOut() {
    Firebase.auth.signOut()
}
