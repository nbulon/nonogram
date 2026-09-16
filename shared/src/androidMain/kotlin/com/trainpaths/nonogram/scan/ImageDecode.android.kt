package com.trainpaths.nonogram.scan

import androidx.compose.ui.graphics.decodeToImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Ints per read-back buffer — a 4 MB window, so one band of a very wide image still fits. */
private const val BAND_BUDGET = 1 shl 20

actual class PickedImage(internal val bytes: ByteArray) {
    actual val sizeBytes: Long get() = bytes.size.toLong()
}

/** Pixels are read back a band of rows at a time into one reused buffer rather than all at once: a 12 MP photo is 48 MB as a single `IntArray` */
internal actual suspend fun PickedImage.decodeToLumaMap(maxSide: Int): LumaMap =
    withContext(Dispatchers.Default) {
        val bitmap = bytes.decodeToImageBitmap()
        val width = bitmap.width
        val height = bitmap.height
        require(width > 0 && height > 0) { "decoded image is empty" }

        val accumulator = LumaAccumulator(width, height, maxSide)
        val bandRows = (BAND_BUDGET / width).coerceIn(1, height)
        val buffer = IntArray(width * bandRows)

        var y = 0
        while (y < height) {
            val rows = minOf(bandRows, height - y)
            bitmap.readPixels(buffer, startX = 0, startY = y, width = width, height = rows)
            accumulator.addRows(buffer, startY = y, rowCount = rows, stride = width)
            y += rows
        }
        accumulator.build()
    }
