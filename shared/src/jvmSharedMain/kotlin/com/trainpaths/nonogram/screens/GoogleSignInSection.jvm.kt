package com.trainpaths.nonogram.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.mmk.kmpauth.core.auth.KMPAuthUserCancelledException
import com.mmk.kmpauth.google.rememberGoogleAuthState
import com.mmk.kmpauth.uihelper.google.GoogleSignInButton
import com.trainpaths.nonogram.BUTTON_SHAPE

@Composable
actual fun GoogleSignInSection(
    onSignedIn: (uid: String, displayName: String?) -> Unit,
    modifier: Modifier,
) {
    val googleAuth = rememberGoogleAuthState { result ->
        result
            .onSuccess { user -> onSignedIn(user.uid, user.displayName) }
            .onFailure { error ->
                if (error !is KMPAuthUserCancelledException) {
                    println("Error Result: ${error.message}")
                }
            }
    }

    GoogleSignInButton(
        modifier = modifier,
        shape = BUTTON_SHAPE,
        fontSize = 19.sp,
        text = "Sign in with Google"
    ) { googleAuth.launch() }
}
