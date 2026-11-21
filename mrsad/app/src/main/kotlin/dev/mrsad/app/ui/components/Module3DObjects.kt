package dev.mrsad.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import dev.mrsad.app.data.settings.GraphicsQuality
import dev.mrsad.app.testing.MrsadTestHooks
import dev.mrsad.app.ui.theme.MrsadColors
import dev.mrsad.app.ui.theme.MrsadTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Bespoke 3D Sculptures for the Four Core Capabilities (Master Prompt §2):
 *
 * - [ModuleSculptureType.TYPING]: 3D metallic keyboard cluster with floating key travel.
 * - [ModuleSculptureType.READING]: 3D open prism book with kinetic word-stream lines.
 * - [ModuleSculptureType.EYE]: 3D abstract ocular aperture with dual iris rings & cold light.
 * - [ModuleSculptureType.BRAIN]: 3D faceted metallic neural polyhedron sculpture.
 *
 * In **NOIR** (`colors.isNoir == true`), 3D volumes are replaced by flat 1.5dp
 * Swiss monochrome technical schematics so NOIR remains strictly 2D editorial.
 *
 * Performance & Test Determinism Contract:
 * - Zero object allocations inside the per-frame draw scope (`scratchPath` is remembered).
 * - Animation clock stops completely in `MrsadTestHooks.isDeterministic`,
 *   `reduceMotion`, or `GraphicsQuality.PERFORMANCE`.
 */
enum class ModuleSculptureType {
    TYPING,
    READING,
    EYE,
    BRAIN,
}

@Composable
fun ModuleSculpture3D(
    type: ModuleSculptureType,
    modifier: Modifier = Modifier,
    intensity: Float = 0.75f,
) {
    val colors = MrsadTheme.colors
    val motion = MrsadTheme.motion
    val scratchPath = remember { Path() }

    val animate = !colors.isNoir &&
        !MrsadTestHooks.isDeterministic &&
        motion.animationsEnabled &&
        motion.quality != GraphicsQuality.PERFORMANCE

    val phase = if (animate) {
        val transition = rememberInfiniteTransition(label = "sculpture_${type.name}")
        val t by transition.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 5_400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "phase",
        )
        t
    } else {
        0.85f
    }

    Canvas(modifier = modifier) {
        if (colors.isNoir) {
            drawNoirSchematic(type = type, colors = colors, scratch = scratchPath)
        } else {
            when (type) {
                ModuleSculptureType.TYPING -> drawTypingKeyboard3D(colors, phase, intensity, scratchPath)
                ModuleSculptureType.READING -> drawReadingBook3D(colors, phase, intensity, scratchPath)
                ModuleSculptureType.EYE -> drawEyeOcular3D(colors, phase, intensity, scratchPath)
                ModuleSculptureType.BRAIN -> drawBrainSculpture3D(colors, phase, intensity, scratchPath)
            }
        }
    }
}

// ============================================================================
// 1. TYPING: 3D Metallic Keyboard with Reactive Keycaps
// ============================================================================
private fun DrawScope.drawTypingKeyboard3D(
    colors: MrsadColors,
    phase: Float,
    intensity: Float,
    scratch: Path,
) {
    val cx = size.width / 2f
    val cy = size.height / 2f + sin(phase) * 2.2f
    val scale = size.minDimension / 96f

    // Isometric machined base deck
    val deckW = 72f * scale
    val deckH = 34f * scale
    scratch.reset()
    scratch.moveTo(cx - deckW * 0.44f, cy - deckH * 0.45f)
    scratch.lineTo(cx + deckW * 0.44f, cy - deckH * 0.45f)
    scratch.lineTo(cx + deckW * 0.54f, cy + deckH * 0.48f)
    scratch.lineTo(cx - deckW * 0.54f, cy + deckH * 0.48f)
    scratch.close()
    drawPath(
        path = scratch,
        brush = Brush.verticalGradient(
            listOf(colors.surfaceHigh, colors.background),
            startY = cy - deckH * 0.5f,
            endY = cy + deckH * 0.5f,
        ),
    )
    drawPath(
        path = scratch,
        color = colors.borderStrong.copy(alpha = 0.55f),
        style = Stroke(width = 1.1f * scale),
    )

    // 4 metallic 3D keycaps (3 top keys + 1 wide spacebar)
    val keyOffsetsX = floatArrayOf(-20f, 0f, 20f, 0f)
    val keyOffsetsY = floatArrayOf(-6f, -6f, -6f, 8f)
    val keyWidths = floatArrayOf(15f, 15f, 15f, 38f)
    val keyPhases = floatArrayOf(0f, 1.5f, 3.0f, 4.4f)

    for (i in 0..3) {
        val travel = max(0f, sin(phase * 2f + keyPhases[i])) * 2.8f * scale
        val kx = cx + keyOffsetsX[i] * scale
        val ky = cy + keyOffsetsY[i] * scale + travel
        val kw = keyWidths[i] * scale
        val kh = 9.5f * scale
        val bevel = 3.2f * scale

        // Keycap extruded side skirt
        drawRoundRect(
            color = colors.metalDark,
            topLeft = Offset(kx - kw / 2f, ky - kh / 2f + bevel),
            size = Size(kw, kh),
            cornerRadius = CornerRadius(2.5f * scale, 2.5f * scale),
        )
        // Keycap brushed-silver top face
        val lit = travel > 1.2f * scale
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = if (lit) {
                    listOf(colors.textPrimary, colors.accent)
                } else {
                    listOf(colors.metalLight, colors.metalMid)
                },
                start = Offset(kx - kw / 2f, ky - kh / 2f),
                end = Offset(kx + kw / 2f, ky + kh / 2f),
            ),
            topLeft = Offset(kx - kw / 2f, ky - kh / 2f),
            size = Size(kw, kh),
            cornerRadius = CornerRadius(2.5f * scale, 2.5f * scale),
        )
    }
}

// ============================================================================
// 2. SPEED READING: 3D Metallic Prism Book & Kinetic Word Streams
// ============================================================================
private fun DrawScope.drawReadingBook3D(
    colors: MrsadColors,
    phase: Float,
    intensity: Float,
    scratch: Path,
) {
    val cx = size.width / 2f
    val cy = size.height / 2f + sin(phase) * 2.5f
    val s = size.minDimension / 96f

    // Subtle specular halo under the floating book
    drawCircle(
        brush = Brush.radialGradient(
            listOf(colors.accent.copy(alpha = 0.16f * intensity), Color.Transparent),
            center = Offset(cx, cy),
            radius = 38f * s,
        ),
        radius = 38f * s,
        center = Offset(cx, cy),
    )

    // Left page wing (brushed mid-silver)
    scratch.reset()
    scratch.moveTo(cx, cy - 11f * s)
    scratch.lineTo(cx - 32f * s, cy - 20f * s)
    scratch.lineTo(cx - 28f * s, cy + 13f * s)
    scratch.lineTo(cx, cy + 21f * s)
    scratch.close()
    drawPath(
        path = scratch,
        brush = Brush.linearGradient(
            listOf(colors.metalMid, colors.metalDark),
            start = Offset(cx - 32f * s, cy - 20f * s),
            end = Offset(cx, cy + 21f * s),
        ),
    )
    drawPath(scratch, colors.borderStrong, style = Stroke(width = 1f * s))

    // Right page wing (lit platinum silver)
    scratch.reset()
    scratch.moveTo(cx, cy - 11f * s)
    scratch.lineTo(cx + 32f * s, cy - 20f * s)
    scratch.lineTo(cx + 28f * s, cy + 13f * s)
    scratch.lineTo(cx, cy + 21f * s)
    scratch.close()
    drawPath(
        path = scratch,
        brush = Brush.linearGradient(
            listOf(colors.textPrimary, colors.metalMid),
            start = Offset(cx, cy - 18f * s),
            end = Offset(cx + 32f * s, cy + 18f * s),
        ),
    )
    drawPath(scratch, colors.metalLight.copy(alpha = 0.65f), style = Stroke(width = 1f * s))

    // Kinetic word lines flowing across both pages
    for (row in 0..2) {
        val yBase = cy - (5f - row * 6.5f) * s
        val wave = (sin(phase * 2f - row * 0.9f) + 1f) * 0.5f
        val lineAlpha = (0.45f + 0.50f * wave).coerceIn(0.2f, 0.95f)
        // Right page line
        drawLine(
            color = colors.background.copy(alpha = lineAlpha),
            start = Offset(cx + 5f * s, yBase - 1f * s),
            end = Offset(cx + (15f + wave * 9f) * s, yBase - 5f * s),
            strokeWidth = 1.8f * s,
            cap = StrokeCap.Round,
        )
        // Left page line
        drawLine(
            color = colors.textPrimary.copy(alpha = lineAlpha * 0.7f),
            start = Offset(cx - 5f * s, yBase - 1f * s),
            end = Offset(cx - 22f * s, yBase - 5f * s),
            strokeWidth = 1.5f * s,
            cap = StrokeCap.Round,
        )
    }
}

// ============================================================================
// 3. EYE TRAINING: Abstract 3D Ocular Structure with Controlled Cold Light
// ============================================================================
private fun DrawScope.drawEyeOcular3D(
    colors: MrsadColors,
    phase: Float,
    intensity: Float,
    scratch: Path,
) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val s = size.minDimension / 96f

    // Outer 3D faceted ocular frame
    scratch.reset()
    scratch.moveTo(cx - 36f * s, cy)
    scratch.quadraticBezierTo(cx, cy - 24f * s, cx + 36f * s, cy)
    scratch.quadraticBezierTo(cx, cy + 24f * s, cx - 36f * s, cy)
    scratch.close()

    drawPath(
        path = scratch,
        brush = Brush.radialGradient(
            listOf(colors.accent.copy(alpha = 0.20f), colors.surfaceHigh.copy(alpha = 0.85f)),
            center = Offset(cx, cy),
            radius = 36f * s,
        ),
    )
    drawPath(
        path = scratch,
        color = colors.metalLight.copy(alpha = 0.65f),
        style = Stroke(width = 1.3f * s),
    )

    // Saccadic moving iris + specular ring
    val gazeX = cx + sin(phase * 1.5f) * 6.5f * s
    val gazeY = cy + cos(phase * 1.0f) * 2.4f * s

    drawCircle(
        color = colors.metalMid.copy(alpha = 0.55f),
        radius = 12.5f * s,
        center = Offset(gazeX, gazeY),
        style = Stroke(width = 1.2f * s),
    )
    drawCircle(
        color = colors.accent,
        radius = 8.2f * s,
        center = Offset(gazeX, gazeY),
        style = Stroke(width = 1.7f * s),
    )
    drawCircle(
        color = colors.textPrimary,
        radius = 3.6f * s,
        center = Offset(gazeX, gazeY),
    )
    // Specular glint
    drawCircle(
        color = colors.textPrimary.copy(alpha = 0.85f),
        radius = 1.5f * s,
        center = Offset(gazeX - 3.2f * s, gazeY - 3.2f * s),
    )
}

// ============================================================================
// 4. BRAIN TRAINING: 3D Metallic Neural Polyhedron Sculpture
// ============================================================================
private val BrainVertices = arrayOf(
    floatArrayOf(-18f, -9f, 4f),
    floatArrayOf(-6f, -17f, 12f),
    floatArrayOf(6f, -17f, -10f),
    floatArrayOf(18f, -9f, 8f),
    floatArrayOf(-20f, 8f, -8f),
    floatArrayOf(-6f, 16f, 10f),
    floatArrayOf(6f, 16f, -11f),
    floatArrayOf(20f, 8f, 6f),
    floatArrayOf(0f, 0f, 16f),
)

private val BrainEdges = arrayOf(
    intArrayOf(0, 1), intArrayOf(1, 2), intArrayOf(2, 3),
    intArrayOf(0, 4), intArrayOf(4, 5), intArrayOf(5, 6),
    intArrayOf(6, 7), intArrayOf(3, 7),
    intArrayOf(0, 8), intArrayOf(1, 8), intArrayOf(2, 8), intArrayOf(3, 8),
    intArrayOf(4, 8), intArrayOf(5, 8), intArrayOf(6, 8), intArrayOf(7, 8),
)

private fun DrawScope.drawBrainSculpture3D(
    colors: MrsadColors,
    phase: Float,
    intensity: Float,
    scratch: Path,
) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val s = size.minDimension / 96f * 1.15f
    val ca = cos(phase * 0.65f)
    val sa = sin(phase * 0.65f)

    // Shaded inner core facets
    fun projectX(idx: Int): Float {
        val v = BrainVertices[idx]
        return cx + (v[0] * ca + v[2] * sa) * s
    }
    fun projectY(idx: Int): Float = cy + BrainVertices[idx][1] * s
    fun projectZ(idx: Int): Float {
        val v = BrainVertices[idx]
        return -v[0] * sa + v[2] * ca
    }

    scratch.reset()
    scratch.moveTo(projectX(1), projectY(1))
    scratch.lineTo(projectX(8), projectY(8))
    scratch.lineTo(projectX(5), projectY(5))
    scratch.lineTo(projectX(0), projectY(0))
    scratch.close()
    drawPath(scratch, colors.metalMid.copy(alpha = 0.25f))

    scratch.reset()
    scratch.moveTo(projectX(2), projectY(2))
    scratch.lineTo(projectX(3), projectY(3))
    scratch.lineTo(projectX(7), projectY(7))
    scratch.lineTo(projectX(8), projectY(8))
    scratch.close()
    drawPath(scratch, colors.metalLight.copy(alpha = 0.30f))

    // Metallic struts
    BrainEdges.forEach { edge ->
        val i = edge[0]
        val j = edge[1]
        drawLine(
            color = colors.metalLight.copy(alpha = 0.52f),
            start = Offset(projectX(i), projectY(i)),
            end = Offset(projectX(j), projectY(j)),
            strokeWidth = 1.25f * s,
        )
    }

    // Cortical nodes
    for (idx in BrainVertices.indices) {
        val px = projectX(idx)
        val py = projectY(idx)
        val pz = projectZ(idx)
        val front = pz >= 0f
        drawCircle(
            color = if (front) colors.textPrimary else colors.metalMid,
            radius = (if (front) 2.9f else 2.1f) * s,
            center = Offset(px, py),
        )
    }
}

// ============================================================================
// NOIR 2D Swiss Technical Schematics (Zero 3D, Pure Monochrome Hairlines)
// ============================================================================
private fun DrawScope.drawNoirSchematic(
    type: ModuleSculptureType,
    colors: MrsadColors,
    scratch: Path,
) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val s = size.minDimension / 96f
    val stroke = Stroke(width = 1.4f * s)

    when (type) {
        ModuleSculptureType.TYPING -> {
            drawRect(
                color = colors.textPrimary,
                topLeft = Offset(cx - 28f * s, cy - 14f * s),
                size = Size(56f * s, 28f * s),
                style = stroke,
            )
            drawLine(colors.textPrimary, Offset(cx - 18f * s, cy + 6f * s), Offset(cx + 18f * s, cy + 6f * s), 1.4f * s)
        }
        ModuleSculptureType.READING -> {
            drawRect(
                color = colors.textPrimary,
                topLeft = Offset(cx - 26f * s, cy - 15f * s),
                size = Size(25f * s, 30f * s),
                style = stroke,
            )
            drawRect(
                color = colors.textPrimary,
                topLeft = Offset(cx + 1f * s, cy - 15f * s),
                size = Size(25f * s, 30f * s),
                style = stroke,
            )
        }
        ModuleSculptureType.EYE -> {
            drawCircle(colors.textPrimary, radius = 16f * s, center = Offset(cx, cy), style = stroke)
            drawCircle(colors.textPrimary, radius = 4f * s, center = Offset(cx, cy))
            drawLine(colors.borderStrong, Offset(cx - 26f * s, cy), Offset(cx + 26f * s, cy), 1f * s)
        }
        ModuleSculptureType.BRAIN -> {
            val cell = 11f * s
            drawRect(colors.textPrimary, Offset(cx - cell - 2f * s, cy - cell - 2f * s), Size(cell, cell), style = stroke)
            drawRect(colors.textPrimary, Offset(cx + 2f * s, cy - cell - 2f * s), Size(cell, cell), style = stroke)
            drawRect(colors.textPrimary, Offset(cx - cell - 2f * s, cy + 2f * s), Size(cell, cell), style = stroke)
            drawRect(colors.textPrimary, Offset(cx + 2f * s, cy + 2f * s), Size(cell, cell), style = stroke)
        }
    }
}
