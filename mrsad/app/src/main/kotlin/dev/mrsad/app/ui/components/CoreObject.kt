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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.mrsad.app.data.settings.GraphicsQuality
import dev.mrsad.app.ui.theme.MrsadColors
import dev.mrsad.app.ui.theme.MrsadTheme
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/**
 * The MRSAD Core: True 3D Volumetric PBR-Shaded & Machined Titanium Reactor.
 *
 * Combines:
 * 1. Multi-layer 3D Phong/PBR liquid-titanium & plasma sphere nucleus with
 *    rotating 3D meridian energy veins, Fresnel rim lighting, and specular hotspots.
 * 2. Solid, back-face culled & front-face bevelled 3D brushed-titanium geodesic
 *    exoskeleton armor plates with recessed reactor aperture windows.
 * 3. Thick 3D cylindrical torus gyroscope rings (shadow base + brushed titanium
 *    cylinder + razor specular crest) and 3D spherical chrome satellites.
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
    val innerPath = remember { Path() }
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
                innerPath = innerPath,
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
    var userPitch by mutableFloatStateOf(-0.18f)
    var yawVelocity by mutableFloatStateOf(0f)
    var pulseProgress by mutableFloatStateOf(-1f)
    private var idleDelay = 0f

    val pitch: Float get() = (userPitch + 0.12f * sin(time * 0.55f)).coerceIn(-1.1f, 1.1f)
    val roll: Float get() = 0.07f * sin(time * 0.38f)

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
        userPitch = (userPitch + amount.y * DRAG_SENSITIVITY).coerceIn(-0.95f, 0.95f)
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
        const val DRAG_SENSITIVITY = 0.0065f
        const val MAX_SPIN = 7.5f
        const val INERTIA_FRICTION = 2.3f
        const val IDLE_SPEED = 0.24f
        const val IDLE_RAMP = 1.5f
        const val PULSE_DURATION = 0.85f
    }
}

private fun DrawScope.drawCore(
    engine: Core3D,
    scene: CoreScene,
    scratchPath: Path,
    innerPath: Path,
    colors: MrsadColors,
    energy: Float,
    streakDays: Int,
    bestWpm: Int,
    quality: GraphicsQuality,
) {
    if (size.minDimension <= 2f) return
    val centerX = size.width / 2f
    val centerY = size.height / 2f + sin(scene.time * 0.75f) * size.minDimension * 0.008f
    val radiusPx = max(8f, size.minDimension * 0.235f)

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

    // ---- 1. Deep Volumetric Ambient Aura & Pedestal Floor Shadow ------------
    val bloomRadius = max(1f, radiusPx * 2.25f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                colors.accent.copy(alpha = 0.20f + 0.18f * energy + 0.14f * pulseGlow),
                colors.navy.copy(alpha = 0.14f),
                Color.Transparent,
            ),
            center = Offset(centerX, centerY),
            radius = bloomRadius,
        ),
        radius = bloomRadius,
        center = Offset(centerX, centerY),
    )

    val poolWidth = max(2f, radiusPx * 2.75f)
    val poolHeight = max(2f, radiusPx * 0.60f)
    val poolCenter = Offset(centerX, centerY + radiusPx * 1.24f)
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.68f),
                colors.accent.copy(alpha = 0.16f + 0.10f * energy),
                Color.Transparent,
            ),
            center = poolCenter,
            radius = max(1f, poolWidth / 2f),
        ),
        topLeft = Offset(poolCenter.x - poolWidth / 2f, poolCenter.y - poolHeight / 2f),
        size = Size(poolWidth, poolHeight),
    )

    // ---- 2. Back halves of the 3D Machined Torus Gyroscope Rings ------------
    drawVolumetricRingHalf(
        engine = engine,
        scratchPath = scratchPath,
        offset = engine.secondRingOffset,
        centerX = centerX,
        centerY = centerY,
        radiusPx = radiusPx,
        tubeRadius = radiusPx * 0.044f,
        metalLight = colors.metalLight,
        metalMid = colors.metalMid,
        metalDark = colors.metalDark,
        accent = colors.accent,
        front = false,
    )
    drawVolumetricRingHalf(
        engine = engine,
        scratchPath = scratchPath,
        offset = engine.thirdRingOffset,
        centerX = centerX,
        centerY = centerY,
        radiusPx = radiusPx,
        tubeRadius = radiusPx * 0.054f,
        metalLight = colors.metalLight,
        metalMid = colors.metalMid,
        metalDark = colors.metalDark,
        accent = colors.accent,
        front = false,
    )
    drawVolumetricRingHalf(
        engine = engine,
        scratchPath = scratchPath,
        offset = 0,
        centerX = centerX,
        centerY = centerY,
        radiusPx = radiusPx,
        tubeRadius = radiusPx * 0.084f,
        metalLight = colors.metalLight,
        metalMid = colors.metalMid,
        metalDark = colors.metalDark,
        accent = colors.accent,
        front = false,
    )

    // ---- 3. Back-facing Orbiting Satellites (behind the core) ---------------
    drawOrbitingNodes(
        engine = engine,
        centerX = centerX,
        centerY = centerY,
        radiusPx = radiusPx,
        colors = colors,
        frontPass = false,
    )

    // ---- 4. Solid Back-Facing Geodesic Chamber Plates (`nz <= 0`) -----------
    for (index in 0 until engine.faceCount) {
        val f = engine.faceOrder[index]
        val nz = engine.faceNormalZ[f]
        if (nz > 0.04f) continue
        val face = CoreGeometry.faces[f]
        val x0 = engine.screenX[face[0]]
        val y0 = engine.screenY[face[0]]
        val x1 = engine.screenX[face[1]]
        val y1 = engine.screenY[face[1]]
        val x2 = engine.screenX[face[2]]
        val y2 = engine.screenY[face[2]]

        scratchPath.reset()
        scratchPath.moveTo(x0, y0)
        scratchPath.lineTo(x1, y1)
        scratchPath.lineTo(x2, y2)
        scratchPath.close()

        val shade = engine.faceShade[f]
        val chamberColor = lerpColor(
            Color(0xFF06080E),
            Color(0xFF1D2638),
            shade * 0.65f,
        ).copy(alpha = 0.94f)
        drawPath(scratchPath, chamberColor)
        drawPath(
            scratchPath,
            color = Color(0xFF2B3548).copy(alpha = 0.55f),
            style = Stroke(width = 1.dp.toPx(), join = StrokeJoin.Round),
        )
    }

    // ---- 5. The Volumetric 3D PBR / Phong Liquid-Metal & Plasma Nucleus -----
    val nucleusRadius = max(4f, radiusPx * (0.64f + 0.08f * energy + 0.08f * pulseGlow))
    val hotspot = Offset(centerX - nucleusRadius * 0.34f, centerY - nucleusRadius * 0.36f)

    // 5a. Solid 3D Sphere Base with PBR Radial Phong Shading
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFF8FBFF),
                colors.accent,
                Color(0xFF3E587A),
                Color(0xFF162032),
                Color(0xFF070A12),
            ),
            center = hotspot,
            radius = max(1f, nucleusRadius * 1.32f),
        ),
        radius = nucleusRadius,
        center = Offset(centerX, centerY),
    )

    // 5b. 3D Rotating Plasma Meridians inside the Sphere
    val yawDeg = (scene.yaw * 57.2958f) % 360f
    val pitchAspect = (0.32f + 0.48f * absCos(scene.pitch)).coerceIn(0.25f, 0.85f)
    rotate(degrees = yawDeg, pivot = Offset(centerX, centerY)) {
        val ovalW = nucleusRadius * 1.72f
        val ovalH = max(2f, nucleusRadius * 1.72f * pitchAspect)
        drawOval(
            color = Color.White.copy(alpha = 0.42f + 0.25f * energy),
            topLeft = Offset(centerX - ovalW / 2f, centerY - ovalH / 2f),
            size = Size(ovalW, ovalH),
            style = Stroke(width = 1.8.dp.toPx()),
        )
    }
    rotate(degrees = -yawDeg * 0.7f + 60f, pivot = Offset(centerX, centerY)) {
        val ovalW = max(2f, nucleusRadius * 1.65f * pitchAspect)
        val ovalH = nucleusRadius * 1.65f
        drawOval(
            color = colors.accent.copy(alpha = 0.48f + 0.25f * energy),
            topLeft = Offset(centerX - ovalW / 2f, centerY - ovalH / 2f),
            size = Size(ovalW, ovalH),
            style = Stroke(width = 1.4.dp.toPx()),
        )
    }

    // 5c. 3D Specular Hotspots & Fresnel Rim Ring on the Nucleus Sphere
    drawCircle(
        color = Color.White.copy(alpha = 0.92f),
        radius = nucleusRadius * 0.16f,
        center = Offset(centerX - nucleusRadius * 0.34f, centerY - nucleusRadius * 0.34f),
    )
    drawCircle(
        color = colors.accent.copy(alpha = 0.72f),
        radius = nucleusRadius,
        center = Offset(centerX, centerY),
        style = Stroke(width = 1.6.dp.toPx()),
    )

    // ---- 6. Solid Front-Facing Bevelled 3D Titanium Exoskeleton Plates ------
    for (index in 0 until engine.faceCount) {
        val f = engine.faceOrder[index]
        val nz = engine.faceNormalZ[f]
        if (nz <= 0.04f) continue

        val face = CoreGeometry.faces[f]
        val x0 = engine.screenX[face[0]]
        val y0 = engine.screenY[face[0]]
        val x1 = engine.screenX[face[1]]
        val y1 = engine.screenY[face[1]]
        val x2 = engine.screenX[face[2]]
        val y2 = engine.screenY[face[2]]

        val cx = (x0 + x1 + x2) / 3f
        val cy = (y0 + y1 + y2) / 3f

        // Compute the inner aperture window vertices (44% opening)
        val openRatio = 0.44f
        val ix0 = cx + (x0 - cx) * openRatio
        val iy0 = cy + (y0 - cy) * openRatio
        val ix1 = cx + (x1 - cx) * openRatio
        val iy1 = cy + (y1 - cy) * openRatio
        val ix2 = cx + (x2 - cx) * openRatio
        val iy2 = cy + (y2 - cy) * openRatio

        val shade = engine.faceShade[f]
        val spec = engine.faceSpecular[f]
        val rim = engine.faceRim[f]
        val nx = engine.faceNormalX[f]
        val ny = engine.faceNormalY[f]

        val plateShadow = lerpColor(Color(0xFF141A24), colors.metalDark, shade * 0.7f)
        val plateMid = lerpColor(colors.metalDark, colors.metalMid, shade)
        val plateBright = lerpColor(
            plateMid,
            Color.White,
            (spec * 0.85f + rim * 0.35f + shade * 0.25f).coerceIn(0f, 1f),
        )

        val dx = nx * radiusPx * 0.45f
        val dy = ny * radiusPx * 0.45f
        val gradStart = Offset(cx - dx, cy + dy)
        val gradEnd = Offset(cx + dx + 1f, cy - dy + 1f)
        val plateBrush = Brush.linearGradient(
            colors = listOf(
                plateBright.copy(alpha = 0.96f),
                plateMid.copy(alpha = 0.93f),
                plateShadow.copy(alpha = 0.96f),
            ),
            start = gradStart,
            end = gradEnd,
        )

        // Draw the 3 solid bevelled armor struts of the triangular plate
        drawQuadFacet(scratchPath, x0, y0, x1, y1, ix1, iy1, ix0, iy0, plateBrush)
        drawQuadFacet(scratchPath, x1, y1, x2, y2, ix2, iy2, ix1, iy1, plateBrush)
        drawQuadFacet(scratchPath, x2, y2, x0, y0, ix0, iy0, ix2, iy2, plateBrush)

        // Inner window bevel thickness
        innerPath.reset()
        innerPath.moveTo(ix0, iy0)
        innerPath.lineTo(ix1, iy1)
        innerPath.lineTo(ix2, iy2)
        innerPath.close()
        drawPath(
            path = innerPath,
            color = colors.accent.copy(alpha = (0.32f + 0.45f * shade + 0.25f * spec).coerceIn(0f, 0.95f)),
            style = Stroke(width = 1.6.dp.toPx(), join = StrokeJoin.Round),
        )

        // Outer machined bevel seam + specular highlight ridge
        scratchPath.reset()
        scratchPath.moveTo(x0, y0)
        scratchPath.lineTo(x1, y1)
        scratchPath.lineTo(x2, y2)
        scratchPath.close()
        drawPath(
            path = scratchPath,
            color = Color(0xFF070A10).copy(alpha = 0.88f),
            style = Stroke(width = 2.2.dp.toPx(), join = StrokeJoin.Round),
        )
        drawPath(
            path = scratchPath,
            color = lerpColor(colors.metalMid, Color.White, (shade * 0.6f + spec * 0.8f).coerceIn(0f, 1f))
                .copy(alpha = (0.48f + 0.48f * shade).coerceIn(0f, 1f)),
            style = Stroke(width = 1.0.dp.toPx(), join = StrokeJoin.Round),
        )
    }

    // ---- 7. Front halves of the 3D Machined Torus Gyroscope Rings -----------
    drawVolumetricRingHalf(
        engine = engine,
        scratchPath = scratchPath,
        offset = 0,
        centerX = centerX,
        centerY = centerY,
        radiusPx = radiusPx,
        tubeRadius = radiusPx * 0.084f,
        metalLight = colors.metalLight,
        metalMid = colors.metalMid,
        metalDark = colors.metalDark,
        accent = colors.accent,
        front = true,
    )
    drawVolumetricRingHalf(
        engine = engine,
        scratchPath = scratchPath,
        offset = engine.thirdRingOffset,
        centerX = centerX,
        centerY = centerY,
        radiusPx = radiusPx,
        tubeRadius = radiusPx * 0.054f,
        metalLight = colors.metalLight,
        metalMid = colors.metalMid,
        metalDark = colors.metalDark,
        accent = colors.accent,
        front = true,
    )
    drawVolumetricRingHalf(
        engine = engine,
        scratchPath = scratchPath,
        offset = engine.secondRingOffset,
        centerX = centerX,
        centerY = centerY,
        radiusPx = radiusPx,
        tubeRadius = radiusPx * 0.044f,
        metalLight = colors.metalLight,
        metalMid = colors.metalMid,
        metalDark = colors.metalDark,
        accent = colors.accent,
        front = true,
    )

    // ---- 8. Front-facing 3D Spherical Chrome Satellites ---------------------
    drawOrbitingNodes(
        engine = engine,
        centerX = centerX,
        centerY = centerY,
        radiusPx = radiusPx,
        colors = colors,
        frontPass = true,
    )

    // ---- 9. Streak Energy Nodes along the Primary Gyroscope Ring ------------
    val sparks = streakDays.coerceIn(0, 24)
    if (sparks > 0) {
        for (s in 0 until sparks) {
            val index = (s * engine.ringSegments / sparks) % engine.ringSegments
            val depth = (engine.ringZ[index] + 1.8f) / 3.6f
            val x = engine.ringScreenX(0, index, centerX, radiusPx)
            val y = engine.ringScreenY(0, index, centerY, radiusPx)
            val r = max(1.5f, radiusPx * (0.022f + 0.018f * depth.coerceIn(0f, 1f)))
            drawCircle(
                color = colors.accent.copy(alpha = (0.45f + 0.50f * depth).coerceIn(0f, 0.95f)),
                radius = r,
                center = Offset(x, y),
            )
            drawCircle(
                color = Color.White.copy(alpha = (0.55f + 0.40f * depth).coerceIn(0f, 1f)),
                radius = r * 0.45f,
                center = Offset(x - r * 0.2f, y - r * 0.2f),
            )
        }
    }

    // ---- 10. Precision Machined Chronometer Ticks on Outer Ring -------------
    if (quality != GraphicsQuality.PERFORMANCE) {
        val ticks = 16 + (bestWpm / 8).coerceIn(0, 24)
        val step = max(1, engine.ringSegments / ticks)
        for (t in 0 until ticks) {
            val index = (t * step) % engine.ringSegments
            val x = engine.ringScreenX(engine.secondRingOffset, index, centerX, radiusPx)
            val y = engine.ringScreenY(engine.secondRingOffset, index, centerY, radiusPx)
            val innerX = centerX + (x - centerX) * 0.92f
            val innerY = centerY + (y - centerY) * 0.92f
            drawLine(
                color = colors.metalLight.copy(alpha = 0.48f),
                start = Offset(innerX, innerY),
                end = Offset(x, y),
                strokeWidth = 1.2f.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }

    // ---- 11. Interactive Shockwave Ring on Tap ------------------------------
    if (pulse >= 0f) {
        val waveRadius = max(2f, radiusPx * (0.55f + 1.55f * pulse))
        drawCircle(
            color = colors.accent.copy(alpha = (1f - pulse) * 0.65f),
            radius = waveRadius,
            center = Offset(centerX, centerY),
            style = Stroke(width = (1.0f + (1f - pulse) * 3.2f).dp.toPx()),
        )
    }
}

private fun absCos(rad: Float): Float {
    val c = cos(rad)
    return if (c < 0f) -c else c
}

private fun DrawScope.drawQuadFacet(
    path: Path,
    x0: Float,
    y0: Float,
    x1: Float,
    y1: Float,
    x2: Float,
    y2: Float,
    x3: Float,
    y3: Float,
    brush: Brush,
) {
    path.reset()
    path.moveTo(x0, y0)
    path.lineTo(x1, y1)
    path.lineTo(x2, y2)
    path.lineTo(x3, y3)
    path.close()
    drawPath(path = path, brush = brush)
}

/**
 * Draws one depth-sorted half of an orbital ring as a true 3D cylindrical
 * metallic torus tube (outer shadow base, brushed-metal body, and sharp
 * specular highlight ridge).
 */
private fun DrawScope.drawVolumetricRingHalf(
    engine: Core3D,
    scratchPath: Path,
    offset: Int,
    centerX: Float,
    centerY: Float,
    radiusPx: Float,
    tubeRadius: Float,
    metalLight: Color,
    metalMid: Color,
    metalDark: Color,
    accent: Color,
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
    val visibility = if (front) 0.95f else 0.58f
    val thickness = max(1.5f, tubeRadius * (0.72f + 0.52f * average))

    // Pass 1: 3D Tube dark metallic shadow base
    drawPath(
        path = scratchPath,
        color = Color(0xFF080B12).copy(alpha = 0.85f * visibility),
        style = Stroke(width = thickness * 1.45f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )

    // Pass 2: 3D Brushed Titanium Cylinder Body
    drawPath(
        path = scratchPath,
        brush = Brush.linearGradient(
            colors = listOf(
                lerpColor(metalDark, metalMid, lowCloseness).copy(alpha = 0.85f * visibility),
                lerpColor(metalMid, metalLight, highCloseness).copy(alpha = 0.96f * visibility),
                accent.copy(alpha = 0.78f * visibility),
            ),
            start = Offset(farX, farY),
            end = Offset(nearX + 1f, nearY + 1f),
        ),
        style = Stroke(width = thickness, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )

    // Pass 3: Razor-sharp Specular Highlight Crest along the top of the 3D cylinder
    drawPath(
        path = scratchPath,
        color = Color.White.copy(alpha = (0.40f + 0.48f * average) * visibility),
        style = Stroke(width = max(1.0f, thickness * 0.28f), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

/**
 * Draws the orbiting nodes as solid 3D shaded metallic spheres with drop
 * shadows and specular reflections.
 */
private fun DrawScope.drawOrbitingNodes(
    engine: Core3D,
    centerX: Float,
    centerY: Float,
    radiusPx: Float,
    colors: MrsadColors,
    frontPass: Boolean,
) {
    for (n in 0 until engine.nodeCount) {
        val z = engine.nodeZ[n]
        val isFront = z >= 0f
        if (isFront != frontPass) continue

        val depth = ((z + 1.8f) / 3.6f).coerceIn(0f, 1f)
        val x = centerX + engine.nodeX[n] * radiusPx
        val y = centerY + engine.nodeY[n] * radiusPx
        val r = max(2f, engine.nodeSize[n] * radiusPx * 1.35f)

        // Ambient shadow behind sphere
        drawCircle(
            color = Color.Black.copy(alpha = 0.65f),
            radius = r * 1.22f,
            center = Offset(x + r * 0.14f, y + r * 0.18f),
        )
        // 3D Sphere shaded body
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White,
                    colors.metalLight,
                    colors.metalMid,
                    Color(0xFF121722),
                ),
                center = Offset(x - r * 0.32f, y - r * 0.32f),
                radius = max(1f, r * 1.35f),
            ),
            radius = r,
            center = Offset(x, y),
        )
        // Specular pinpoint reflection
        drawCircle(
            color = Color.White.copy(alpha = 0.85f + 0.15f * depth),
            radius = r * 0.24f,
            center = Offset(x - r * 0.34f, y - r * 0.34f),
        )
    }
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
