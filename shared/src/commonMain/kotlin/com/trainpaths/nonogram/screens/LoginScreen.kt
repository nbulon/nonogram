package com.trainpaths.nonogram.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trainpaths.nonogram.BUTTON_SHAPE
import com.trainpaths.nonogram.screens.viewModel.AuthViewModel
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing

@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    onLoginSuccess: () -> Unit,
    onContinueAsGuest: () -> Unit,
) {
    var isSigningIn by remember { mutableStateOf(false) }
    val signInComplete by authViewModel.signInComplete.collectAsState()

    LaunchedEffect(signInComplete) {
        if (signInComplete && isSigningIn) {
            isSigningIn = false
            onLoginSuccess()
        }
    }

    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Nonogram",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onPrimary,
        )

        Spacer(modifier = Modifier.height(48.dp))

        if (isSigningIn) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Syncing progress...", color = MaterialTheme.colorScheme.onPrimary)
        } else {
            Column(
                modifier = Modifier.width(280.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                GoogleSignInSection(
                    onSignedIn = { uid, displayName ->
                        isSigningIn = true
                        authViewModel.onFirebaseSignInSuccess(uid, displayName)
                    },
                    modifier = Modifier.height(48.dp),
                )

                OutlinedButton(
                    onClick = {
                        authViewModel.completeOnboarding()
                        onContinueAsGuest()
                    },
                    shape = BUTTON_SHAPE,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSecondary,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Text("Continue as Guest")
                }
            }
        }
    }
}
