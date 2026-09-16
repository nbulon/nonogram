@file:OptIn(ExperimentalWasmJsInterop::class)

package com.trainpaths.nonogram.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.trainpaths.nonogram.scan.PickedImage
import kotlinx.browser.document
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import kotlin.js.ExperimentalWasmJsInterop

/**
 * The picked `File` *is* a `Blob`, so it goes straight into [PickedImage]: reading it into a
 * `ByteArray` first copied a whole photo across the JS boundary on the UI thread.
 *
 * This lives in `webMain` rather than split into `jsMain`/`wasmJsMain` actuals: the DOM API here
 * comes from `kotlinx-browser` for *both* targets, so the code compiles unchanged for js and wasmJs.
 */
@Composable
actual fun rememberImagePicker(
    onPicked: (PickedImage) -> Unit,
    onError: (String) -> Unit,
): () -> Unit {
    val picked by rememberUpdatedState(onPicked)

    return remember {
        {
            val input = document.createElement("input") as HTMLInputElement
            input.type = "file"
            input.accept = "image/*"
            input.addEventListener("change") { _: Event ->
                val file = input.files?.item(0)
                if (file != null) picked(PickedImage(file))
            }
            input.click()
        }
    }
}
