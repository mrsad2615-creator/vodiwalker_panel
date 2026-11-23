package dev.hgallery.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hgallery.app.R
import dev.hgallery.app.data.AppContainer
import dev.hgallery.app.data.HgSettings
import dev.hgallery.app.ui.components.HgTestTags
import dev.hgallery.app.ui.icons.HgIcons
import dev.hgallery.app.ui.theme.CountLabel
import dev.hgallery.app.ui.theme.HgMetrics
import dev.hgallery.app.ui.theme.hgPalette
import dev.hgallery.app.ui.theme.hgShapes
import dev.hgallery.app.ui.theme.HgThemeMode
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Settings.
 *
 * Every control on this screen writes something the rest of the app reads. A control that
 * does not is not drawn here: the alternative is a switch that moves and changes nothing,
 * which is the failure this project is written to avoid.
 *
 * The sections follow the questions a user asks in order: how does it look, how dense is
 * the grid, how does video behave, and what is this app doing with my data.
 */
@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = hgPalette
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize().background(palette.page)) {
        Spacer(Modifier.statusBarsPadding())
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(HgMetrics.MinTouchTarget)
                .padding(end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            dev.hgallery.app.ui.components.HgIconButton(
                icon = HgIcons.Back,
                contentDescription = stringResource(R.string.back),
                onClick = onBack,
                modifier = Modifier.testTag(HgTestTags.SETTINGS_BACK),
            )
        }
        Text(
            text = stringResource(R.string.settings),
            style = MaterialTheme.typography.displaySmall,
            color = palette.textPrimary,
            modifier = Modifier.padding(start = HgMetrics.Gutter, end = HgMetrics.Gutter),
        )
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = HgMetrics.Gutter,
                end = HgMetrics.Gutter,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .testTag(HgTestTags.SETTINGS_LIST),
        ) {
            item { SectionLabel(stringResource(R.string.settings_appearance)) }

            // Three states, not a switch: "follow the system" is a real choice and a
            // two-state switch cannot express it.
            HgThemeMode.entries.forEach { mode ->
                item(key = "theme-${mode.name}") {
                    ChoiceRow(
                        label = stringResource(themeLabel(mode)),
                        selected = settings.themeMode == mode,
                        onClick = { scope.launch { container.settings.setThemeMode(mode) } },
                        modifier = Modifier.testTag(HgTestTags.themeOption(mode.name)),
                    )
                }
            }

            item { SectionLabel(stringResource(R.string.settings_grid)) }
            item {
                GridDensityRow(
                    columns = settings.gridColumns,
                    onChange = { scope.launch { container.settings.setGridColumns(it) } },
                )
            }

            item { SectionLabel(stringResource(R.string.settings_video)) }
            item {
                ToggleRow(
                    label = stringResource(R.string.settings_filmstrip),
                    detail = stringResource(R.string.settings_filmstrip_detail),
                    checked = settings.filmstripVisible,
                    onChange = { scope.launch { container.settings.setFilmstripVisible(it) } },
                    modifier = Modifier.testTag(HgTestTags.FILMSTRIP_SWITCH),
                )
            }
            item {
                ToggleRow(
                    label = stringResource(R.string.settings_autoplay),
                    detail = stringResource(R.string.settings_autoplay_detail),
                    checked = settings.autoplayNext,
                    onChange = { scope.launch { container.settings.setAutoplayNext(it) } },
                    modifier = Modifier.testTag(HgTestTags.AUTOPLAY_SWITCH),
                )
            }

            item { SectionLabel(stringResource(R.string.settings_privacy)) }
            // Read-only facts, stated as facts. These are the claims the app can keep;
            // a control that pretended to change them would be a lie with a switch.
            item {
                FactRow(
                    label = stringResource(R.string.settings_offline_title),
                    detail = stringResource(R.string.settings_offline_body),
                )
            }
            item {
                FactRow(
                    label = stringResource(R.string.settings_storage_title),
                    detail = stringResource(R.string.settings_storage_body),
                )
            }

            item { SectionLabel(stringResource(R.string.settings_about)) }
            item {
                FactRow(
                    label = stringResource(R.string.settings_version_title),
                    detail = buildString {
                        append(container.versionName())
                        append(" · ")
                        append(Locale.getDefault().toLanguageTag())
                    },
                )
            }
        }
    }
}

/** The resource for one theme mode. Kept together so a new mode cannot be forgotten. */
private fun themeLabel(mode: HgThemeMode): Int = when (mode) {
    HgThemeMode.SYSTEM -> R.string.settings_theme_system
    HgThemeMode.LIGHT -> R.string.settings_theme_light
    HgThemeMode.DARK -> R.string.settings_theme_dark
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = hgPalette.textSecondary,
        modifier = Modifier.padding(top = 16.dp, bottom = 2.dp),
    )
}

/**
 * One option in a single-choice list.
 *
 * The tick is a real icon rather than a colour change: a selected row must be recognisable
 * without colour, and a screen reader must hear which one is selected.
 */
@Composable
private fun ChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = hgPalette
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = HgMetrics.MinTouchTarget)
            .clip(RoundedCornerShape(hgShapes.tile))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = palette.textPrimary,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = HgIcons.Check,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.height(20.dp),
            )
        }
    }
}

/** Density as a slider with the resulting count, which is easier to aim than a pinch. */
@Composable
private fun GridDensityRow(columns: Int, onChange: (Int) -> Unit) {
    val palette = hgPalette
    val label = stringResource(R.string.settings_grid_columns, columns)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.settings_grid_density),
                style = MaterialTheme.typography.bodyLarge,
                color = palette.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(text = label, style = CountLabel, color = palette.textSecondary)
        }
        Slider(
            value = columns.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = HgSettings.MIN_GRID_COLUMNS.toFloat()..HgSettings.MAX_GRID_COLUMNS.toFloat(),
            // Five stops for four gaps: a slider that can land between two columns would
            // store a density the grid cannot draw.
            steps = HgSettings.MAX_GRID_COLUMNS - HgSettings.MIN_GRID_COLUMNS - 1,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(HgTestTags.GRID_SLIDER),
        )
        Text(
            text = stringResource(R.string.settings_grid_detail),
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary,
        )
    }
}

/** A labelled on/off row. The whole row is the target, not only the switch. */
@Composable
private fun ToggleRow(
    label: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = hgPalette
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = HgMetrics.MinTouchTarget)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge, color = palette.textPrimary)
            Text(text = detail, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * A statement of fact with no control.
 *
 * The privacy section is built from these. There is nothing to switch: the app has no
 * network permission and keeps no copy of the media, and a switch would suggest both are
 * choices the user is making rather than properties of the build.
 */
@Composable
private fun FactRow(label: String, detail: String) {
    val palette = hgPalette
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$label. $detail" },
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = palette.textPrimary)
        Spacer(Modifier.height(2.dp))
        Text(text = detail, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
    }
}
