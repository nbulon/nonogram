package com.trainpaths.nonogram.classes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trainpaths.nonogram.color

private val MIN_CARD_WIDTH = 220.dp

/** What a card's top-right dot reports instead of the puzzle's difficulty. */
enum class CardStatus(val label: String) { INVALID("Invalid"), UNPUBLISHED("Valid"), PUBLISHED("Public") }

val Nonogram.cardStatus: CardStatus
    get() = when {
        isPublic -> CardStatus.PUBLISHED
        !isKnownValid -> CardStatus.INVALID
        else -> CardStatus.UNPUBLISHED
    }

@Composable
fun NonogramGrid(
    modifier: Modifier = Modifier,
    content: LazyGridScope.() -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed((maxWidth / MIN_CARD_WIDTH).toInt().coerceAtLeast(2)),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
fun NonogramCard(
    nonogram: Nonogram,
    modifier: Modifier = Modifier,
    progress: List<List<Int>> = emptyList(),
    beatCount: Long? = null,
    isOwn: Boolean = false,
    alwaysShowName: Boolean = true,
    status: CardStatus? = null,
    onShow: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val isBeaten = (beatCount ?: 0L) > 0L
    val showsSolution =
        (beatCount == null || isBeaten) && progress.all { row -> row.all { it == 0 } }
    val nameVisible = alwaysShowName || isBeaten || isOwn
    val accent = when {
        isBeaten -> MaterialTheme.colorScheme.tertiary
        isOwn -> MaterialTheme.colorScheme.onSecondary
        else -> MaterialTheme.colorScheme.onPrimary
    }
    val shape = RoundedCornerShape(6.dp)

    Box(modifier = modifier.fillMaxWidth().height(220.dp)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 4.dp, y = 4.dp)
                .background(color = accent, shape = shape)
        )
        Card(
            modifier = Modifier
                .matchParentSize()
                .clickable(onClick = onClick),
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.outline,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(modifier = Modifier.fillMaxHeight().padding(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (nameVisible) (nonogram.name
                                ?: UNNAMED_NONOGRAM_TITLE) else UNNAMED_NONOGRAM_TITLE,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp, top = 4.dp)
                            .size(12.dp)
                            .background(
                                color = when (status) {
                                    CardStatus.INVALID -> MaterialTheme.colorScheme.tertiaryFixed
                                    CardStatus.UNPUBLISHED -> MaterialTheme.colorScheme.tertiary
                                    CardStatus.PUBLISHED -> MaterialTheme.colorScheme.onTertiary
                                    null -> nonogram.difficulty.color()
                                },
                                shape = CircleShape,
                            )
                    )
                }
                Text(
                    text = "${nonogram.width}x${nonogram.height}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Row(Modifier.fillMaxSize()) {
                    DrawNonogram(if (showsSolution) nonogram.solution else progress)
                }
            }
        }
        if (onShow != null && isBeaten) {
            TextButton(
                onClick = onShow,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .background(MaterialTheme.colorScheme.outline, shape),
            ) {
                Text("Show")
            }
        }
    }
}

@Composable
fun DrawNonogram(progress: List<List<Int>>) {
    val rows = progress.size
    val cols = progress.firstOrNull()?.size ?: 0
    if (rows == 0 || cols == 0) return

    Canvas(Modifier.fillMaxSize()) {
        val cell = minOf(size.width / cols, size.height / rows)
        val originX = (size.width - cell * cols) / 2f
        val originY = (size.height - cell * rows) / 2f

        progress.forEachIndexed { rowIndex, row ->
            row.forEachIndexed { colIndex, tile ->
                drawRect(
                    color = if (tile == 1) Color.Black else Color.White,
                    topLeft = Offset(originX + colIndex * cell, originY + rowIndex * cell),
                    size = Size(cell, cell),
                )
            }
        }
    }
}
