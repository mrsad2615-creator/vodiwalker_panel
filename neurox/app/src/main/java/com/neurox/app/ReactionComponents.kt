package com.neurox.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.neurox.domain.*

@Composable
internal fun colorName(color: ReactionColor): String = when (color) {
    ReactionColor.RED -> tr("قرمز", "Red")
    ReactionColor.BLUE -> tr("آبی", "Blue")
    ReactionColor.GREEN -> tr("سبز", "Green")
    ReactionColor.YELLOW -> tr("زرد", "Yellow")
}

@Composable
internal fun ColorSignal(color: ReactionColor, modifier: Modifier = Modifier) {
    val name = colorName(color)
    // Stimulus colors are fixed; the user's theme must not change their meaning.
    val value = when (color) {
        ReactionColor.RED -> Color(0xFFD32F2F)
        ReactionColor.BLUE -> Color(0xFF1565C0)
        ReactionColor.GREEN -> Color(0xFF2E7D32)
        ReactionColor.YELLOW -> Color(0xFFF9D423)
    }
    val outline = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.semantics { contentDescription = name }) {
        drawCircle(value, size.minDimension * 0.44f)
        drawCircle(outline, size.minDimension * 0.44f, style = Stroke(2.dp.toPx()))
    }
}

@Composable
internal fun directionName(direction: PhysicalDirection): String = when (direction) {
    PhysicalDirection.UP -> tr("بالا", "Up")
    PhysicalDirection.RIGHT -> tr("راست", "Right")
    PhysicalDirection.DOWN -> tr("پایین", "Down")
    PhysicalDirection.LEFT -> tr("چپ", "Left")
}

@Composable
private fun DirectionArrow(direction: PhysicalDirection, modifier: Modifier) {
    val name = directionName(direction)
    val tint = MaterialTheme.colorScheme.primary
    Canvas(modifier.semantics { contentDescription = name }) {
        val unit = size.minDimension / 24f
        val x = (size.width - unit * 24) / 2
        val y = (size.height - unit * 24) / 2
        rotate(direction.ordinal * 90f) {
            val path = Path().apply {
                moveTo(x + 12 * unit, y + 3 * unit)
                lineTo(x + 20 * unit, y + 12 * unit)
                lineTo(x + 15 * unit, y + 12 * unit)
                lineTo(x + 15 * unit, y + 21 * unit)
                lineTo(x + 9 * unit, y + 21 * unit)
                lineTo(x + 9 * unit, y + 12 * unit)
                lineTo(x + 4 * unit, y + 12 * unit)
                close()
            }
            drawPath(path, tint)
        }
    }
}

@Composable
internal fun DirectionSignal(trial: DirectionTrial?, modifier: Modifier = Modifier) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier) {
            if (trial == null) Text(tr("منتظر پیکان بمانید", "Wait for the arrow"), Modifier.align(Alignment.Center))
            else DirectionArrow(trial.direction, Modifier.size(64.dp).align(when (trial.position) {
                PhysicalDirection.UP -> Alignment.TopCenter
                PhysicalDirection.RIGHT -> Alignment.CenterEnd
                PhysicalDirection.DOWN -> Alignment.BottomCenter
                PhysicalDirection.LEFT -> Alignment.CenterStart
            }))
        }
    }
}

@Composable
internal fun DirectionAnswer(direction: PhysicalDirection, current: SessionView, vm: AppViewModel) {
    OutlinedButton(onClick = { vm.tap(direction.ordinal, current.inputRevision) }, contentPadding = PaddingValues(8.dp)) {
        DirectionArrow(direction, Modifier.size(24.dp))
        Spacer(Modifier.width(4.dp))
        Text(directionName(direction))
    }
}
