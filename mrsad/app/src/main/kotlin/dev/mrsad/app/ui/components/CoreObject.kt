package dev.mrsad.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.mrsad.app.data.settings.GraphicsQuality
import dev.mrsad.app.ui.theme.MrsadColors
import dev.mrsad.app.ui.theme.MrsadTheme
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/**
 * The MRSAD Core: True 3D Volumetric PBR-Shaded & Machined Titanium Reactor.
 *
 * Renders:
 * 1. A solid 3D Phong/PBR shaded geodesic titanium shell with multi-step
 *    bevelled armor plates and recessed glowing plasma reactor apertures.
 * 2. A volumetric 3D liquid-metal & plasma nucleus sphere with specular
 *    hotspots and Fresnel rim lighting.
 * 3. Thick 3D cylindrical torus gyroscope rings (dark shadow base + brushed
 *    titanium cylinder + razor-sharp white specular crest) and 3D spherical
 *    chrome satellites.
 */
@Composable
fun MrsadCore(
    energy: Float,
    modifier: Modifier = Modifier,
    pulse: Boolean = true,
    streakDays: Int = 0,
    bestWpm: Int = 0,
) {
    val colors = MrsadTheme.colors
    val motion = MrsadTheme.motion
    val quality = motion.quality

    val ringSegments = when (quality) {
        GraphicsQuality.HIGH -> 56
        GraphicsQuality.BALANCED -> 40
        GraphicsQuality.PERFORMANCE -> 24
    }
    val engine = remember(ringSegments) { Core3D(ringSegments) }
    engine.nodeCount = when (quality) {
        GraphicsQuality.HIGH -> 6
        GraphicsQuality.BALANCED -> 4
        GraphicsQuality.PERFORMANCE -> 0
    }
    val scene = remember { CoreScene() }
    val scratchPath = remember { Path() }
    val clampedEnergy = energy.coerceIn(0f, 1f)

    LaunchedEffect(motion.animationsEnabled, pulse) {
        if (!motion.animationsEnabled) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val delta = if (last == 0L) {
                    0f
                } else {
                    ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                }
                last = now
                scene.advance(delta, turntable = pulse)
            }
        }
    }

    Box(modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "هستهٔ سه‌بعدی MRSAD"
                }
                .pointerInput(Unit) {
                    val tracker = VelocityTracker()
                    detectDragGestures(
                        onDragStart = { tracker.resetTracking() },
                        onDrag = { change, amount ->
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            scene.onDrag(amount)
                        },
                        onDragEnd = {
                            val velocity = tracker.calculateVelocity()
                            scene.onRelease(velocity.x)
                        },
                        onDragCancel = { scene.onRelease(0f) },
                    )
                }
                .pointerInput(motion.animationsEnabled) {
                    detectTapGestures(
                        onTap = {
                            if (motion.animationsEnabled) scene.onTap()
                        },
                    )
                },
        ) {
            drawCore(
                engine = engine,
                scene = scene,
                scratchPath = scratchPath,
                colors = colors,
                energy = clampedEnergy,
                streakDays = streakDays,
                bestWpm = bestWpm,
                quality = quality,
            )
        }
    }
}

private class CoreScene {
    var time by mutableFloatStateOf(0f)
    var yaw by mutableFloatStateOf(0.62f)
    var userPitch by mutableFloatStateOf(-0.16f)
    var yawVelocity by mutableFloatStateOf(0f)
    var pulseProgress by mutableFloatStateOf(-1f)
    private var idleDelay = 0f

    val pitch: Float get() = (userPitch + 0.10f * sin(time * 0.45f)).coerceIn(-1.1f, 1.1f)
    val roll: Float get() = 0.05f * sin(time * 0.33f)

    fun advance(delta: Float, turntable: Boolean) {
        time += delta
        yaw += yawVelocity * delta
        yawVelocity *= exp(-INERTIA_FRICTION * delta)
        if (yawVelocity < -0.001f || yawVelocity > 0.001f) {
            idleDelay = IDLE_RAMP
        }
        idleDelay = max(0f, idleDelay - delta)
        if (turntable) {
            val ramp = 1f - (idleDelay / IDLE_RAMP)
            yaw += IDLE_SPEED * ramp * delta
        }
        if (pulseProgress >= 0f) {
            pulseProgress += delta / PULSE_DURATION
            if (pulseProgress >= 1f) pulseProgress = -1f
        }
    }

    fun onDrag(amount: Offset) {
        yaw += amount.x * DRAG_SENSITIVITY
        userPitch = (userPitch + amount.y * DRAG_SENSITIVITY).coerceIn(-0.9f, 0.9f)
        idleDelay = IDLE_RAMP
    }

    fun onRelease(velocityX: Float) {
        yawVelocity = (velocityX * DRAG_SENSITIVITY).coerceIn(-MAX_SPIN, MAX_SPIN)
        idleDelay = IDLE_RAMP
    }

    fun onTap() {
        pulseProgress = 0f
        idleDelay = IDLE_RAMP
    }

    private companion object {
        const val DRAG_SENSITIVITY = 0.006f
        const val MAX_SPIN = 7f
        const val INERTIA_FRICTION = 2.4f
        const val IDLE_SPEED = 0.20f
        const val IDLE_RAMP = 1.6f
        const val PULSE_DURATION = 0.85f
    }
}

private fun DrawScope.drawCore(
    engine: Core3D,
    scene: CoreScene,
    scratchPath: Path,
    colors: MrsadColors,
    energy: Float,
    streakDays: Int,
    bestWpm: Int,
    quality: GraphicsQuality,
) {
    if (size.minDimension <= 4f) return
    val centerX = size.width / 2f
    val centerY = size.height / 2f + sin(scene.time * 0.62f) * size.minDimension * 0.006f
    val radiusPx = size.minDimension * 0.23f

    engine.update(
        yaw = scene.yaw,
        pitch = scene.pitch,
        roll = scene.roll,
        centerX = centerX,
        centerY = centerY,
        radiusPx = radiusPx,
        time = scene.time,
    )

    val pulse = if (scene.pulseProgress in 0f..1f) scene.pulseProgress else -1f
    val pulseGlow = if (pulse >= 0f) (1f - pulse) else 0f

    // ---- 1. Volumetric Ambient Halo & 3D Floor Shadow -----------------------
    val bloomRadius = radiusPx * 2.20f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                colors.accent.copy(alpha = 0.16f + 0.18f * energy + 0.12f * pulseGlow),
                Color.Transparent,
            ),
            center = Offset(centerX, centerY),
            radius = bloomRadius,
        ),
        radius = bloomRadius,
        center = Offset(centerX, centerY),
    )

    val poolWidth = radiusPx * 2.65f
    val poolHeight = radiusPx * 0.62f
    val poolCenter = Offset(centerX, centerY + radiusPx * 1.18f)
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(
                colors.accent.copy(alpha = 0.14f + 0.08f * energy),
                Color.Transparent,
            ),
            center = poolCenter,
            radius = poolWidth / 2f,
        ),
        topLeft = Offset(poolCenter.x - poolWidth / 2f, poolCenter.y - poolHeight / 2f),
        size = Size(poolWidth, poolHeight),
    )

    // ---- 2. Back halves of the 3D Cylindrical Gyroscope Rings ---------------
    drawRingHalf(
        engine = engine,
        scratchPath = scratchPath,
        offset = 0,
        centerX = centerX,
        centerY = centerY,
        radiusPx = radiusPx,
        bandWidth = radiusPx * 0.085f,
        near = colors.metalLight,
        far = colors.accent,
        front = false,
    )
    drawRingHalf(
        engine = engine,
        scratchPath = scratchPath,
        offset = engine.secondRingOffset,
        centerX = centerX,
        centerY = centerY,
        radiusPx = radiusPx,
        bandWidth = radiusPx * 0.038f,
        near = colors.metalLight,
        far = colors.metalMid,
        front = false,
    )
    drawRingHalf(
        engine = engine,
        scratchPath = scratchPath,
        offset = engine.thirdRingOffset,
        centerX = centerX,
        centerY = centerY,
        radiusPx = radiusPx,
        bandWidth = radiusPx * 0.048f,
        near = colors.metalLight,
        far = colors.metalDark,
        front = false,
    )

    // ---- 3. Solid Back-Facing Geodesic Chamber Plates (`nz <= 0`) -----------
    for (index in 0 until engine.faceCount) {
        val f = engine.faceOrder[index]
        if (engine.faceNormalZ[f] > 0.02f) continue
        val face = CoreGeometry.faces[f]
        scratchPath.reset()
        scratchPath.moveTo(engine.screenX[face[0]], engine.screenY[face[0]])
        scratchPath.lineTo(engine.screenX[face[1]], engine.screenY[face[1]])
        scratchPath.lineTo(engine.screenX[face[2]], engine.screenY[face[2]])
        scratchPath.close()
        val shade = engine.faceShade[f]
        val backColor = lerpColor(Color(0xFF070A10), Color(0xFF1C2434), shade * 0.6f)
            .copy(alpha = 0.94f)
        drawPath(scratchPath, backColor)
    }

    // ---- 4. Volumetric 3D PBR / Phong Plasma & Chrome Nucleus Sphere --------
    val nucleusRadius = radiusPx * (0.58f + 0.10f * energy + 0.10f * pulseGlow)
    val hotspot = Offset(centerX - nucleusRadius * 0.30f, centerY - nucleusRadius * 0.32f)
    drawCircle(
        color = Color(0xFF0D1422),
        radius = nucleusRadius,
        center = Offset(centerX, centerY),
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                colors.accent,
                Color(0xFF1A283E),
            ),
            center = hotspot,
            radius = nucleusRadius * 1.20f,
        ),
        radius = nucleusRadius * 0.94f,
        center = Offset(centerX, centerY),
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White,
                Color.Transparent,
            ),
            center = hotspot,
            radius = nucleusRadius * 0.68f,
        ),
        radius = nucleusRadius * 0.68f,
        center = hotspot,
    )

    // ---- 5. Solid Front-Facing 3D Bevelled Titanium Exoskeleton Plates ------
    // Each front-facing facet is rendered as a 3-layer solid 3D machined plate:
    // (a) Outer lit brushed-titanium bevel plate,
    // (b) Recessed dark gunmetal inner chamfer step,
    // (c) Luminous plasma reactor aperture core.
    for (index in 0 until engine.faceCount) {
        val f = engine.faceOrder[index]
        if (engine.faceNormalZ[f] <= 0.02f) continue
        val face = CoreGeometry.faces[f]
        val x0 = engine.screenX[face[0]]
        val y0 = engine.screenY[face[0]]
        val x1 = engine.screenX[face[1]]
        val y1 = engine.screenY[face[1]]
        val x2 = engine.screenX[face[2]]
        val y2 = engine.screenY[face[2]]
        val cx = (x0 + x1 + x2) / 3f
        val cy = (y0 + y1 + y2) / 3f

        val shade = engine.faceShade[f]
        val spec = engine.faceSpecular[f]
        val rim = engine.faceRim[f]

        // 5a. Solid Outer Titanium Plate (PBR normal-shaded)
        scratchPath.reset()
        scratchPath.moveTo(x0, y0)
        scratchPath.lineTo(x1, y1)
        scratchPath.lineTo(x2, y2)
        scratchPath.close()

        val basePlate = lerpColor(Color(0xFF1A2232), colors.metalMid, shade)
        val litPlate = lerpColor(
            basePlate,
            Color(0xFFF4F8FF),
            (spec * 0.85f + rim * 0.30f + shade * 0.22f).coerceIn(0f, 1f),
        ).copy(alpha = 0.94f)
        drawPath(scratchPath, litPlate)

        // 5b. Recessed Inner Machined Step (creates 3D armor plate thickness)
        val stepRatio = 0.66f
        scratchPath.reset()
        scratchPath.moveTo(cx + (x0 - cx) * stepRatio, cy + (y0 - cy) * stepRatio)
        scratchPath.lineTo(cx + (x1 - cx) * stepRatio, cy + (y1 - cy) * stepRatio)
        scratchPath.lineTo(cx + (x2 - cx) * stepRatio, cy + (y2 - cy) * stepRatio)
        scratchPath.close()
        val trenchColor = lerpColor(Color(0xFF0A0E17), Color(0xFF28344A), shade * 0.7f)
            .copy(alpha = 0.95f)
        drawPath(scratchPath, trenchColor)

        // 5c. Inner Glowing Reactor Aperture Window
        val coreRatio = 0.42f
        scratchPath.reset()
        scratchPath.moveTo(cx + (x0 - cx) * coreRatio, cy + (y0 - cy) * coreRatio)
        scratchPath.lineTo(cx + (x1 - cx) * coreRatio, cy + (y1 - cy) * coreRatio)
        scratchPath.lineTo(cx + (x2 - cx) * coreRatio, cy + (y2 - cy) * coreRatio)
        scratchPath.close()
        val windowGlow = lerpColor(
            colors.accent,
            Color.White,
            (shade * 0.55f + spec * 0.75f + energy * 0.35f).coerceIn(0f, 1f),
        ).copy(alpha = (0.55f + 0.38f * shade + 0.20f * energy).coerceIn(0f, 0.96f))
        drawPath(scratchPath, windowGlow)
    }

    // ---- 6. Precision Machined Seams & Specular Bevel Ridges ----------------
    if (quality != GraphicsQuality.PERFORMANCE) {
        val seamWidth = 2.2f.dp.toPx()
        val ridgeWidth = 1.1f.dp.toPx()
        for (e in CoreGeometry.edges.indices) {
            val edge = CoreGeometry.edges[e]
            val owners = CoreGeometry.edgeFaces[e]
            val frontVisible = owners.any { engine.faceNormalZ[it] > 0.0f }
            if (!frontVisible) continue

            val shade = if (owners.size == 2) {
                0.5f * (engine.faceShade[owners[0]] + engine.faceShade[owners[1]])
            } else {
                0.5f
            }
            val spec = if (owners.size == 2) {
                max(engine.faceSpecular[owners[0]], engine.faceSpecular[owners[1]])
            } else {
                0f
            }
            val start = Offset(engine.screenX[edge[0]], engine.screenY[edge[0]])
            val end = Offset(engine.screenX[edge[1]], engine.screenY[edge[1]])

            // Dark machined groove between armor plates
            drawLine(
                color = Color(0xFF06090F).copy(alpha = 0.88f),
                start = start,
                end = end,
                strokeWidth = seamWidth,
            )
            // Bright specular highlight along the chamfered edge
            val ridgeColor = lerpColor(colors.metalMid, Color.White, (shade * 0.65f + spec * 0.75f).coerceIn(0f, 1f))
                .copy(alpha = (0.50f + 0.48f * shade).coerceIn(0f, 1f))
            drawLine(
                color = ridgeColor,
                start = start,
                end = end,
                strokeWidth = ridgeWidth,
            )
        }
    }

    // ---- 7. Front halves of the 3D Cylindrical Gyroscope Rings --------------
    drawRingHalf(
        engine = engine,
        scratchPath = scratchPath,
        offset = 0,
        centerX = centerX,
        centerY = centerY,
        radiusPx = radiusPx,
        bandWidth = radiusPx * 0.085f,
        near = colors.metalLight,
        far = colors.accent,
        front = true,
    )
    drawRingHalf(
        engine = engine,
        scratchPath = scratchPath,
        offset = engine.secondRingOffset,
        centerX = centerX,
        centerY = centerY,
        radiusPx = radiusPx,
        bandWidth = radiusPx * 0.038f,
        near = colors.metalLight,
        far = colors.metalMid,
        front = true,
    )
    drawRingHalf(
        engine = engine,
        scratchPath = scratchPath,
        offset = engine.thirdRingOffset,
        centerX = centerX,
        centerY = centerY,
        radiusPx = radiusPx,
        bandWidth = radiusPx * 0.048f,
        near = colors.metalLight,
        far = colors.metalDark,
        front = true,
    )

    // ---- 8. 3D Spherical Chrome Orbiting Satellites -------------------------
    for (n in 0 until engine.nodeCount) {
        val depth = ((engine.nodeZ[n] + 1.8f) / 3.6f).coerceIn(0f, 1f)
        val x = centerX + engine.nodeX[n] * radiusPx
        val y = centerY + engine.nodeY[n] * radiusPx
        val radius = engine.nodeSize[n] * radiusPx * 1.32f
        drawCircle(
            color = Color(0xFF06080E).copy(alpha = 0.75f),
            radius = radius * 1.20f,
            center = Offset(x + radius * 0.14f, y + radius * 0.16f),
        )
        drawCircle(
            color = colors.metalDark.copy(alpha = 0.85f + 0.15f * depth),
            radius = radius,
            center = Offset(x, y),
        )
        drawCircle(
            color = colors.metalLight.copy(alpha = 0.75f + 0.25f * depth),
            radius = radius * 0.68f,
            center = Offset(x - radius * 0.18f, y - radius * 0.18f),
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.90f),
            radius = radius * 0.26f,
            center = Offset(x - radius * 0.30f, y - radius * 0.30f),
        )
    }

    // ---- 9. Streak Energy Sparks --------------------------------------------
    val sparks = streakDays.coerceIn(0, 24)
    if (sparks > 0) {
        for (s in 0 until sparks) {
            val index = (s * engine.ringSegments / sparks) % engine.ringSegments
            val depth = (engine.ringZ[index] + 1.8f) / 3.6f
            val x = engine.ringScreenX(0, index, centerX, radiusPx)
            val y = engine.ringScreenY(0, index, centerY, radiusPx)
            val radius = radiusPx * (0.020f + 0.016f * depth.coerceIn(0f, 1f))
            drawCircle(
                color = colors.accent.copy(alpha = (0.45f + 0.50f * depth).coerceIn(0f, 0.95f)),
                radius = radius,
                center = Offset(x, y),
            )
        }
    }

    // ---- 10. Precision Chronometer Graduations ------------------------------
    if (quality != GraphicsQuality.PERFORMANCE) {
        val ticks = 16 + (bestWpm / 8).coerceIn(0, 24)
        val step = engine.ringSegments / ticks
        if (step > 0) {
            for (t in 0 until ticks) {
                val index = (t * step) % engine.ringSegments
                val x = engine.ringScreenX(engine.secondRingOffset, index, centerX, radiusPx)
                val y = engine.ringScreenY(engine.secondRingOffset, index, centerY, radiusPx)
                val innerX = centerX + (x - centerX) * 0.91f
                val innerY = centerY + (y - centerY) * 0.91f
                drawLine(
                    color = colors.metalLight.copy(alpha = 0.45f),
                    start = Offset(innerX, innerY),
                    end = Offset(x, y),
                    strokeWidth = 1.1f.dp.toPx(),
                )
            }
        }
    }

    // ---- 11. Volumetric Specular Key-Light Glaze ----------------------------
    val sheenColor = if (colors.isLight) colors.metalLight else Color.White
    val sheenCenter = Offset(centerX - radiusPx * 0.34f, centerY - radiusPx * 0.40f)
    val sheenRadius = radiusPx * 0.92f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(sheenColor.copy(alpha = 0.14f), Color.Transparent),
            center = sheenCenter,
            radius = sheenRadius,
        ),
        radius = sheenRadius,
        center = sheenCenter,
    )

    // ---- 12. Interactive Shockwave ------------------------------------------
    if (pulse >= 0f) {
        val waveRadius = radiusPx * (0.5f + 1.6f * pulse)
        drawCircle(
            color = colors.accent.copy(alpha = (1f - pulse) * 0.55f),
            radius = waveRadius,
            center = Offset(centerX, centerY),
            style = Stroke(width = (0.8f + (1f - pulse) * 2.6f).dp.toPx()),
        )
    }
}

/**
 * Draws one half of an orbital ring as a 3-pass 3D cylindrical tube
 * (dark shadow base, metallic cylinder body, and white specular ridge).
 */
private fun DrawScope.drawRingHalf(
    engine: Core3D,
    scratchPath: Path,
    offset: Int,
    centerX: Float,
    centerY: Float,
    radiusPx: Float,
    bandWidth: Float,
    near: Color,
    far: Color,
    front: Boolean,
) {
    val segments = engine.ringSegments
    scratchPath.reset()
    var hasAny = false
    var connected = false
    var lowest = Float.MAX_VALUE
    var highest = -Float.MAX_VALUE
    var farX = 0f
    var farY = 0f
    var nearX = 0f
    var nearY = 0f

    for (i in 0 until segments) {
        val j = i + 1
        val depth = (engine.ringZ[offset + i] + engine.ringZ[offset + j]) / 2f
        val isFront = depth >= 0f
        if (isFront != front) {
            connected = false
            continue
        }

        val x1 = engine.ringScreenX(offset, i, centerX, radiusPx)
        val y1 = engine.ringScreenY(offset, i, centerY, radiusPx)
        val x2 = engine.ringScreenX(offset, j, centerX, radiusPx)
        val y2 = engine.ringScreenY(offset, j, centerY, radiusPx)

        if (!connected) {
            scratchPath.moveTo(x1, y1)
            connected = true
            hasAny = true
        }
        scratchPath.lineTo(x2, y2)

        if (depth < lowest) {
            lowest = depth
            farX = x1
            farY = y1
        }
        if (depth > highest) {
            highest = depth
            nearX = x1
            nearY = y1
        }
    }
    if (!hasAny) return

    val lowCloseness = ((lowest + 1.8f) / 3.6f).coerceIn(0f, 1f)
    val highCloseness = ((highest + 1.8f) / 3.6f).coerceIn(0f, 1f)
    val average = (lowCloseness + highCloseness) / 2f
    val baseAlpha = if (front) 0.55f else 0.28f
    val alphaRange = if (front) 0.40f else 0.25f
    val width = bandWidth * (0.65f + 0.55f * average)

    // Pass 1: 3D Cylinder Shadow Underside
    drawPath(
        path = scratchPath,
        color = Color(0xFF070A10).copy(alpha = if (front) 0.82f else 0.45f),
        style = Stroke(width = width * 1.42f),
    )

    // Pass 2: 3D Brushed Titanium Cylinder Body
    if (abs(farX - nearX) + abs(farY - nearY) > 2f) {
        drawPath(
            path = scratchPath,
            brush = Brush.linearGradient(
                colors = listOf(
                    lerpColor(far, near, lowCloseness)
                        .copy(alpha = baseAlpha + alphaRange * lowCloseness),
                    lerpColor(far, near, highCloseness)
                        .copy(alpha = baseAlpha + alphaRange * highCloseness),
                ),
                start = Offset(farX, farY),
                end = Offset(nearX, nearY),
            ),
            style = Stroke(width = width),
        )
    } else {
        drawPath(
            path = scratchPath,
            color = lerpColor(far, near, average).copy(alpha = baseAlpha + alphaRange * average),
            style = Stroke(width = width),
        )
    }

    // Pass 3: Specular Highlight Crest along the top of the 3D cylinder
    drawPath(
        path = scratchPath,
        color = Color.White.copy(alpha = if (front) (0.45f + 0.45f * average) else (0.20f + 0.20f * average)),
        style = Stroke(width = max(0.9f, width * 0.26f)),
    )
}

private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val fraction = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * fraction,
        green = a.green + (b.green - a.green) * fraction,
        blue = a.blue + (b.blue - a.blue) * fraction,
        alpha = a.alpha + (b.alpha - a.alpha) * fraction,
    )
}
