package com.trainpaths.nonogram.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("CheckReturnValue")
val download: ImageVector
    get() {
        if (_download != null) {
            return _download!!
        }
        _download =
            ImageVector.Builder(
                name = "download",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            )
                .apply {
                    path(
                        fill = SolidColor(Color.Black),
                        fillAlpha = 1f,
                        stroke = null,
                        strokeAlpha = 1f,
                        strokeLineWidth = 1f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Bevel,
                        strokeLineMiter = 1f,
                        pathFillType = PathFillType.NonZero,
                    ) {
                        // arrow shaft + head, pointing down
                        moveTo(11f, 3f)
                        horizontalLineTo(13f)
                        verticalLineTo(12.17f)
                        lineToRelative(3.59f, -3.58f)
                        lineTo(18f, 10f)
                        lineToRelative(-6f, 6f)
                        lineToRelative(-6f, -6f)
                        lineToRelative(1.41f, -1.41f)
                        lineTo(11f, 12.17f)
                        close()
                        // tray
                        moveTo(5f, 18f)
                        horizontalLineTo(19f)
                        verticalLineTo(20f)
                        horizontalLineTo(5f)
                        close()
                    }
                }
                .build()
        return _download!!
    }

private var _download: ImageVector? = null
