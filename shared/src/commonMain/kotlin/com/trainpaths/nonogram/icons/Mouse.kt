package com.trainpaths.nonogram.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** A mouse with its left button pressed. */
@Suppress("CheckReturnValue")
val mouseLeft: ImageVector
    get() {
        if (_mouseLeft != null) return _mouseLeft!!
        _mouseLeft = mouseIcon("mouse_left") {
            moveTo(12f, 2f)
            lineTo(11f, 2f)
            arcTo(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = false, x1 = 6f, y1 = 7f)
            lineTo(6f, 10f)
            lineTo(12f, 10f)
            close()
        }
        return _mouseLeft!!
    }

/** A mouse with its right button pressed. */
@Suppress("CheckReturnValue")
val mouseRight: ImageVector
    get() {
        if (_mouseRight != null) return _mouseRight!!
        _mouseRight = mouseIcon("mouse_right") {
            moveTo(12f, 2f)
            lineTo(13f, 2f)
            arcTo(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 18f, y1 = 7f)
            lineTo(18f, 10f)
            lineTo(12f, 10f)
            close()
        }
        return _mouseRight!!
    }

/** The outlined body and button seam, plus one filled button. */
@Suppress("CheckReturnValue")
private inline fun mouseIcon(name: String, crossinline button: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(11f, 2f)
            lineTo(13f, 2f)
            arcTo(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 18f, y1 = 7f)
            lineTo(18f, 17f)
            arcTo(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 13f, y1 = 22f)
            lineTo(11f, 22f)
            arcTo(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 6f, y1 = 17f)
            lineTo(6f, 7f)
            arcTo(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 11f, y1 = 2f)
            close()
            moveTo(12f, 2f)
            lineTo(12f, 10f)
            moveTo(6f, 10f)
            lineTo(18f, 10f)
        }
        path(fill = SolidColor(Color.Black)) { button() }
    }.build()

private var _mouseLeft: ImageVector? = null
private var _mouseRight: ImageVector? = null
