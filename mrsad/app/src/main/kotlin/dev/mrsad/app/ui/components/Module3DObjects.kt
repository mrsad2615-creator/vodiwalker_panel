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
 * - [ModuleSculptureType.READING]: 3D open tome with thick page-stack volume & floating glass prisms.
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
        if (size.minDimension <= 2f) return@Canvas
        if (colors.isNoir) {
            drawNoirSchematic(type, scratchPath, colors)
        } else {
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

    // Ambient volumetric glow under the CNC chassis
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(colors.accent.copy(alpha = 0.25f * intensity), Color.Transparent),
            center = Offset(cx, cy),
            radius = w * 0.50f,
        ),
        radius = w * 0.50f,
        center = Offset(cx, cy),
    )

    // 3D Isometric CNC Aluminum Chassis Base (Top Deck + Left Front Wall + Right Shadow Wall)
    val topX = cx
    val topY = cy - h * 0.30f
    val leftX = cx - w * 0.44f
    val leftY = cy - h * 0.04f
    val rightX = cx + w * 0.44f
    val rightY = cy - h * 0.04f
    val botX = cx
    val botY = cy + h * 0.22f
    val wallDepth = h * 0.14f

    // Drop shadow on floor
    path.reset()
    path.moveTo(leftX, leftY + wallDepth + h * 0.04f)
    path.lineTo(botX, botY + wallDepth + h * 0.06f)
    path.lineTo(rightX, rightY + wallDepth + h * 0.04f)
    path.lineTo(topX, topY + wallDepth)
    path.close()
    drawPath(path, Color.Black.copy(alpha = 0.65f))

    // Left Front Extruded Chassis Wall (lit bevel)
    path.reset()
    path.moveTo(leftX, leftY)
    path.lineTo(botX, botY)
    path.lineTo(botX, botY + wallDepth)
    path.lineTo(leftX, leftY + wallDepth)
    path.close()
    drawPath(
        path = path,
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFF3E4A5E), Color(0xFF1B2230)),
            start = Offset(leftX, leftY),
            end = Offset(botX, botY + wallDepth),
        ),
    )

    // Right Extruded Chassis Wall (deep shadow side)
    path.reset()
    path.moveTo(botX, botY)
    path.lineTo(rightX, rightY)
    path.lineTo(rightX, rightY + wallDepth)
    path.lineTo(botX, botY + wallDepth)
    path.close()
    drawPath(
        path = path,
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFF263042), Color(0xFF0D121B)),
            start = Offset(botX, botY),
            end = Offset(rightX, rightY + wallDepth),
        ),
    )

    // Top CNC Machined Deck
    path.reset()
    path.moveTo(topX, topY)
    path.lineTo(rightX, rightY)
    path.lineTo(botX, botY)
    path.lineTo(leftX, leftY)
    path.close()
    drawPath(
        path = path,
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFF2E384B), Color(0xFF151B26)),
            start = Offset(topX, topY),
            end = Offset(botX, botY),
        ),
    )
    drawPath(
        path = path,
        color = colors.metalLight.copy(alpha = 0.55f),
        style = Stroke(width = 1.3.dp.toPx(), join = StrokeJoin.Round),
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
        val travel = if (idx == 3) (0.5f + 0.5f * sin(phase + idx)) * (h * 0.025f) else 0f
        val topKy = ky - keyHeight + travel
        val isHeroKey = idx == 3

        // Switch underglow well
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    (if (isHeroKey) colors.accent else colors.metalLight).copy(alpha = 0.55f),
                    Color.Transparent,
                ),
                center = Offset(kx, ky + kh * 0.3f),
                radius = kw * 1.15f,
            ),
            radius = kw * 1.15f,
            center = Offset(kx, ky + kh * 0.3f),
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
            brush = Brush.verticalGradient(
                colors = if (isHeroKey) {
                    listOf(colors.accent.copy(alpha = 0.85f), Color(0xFF283A52))
                } else {
                    listOf(Color(0xFF7B889E), Color(0xFF343E50))
                },
            ),
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
            brush = Brush.verticalGradient(
                colors = if (isHeroKey) {
                    listOf(Color(0xFF4C688A), Color(0xFF182436))
                } else {
                    listOf(Color(0xFF4A5568), Color(0xFF1D2432))
                },
            ),
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
            brush = Brush.linearGradient(
                colors = if (isHeroKey) {
                    listOf(Color.White, colors.accent, Color(0xFF7896B8))
                } else {
                    listOf(Color(0xFFF5F8FC), colors.metalLight, colors.metalMid)
                },
                start = Offset(kx - kw, topKy - kh),
                end = Offset(kx + kw, topKy + kh),
            ),
        )
        drawPath(
            path = path,
            color = Color.White.copy(alpha = 0.80f),
            style = Stroke(width = 0.9.dp.toPx(), join = StrokeJoin.Round),
        )
    }
}

/**
 * 2. READING — 3D Sculpted Open Tome with Thick Page-Stack Volume & Floating Glass Prisms.
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
    val lift = sin(phase) * (h * 0.02f)

    // Ambient volumetric backlight
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(colors.accent.copy(alpha = 0.24f * intensity), Color.Transparent),
            center = Offset(cx, cy - h * 0.04f),
            radius = w * 0.48f,
        ),
        radius = w * 0.48f,
        center = Offset(cx, cy - h * 0.04f),
    )

    // Floor shadow under the 3D book
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(Color.Black.copy(alpha = 0.68f), Color.Transparent),
            center = Offset(cx, cy + h * 0.25f),
            radius = w * 0.42f,
        ),
        topLeft = Offset(cx - w * 0.40f, cy + h * 0.16f),
        size = Size(w * 0.80f, h * 0.18f),
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
    drawPath(
        path = path,
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFF7B889E), Color(0xFF283244)),
            start = Offset(cx - w * 0.38f, spineBot),
            end = Offset(cx, spineBot + pageThickness),
        ),
    )

    // Right Page Block 3D Front Thickness Wall
    path.reset()
    path.moveTo(cx, spineBot)
    path.lineTo(cx + w * 0.38f, spineBot - h * 0.08f)
    path.lineTo(cx + w * 0.38f, spineBot - h * 0.08f + pageThickness)
    path.lineTo(cx, spineBot + pageThickness)
    path.close()
    drawPath(
        path = path,
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFF566378), Color(0xFF1A2230)),
            start = Offset(cx, spineBot),
            end = Offset(cx + w * 0.38f, spineBot + pageThickness),
        ),
    )

    // Left Sculpted 3D Curved Page Surface
    path.reset()
    path.moveTo(cx, spineTop)
    path.cubicTo(
        cx - w * 0.14f, spineTop - h * 0.08f,
        cx - w * 0.28f, spineTop - h * 0.08f,
        cx - w * 0.38f, spineTop - h * 0.04f,
    )
    path.lineTo(cx - w * 0.38f, spineBot - h * 0.08f)
    path.cubicTo(
        cx - w * 0.26f, spineBot - h * 0.12f,
        cx - w * 0.12f, spineBot - h * 0.08f,
        cx, spineBot,
    )
    path.close()
    drawPath(
        path = path,
        brush = Brush.horizontalGradient(
            colors = listOf(Color(0xFFF3F7FC), colors.metalLight, Color(0xFF6B778D)),
            startX = cx - w * 0.38f,
            endX = cx,
        ),
    )

    // Right Sculpted 3D Curved Page Surface
    path.reset()
    path.moveTo(cx, spineTop)
    path.cubicTo(
        cx + w * 0.14f, spineTop - h * 0.08f,
        cx + w * 0.28f, spineTop - h * 0.08f,
        cx + w * 0.38f, spineTop - h * 0.04f,
    )
    path.lineTo(cx + w * 0.38f, spineBot - h * 0.08f)
    path.cubicTo(
        cx + w * 0.26f, spineBot - h * 0.12f,
        cx + w * 0.12f, spineBot - h * 0.08f,
        cx, spineBot,
    )
    path.close()
    drawPath(
        path = path,
        brush = Brush.horizontalGradient(
            colors = listOf(Color(0xFF5A657A), colors.metalMid, Color(0xFFDCE5F2)),
            startX = cx,
            endX = cx + w * 0.38f,
        ),
    )

    // Levitating 3D Glass RSVP Prism Plane above the book
    val prismY = spineTop - h * 0.08f
    path.reset()
    path.moveTo(cx - w * 0.26f, prismY)
    path.lineTo(cx + w * 0.26f, prismY - h * 0.04f)
    path.lineTo(cx + w * 0.22f, prismY + h * 0.12f)
    path.lineTo(cx - w * 0.22f, prismY + h * 0.16f)
    path.close()
    drawPath(
        path = path,
        brush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.50f),
                colors.accent.copy(alpha = 0.28f),
            ),
            start = Offset(cx - w * 0.26f, prismY),
            end = Offset(cx + w * 0.22f, prismY + h * 0.14f),
        ),
    )
    drawPath(
        path = path,
        color = Color.White.copy(alpha = 0.85f),
        style = Stroke(width = 1.2.dp.toPx(), join = StrokeJoin.Round),
    )

    // Glowing kinetic reading lines on the pages & prism
    for (row in 0..2) {
        val ly = spineTop + h * (0.06f + row * 0.07f)
        drawLine(
            color = Color(0xFF1F2838).copy(alpha = 0.72f),
            start = Offset(cx - w * 0.30f, ly - h * 0.03f),
            end = Offset(cx - w * 0.08f, ly),
            strokeWidth = 1.8.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color(0xFF1F2838).copy(alpha = 0.72f),
            start = Offset(cx + w * 0.08f, ly),
            end = Offset(cx + w * 0.30f, ly - h * 0.03f),
            strokeWidth = 1.8.dp.toPx(),
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
        color = Color.Black.copy(alpha = 0.65f),
        radius = r * 1.18f,
        center = Offset(cx, cy + r * 0.08f),
    )
    drawCircle(
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFFF2F6FC), colors.metalMid, Color(0xFF18202E)),
            start = Offset(cx - r, cy - r),
            end = Offset(cx + r, cy + r),
        ),
        radius = r * 1.12f,
        center = Offset(cx, cy),
        style = Stroke(width = r * 0.14f),
    )

    // 2. 3D Shaded Sclera / Metallic Globe
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFD8E3F2),
                Color(0xFF6B7890),
                Color(0xFF1E2636),
                Color(0xFF090C14),
            ),
            center = Offset(cx - r * 0.28f, cy - r * 0.28f),
            radius = r * 1.25f,
        ),
        radius = r * 0.96f,
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
                Color(0xFF42638E),
                Color(0xFF142034),
            ),
            center = Offset(gazeX, gazeY),
            radius = irisR,
        ),
        radius = irisR,
        center = Offset(gazeX, gazeY),
    )

    // 20 Radial 3D Iris Fibers
    val fibers = 20
    for (i in 0 until fibers) {
        val angle = (2.0 * PI * i / fibers).toFloat() + phase * 0.12f
        val innerR = irisR * 0.38f
        val outerR = irisR * 0.92f
        drawLine(
            color = Color.White.copy(alpha = if (i % 2 == 0) 0.45f else 0.22f),
            start = Offset(gazeX + cos(angle) * innerR, gazeY + sin(angle) * innerR),
            end = Offset(gazeX + cos(angle) * outerR, gazeY + sin(angle) * outerR),
            strokeWidth = 1.2.dp.toPx(),
            cap = StrokeCap.Round,
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
        color = colors.accent.copy(alpha = 0.75f),
        radius = pupilR,
        center = Offset(gazeX, gazeY),
        style = Stroke(width = 1.2.dp.toPx()),
    )

    // 5. 3D Curved Corneal Specular Highlights (makes the eye unmistakably 3D spherical!)
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

    // 6. Sculpted 3D Metallic Upper & Lower Eyelid Bevels
    path.reset()
    path.moveTo(cx - r * 1.15f, cy)
    path.quadraticTo(cx, cy - r * 1.05f, cx + r * 1.15f, cy)
    drawPath(
        path = path,
        color = Color.White.copy(alpha = 0.65f),
        style = Stroke(width = 2.0.dp.toPx(), cap = StrokeCap.Round),
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

    // Ambient volumetric halo + floor shadow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(colors.accent.copy(alpha = 0.28f * intensity), Color.Transparent),
            center = Offset(cx, cy),
            radius = r * 1.5f,
        ),
        radius = r * 1.5f,
        center = Offset(cx, cy),
    )

    // Solid Shaded 3D Facets (ordered back to front)
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
            style = Stroke(width = 1.6.dp.toPx(), join = StrokeJoin.Round),
        )
        drawPath(
            path = path,
            color = Color.White.copy(alpha = (0.35f + 0.55f * shade).coerceIn(0f, 1f)),
            style = Stroke(width = 0.8.dp.toPx(), join = StrokeJoin.Round),
        )
    }

    // 3D Spherical Neural Synapse Nodes on Front Vertices
    for (v in 0 until engine.vertexCount) {
        if (engine.screenZ[v] < 0f) continue
        val vx = engine.screenX[v]
        val vy = engine.screenY[v]
        val nr = r * 0.085f
        drawCircle(
            color = Color.White,
            radius = nr,
            center = Offset(vx, vy),
        )
        drawCircle(
            color = colors.accent.copy(alpha = 0.65f),
            radius = nr * 1.7f,
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
