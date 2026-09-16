package com.trainpaths.nonogram.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trainpaths.nonogram.classes.Nonogram
import com.trainpaths.nonogram.classes.NonogramCard
import com.trainpaths.nonogram.classes.NonogramGrid
import com.trainpaths.nonogram.classes.cardStatus
import com.trainpaths.nonogram.filter.FilterMenuButton
import com.trainpaths.nonogram.navigation.AppBarMode
import com.trainpaths.nonogram.navigation.TopAppBar
import com.trainpaths.nonogram.AppButton
import com.trainpaths.nonogram.BUTTON_SHAPE
import com.trainpaths.nonogram.MAX_CONTENT_WIDTH
import com.trainpaths.nonogram.screens.viewModel.GenViewModel
import com.trainpaths.nonogram.screens.viewModel.GeneratorSyncState
import com.trainpaths.nonogram.tutorial.TutorialStep
import com.trainpaths.nonogram.tutorial.tutorialAnchor

@Composable
fun GenListScreen(
    genViewModel: GenViewModel,
    generatorSyncState: GeneratorSyncState,
    onRetrySync: () -> Unit,
    onSwap: () -> Unit,
    onNewClick: () -> Unit,
    onScanClick: () -> Unit,
    onEditClick: (Nonogram) -> Unit,
    onMoreFilters: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopAppBar(
            showSettings = true,
            mode = AppBarMode.GENERATOR,
            onSwapMode = { onSwap() },
            swapTutorialStep = TutorialStep.GENLIST_SWAP_TO_PUZZLES,
            navigationContent = {
                Box(modifier = Modifier.tutorialAnchor(TutorialStep.GENLIST_FILTER)) {
                    FilterMenuButton(
                        entries = genViewModel.filterEntries,
                        state = genViewModel.filterSort,
                        onApply = genViewModel::applyFilterSort,
                        onMore = onMoreFilters,
                    )
                }
            },
        )

        Column(modifier = Modifier.widthIn(max = MAX_CONTENT_WIDTH).fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppButton(
                    text = "+ New",
                    onClick = onNewClick,
                    modifier = Modifier
                        .weight(1f)
                        .tutorialAnchor(TutorialStep.GENLIST_NEW),
                )

                AppButton(
                    text = "Scan image",
                    onClick = onScanClick,
                    modifier = Modifier
                        .weight(1f)
                        .tutorialAnchor(TutorialStep.GENLIST_SCAN),
                )
            }

            when (generatorSyncState) {
                GeneratorSyncState.IDLE -> Unit
                GeneratorSyncState.SYNCING -> {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }

                GeneratorSyncState.ERROR -> {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.outline),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = "Your nonograms couldn't be synced. Check your connection and try again.",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            OutlinedButton(onClick = onRetrySync, shape = BUTTON_SHAPE) {
                                Text("Retry")
                            }
                        }
                    }
                }
            }

            if (genViewModel.isLoadingMine) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
                }
            } else if (genViewModel.myNonograms.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "You haven't created any nonograms yet.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            } else if (genViewModel.visibleNonograms.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No puzzles match",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            } else {
                NonogramGrid {
                    itemsIndexed(genViewModel.visibleNonograms) { index, nonogram ->
                        NonogramCard(
                            nonogram = nonogram,
                            modifier = Modifier.tutorialAnchor(
                                TutorialStep.GENLIST_EDIT.takeIf { index == 0 }
                            ),
                            status = nonogram.cardStatus,
                            onClick = { onEditClick(nonogram) },
                        )
                    }
                }
            }
        }
    }
}
