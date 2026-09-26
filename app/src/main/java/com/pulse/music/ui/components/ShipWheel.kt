package com.pulse.music.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

val ShipWheelFilled: ImageVector by lazy { shipWheel(filled = true) }
val ShipWheelOutline: ImageVector by lazy { shipWheel(filled = false) }

private fun shipWheel(filled: Boolean): ImageVector {
    val ink = SolidColor(Color.Black)
    val c = 12f
    return ImageVector.Builder(
        name = if (filled) "ShipWheelFilled" else "ShipWheelOutline",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        if (filled) path(fill = ink, fillAlpha = 0.3f) { circle(c, c, 6.8f) }
        path(stroke = ink, strokeLineWidth = 1.6f) { circle(c, c, 6.8f) }
        path(stroke = ink, strokeLineWidth = 1.4f, strokeLineCap = StrokeCap.Round) {
            for (i in 0 until 8) {
                val a = i * PI / 4
                moveTo(c + 2.4f * cos(a).toFloat(), c + 2.4f * sin(a).toFloat())
                lineTo(c + 9.2f * cos(a).toFloat(), c + 9.2f * sin(a).toFloat())
            }
        }
        path(fill = ink) {
            for (i in 0 until 8) {
                val a = i * PI / 4
                circle(c + 10.3f * cos(a).toFloat(), c + 10.3f * sin(a).toFloat(), 1.3f)
            }
        }
        if (filled) path(fill = ink) { circle(c, c, 2.6f) }
        else path(stroke = ink, strokeLineWidth = 1.4f) { circle(c, c, 2.4f) }
    }.build()
}

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcToRelative(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = 2 * r, dy1 = 0f)
    arcToRelative(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = -2 * r, dy1 = 0f)
    close()
}
