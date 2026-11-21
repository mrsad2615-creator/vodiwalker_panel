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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.mrsad.app.data.settings.GraphicsQuality
import dev.mrsad.app.testing.MrsadTestHooks
import dev.mrsad.app.ui.theme.MrsadColors
import dev.mrsad.app.ui.theme.MrsadTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * High-Detail Volumetric 3D Sculptures for the Four Core Capabilities (Master Prompt §2):
 *
 * - [ModuleSculptureType.TYPING]: 3D extruded mechanical keycap & aluminum chassis cluster.
 * - [ModuleSculptureType.READING]: 3D open tome with thick page-stack volume & floating glass prism.
 * - [ModuleSculptureType.EYE]: 3D shaded ocular sphere, radial iris fibers & corneal reflection.
 * - [ModuleSculptureType.BRAIN]: 3D solid-shaded geodesic polyhedron cortex (`Core3D` projection).
 *
 * In **NOIR** (`colors.isNoir == true`), 3D volumes are replaced by flat 1.5dp
 * Swiss monochrome technical schematics so NOIR remains strictly 2D editorial.
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
    intensity: Float = 0.85f,
    animated: Boolean = false,
) {
    val colors = MrsadTheme.colors
    val motion = MrsadTheme.motion
    val scratchPath = remember { Path() }
    val brainEngine = remember { Core3D(ringSegments = 24) }

    val animate = animated &&
        !colors.isNoir &&
        !MrsadTestHooks.isDeterministic &&
        motion.animationsEnabled &&
        motion.quality != GraphicsQuality.PERFORMANCE

    val phase = if (animate) {
        val transition = rememberInfiniteTransition(label = "sculpture3d_${type.name}")
        val p by transition.animateFloat(
            initialValue = 0f,
            targetValue = (2.0 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 6400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "phase",
        )
        p
    } else {
        0.85f
    }

    Canvas(modifier = modifier) {
        if (size.minDimension <= 4f) return@Canvas
        if (colors.isNoir) {
            drawNoirSchematic(type, scratchPath, colors)
        } else {
            val wellCenter = Offset(size.width * 0.5f, size.height * 0.5f)
            val wellRadius = size.minDimension * 0.47f
            drawCircle(
                color = Color(0xFF0A0E16),
                radius = wellRadius,
                center = wellCenter,
            )
            drawCircle(
                color = colors.metalMid.copy(alpha = 0.30f),
                radius = wellRadius,
                center = wellCenter,
                style = Stroke(width = 1.dp.toPx()),
            )
            when (type) {
                ModuleSculptureType.TYPING -> drawObsidianTyping3D(phase, intensity, scratchPath, colors)
                ModuleSculptureType.READING -> drawObsidianReading3D(phase, intensity, scratchPath, colors)
                ModuleSculptureType.EYE -> drawObsidianEye3D(phase, intensity, scratchPath, colors)
                ModuleSculptureType.BRAIN -> drawObsidianBrain3D(phase, intensity, scratchPath, brainEngine, colors)
            }
        }
    }
}

/**
 * 1. TYPING — 3D Extruded Mechanical Keycap Cluster & CNC Aluminum Deck.
 */
private fun DrawScope.drawObsidianTyping3D(
    phase: Float,
    intensity: Float,
    path: Path,
    colors: MrsadColors,
) {
    val w = size.width
    val h = size.height
    val cx = w * 0.5f
    val cy = h * 0.54f

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(colors.accent.copy(alpha = 0.24f * intensity), Color.Transparent),
            center = Offset(cx, cy),
            radius = w * 0.48f,
        ),
        radius = w * 0.48f,
        center = Offset(cx, cy),
    )

    val topX = cx
    val topY = cy - h * 0.30f
    val leftX = cx - w * 0.44f
    val leftY = cy - h * 0.04f
    val rightX = cx + w * 0.44f
    val rightY = cy - h * 0.04f
    val botX = cx
    val botY = cy + h * 0.22f
    val wallDepth = h * 0.14f

    // Floor drop shadow
    path.reset()
    path.moveTo(leftX, leftY + wallDepth + h * 0.04f)
    path.lineTo(botX, botY + wallDepth + h * 0.06f)
    path.lineTo(rightX, rightY + wallDepth + h * 0.04f)
    path.lineTo(topX, topY + wallDepth)
    path.close()
    drawPath(path, Color.Black.copy(alpha = 0.62f))

    // Left Front Extruded Chassis Wall (lit bevel)
    path.reset()
    path.moveTo(leftX, leftY)
    path.lineTo(botX, botY)
    path.lineTo(botX, botY + wallDepth)
    path.lineTo(leftX, leftY + wallDepth)
    path.close()
    drawPath(path, Color(0xFF344054))

    // Right Extruded Chassis Wall (deep shadow side)
    path.reset()
    path.moveTo(botX, botY)
    path.lineTo(rightX, rightY)
    path.lineTo(rightX, rightY + wallDepth)
    path.lineTo(botX, botY + wallDepth)
    path.close()
    drawPath(path, Color(0xFF1A2230))

    // Top CNC Machined Deck
    path.reset()
    path.moveTo(topX, topY)
    path.lineTo(rightX, rightY)
    path.lineTo(botX, botY)
    path.lineTo(leftX, leftY)
    path.close()
    drawPath(path, Color(0xFF242E40))
    drawPath(
        path = path,
        color = colors.metalLight.copy(alpha = 0.55f),
        style = Stroke(width = 1.2.dp.toPx()),
    )

    // 4 Sculpted 3D Isometric Keycaps in 2×2 grid on the deck
    val keyCoords = listOf(
        Triple(cx, cy - h * 0.17f, 0),
        Triple(cx - w * 0.17f, cy - h * 0.06f, 1),
        Triple(cx + w * 0.17f, cy - h * 0.06f, 2),
        Triple(cx, cy + h * 0.05f, 3),
    )
    val kw = w * 0.135f
    val kh = h * 0.082f
    val keyHeight = h * 0.095f

    keyCoords.forEach { (kx, ky, idx) ->
        val travel = if (idx == 3) (0.5f + 0.5f * sin(phase + idx)) * (h * 0.022f) else 0f
        val topKy = ky - keyHeight + travel
        val isHeroKey = idx == 3

        // Switch underglow well
        drawCircle(
            color = (if (isHeroKey) colors.accent else colors.metalLight).copy(alpha = 0.35f),
            radius = kw * 0.85f,
            center = Offset(kx, ky + kh * 0.2f),
        )

        // Left Front 3D Keycap Skirt
        path.reset()
        path.moveTo(kx - kw, topKy)
        path.lineTo(kx, topKy + kh)
        path.lineTo(kx, ky + kh * 1.15f)
        path.lineTo(kx - kw * 1.12f, ky)
        path.close()
        drawPath(
            path = path,
            color = if (isHeroKey) colors.accent.copy(alpha = 0.78f) else colors.metalMid,
        )

        // Right Shadow 3D Keycap Skirt
        path.reset()
        path.moveTo(kx, topKy + kh)
        path.lineTo(kx + kw, topKy)
        path.lineTo(kx + kw * 1.12f, ky)
        path.lineTo(kx, ky + kh * 1.15f)
        path.close()
        drawPath(
            path = path,
            color = if (isHeroKey) Color(0xFF3A5272) else colors.metalDark,
        )

        // Top Sculpted Keycap Dish
        path.reset()
        path.moveTo(kx, topKy - kh)
        path.lineTo(kx + kw, topKy)
        path.lineTo(kx, topKy + kh)
        path.lineTo(kx - kw, topKy)
        path.close()
        drawPath(
            path = path,
            color = if (isHeroKey) Color(0xFFEAF3FF) else colors.metalLight,
        )
        drawPath(
            path = path,
            color = Color.White.copy(alpha = 0.85f),
            style = Stroke(width = 0.9.dp.toPx()),
        )
    }
}

/**
 * 2. READING — 3D Sculpted Open Tome with Thick Page-Stack Volume & Floating Glass Prism.
 */
private fun DrawScope.drawObsidianReading3D(
    phase: Float,
    intensity: Float,
    path: Path,
    colors: MrsadColors,
) {
    val w = size.width
    val h = size.height
    val cx = w * 0.5f
    val cy = h * 0.54f
    val lift = sin(phase) * (h * 0.018f)

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(colors.accent.copy(alpha = 0.24f * intensity), Color.Transparent),
            center = Offset(cx, cy - h * 0.04f),
            radius = w * 0.48f,
        ),
        radius = w * 0.48f,
        center = Offset(cx, cy - h * 0.04f),
    )

    val spineTop = cy - h * 0.20f + lift
    val spineBot = cy + h * 0.14f + lift
    val pageThickness = h * 0.10f

    // Left Page Block 3D Front Thickness Wall
    path.reset()
    path.moveTo(cx, spineBot)
    path.lineTo(cx - w * 0.38f, spineBot - h * 0.08f)
    path.lineTo(cx - w * 0.38f, spineBot - h * 0.08f + pageThickness)
    path.lineTo(cx, spineBot + pageThickness)
    path.close()
    drawPath(path, colors.metalMid)

    // Right Page Block 3D Front Thickness Wall
    path.reset()
    path.moveTo(cx, spineBot)
    path.lineTo(cx + w * 0.38f, spineBot - h * 0.08f)
    path.lineTo(cx + w * 0.38f, spineBot - h * 0.08f + pageThickness)
    path.lineTo(cx, spineBot + pageThickness)
    path.close()
    drawPath(path, colors.metalDark)

    // Left Sculpted 3D Page Surface
    path.reset()
    path.moveTo(cx, spineTop)
    path.lineTo(cx - w * 0.38f, spineTop - h * 0.06f)
    path.lineTo(cx - w * 0.38f, spineBot - h * 0.08f)
    path.lineTo(cx, spineBot)
    path.close()
    drawPath(path, colors.metalLight)
    drawPath(path, Color.White.copy(alpha = 0.75f), style = Stroke(width = 1.dp.toPx()))

    // Right Sculpted 3D Page Surface
    path.reset()
    path.moveTo(cx, spineTop)
    path.lineTo(cx + w * 0.38f, spineTop - h * 0.06f)
    path.lineTo(cx + w * 0.38f, spineBot - h * 0.08f)
    path.lineTo(cx, spineBot)
    path.close()
    drawPath(path, Color(0xFFB6C2D4))
    drawPath(path, Color.White.copy(alpha = 0.55f), style = Stroke(width = 1.dp.toPx()))

    // Central 3D Spine Ridge
    drawLine(
        color = Color(0xFF1A2232),
        start = Offset(cx, spineTop),
        end = Offset(cx, spineBot + pageThickness),
        strokeWidth = 2.4.dp.toPx(),
    )

    // Levitating 3D Glass RSVP Prism Plane above the book
    val prismY = spineTop - h * 0.08f
    path.reset()
    path.moveTo(cx - w * 0.26f, prismY)
    path.lineTo(cx + w * 0.26f, prismY - h * 0.04f)
    path.lineTo(cx + w * 0.22f, prismY + h * 0.12f)
    path.lineTo(cx - w * 0.22f, prismY + h * 0.16f)
    path.close()
    drawPath(path, colors.accent.copy(alpha = 0.38f))
    drawPath(path, Color.White.copy(alpha = 0.85f), style = Stroke(width = 1.2.dp.toPx()))

    for (row in 0..2) {
        val ly = spineTop + h * (0.06f + row * 0.07f)
        drawLine(
            color = Color(0xFF1F2838).copy(alpha = 0.72f),
            start = Offset(cx - w * 0.30f, ly - h * 0.03f),
            end = Offset(cx - w * 0.08f, ly),
            strokeWidth = 1.6.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color(0xFF1F2838).copy(alpha = 0.72f),
            start = Offset(cx + w * 0.08f, ly),
            end = Offset(cx + w * 0.30f, ly - h * 0.03f),
            strokeWidth = 1.6.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/**
 * 3. EYE — 3D Shaded Ocular Globe, Radial Iris Dome & Corneal Reflection.
 */
private fun DrawScope.drawObsidianEye3D(
    phase: Float,
    intensity: Float,
    path: Path,
    colors: MrsadColors,
) {
    val w = size.width
    val h = size.height
    val cx = w * 0.5f
    val cy = h * 0.5f
    val r = size.minDimension * 0.36f

    // 1. Outer 3D Machined Gimbal Housing Ring
    drawCircle(
        color = Color.Black.copy(alpha = 0.62f),
        radius = r * 1.18f,
        center = Offset(cx, cy + r * 0.08f),
    )
    drawCircle(
        color = colors.metalMid,
        radius = r * 1.12f,
        center = Offset(cx, cy),
        style = Stroke(width = r * 0.14f),
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.75f),
        radius = r * 1.16f,
        center = Offset(cx, cy),
        style = Stroke(width = 1.dp.toPx()),
    )

    // 2. 3D Shaded Sclera / Metallic Globe
    drawCircle(
        color = Color(0xFF141B28),
        radius = r * 0.96f,
        center = Offset(cx, cy),
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFD8E3F2),
                Color(0xFF1E2636),
            ),
            center = Offset(cx - r * 0.28f, cy - r * 0.28f),
            radius = r * 1.15f,
        ),
        radius = r * 0.92f,
        center = Offset(cx, cy),
    )

    // 3. 3D Iris Cavity & Radial Fibers
    val gazeX = cx + cos(phase) * (r * 0.08f)
    val gazeY = cy + sin(phase * 1.5f) * (r * 0.05f)
    val irisR = r * 0.62f

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                colors.accent,
                Color(0xFF162236),
            ),
            center = Offset(gazeX, gazeY),
            radius = irisR,
        ),
        radius = irisR,
        center = Offset(gazeX, gazeY),
    )

    val fibers = 16
    for (i in 0 until fibers) {
        val angle = (2.0 * PI * i / fibers).toFloat() + phase * 0.12f
        val innerR = irisR * 0.40f
        val outerR = irisR * 0.90f
        drawLine(
            color = Color.White.copy(alpha = if (i % 2 == 0) 0.45f else 0.22f),
            start = Offset(gazeX + cos(angle) * innerR, gazeY + sin(angle) * innerR),
            end = Offset(gazeX + cos(angle) * outerR, gazeY + sin(angle) * outerR),
            strokeWidth = 1.1.dp.toPx(),
        )
    }

    // 4. Deep 3D Pupil Well
    val pupilR = irisR * 0.42f
    drawCircle(
        color = Color(0xFF04060A),
        radius = pupilR,
        center = Offset(gazeX, gazeY),
    )
    drawCircle(
        color = colors.accent.copy(alpha = 0.78f),
        radius = pupilR,
        center = Offset(gazeX, gazeY),
        style = Stroke(width = 1.2.dp.toPx()),
    )

    // 5. 3D Curved Corneal Specular Highlights
    drawCircle(
        color = Color.White.copy(alpha = 0.92f),
        radius = r * 0.15f,
        center = Offset(gazeX - irisR * 0.36f, gazeY - irisR * 0.38f),
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.55f),
        radius = r * 0.06f,
        center = Offset(gazeX + irisR * 0.32f, gazeY + irisR * 0.30f),
    )
}

/**
 * 4. BRAIN — 3D Solid-Shaded Geodesic Polyhedron Cortex (`Core3D` engine).
 */
private fun DrawScope.drawObsidianBrain3D(
    phase: Float,
    intensity: Float,
    path: Path,
    engine: Core3D,
    colors: MrsadColors,
) {
    val cx = size.width * 0.5f
    val cy = size.height * 0.5f
    val r = size.minDimension * 0.36f

    engine.update(
        yaw = phase + 0.55f,
        pitch = -0.24f,
        roll = 0.12f,
        centerX = cx,
        centerY = cy,
        radiusPx = r,
        time = phase,
    )

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(colors.accent.copy(alpha = 0.26f * intensity), Color.Transparent),
            center = Offset(cx, cy),
            radius = r * 1.45f,
        ),
        radius = r * 1.45f,
        center = Offset(cx, cy),
    )

    for (idx in 0 until engine.faceCount) {
        val f = engine.faceOrder[idx]
        if (engine.faceNormalZ[f] <= 0f) continue
        val face = CoreGeometry.faces[f]
        val x0 = engine.screenX[face[0]]
        val y0 = engine.screenY[face[0]]
        val x1 = engine.screenX[face[1]]
        val y1 = engine.screenY[face[1]]
        val x2 = engine.screenX[face[2]]
        val y2 = engine.screenY[face[2]]

        path.reset()
        path.moveTo(x0, y0)
        path.lineTo(x1, y1)
        path.lineTo(x2, y2)
        path.close()

        val shade = engine.faceShade[f]
        val spec = engine.faceSpecular[f]
        val fill = Color(
            red = (0.14f + 0.58f * shade + 0.28f * spec).coerceIn(0f, 1f),
            green = (0.18f + 0.62f * shade + 0.26f * spec).coerceIn(0f, 1f),
            blue = (0.26f + 0.66f * shade + 0.22f * spec).coerceIn(0f, 1f),
            alpha = 0.94f,
        )
        drawPath(path, fill)
        drawPath(
            path = path,
            color = Color(0xFF0A0E17).copy(alpha = 0.85f),
            style = Stroke(width = 1.4.dp.toPx()),
        )
    }

    for (v in 0 until engine.vertexCount) {
        if (engine.screenZ[v] < 0f) continue
        val vx = engine.screenX[v]
        val vy = engine.screenY[v]
        val nr = r * 0.08f
        drawCircle(
            color = Color.White,
            radius = nr,
            center = Offset(vx, vy),
        )
    }
}

/**
 * THEME 02 — NOIR: Flat 2D Swiss Technical Schematics (1.5dp monochrome lines).
 */
private fun DrawScope.drawNoirSchematic(
    type: ModuleSculptureType,
    path: Path,
    colors: MrsadColors,
) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Square)
    val thinStroke = Stroke(width = 1.dp.toPx())

    drawRect(
        color = colors.border,
        topLeft = Offset(w * 0.08f, h * 0.08f),
        size = Size(w * 0.84f, h * 0.84f),
        style = thinStroke,
    )

    when (type) {
        ModuleSculptureType.TYPING -> {
            val cols = 3
            val rows = 2
            val kw = w * 0.19f
            val kh = h * 0.19f
            val gap = w * 0.045f
            val startX = cx - (cols * kw + (cols - 1) * gap) / 2f
            val startY = cy - h * 0.22f
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    val x = startX + c * (kw + gap)
                    val y = startY + r * (kh + gap)
                    drawRect(
                        color = colors.textPrimary,
                        topLeft = Offset(x, y),
                        size = Size(kw, kh),
                        style = stroke,
                    )
                }
            }
            drawRect(
                color = colors.textPrimary,
                topLeft = Offset(startX, startY + 2 * (kh + gap)),
                size = Size(cols * kw + (cols - 1) * gap, kh * 0.65f),
                style = stroke,
            )
        }
        ModuleSculptureType.READING -> {
            val bw = w * 0.68f
            val bh = h * 0.56f
            val left = cx - bw / 2f
            val top = cy - bh / 2f
            drawRect(colors.textPrimary, Offset(left, top), Size(bw, bh), style = stroke)
            drawLine(colors.textPrimary, Offset(cx, top), Offset(cx, top + bh), strokeWidth = 1.5.dp.toPx())
            for (i in 1..3) {
                val ly = top + bh * (i / 4f)
                drawLine(colors.textSecondary, Offset(left + w * 0.06f, ly), Offset(cx - w * 0.05f, ly), strokeWidth = 1.2.dp.toPx())
                drawLine(colors.textSecondary, Offset(cx + w * 0.05f, ly), Offset(left + bw - w * 0.06f, ly), strokeWidth = 1.2.dp.toPx())
            }
        }
        ModuleSculptureType.EYE -> {
            val rx = w * 0.34f
            val ry = h * 0.21f
            path.reset()
            path.moveTo(cx - rx, cy)
            path.quadraticTo(cx, cy - ry * 1.6f, cx + rx, cy)
            path.quadraticTo(cx, cy + ry * 1.6f, cx - rx, cy)
            path.close()
            drawPath(path, colors.textPrimary, style = stroke)
            drawCircle(colors.textPrimary, radius = minOf(w, h) * 0.12f, center = Offset(cx, cy), style = stroke)
            drawCircle(colors.textPrimary, radius = minOf(w, h) * 0.04f, center = Offset(cx, cy))
        }
        ModuleSculptureType.BRAIN -> {
            val r = minOf(w, h) * 0.32f
            val pts = Array(6) { i ->
                val a = (2.0 * PI * i / 6.0 - PI / 2.0).toFloat()
                Offset(cx + cos(a) * r, cy + sin(a) * r)
            }
            for (i in 0 until 6) {
                drawLine(colors.textPrimary, pts[i], pts[(i + 1) % 6], strokeWidth = 1.5.dp.toPx())
                drawLine(colors.borderStrong, Offset(cx, cy), pts[i], strokeWidth = 1.dp.toPx())
                drawCircle(colors.textPrimary, radius = 2.5.dp.toPx(), center = pts[i])
            }
            drawCircle(colors.textPrimary, radius = 3.5.dp.toPx(), center = Offset(cx, cy))
        }
    }
}
