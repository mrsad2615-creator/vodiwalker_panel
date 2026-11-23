package dev.hgallery.app.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.hgallery.app.R
import dev.hgallery.app.ui.motion.HgMotion
import dev.hgallery.app.ui.motion.rememberReduceMotion
import dev.hgallery.app.ui.theme.hgColors
import dev.hgallery.app.ui.theme.hgShapes
import dev.hgallery.app.ui.theme.hgSpacing
import kotlinx.coroutines.delay

/**
 * The permission screen.
 *
 * ## Why this exists
 *
 * The previous build requested media access in `onCreate`, before any UI was composed.
 * On a first install the user saw the system dialog appear over a blank window with no
 * explanation of what it was for, and if they denied it the app opened to an empty grid
 * with no way to recover other than reinstalling.
 *
 * This screen explains the request, states plainly that nothing leaves the device, and
 * offers a route back if access was denied with "don't ask again" — which the platform
 * otherwise makes unrecoverable from inside the app.
 */
@Composable
fun PermissionScreen(
    permanentlyDenied: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = hgColors
    val spacing = hgSpacing
    val reduceMotion = rememberReduceMotion()

    // The mark arrives with a small scale, then the copy follows. Staggering the two
    // stops the screen from appearing as one flat block.
    var markVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(MARK_DELAY_MS)
        markVisible = true
    }
    val markScale by animateFloatAsState(
        targetValue = if (markVisible) 1f else MARK_START_SCALE,
        animationSpec = HgMotion.defaultSpatial(reduceMotion),
        label = "markScale",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = spacing.gutter, vertical = spacing.sectionGap),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AnimatedVisibility(
            visible = markVisible,
            enter = fadeIn(HgMotion.defaultEffects(reduceMotion)) +
                scaleIn(HgMotion.defaultSpatial(reduceMotion), initialScale = MARK_START_SCALE),
            exit = fadeOut() + scaleOut(),
        ) {
            Box(
                modifier = Modifier
                    .size(MARK_SIZE)
                    .clip(RoundedCornerShape(hgShapes.card))
                    .background(colors.surfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_splash_logo),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    // The vector carries its own colours, so it needs no tint; the card
                    // behind it is only there to give the mark a frame.
                    modifier = Modifier.size(MARK_SIZE),
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        Text(
            text = stringResource(R.string.permission_title),
            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = stringResource(
                if (permanentlyDenied) {
                    R.string.permission_denied_body
                } else {
                    R.string.permission_body
                },
            ),
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        // The privacy claim sits below the explanation, not inside it: it answers the
        // question the explanation raises, and it must be readable on its own line.
        Text(
            text = stringResource(R.string.permission_offline_note),
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            color = colors.textTertiary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = if (permanentlyDenied) onOpenSettings else onRequest,
            shape = RoundedCornerShape(hgShapes.button),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accent,
                contentColor = colors.onAccent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(spacing.minTouch),
        ) {
            Text(
                text = stringResource(
                    if (permanentlyDenied) R.string.permission_open_settings else R.string.permission_allow,
                ),
                style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** The mark is 88 dp: large enough to be the subject of the screen, small enough for one hand. */
private val MARK_SIZE = 88.dp

/** Where the mark starts before it settles, so the scale has somewhere to come from. */
private const val MARK_START_SCALE = 0.82f

/** One beat before the mark appears, so the screen is not a flash of everything at once. */
private const val MARK_DELAY_MS = 80L
