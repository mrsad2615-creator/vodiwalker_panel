package dev.hgallery.app.ui.brand

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.hgallery.app.ui.components.HgTestTags
import dev.hgallery.app.ui.motion.rememberReduceMotion
import dev.hgallery.app.ui.theme.hgColors

/**
 * HGallery Ignition: the launch sequence, drawn over a screen that is already there.
 *
 * ## The rule this obeys
 *
 * The splash never delays the start. It is not a screen that stands in front of the
 * application while the application gets ready; the application is drawn first and
 * this is a cover that lifts off it. That is why it lives inside the content and not
 * in `installSplashScreen`: the system splash can only be extended by holding the
 * window, which is exactly the delay the directive forbids.
 *
 * The consequence is honest and visible: if the library has already loaded, the
 * cover lifts off a finished grid. If it has not, it lifts off a skeleton. In neither
 * case does it add time to anything.
 *
 * ## The three phases
 *
 * 1. **Ignition**, 0 to 220 ms. The housing draws itself from nothing as a short arc
 *    that extends in both directions. The mark is not faded in; it is drawn, which
 *    reads as a shutter opening rather than an image appearing.
 * 2. **Aperture**, 220 to 620 ms. The six blades close from their outer position to
 *    their rest position, and the pupil fills. The blades move, the housing does not:
 *    a camera settles its iris, it does not move its body.
 * 3. **Lift**, 620 to 900 ms. The whole cover fades and rises a few dp while the
 *    scale grows by 4 per cent. It leaves the screen rather than vanishing, so the
 *    eye follows it off the content underneath.
 *
 * Total 900 ms, which is the directive's figure. With reduced motion every phase is
 * an instant state change and the cover is gone in one frame.
 */
/**
 * The intent extra that slows the sequence for a photograph.
 *
 * It lives beside the composable that reads it so the name cannot drift from the
 * harness that sets it, and it is a float so the factor is visible in the command.
 */
const val HG_IGNITION_SCALE_EXTRA = "hgallery.capture.ignitionScale"

private object Ignition {
    const val IGNITE_MS = 220
    const val APERTURE_MS = 400
    const val LIFT_MS = 280
    const val TOTAL_MS = IGNITE_MS + APERTURE_MS + LIFT_MS
}

/**
 * Draws the Ignition sequence and calls [onFinished] when it has lifted.
 *
 * [onFinished] fires once. If the caller is recomposed during the sequence the
 * animation continues from where it was, because the animatables are remembered
 * against the composition and not restarted.
 */
@Composable
fun HgIgnition(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Multiply every phase by this factor. 1 is the product; 4 is what the screenshot
     * harness asks for so a camera slower than the animation can still photograph it.
     *
     * It exists because `screencap` on the CI emulator takes about a second to start,
     * which is longer than the whole sequence. Two runs of retries caught the finished
     * grid both times, because the cover had lifted before the camera opened.
     *
     * Slowing the sequence for a photograph is only honest if the photograph says so.
     * The evidence file for a slowed run is named for the factor and the step logs it,
     * so nobody reads a 3.6-second launch into a build that takes 0.9.
     */
    captureTimeScale: Float = 1f,
) {
    val palette = hgColors
    val reduceMotion = rememberReduceMotion()

    // One value per phase, not one per shape. The design rule is "one animated value
    // per screen": the six blades and the pupil all read from `aperture`, so a slow
    // phone runs one animation, not seven.
    val ignite = remember { Animatable(0f) }
    val aperture = remember { Animatable(0f) }
    val lift = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        if (reduceMotion) {
            // The state change still happens; only the interpolation goes.
            onFinished()
            return@LaunchedEffect
        }
        val scale = captureTimeScale.coerceIn(1f, 10f)
        ignite.animateTo(1f, tween((Ignition.IGNITE_MS * scale).toInt(), easing = FastOutSlowInEasing))
        aperture.animateTo(1f, tween((Ignition.APERTURE_MS * scale).toInt(), easing = FastOutSlowInEasing))
        lift.animateTo(1f, tween((Ignition.LIFT_MS * scale).toInt(), easing = FastOutSlowInEasing))
        onFinished()
    }

    // The cover is dismissed by the caller when `lift` reaches 1, so it is drawn with
    // alpha 1 until then. A phase two value of 1 is not "done"; the caller removes it.
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .testTag(HgTestTags.IGNITION),
        contentAlignment = Alignment.Center,
    ) {
        // Phase 3 moves the mark, it does not only fade it. The whole cover rises a
        // few dp and grows by 4 per cent while it fades, so the eye follows it off the
        // content underneath. A pure fade reads as "the app appeared"; a rise reads as
        // "the cover left".
        ApertureMark(
            ignite = ignite.value,
            aperture = aperture.value,
            // The mark is 52 of the 108 units the drawing is composed in, so it fills
            // 48 per cent of whatever box it is given. The box is 220 dp to produce a
            // mark of about 106 dp: the emulator still at 132 dp drew a mark of 63 dp,
            // which reads as a small logo on a large empty screen rather than as a
            // shutter opening. The number below is the box, not the mark.
            modifier = Modifier
                .size(220.dp)
                .graphicsLayer {
                    val eased = lift.value
                    translationY = -16.dp.toPx() * eased
                    val grow = 1f + 0.04f * eased
                    scaleX = grow
                    scaleY = grow
                    alpha = 1f - eased
                },
            housing = palette.accent,
            pupil = palette.accent,
        )
    }
}

/**
 * The aperture mark, as geometry.
 *
 * The launcher icon and `ic_splash_logo.xml` carry the same drawing. It is repeated
 * here because a vector drawable cannot animate its parts: the icon is one path, and
 * this needs the housing and the blades to move on different clocks. The coordinates
 * below are the same numbers as the drawable, scaled from a 108-unit box.
 */
@Composable
private fun ApertureMark(
    ignite: Float,
    aperture: Float,
    modifier: Modifier = Modifier,
    housing: Color,
    pupil: Color,
) {
    Canvas(modifier) {
        val unit = size.minDimension / 108f
        fun p(x: Float, y: Float) = Offset(x * unit, y * unit)

        val centre = p(54f, 54f)
        val housingRadius = 26f * unit
        val pupilRadius = 6.2f * unit

        // Phase 1. The housing is two 164 degree arcs, drawn in opposite directions
        // from their own start angles, so it appears to open. `ignite` drives the arc
        // sweep; at 0 nothing is drawn, at 1 both arcs are complete.
        val sweep = 164f * ignite
        if (sweep > 0.5f) {
            val stroke = 4f * unit
            drawArc(
                color = housing,
                startAngle = -96f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(centre.x - housingRadius, centre.y - housingRadius),
                size = Size(housingRadius * 2, housingRadius * 2),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = housing,
                startAngle = 84f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(centre.x - housingRadius, centre.y - housingRadius),
                size = Size(housingRadius * 2, housingRadius * 2),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }

        // Phase 2. The blades travel from 1.45 times their rest length to rest, so they
        // close inward. Each blade is a chord that ends on the housing, and the length
        // is interpolated, not the angle: the blades rotate as they close.
        val bladeScale = 1f + (1f - aperture.coerceIn(0f, 1f)) * 0.45f
        val bladeStroke = 3.4f * unit
        val blades = listOf(
            // (start, end) in the 108-unit box, rest position
            p(57.2f, 48.5f) to p(66f, 33.2f),
            p(60.4f, 54f) to p(78f, 54f),
            p(57.2f, 59.5f) to p(66f, 74.8f),
            p(50.8f, 59.5f) to p(42f, 74.8f),
            p(47.6f, 54f) to p(30f, 54f),
            p(50.8f, 48.5f) to p(42f, 33.2f),
        )
        // The blades fade in with the housing rather than being present from the first
        // frame. The emulator capture of phase 1 showed why: six lines at full opacity
        // with no arcs behind them read as scattered spokes, not as an iris. Arriving
        // after the housing means the first thing the eye sees is the opening, and the
        // blades are the second beat of the same movement.
        val bladeAlpha = ignite.coerceIn(0f, 1f) * (0.55f + 0.45f * aperture.coerceIn(0f, 1f))
        blades.forEach { (start, end) ->
            // Grow away from the pupil along the blade's own direction.
            val far = start + (end - start) * bladeScale
            drawLine(
                color = housing.copy(alpha = bladeAlpha),
                start = start,
                end = far,
                strokeWidth = bladeStroke,
                cap = StrokeCap.Round,
            )
        }

        // The pupil is the only solid shape. It fills as the blades arrive, so the last
        // thing the sequence does is give the mark its centre.
        if (aperture > 0f) {
            val r = pupilRadius * aperture.coerceIn(0f, 1f)
            val hex = listOf(
                Offset(centre.x, centre.y - r),
                Offset(centre.x + r * 0.866f, centre.y - r * 0.5f),
                Offset(centre.x + r * 0.866f, centre.y + r * 0.5f),
                Offset(centre.x, centre.y + r),
                Offset(centre.x - r * 0.866f, centre.y + r * 0.5f),
                Offset(centre.x - r * 0.866f, centre.y - r * 0.5f),
            )
            val path = Path().apply {
                moveTo(hex[0].x, hex[0].y)
                hex.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            drawPath(path, color = pupil)
        }
    }
}

/** Convenience for the caller: the Ignition lasts this long when motion is enabled. */
val ignitionDurationMs: Int = Ignition.TOTAL_MS
