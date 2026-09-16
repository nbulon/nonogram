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
val more_horiz: ImageVector
    get() {
        if (_more_horiz != null) {
            return _more_horiz!!
        }
        _more_horiz =
            ImageVector.Builder(
                name = "more_horiz",
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
                        moveTo(6f, 14f)
                        quadToRelative(-0.825f, 0f, -1.413f, -0.588f)
                        reflectiveQuadTo(4f, 12f)
                        quadToRelative(0f, -0.825f, 0.588f, -1.413f)
                        reflectiveQuadTo(6f, 10f)
                        quadToRelative(0.825f, 0f, 1.413f, 0.588f)
                        reflectiveQuadTo(8f, 12f)
                        quadToRelative(0f, 0.825f, -0.588f, 1.413f)
                        reflectiveQuadTo(6f, 14f)
                        close()
                        moveToRelative(6f, 0f)
                        quadToRelative(-0.825f, 0f, -1.413f, -0.588f)
                        reflectiveQuadTo(10f, 12f)
                        quadToRelative(0f, -0.825f, 0.588f, -1.413f)
                        reflectiveQuadTo(12f, 10f)
                        quadToRelative(0.825f, 0f, 1.413f, 0.588f)
                        reflectiveQuadTo(14f, 12f)
                        quadToRelative(0f, 0.825f, -0.588f, 1.413f)
                        reflectiveQuadTo(12f, 14f)
                        close()
                        moveToRelative(6f, 0f)
                        quadToRelative(-0.825f, 0f, -1.413f, -0.588f)
                        reflectiveQuadTo(16f, 12f)
                        quadToRelative(0f, -0.825f, 0.588f, -1.413f)
                        reflectiveQuadTo(18f, 10f)
                        quadToRelative(0.825f, 0f, 1.413f, 0.588f)
                        reflectiveQuadTo(20f, 12f)
                        quadToRelative(0f, 0.825f, -0.588f, 1.413f)
                        reflectiveQuadTo(18f, 14f)
                        close()
                    }
                }
                .build()
        return _more_horiz!!
    }

private var _more_horiz: ImageVector? = null
