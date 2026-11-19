package dev.mrsad.hr

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*

/** Static, size-cached optics: no infinite animation loop when the clock is idle. */
fun Modifier.atmosphere(theme: String, m: Finish) = drawWithCache {
    val w = size.width
    val h = size.height
    val foundation = Brush.verticalGradient(listOf(m.bg, m.panel, m.bg))
    val light =
        Brush.radialGradient(
            listOf(m.rim.copy(if (theme == "Pure") .21f else .24f), Color.Transparent),
            if (theme == "Pure") Offset(w * .16f, h * .15f) else Offset(w * .92f, h * .24f),
            w * .98f,
        )
    val secondary =
        Brush.radialGradient(
            listOf(m.beam.copy(if (theme == "Aurora") .15f else .045f), Color.Transparent),
            Offset(0f, h * .65f),
            w * .8f,
        )
    onDrawBehind {
        drawRect(foundation)
        drawRect(light)
        drawRect(secondary)
        when (theme) {
            "Obsidian" -> {
                var y = 0f
                while (y < h) {
                    drawLine(m.ink.copy(.014f), Offset(0f, y), Offset(w, y - w * .16f))
                    y += 9f
                }
            }
            "Pure" -> {
                for (i in 0..4) drawCircle(
                    m.rim.copy(.06f),
                    w * (.4f + i * .08f),
                    Offset(w, h * .3f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1f),
                )
            }
            else -> {
                for (i in 0..30) {
                    val x = ((i * 73) % 101) / 100f * w
                    val y = ((i * 29) % 97) / 96f * h
                    drawCircle(m.beam.copy(.19f), 1.25f, Offset(x, y))
                }
            }
        }
    }
}
