@file:OptIn(ExperimentalWasmJsInterop::class)

package com.trainpaths.nonogram.scan

import kotlinx.browser.document
import kotlinx.coroutines.suspendCancellableCoroutine
import org.khronos.webgl.get
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLImageElement
import org.w3c.dom.events.Event
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.ExperimentalWasmJsInterop

actual class PickedImage(internal val blob: Blob) {
    actual val sizeBytes: Long get() = blob.size.toDouble().toLong()
}

/**
 * The browser owns both halves: an `<img>` decodes off the main thread, and `drawImage` into a canvas
 * already sized to the target does the downscale, so only [WORKING_SIDE]-sized pixels reach Kotlin.
 * The resample is therefore the browser's rather than [LumaAccumulator]'s box filter; both feed the
 * same interactive threshold, so the difference does not show.
 */
internal actual suspend fun PickedImage.decodeToLumaMap(maxSide: Int): LumaMap {
    val objectUrl = URL.createObjectURL(blob)
    val image = try {
        loadImage(objectUrl)
    } finally {
        URL.revokeObjectURL(objectUrl)
    }

    val width = image.naturalWidth
    val height = image.naturalHeight
    require(width > 0 && height > 0) { "decoded image is empty" }

    val (outWidth, outHeight) = scaledSize(width, height, maxSide)
    val canvas = document.createElement("canvas") as HTMLCanvasElement
    canvas.width = outWidth
    canvas.height = outHeight
    val context = canvas.getContext("2d") as CanvasRenderingContext2D
    context.drawImage(image, 0.0, 0.0, outWidth.toDouble(), outHeight.toDouble())
    val rgba = context.getImageData(0.0, 0.0, outWidth.toDouble(), outHeight.toDouble()).data

    val luma = ByteArray(outWidth * outHeight)
    for (index in luma.indices) {
        val offset = index * 4
        val argb = ((rgba[offset + 3].toInt() and 0xFF) shl 24) or
                ((rgba[offset].toInt() and 0xFF) shl 16) or
                ((rgba[offset + 1].toInt() and 0xFF) shl 8) or
                (rgba[offset + 2].toInt() and 0xFF)
        luma[index] = lumaOverWhite(argb).toByte()
    }
    return LumaMap(outWidth, outHeight, luma)
}

private suspend fun loadImage(objectUrl: String): HTMLImageElement =
    suspendCancellableCoroutine { continuation ->
        val image = document.createElement("img") as HTMLImageElement
        image.addEventListener("load") { _: Event ->
            if (continuation.isActive) continuation.resume(image)
        }
        image.addEventListener("error") { _: Event ->
            if (continuation.isActive) {
                continuation.resumeWithException(IllegalStateException("Could not read that image."))
            }
        }
        image.src = objectUrl
    }
