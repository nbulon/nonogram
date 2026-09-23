package com.trainpaths.nonogram.dialogs

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.trainpaths.nonogram.classes.DrawNonogram
import com.trainpaths.nonogram.classes.Nonogram
import com.trainpaths.nonogram.classes.UNNAMED_NONOGRAM_TITLE
import com.trainpaths.nonogram.icons.close
import com.trainpaths.nonogram.icons.home
import com.trainpaths.nonogram.icons.refresh

/**
 * A beaten puzzle's solution as a picture on a card. Opened by a win ([won]: the art rises in from
 * where the board left, with Home/Restart) or by a menu card's Show button (static, close only).
 */
@Composable
fun ArtCardDialog(
    nonogram: Nonogram,
    won: Boolean,
    onHome: () -> Unit,
    onRestart: () -> Unit,
    onClose: () -> Unit,
) {
    var settled by remember { mutableStateOf(!won) }
    LaunchedEffect(Unit) { settled = true }
    val rise = animateFloatAsState(
        targetValue = if (settled) 1f else 0f,
        animationSpec = tween(450),
        label = "artRise",
    )
    val shape = RoundedCornerShape(6.dp)
    val fadeIn = Modifier.graphicsLayer { alpha = rise.value }

    Dialog(
        onDismissRequest = if (won) onRestart else onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.padding(24.dp).widthIn(max = 360.dp)) {
            Box(modifier = Modifier.matchParentSize().then(fadeIn)) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .offset(x = 4.dp, y = 4.dp)
                        .background(MaterialTheme.colorScheme.tertiary, shape)
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.outline, shape)
                )
            }
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (won) "You won!" else nonogram.name ?: UNNAMED_NONOGRAM_TITLE,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = fadeIn.padding(bottom = 12.dp),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .graphicsLayer {
                            val scale = 1.3f - 0.3f * rise.value
                            scaleX = scale
                            scaleY = scale
                            translationY = (1f - rise.value) * size.height * 0.4f
                        },
                ) {
                    DrawNonogram(nonogram.solution)
                }
                Row(
                    modifier = fadeIn.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (won) {
                        IconButton(onClick = onRestart) {
                            Icon(imageVector = refresh, contentDescription = "Restart")
                        }
                        IconButton(onClick = onHome) {
                            Icon(imageVector = home, contentDescription = "Home")
                        }
                    } else {
                        IconButton(onClick = onClose) {
                            Icon(imageVector = close, contentDescription = "Close")
                        }
                    }
                }
            }
        }
    }
}
