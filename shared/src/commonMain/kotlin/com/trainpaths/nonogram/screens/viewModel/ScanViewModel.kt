package com.trainpaths.nonogram.screens.viewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.trainpaths.nonogram.classes.sanitizeNameInput
import com.trainpaths.nonogram.scan.DEFAULT_SCAN_SIDE
import com.trainpaths.nonogram.scan.LumaMap
import com.trainpaths.nonogram.scan.PickedImage
import com.trainpaths.nonogram.scan.ScanOptions
import com.trainpaths.nonogram.scan.decodeToLumaMap
import com.trainpaths.nonogram.scan.defaultDimensions
import com.trainpaths.nonogram.scan.otsuThreshold
import com.trainpaths.nonogram.scan.toGrid

/**
 * Bigger than any image worth turning into a 50x50 grid, and past the point where decoding one is
 * a good idea on a phone — the decoder materializes the whole bitmap however carefully the pixels
 * are read back afterward.
 */
private const val MAX_IMAGE_BYTES = 20L * 1024 * 1024

/**
 * Holds a picked image while the user tunes it into a grid.
 *
 * The image is reduced to a small [LumaMap] once, on the way in; every later recompute — the
 * threshold slider, the size field, invert — runs against that map.
 *
 * This is a ViewModel rather than `remember`ed screen state because `MainActivity` declares no
 * `android:configChanges`, a rotation would otherwise throw the picked image away.
 */
class ScanViewModel : ViewModel() {

    private var lumaMap: LumaMap? = null

    /** True once an image has been decoded and there is something to preview. */
    var hasImage by mutableStateOf(false)
        private set

    var isProcessing by mutableStateOf(false)
        private set

    /** A user-facing message when the last pick could not be turned into a grid. */
    var error by mutableStateOf<String?>(null)
        private set

    /** The longest grid side the user wants, as typed. Pre-filled, so it can be set before a pick. */
    var sideInput by mutableStateOf(DEFAULT_SCAN_SIDE.toString())
        private set

    private var sourceWidth by mutableStateOf(0)
    private var sourceHeight by mutableStateOf(0)

    var threshold by mutableStateOf(128)
        private set

    var invert by mutableStateOf(false)
        private set

    var name by mutableStateOf("")
        private set

    /** The grid as it currently stands, ready to hand to the generator. */
    var previewGrid by mutableStateOf<List<List<Int>>>(emptyList())
        private set

    /** The grid that will actually be produced: the typed side, the other one from the aspect ratio. */
    private val dimensions: Pair<Int, Int>
        get() = defaultDimensions(sourceWidth, sourceHeight, side = sideInput.toIntOrNull() ?: 0)

    val rows: Int get() = dimensions.first
    val cols: Int get() = dimensions.second

    fun onImagePicked(image: PickedImage) {
        if (isProcessing) return
        error = null
        if (image.sizeBytes > MAX_IMAGE_BYTES) {
            error = "That image is too large. Try one under 20 MB."
            return
        }
        isProcessing = true
        launchGuarded(onError = { error = it.message ?: "Could not read that image." }) {
            try {
                val map = image.decodeToLumaMap()
                lumaMap = map
                sourceWidth = map.width
                sourceHeight = map.height
                sideInput = maxOf(rows, cols).toString()
                threshold = map.otsuThreshold()
                hasImage = true
                recomputePreview()
            } finally {
                isProcessing = false
            }
        }
    }

    fun onPickFailed(message: String) {
        error = message
    }

    fun updateSide(value: String) {
        sideInput = value.filter { it.isDigit() }.take(2)
        recomputePreview()
    }

    fun updateThreshold(value: Int) {
        threshold = value.coerceIn(1, 255)
        recomputePreview()
    }

    fun updateInvert(value: Boolean) {
        invert = value
        recomputePreview()
    }

    fun updateName(value: String) {
        name = sanitizeNameInput(value)
    }

    /** Snaps the size field back to what was actually used, once the user leaves it. */
    fun normalizeSizeInput() {
        sideInput = maxOf(rows, cols).toString()
        recomputePreview()
    }

    private fun recomputePreview() {
        val map = lumaMap ?: return
        previewGrid = map.toGrid(
            ScanOptions(
                rows = rows,
                cols = cols,
                threshold = threshold,
                invert = invert,
            )
        )
    }
}
