package com.trainpaths.nonogram.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.trainpaths.nonogram.scan.PickedImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "bmp", "webp")

@Composable
actual fun rememberImagePicker(
    onPicked: (PickedImage) -> Unit,
    onError: (String) -> Unit,
): () -> Unit {
    val scope = rememberCoroutineScope()
    val picked by rememberUpdatedState(onPicked)
    val failed by rememberUpdatedState(onError)

    return remember {
        {
            // The native dialog is modal; Compose's UI thread is the EDT, so showing it from a click is the normal pattern.
            val dialog = FileDialog(null as Frame?, "Choose an image", FileDialog.LOAD).apply {
                setFilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS }
                isVisible = true
            }
            val file = dialog.files.firstOrNull() ?: dialog.file?.let { File(dialog.directory, it) }
            if (file != null) {
                scope.launch {
                    val bytes = withContext(Dispatchers.IO) { runCatching { file.readBytes() } }
                    bytes.fold(
                        onSuccess = { picked(PickedImage(it)) },
                        onFailure = { failed(it.message ?: "Could not open that image.") },
                    )
                }
            }
        }
    }
}
