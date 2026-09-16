package com.trainpaths.nonogram.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.trainpaths.nonogram.AppButton
import com.trainpaths.nonogram.color
import com.trainpaths.nonogram.classes.Difficulty
import com.trainpaths.nonogram.classes.DrawNonogram
import com.trainpaths.nonogram.classes.Nonogram
import com.trainpaths.nonogram.classes.UNNAMED_NONOGRAM_TITLE
import com.trainpaths.nonogram.navigation.TopAppBar
import com.trainpaths.nonogram.MAX_CONTENT_WIDTH
import com.trainpaths.nonogram.outlinedFieldColors
import com.trainpaths.nonogram.screens.viewModel.AdminViewModel

@Composable
fun AdminScreen(
    adminViewModel: AdminViewModel,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopAppBar(title = "Admin panel", onBack = onBack, backArrow = true)

        val pending = adminViewModel.current
        when {
            adminViewModel.isLoading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
            }

            pending == null -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                val failure = adminViewModel.error
                Text(
                    failure ?: "No pending requests.",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (failure != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onPrimary
                    },
                )
            }

            else -> ReviewCard(nonogram = pending, adminViewModel = adminViewModel)
        }
    }
}

@Composable
private fun ReviewCard(nonogram: Nonogram, adminViewModel: AdminViewModel) {
    val isDeciding = adminViewModel.isDeciding
    Column(
        modifier = Modifier.widthIn(max = MAX_CONTENT_WIDTH).fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        NameField(
            value = adminViewModel.name,
            onValueChange = adminViewModel::updateName,
            colors = outlinedFieldColors(),
            enabled = !isDeciding,
            placeholder = UNNAMED_NONOGRAM_TITLE,
        )
        Text(
            "${nonogram.width}x${nonogram.height}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .aspectRatio(1f)
                .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(12.dp))
                .padding(12.dp),
        ) {
            DrawNonogram(nonogram.solution)
        }

        adminViewModel.error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Difficulty.entries.forEach { difficulty ->
                val selected = difficulty == adminViewModel.selectedDifficulty
                val accent = difficulty.color()
                AppButton(
                    text = difficulty.label,
                    onClick = { adminViewModel.selectDifficulty(difficulty) },
                    enabled = !isDeciding,
                    height = 40.dp,
                    containerColor = if (selected) accent else MaterialTheme.colorScheme.outline,
                    contentColor = if (selected) MaterialTheme.colorScheme.outline else accent,
                    textStyle = MaterialTheme.typography.bodySmall,
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppButton(
                text = "Deny",
                onClick = { adminViewModel.deny() },
                enabled = !isDeciding,
                containerColor = MaterialTheme.colorScheme.tertiaryFixed,
                contentColor = MaterialTheme.colorScheme.outline,
                modifier = Modifier.weight(1f),
            )
            AppButton(
                text = "Accept",
                onClick = { adminViewModel.accept() },
                enabled = !isDeciding && adminViewModel.nameProblem == null,
                containerColor = MaterialTheme.colorScheme.onTertiary,
                contentColor = MaterialTheme.colorScheme.outline,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
