package dev.hgallery.app.ui.shell

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.hgallery.app.ui.components.HgTestTags
import dev.hgallery.app.ui.icons.HgIcons
import dev.hgallery.app.ui.theme.HgMetrics
import dev.hgallery.app.ui.theme.hgPalette

/** The four destinations of the reference's bottom bar, in order. */
enum class HgTab(val label: String, val icon: ImageVector) {
    Photos("Photos", HgIcons.Photos),
    Albums("Albums", HgIcons.Albums),
    Highlights("Highlights", HgIcons.Highlights),
    Discover("Discover", HgIcons.Discover),
}

/**
 * Bottom navigation, matched to reference R2.
 *
 * A 24 dp icon above a 10 sp label, the active tab in the accent blue and the
 * rest in secondary grey. The bar has no elevation and no pill indicator: the
 * reference distinguishes the active tab by colour alone, and adding a Material
 * navigation-bar bubble would be a visible deviation.
 */
@Composable
fun HgBottomBar(
    selected: HgTab,
    onSelect: (HgTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = hgPalette
    Column(modifier = modifier.fillMaxWidth().background(palette.page)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.6.dp)
                .background(palette.divider),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(HgMetrics.BottomBarHeight)
                .navigationBarsPadding(),
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
    val palette = hgPalette
    val tint by animateColorAsState(
        targetValue = if (selected) palette.accent else palette.textSecondary,
        animationSpec = tween(durationMillis = 150),
        label = "tabTint",
    )
    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .testTag(HgTestTags.tab(tab.name))
            .height(HgMetrics.MinTouchTarget + 8.dp)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = interaction,
                indication = null,
            )
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(23.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
        )
    }
}
