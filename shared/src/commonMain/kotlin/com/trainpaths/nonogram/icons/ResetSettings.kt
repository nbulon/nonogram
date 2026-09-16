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
val reset_settings: ImageVector
    get() {
        if (_reset_settings != null) {
            return _reset_settings!!
        }
        _reset_settings =
            ImageVector.Builder(
                name = "reset_settings",
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
                        moveTo(13f, 15.75f)
                        verticalLineToRelative(-1.5f)
                        horizontalLineToRelative(4f)
                        verticalLineToRelative(1.5f)
                        horizontalLineTo(13f)
                        close()
                        moveToRelative(1.5f, 5.25f)
                        verticalLineToRelative(-1.25f)
                        horizontalLineToRelative(-1.5f)
                        verticalLineToRelative(-1.5f)
                        horizontalLineToRelative(1.5f)
                        verticalLineToRelative(-1.25f)
                        horizontalLineToRelative(1.5f)
                        verticalLineToRelative(4f)
                        horizontalLineToRelative(-1.5f)
                        close()
                        moveToRelative(2.5f, -1.25f)
                        verticalLineToRelative(-1.5f)
                        horizontalLineToRelative(4f)
                        verticalLineToRelative(1.5f)
                        horizontalLineTo(17f)
                        close()
                        moveToRelative(1f, -2.75f)
                        verticalLineToRelative(-4f)
                        horizontalLineToRelative(1.5f)
                        verticalLineToRelative(1.25f)
                        horizontalLineToRelative(1.5f)
                        verticalLineToRelative(1.5f)
                        horizontalLineToRelative(-1.5f)
                        verticalLineToRelative(1.25f)
                        horizontalLineToRelative(-1.5f)
                        close()
                        moveToRelative(2.775f, -7f)
                        horizontalLineToRelative(-2.075f)
                        quadToRelative(-0.65f, -2.2f, -2.475f, -3.6f)
                        reflectiveQuadToRelative(-4.225f, -1.4f)
                        quadToRelative(-2.925f, 0f, -4.963f, 2.038f)
                        reflectiveQuadTo(5f, 12f)
                        quadToRelative(0f, 1.8f, 0.812f, 3.3f)
                        reflectiveQuadToRelative(2.188f, 2.45f)
                        verticalLineToRelative(-2.75f)
                        horizontalLineToRelative(2f)
                        verticalLineToRelative(6f)
                        horizontalLineTo(4f)
                        verticalLineToRelative(-2f)
                        horizontalLineToRelative(2.35f)
                        quadToRelative(-1.55f, -1.25f, -2.45f, -3.062f)
                        reflectiveQuadTo(3f, 12f)
                        quadToRelative(0f, -1.875f, 0.713f, -3.513f)
                        reflectiveQuadToRelative(1.925f, -2.85f)
                        quadToRelative(1.213f, -1.213f, 2.85f, -1.925f)
                        reflectiveQuadTo(12f, 3f)
                        quadToRelative(3.225f, 0f, 5.663f, 1.988f)
                        reflectiveQuadTo(20.775f, 10f)
                        close()
                    }
                }
                .build()
        return _reset_settings!!
    }

private var _reset_settings: ImageVector? = null
