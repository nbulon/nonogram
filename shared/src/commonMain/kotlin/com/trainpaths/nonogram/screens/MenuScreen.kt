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
import androidx.compose.runtime.remember
import com.trainpaths.nonogram.navigation.AppBarMode
import com.trainpaths.nonogram.navigation.TopAppBar
import com.trainpaths.nonogram.MAX_CONTENT_WIDTH
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
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
    onShowClick: (Nonogram) -> Unit,
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
                NonogramGrid(modifier = Modifier.pullToRefreshByTouchOnly()) {
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
                            onShow = { onShowClick(nonogram) },
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

/** Pull-to-refresh follows a finger only: a wheel scroll is UserInput too but never flings, so it would never let go. */
@Composable
private fun Modifier.pullToRefreshByTouchOnly(): Modifier {
    val gate = remember { TouchOnlyPullGate() }
    return pointerInput(gate) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                gate.touchDown = event.changes.any { it.pressed && it.type != PointerType.Mouse }
            }
        }
    }.nestedScroll(gate)
}

private class TouchOnlyPullGate : NestedScrollConnection {
    var touchDown = false

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        if (touchDown) Offset.Zero else Offset(0f, available.y)
}
