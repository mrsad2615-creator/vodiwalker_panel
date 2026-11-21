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
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/**
 * The MRSAD Core: the home dashboard's centrepiece.
 *
 * It is a real three-dimensional object, rendered by hand: a faceted
 * icosahedral shell around a lit nucleus, wrapped in two orbital rings.
 * There is no bitmap, no 3D library and no fake data anywhere in the
 * pipeline — the geometry, the perspective projection and the lighting all
 * live in [Core3D] and [CoreGeometry], and the object reacts to the user's
 * own finger.
 *
 * The values it visualises are real ones the caller measured: [energy] is
 * the actual progress towards today's goal, [streakDays] is the real
 * streak, and [bestWpm] is the real personal record. The object's
 * brightness, the density of its graduations and the number of sparks in
 * its orbit are all functions of those numbers, and nothing else.
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

    // The number of segments in an orbital ring follows the graphics tier:
    // a smooth ring on a strong device, a coarse one on a weak device.
    val ringSegments = when (quality) {
        GraphicsQuality.HIGH -> 56
        GraphicsQuality.BALANCED -> 40
        GraphicsQuality.PERFORMANCE -> 24
    }
    val engine = remember(ringSegments) { Core3D(ringSegments) }
    // The orbiting nodes follow the same tier: a full orrery on a strong
    // device, none at all on a weak one.
    engine.nodeCount = when (quality) {
        GraphicsQuality.HIGH -> 6
        GraphicsQuality.BALANCED -> 4
        GraphicsQuality.PERFORMANCE -> 0
    }
    val scene = remember { CoreScene() }
    val scratchPath = remember { Path() }
    val clampedEnergy = energy.coerceIn(0f, 1f)

    // The clock of the object. It is a real frame clock rather than an
    // infinite transition, because the object has to integrate momentum
    // from the user's finger: a fling has to keep travelling after the
    // finger has left the glass.
    //
    // It also stops completely when the motion budget says so, which is
    // what keeps an instrumented test from waiting for a frame that will
    // never come.
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
                // The drag detector comes first: it is the one that consumes
                // a moving pointer, and the tap detector has to see that
                // consumption in order to stay silent after a drag.
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

/**
 * The mutable state of the core.
 *
 * Every field is a Compose state, but they are only ever *read* inside the
 * draw phase, so the object animates without a single recomposition: the
 * frame loop writes a float and the canvas redraws.
 */
private class CoreScene {

    /** The clock of the object, in seconds. */
    var time by mutableFloatStateOf(0f)

    /** The accumulated rotation around the vertical axis. */
    var yaw by mutableFloatStateOf(0.62f)

    /** The tilt the user has dragged the object into. */
    var userPitch by mutableFloatStateOf(-0.12f)

    /** The angular velocity of a fling, in radians per second. */
    var yawVelocity by mutableFloatStateOf(0f)

    /** The progress of the shockwave, or -1 when there is none. */
    var pulseProgress by mutableFloatStateOf(-1f)

    /** How long the turntable has been held back, in seconds. */
    private var idleDelay = 0f

    /** The pitch the object is drawn at. */
    val pitch: Float get() = (userPitch + 0.10f * sin(time * 0.45f)).coerceIn(-1.1f, 1.1f)

    /** The roll the object is drawn at. */
    val roll: Float get() = 0.05f * sin(time * 0.33f)

    /**
     * Advances the object by one frame.
     *
     * @param delta the length of the frame, in seconds
     * @param turntable whether the slow idle rotation is allowed
     */
    fun advance(delta: Float, turntable: Boolean) {
        time += delta

        // The momentum of a fling.
        yaw += yawVelocity * delta
        yawVelocity *= exp(-INERTIA_FRICTION * delta)
        if (yawVelocity < -0.001f || yawVelocity > 0.001f) {
            idleDelay = IDLE_RAMP
        }

        // The turntable: a very slow rotation that waits for the user to
        // stop touching the object before it takes over again.
        idleDelay = max(0f, idleDelay - delta)
        if (turntable) {
            val ramp = 1f - (idleDelay / IDLE_RAMP)
            yaw += IDLE_SPEED * ramp * delta
        }

        // The shockwave.
        if (pulseProgress >= 0f) {
            pulseProgress += delta / PULSE_DURATION
            if (pulseProgress >= 1f) pulseProgress = -1f
        }
    }

    /** The user is dragging the object. */
    fun onDrag(amount: Offset) {
        yaw += amount.x * DRAG_SENSITIVITY
        userPitch = (userPitch + amount.y * DRAG_SENSITIVITY).coerceIn(-0.9f, 0.9f)
        idleDelay = IDLE_RAMP
    }

    /** The user let go of the object, possibly with a velocity. */
    fun onRelease(velocityX: Float) {
        yawVelocity = (velocityX * DRAG_SENSITIVITY).coerceIn(-MAX_SPIN, MAX_SPIN)
        idleDelay = IDLE_RAMP
    }

    /** The user tapped the object. */
    fun onTap() {
        pulseProgress = 0f
        idleDelay = IDLE_RAMP
    }

    private companion object {
        /** Radians per pixel of drag. */
        const val DRAG_SENSITIVITY = 0.006f

        /** The fastest the object may spin after a fling. */
        const val MAX_SPIN = 7f

        /** How quickly a fling loses its speed. */
        const val INERTIA_FRICTION = 2.4f

        /** The speed of the idle turntable, in radians per second. */
        const val IDLE_SPEED = 0.16f

        /** How long the turntable stays out of the way after a touch. */
        const val IDLE_RAMP = 1.6f

        /** How long the shockwave takes to travel. */
        const val PULSE_DURATION = 0.85f
    }
}

/**
 * Draws the core.
 *
 * The order of the passes is the order of the light: the bloom first, then
 * the part of the object that is behind the body, then the body, then the
 * part that is in front, and the highlights last.
 */
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
    val centerX = size.width / 2f
    // The object hovers: a very small, slow vertical drift.
    val centerY = size.height / 2f + sin(scene.time * 0.62f) * size.minDimension * 0.006f
    val radiusPx = size.minDimension * 0.215f

    engine.update(
        yaw = scene.yaw,
        pitch = scene.pitch,
        roll = scene.roll,
        centerX = centerX,
        centerY = centerY,
        radiusPx = radiusPx,
        time = scene.time,
    )

    val shadow = lerpColor(colors.metalDark, colors.accent, 0.35f)
    val body = colors.metalMid
    val highlight = colors.metalLight
    val pulse = if (scene.pulseProgress in 0f..1f) scene.pulseProgress else -1f
    val pulseGlow = if (pulse >= 0f) (1f - pulse) else 0f

    // ---- 1. The light that surrounds the object ------------------------------
    val bloomRadius = radiusPx * 2.15f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                colors.accent.copy(alpha = 0.08f + 0.18f * energy + 0.10f * pulseGlow),
                Color.Transparent,
            ),
            center = Offset(centerX, centerY),
            radius = bloomRadius,
        ),
        radius = bloomRadius,
        center = Offset(centerX, centerY),
    )

    // ---- 2. The pool of light the object stands in --------------------------
    val poolWidth = radiusPx * 2.6f
    val poolHeight = radiusPx * 0.66f
    val poolCenter = Offset(centerX, centerY + radiusPx * 1.12f)
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(
                colors.accent.copy(alpha = 0.10f + 0.08f * energy),
                Color.Transparent,
            ),
            center = poolCenter,
            radius = poolWidth / 2f,
        ),
        topLeft = Offset(poolCenter.x - poolWidth / 2f, poolCenter.y - poolHeight / 2f),
        size = Size(poolWidth, poolHeight),
    )

    // ---- 3. The half of each ring that is behind the body -------------------
    drawRingHalf(
        engine = engine,
        scratchPath = scratchPath,
        offset = 0,
        centerX = centerX,
        centerY = centerY,
        radiusPx = radiusPx,
        bandWidth = radiusPx * 0.075f,
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
        bandWidth = radiusPx * 0.018f,
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
        bandWidth = radiusPx * 0.026f,
        near = colors.metalLight,
        far = colors.metalDark,
        front = false,
    )

    // ---- 4. The body --------------------------------------------------------
    // The facets are translucent, so the light of the nucleus shines
    // through the shell instead of being hidden behind it.
    for (index in 0 until engine.faceCount) {
        val face = CoreGeometry.faces[engine.faceOrder[index]]
        scratchPath.reset()
        scratchPath.moveTo(engine.screenX[face[0]], engine.screenY[face[0]])
        scratchPath.lineTo(engine.screenX[face[1]], engine.screenY[face[1]])
        scratchPath.lineTo(engine.screenX[face[2]], engine.screenY[face[2]])
        scratchPath.close()
        val shade = engine.faceShade[engine.faceOrder[index]]
        val fill = lerpColor(shadow, highlight, shade).copy(alpha = 0.13f + 0.13f * shade)
        drawPath(scratchPath, fill)
    }

    // ---- 5. The nucleus -----------------------------------------------------
    val nucleusRadius = radiusPx * (0.30f + 0.12f * energy + 0.14f * pulseGlow)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                highlight.copy(alpha = 0.60f + 0.30f * energy + 0.30f * pulseGlow),
                colors.accent.copy(alpha = 0.34f + 0.28f * energy),
                Color.Transparent,
            ),
            center = Offset(centerX, centerY),
            radius = nucleusRadius,
        ),
        radius = nucleusRadius,
        center = Offset(centerX, centerY),
    )
    drawCircle(
        color = highlight.copy(alpha = 0.75f + 0.25f * energy),
        radius = radiusPx * (0.055f + 0.035f * energy),
        center = Offset(centerX, centerY),
    )
    // The energy halo: a slow, breathing ring of light around the nucleus
    // whose radius and brightness follow the real goal progress. It is the
    // orrery's pulse — a controlled luminous edge, not a glow.
    if (quality != GraphicsQuality.PERFORMANCE) {
        val breath = 0.5f + 0.5f * sin(scene.time * 1.4f)
        val haloRadius = radiusPx * (0.42f + 0.20f * energy + 0.05f * breath)
        drawCircle(
            color = colors.accent.copy(alpha = 0.10f + 0.22f * energy + 0.06f * breath),
            radius = haloRadius,
            center = Offset(centerX, centerY),
            style = Stroke(width = (1.1f + 0.7f * breath).dp.toPx()),
        )
    }

    // ---- 6. The edges of the shell ------------------------------------------
    if (quality != GraphicsQuality.PERFORMANCE) {
        val edgeWidth = if (quality == GraphicsQuality.HIGH) 1.25f else 1f
        for (e in CoreGeometry.edges.indices) {
            val edge = CoreGeometry.edges[e]
            val owners = CoreGeometry.edgeFaces[e]
            val shade = if (owners.size == 2) {
                0.5f * (engine.faceShade[owners[0]] + engine.faceShade[owners[1]])
            } else {
                0.5f
            }
            val rim = if (owners.size == 2) {
                0.5f * (engine.faceRim[owners[0]] + engine.faceRim[owners[1]])
            } else {
                0f
            }
            val color = lerpColor(body, highlight, shade)
                .copy(alpha = (0.30f + 0.55f * shade + 0.22f * rim).coerceIn(0f, 1f))
            drawLine(
                color = color,
                start = Offset(engine.screenX[edge[0]], engine.screenY[edge[0]]),
                end = Offset(engine.screenX[edge[1]], engine.screenY[edge[1]]),
                strokeWidth = edgeWidth.dp.toPx(),
            )
        }
    }

    // ---- 7. The half of each ring that is in front of the body --------------
    drawRingHalf(
        engine = engine,
        scratchPath = scratchPath,
        offset = 0,
        centerX = centerX,
        centerY = centerY,
        radiusPx = radiusPx,
        bandWidth = radiusPx * 0.075f,
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
        bandWidth = radiusPx * 0.018f,
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
        bandWidth = radiusPx * 0.026f,
        near = colors.metalLight,
        far = colors.metalDark,
        front = true,
    )

    // ---- 8. The orbiting nodes ----------------------------------------------
    // Small metallic bodies riding the rings. Each is a lit sphere: a dark
    // base, a bright cap towards the key light, and a point of light on
    // the ones that are near.
    for (n in 0 until engine.nodeCount) {
        val depth = ((engine.nodeZ[n] + 1.8f) / 3.6f).coerceIn(0f, 1f)
        val x = centerX + engine.nodeX[n] * radiusPx
        val y = centerY + engine.nodeY[n] * radiusPx
        val radius = engine.nodeSize[n] * radiusPx
        drawCircle(
            color = colors.metalDark.copy(alpha = 0.55f + 0.35f * depth),
            radius = radius,
            center = Offset(x, y),
        )
        drawCircle(
            color = colors.metalLight.copy(alpha = 0.35f + 0.55f * depth),
            radius = radius * 0.55f,
            center = Offset(x - radius * 0.22f, y - radius * 0.22f),
        )
    }

    // ---- 9. The sparks of the streak ----------------------------------------
    val sparks = streakDays.coerceIn(0, 24)
    if (sparks > 0) {
        for (s in 0 until sparks) {
            val index = (s * engine.ringSegments / sparks) % engine.ringSegments
            val depth = (engine.ringZ[index] + 1.8f) / 3.6f
            val x = engine.ringScreenX(0, index, centerX, radiusPx)
            val y = engine.ringScreenY(0, index, centerY, radiusPx)
            val radius = radiusPx * (0.018f + 0.016f * depth.coerceIn(0f, 1f))
            drawCircle(
                color = colors.accent.copy(alpha = (0.35f + 0.55f * depth).coerceIn(0f, 0.9f)),
                radius = radius,
                center = Offset(x, y),
            )
        }
    }

    // ---- 10. The graduations of the record -----------------------------------
    // The outer ring is graduated like a dial, and the number of
    // graduations follows the personal record: a faster typist gets a
    // denser dial.
    if (quality != GraphicsQuality.PERFORMANCE && bestWpm > 0) {
        val ticks = 12 + (bestWpm / 8).coerceIn(0, 24)
        val step = engine.ringSegments / ticks
        if (step > 0) {
            for (t in 0 until ticks) {
                val index = (t * step) % engine.ringSegments
                val x = engine.ringScreenX(engine.secondRingOffset, index, centerX, radiusPx)
                val y = engine.ringScreenY(engine.secondRingOffset, index, centerY, radiusPx)
                val innerX = centerX + (x - centerX) * 0.90f
                val innerY = centerY + (y - centerY) * 0.90f
                drawLine(
                    color = colors.metalLight.copy(alpha = 0.32f),
                    start = Offset(innerX, innerY),
                    end = Offset(x, y),
                    strokeWidth = 0.9f.dp.toPx(),
                )
            }
        }
    }

    // ---- 11. The broad highlight --------------------------------------------
    val sheenColor = if (colors.isLight) colors.metalLight else Color.White
    val sheenCenter = Offset(centerX - radiusPx * 0.34f, centerY - radiusPx * 0.40f)
    val sheenRadius = radiusPx * 0.92f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(sheenColor.copy(alpha = 0.09f), Color.Transparent),
            center = sheenCenter,
            radius = sheenRadius,
        ),
        radius = sheenRadius,
        center = sheenCenter,
    )

    // ---- 12. The shockwave ---------------------------------------------------
    if (pulse >= 0f) {
        val waveRadius = radiusPx * (0.5f + 1.6f * pulse)
        drawCircle(
            color = colors.accent.copy(alpha = (1f - pulse) * 0.5f),
            radius = waveRadius,
            center = Offset(centerX, centerY),
            style = Stroke(width = (0.6f + (1f - pulse) * 2.4f).dp.toPx()),
        )
    }
}

/**
 * Draws one half of an orbital ring.
 *
 * The ring is split at the depth of the body: the half that is behind it
 * is drawn before the body and the half that is in front of it after, so
 * the ring passes *through* the object instead of floating over it.
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

    // The band is drawn as one reused path, not as one line per segment
    // and without allocating a fresh Path per call. Whenever segments are
    // skipped across the depth split, `connected` resets so the next arc
    // starts with `moveTo` instead of drawing a chord across the gap.
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

        // The deepest and the shallowest point of this half: they are the
        // two ends of the depth ramp of the band.
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

    // How near the two ends of the half are, in 0..1. The near end is
    // the brighter and the wider one.
    val lowCloseness = ((lowest + 1.8f) / 3.6f).coerceIn(0f, 1f)
    val highCloseness = ((highest + 1.8f) / 3.6f).coerceIn(0f, 1f)
    val average = (lowCloseness + highCloseness) / 2f
    val baseAlpha = if (front) 0.30f else 0.14f
    val alphaRange = if (front) 0.45f else 0.22f
    val width = bandWidth * (0.55f + 0.55f * average)

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
    // The lit edge along the top of the band.
    if (front) {
        drawPath(
            path = scratchPath,
            color = near.copy(alpha = 0.30f + 0.45f * average),
            style = Stroke(width = max(0.7f, width * 0.22f)),
        )
    }
}

/** Blends two colours. */
private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val fraction = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * fraction,
        green = a.green + (b.green - a.green) * fraction,
        blue = a.blue + (b.blue - a.blue) * fraction,
        alpha = a.alpha + (b.alpha - a.alpha) * fraction,
    )
}
