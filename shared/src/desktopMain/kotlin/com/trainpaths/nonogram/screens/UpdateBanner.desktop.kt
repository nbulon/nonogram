package com.trainpaths.nonogram.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trainpaths.nonogram.AppButton
import com.trainpaths.nonogram.icons.close
import com.trainpaths.nonogram.update.UpdateCheck
import com.trainpaths.nonogram.update.openInBrowser

private val BANNER_MAX_WIDTH = 280.dp

@Composable
actual fun UpdateBanner(modifier: Modifier) {
    val update = UpdateCheck.available ?: return

    Card(
        modifier = modifier.widthIn(max = BANNER_MAX_WIDTH),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Update available",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = UpdateCheck::dismiss) {
                    Icon(close, contentDescription = "Dismiss", modifier = Modifier.size(20.dp))
                }
            }
            Text(
                text = "Version ${update.latest} is out — you have ${update.current}.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                AppButton(
                    text = "Download",
                    onClick = {
                        openInBrowser(update.installerUrl)
                        UpdateCheck.dismiss()
                    },
                    height = null,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
