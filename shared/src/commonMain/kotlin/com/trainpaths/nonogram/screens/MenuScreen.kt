package com.trainpaths.nonogram.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.trainpaths.nonogram.navigation.AppBarMode
import com.trainpaths.nonogram.navigation.TopAppBar
import com.trainpaths.nonogram.MAX_CONTENT_WIDTH
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.trainpaths.nonogram.classes.Nonogram
import com.trainpaths.nonogram.screens.viewModel.MenuViewModel
import com.trainpaths.nonogram.classes.NonogramCard
import com.trainpaths.nonogram.classes.NonogramGrid
import com.trainpaths.nonogram.filter.FilterMenuButton
import com.trainpaths.nonogram.tutorial.TutorialStep
import com.trainpaths.nonogram.tutorial.tutorialAnchor

@Composable
fun MenuScreen(
    viewModel: MenuViewModel,
    onRefresh: () -> Unit,
    onNonogramClick: (Nonogram) -> Unit,
    onGenClick: () -> Unit,
    onMoreFilters: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopAppBar(
            showSettings = true,
            mode = AppBarMode.PUZZLE,
            onSwapMode = { onGenClick() },
            swapTutorialStep = TutorialStep.MENU_SWAP_TO_GENERATOR,
            navigationContent = {
                Box(modifier = Modifier.tutorialAnchor(TutorialStep.MENU_FILTER)) {
                    FilterMenuButton(
                        entries = viewModel.filterEntries,
                        state = viewModel.filterSort,
                        onApply = viewModel::applyFilterSort,
                        onMore = onMoreFilters,
                    )
                }
            },
        )

        if (viewModel.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary)
            }
        } else {
            val pullState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = viewModel.isRefreshing,
                onRefresh = onRefresh,
                state = pullState,
                modifier = Modifier.widthIn(max = MAX_CONTENT_WIDTH).fillMaxSize(),
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = pullState,
                        isRefreshing = viewModel.isRefreshing,
                        modifier = Modifier.align(Alignment.TopCenter),
                        containerColor = MaterialTheme.colorScheme.primary,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                },
            ) {
                val visible = viewModel.visibleNonograms
                val showAllNames by viewModel.showNames.collectAsState()
                NonogramGrid {
                    itemsIndexed(visible) { index, nonogram ->
                        NonogramCard(
                            nonogram = nonogram,
                            modifier = Modifier.tutorialAnchor(
                                TutorialStep.MENU_PLAY.takeIf { index == 0 }
                            ),
                            progress = viewModel.getProgress(nonogram.id),
                            beatCount = viewModel.getBeatCount(nonogram.id),
                            isOwn = nonogram.isOwned(viewModel.authorUid),
                            alwaysShowName = showAllNames,
                            onClick = { onNonogramClick(nonogram) })
                    }
                }
                if (visible.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No puzzles match",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }
        }
    }
}
