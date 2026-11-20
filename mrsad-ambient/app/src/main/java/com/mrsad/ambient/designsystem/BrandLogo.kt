package com.mrsad.ambient.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MrsadBrandSymbol(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    color: Color = MrsadColors.PureWhite
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.065f, cap = StrokeCap.Square, join = StrokeJoin.Miter)

        // Outer geometric ring
        drawCircle(
            color = color,
            radius = w * 0.42f,
            center = Offset(w / 2f, h / 2f),
            style = stroke
        )

        // Architectural M-monogram
        val path = Path().apply {
            moveTo(w * 0.28f, h * 0.63f)
            lineTo(w * 0.28f, h * 0.36f)
            lineTo(w * 0.50f, h * 0.52f)
            lineTo(w * 0.72f, h * 0.36f)
            lineTo(w * 0.72f, h * 0.63f)
        }
        drawPath(path = path, color = color, style = stroke)

        // Precision dot
        drawCircle(
            color = color,
            radius = w * 0.045f,
            center = Offset(w * 0.50f, h * 0.70f)
        )
    }
}

@Composable
fun MrsadWordmark(
    modifier: Modifier = Modifier,
    symbolSize: Dp = 28.dp
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MrsadBrandSymbol(size = symbolSize)
        Text(
            text = "MRSAD",
            color = MrsadColors.PureWhite,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 4.sp
        )
        Text(
            text = "AMBIENT",
            color = MrsadColors.GraySecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 2.5.sp
        )
    }
}
