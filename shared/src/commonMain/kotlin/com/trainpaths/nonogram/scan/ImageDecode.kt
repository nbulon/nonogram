package com.trainpaths.nonogram.scan

/**
 * A picked image as its platform holds it: the encoded bytes on Android, the browser's `Blob` on
 * web. Not a `ByteArray`, because on web that would copy a whole photo across the JS boundary on
 * the UI thread only to hand it back to a decoder that wanted the `Blob`.
 */
expect class PickedImage {
    val sizeBytes: Long
}

/**
 * Reduces the image to a [LumaMap] of at most [maxSide] on its longest side. Suspending because
 * on web the decode belongs to the browser, which does it off the main thread.
 */
internal expect suspend fun PickedImage.decodeToLumaMap(maxSide: Int = WORKING_SIDE): LumaMap
