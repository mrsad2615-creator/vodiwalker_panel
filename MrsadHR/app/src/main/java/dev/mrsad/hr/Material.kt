package dev.mrsad.hr

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import java.util.Locale
import kotlin.math.*

@Immutable
data class Finish(
    val bg: Color,
    val panel: Color,
    val ink: Color,
    val muted: Color,
    val beam: Color,
    val rim: Color,
)

fun alloy(bg: Long, p: Long, i: Long, m: Long, b: Long, r: Long) =
    Finish(Color(bg), Color(p), Color(i), Color(m), Color(b), Color(r))

val finishes =
    linkedMapOf(
        "Obsidian" to alloy(0xff0c1012, 0xff192025, 0xfff1f3ee, 0xffabb3b5, 0xffc5ece1, 0xff647479),
        "Pure" to alloy(0xffeeede7, 0xfffaf9f4, 0xff20292a, 0xff566461, 0xff15594e, 0xff9aaba7),
        "Aurora" to alloy(0xff0d1022, 0xff211f38, 0xfff0f0ff, 0xffb6b7d1, 0xff81f5ed, 0xff9272ce),
    )
val Metal = staticCompositionLocalOf { finishes.values.first() }
val Reduced = compositionLocalOf { false }
val Grotesk = FontFamily(Font(R.font.spacegrotesk))

object M1 {
    const val PRESS = 150
    const val PAGE = 340
    const val THEME = 540
    const val FINISH = 1150
}

@Composable
fun blend(target: Finish): Finish {
    @Composable
    fun mix(c: Color) =
        animateColorAsState(c, tween(if (Reduced.current) 0 else M1.THEME), label = "material")
            .value
    return Finish(
        mix(target.bg),
        mix(target.panel),
        mix(target.ink),
        mix(target.muted),
        mix(target.beam),
        mix(target.rim),
    )
}

@Composable
fun Type(
    s: String,
    size: Int = 14,
    color: Color = Metal.current.ink,
    weight: FontWeight = FontWeight.Normal,
    spacing: Float = 0f,
) {
    Text(
        localized(s),
        color = color,
        fontSize = size.sp,
        lineHeight = (size * 1.2).sp,
        fontFamily = if (Persian.current) FontFamily(Font(R.font.vazirmatn)) else Grotesk,
        fontWeight = weight,
        letterSpacing = spacing.sp,
        style = TextStyle(fontFeatureSettings = "tnum"),
    )
}

@Composable
fun Tag(s: String, color: Color = Metal.current.muted) = Type(s, 10, color, FontWeight.Medium, 1.5f)

@Composable
fun Pad(s: String, modifier: Modifier = Modifier, hot: Boolean = false, click: () -> Unit) {
    val m = Metal.current
    val source = remember { MutableInteractionSource() }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val tactile = Tactile.current
    val pressed by source.collectIsPressedAsState()
    val scale by
        animateFloatAsState(
            if (pressed) .966f else 1f,
            tween(if (Reduced.current) 0 else M1.PRESS),
            label = "compression",
        )
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(if (hot) m.beam else m.panel)
            .border(1.dp, m.rim.copy(alpha = .4f), shape)
            .clickable(source, null, role = Role.Button) {
                if (tactile)
                    haptic.performHapticFeedback(
                        androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove,
                    )
                click()
            }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Type(s, 14, if (hot) m.bg else m.ink, FontWeight.Medium)
    }
}

fun digits(ms: Long, ceil: Boolean = false): String {
    val t = (ms + if (ceil) 999 else 0) / 1000
    return if (t >= 3600)
        String.format(Locale.ROOT, "%02d:%02d:%02d", t / 3600, t / 60 % 60, t % 60)
    else String.format(Locale.ROOT, "%02d:%02d", t / 60, t % 60)
}

@Composable
fun DialFace(progress: Float, modifier: Modifier = Modifier) {
    val m = Metal.current
    Canvas(modifier) {
        val r = size.minDimension * .475f
        val c = center
        fun pt(a: Double, k: Float) =
            Offset(c.x + cos(a).toFloat() * r * k, c.y + sin(a).toFloat() * r * k)
        drawCircle(
            Brush.radialGradient(listOf(m.rim.copy(.36f), m.bg.copy(0f)), c, r * 1.08f),
            r * 1.08f,
        )
        drawCircle(Brush.linearGradient(listOf(m.rim, m.bg, m.rim, m.panel, m.rim)), r)
        drawCircle(m.bg, r * .987f)
        drawCircle(
            Brush.sweepGradient(listOf(m.panel, m.rim, m.ink, m.panel, m.rim, m.panel)),
            r * .965f,
        )
        drawCircle(Brush.linearGradient(listOf(m.panel, m.bg, m.panel)), r * .94f)
        repeat(120) {
            val a = (it * 3 - 90) * Math.PI / 180
            val major = it % 10 == 0
            drawLine(
                if (major) m.ink.copy(.8f) else m.rim,
                pt(a, if (major) .82f else .865f),
                pt(a, .9f),
                if (major) 2.2f else 1f,
            )
        }
        val rr = r * .765f
        drawCircle(m.rim.copy(.25f), rr, style = Stroke(r * .018f))
        drawArc(
            Brush.sweepGradient(listOf(m.beam, m.rim, m.beam)),
            -90f,
            360 * progress.coerceIn(0f, 1f),
            false,
            Offset(c.x - rr, c.y - rr),
            Size(rr * 2, rr * 2),
            style = Stroke(r * .022f, cap = StrokeCap.Round),
        )
        drawCircle(m.rim.copy(.17f), r * .708f, style = Stroke(1f))
        drawCircle(m.beam, r * .021f, pt((progress * 360 - 90) * Math.PI / 180, .765f))
    }
}
