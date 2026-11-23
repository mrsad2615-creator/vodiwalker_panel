package dev.hgallery.app.ui.shell

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import dev.hgallery.app.R
import dev.hgallery.app.ui.components.HgTestTags
import dev.hgallery.app.ui.icons.HgIcons
import dev.hgallery.app.ui.motion.HgMotion
import dev.hgallery.app.ui.motion.hgScale
import dev.hgallery.app.ui.motion.rememberPressScale
import dev.hgallery.app.ui.motion.rememberReduceMotion
import dev.hgallery.app.ui.theme.hgColors
import dev.hgallery.app.ui.theme.hgShapes
import dev.hgallery.app.ui.theme.hgSpacing

/** The four destinations, in order. */
enum class HgTab(@StringRes val label: Int, val icon: ImageVector) {
    Photos(R.string.tab_photos, HgIcons.Photos),
    Albums(R.string.tab_albums, HgIcons.Albums),
    Highlights(R.string.tab_highlights, HgIcons.Highlights),
    Discover(R.string.tab_discover, HgIcons.Discover),
}

/**
 * Bottom navigation.
 *
 * ## What this replaced
 *
 * A row of four icons that only changed colour. Colour alone is a weak signal: on a
 * bright page the blue is a small change, and nothing told the finger that the tap had
 * landed. The bar now states the selection three ways — a tinted capsule behind the
 * icon, a bolder label, and an icon that scales up slightly — and each item responds to
 * a press before the destination changes.
 *
 * The capsule is the expressive shape: it grows from the icon outward when it appears,
 * driven by the same spatial spring as everything else, and it does not overshoot into
 * its neighbours.
 */
@Composable
fun HgBottomBar(
    selected: HgTab,
    onSelect: (HgTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = hgColors
    val spacing = hgSpacing

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface),
    ) {
        // A hairline, not a shadow: the bar sits on the page and a hard divider reads
        // as two separate screens.
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.7.dp)
                .background(colors.divider),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(spacing.bottomBarHeight)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            HgTab.entries.forEach { tab ->
                TabItem(
                    tab = tab,
                    selected = tab == selected,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TabItem(
    tab: HgTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = hgColors
    val shapes = hgShapes
    val reduceMotion = rememberReduceMotion()
    val interaction = remember { MutableInteractionSource() }
    val pressScale = rememberPressScale(interaction = interaction)

    val tint by animateColorAsState(
        targetValue = if (selected) colors.accent else colors.textSecondary,
        animationSpec = HgMotion.fastEffects(reduceMotion),
        label = "tabTint",
    )

    // The capsule fades rather than scales its own width: animating a width inside a
    // weighted row would re-measure the three other items on every frame.
    val capsuleAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = HgMotion.fastEffects(reduceMotion),
        label = "capsuleAlpha",
    )

    // The icon grows by 2 dp when it is the destination. Small enough to read as a
    // response rather than as a jump.
    val iconSize by animateDpAsState(
        targetValue = if (selected) 26.dp else 23.dp,
        animationSpec = HgMotion.defaultSpatial(reduceMotion),
        label = "iconSize",
    )

    Column(
        modifier = modifier
            .testTag(HgTestTags.tab(tab.name))
            .height(hgSpacing.minTouch)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = interaction,
                indication = null,
            )
            .hgScale(pressScale),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (capsuleAlpha > 0.01f) {
                Box(
                    Modifier
                        .size(width = CAPSULE_WIDTH, height = CAPSULE_HEIGHT)
                        .clip(RoundedCornerShape(shapes.pill))
                        .background(colors.accent.copy(alpha = capsuleAlpha * CAPSULE_MAX_ALPHA)),
                )
            }
            Icon(
                imageVector = tab.icon,
                // The label below already names the destination; a second reading of
                // the same word makes a screen reader say it twice.
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(iconSize),
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = stringResource(tab.label),
            style = if (selected) {
                MaterialTheme.typography.labelMedium
            } else {
                MaterialTheme.typography.labelSmall
            },
            color = tint,
            maxLines = 1,
        )
    }
}

/** Wide enough to read as a capsule around the widest icon, not as a dot. */
private val CAPSULE_WIDTH = 44.dp

private val CAPSULE_HEIGHT = 26.dp

/** The capsule is a hint, not a button: full-strength accent would fight the icon. */
private const val CAPSULE_MAX_ALPHA = 0.16f
