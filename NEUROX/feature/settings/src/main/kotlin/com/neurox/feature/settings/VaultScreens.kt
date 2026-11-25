package com.neurox.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.neurox.designsystem.components.ChipTone
import com.neurox.designsystem.components.NxChip
import com.neurox.designsystem.components.NxPrimaryButton
import com.neurox.designsystem.components.NxSecondaryButton
import com.neurox.designsystem.components.NxSectionHeader
import com.neurox.designsystem.tokens.NxSpacing
import com.neurox.designsystem.components.NxSurface
import com.neurox.designsystem.theme.NxTheme
import com.neurox.designsystem.theme.NxTypography
import com.neurox.presentation.i18n.Strings
import com.neurox.presentation.screen.ImportHeader
import com.neurox.presentation.screen.VaultExportState
import com.neurox.presentation.screen.VaultImportState

/**
 * Backup export.
 *
 * SECURITY COMMUNICATION: the screen states, in the plainest words available, that
 * (a) the file is encrypted, (b) the password is not recoverable, and (c) the app
 * never sees the file server-side because there is no server. Users who do not know
 * (b) will lose their data; saying it once on the screen they are standing on is the
 * cheapest possible insurance.
 */
@Composable
fun VaultExportScreen(
    state: VaultExportState,
    strings: Strings,
    onToggleSet: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmChange: (String) -> Unit,
    onExport: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = NxSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(NxSpacing.lg),
    ) {
        Text(
            text = strings.raw("vault.export.title"),
            style = NxTypography.headline,
            color = NxTheme.colors.textPrimary,
            modifier = Modifier.padding(top = NxSpacing.lg),
        )

        NxSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(NxSpacing.lg)) {
                NxSectionHeader(title = strings.raw("vault.export.choose"))
                SelectionRow("profiles", strings, state, onToggleSet)
                SelectionRow("sessions", strings, state, onToggleSet)
                SelectionRow("exercises", strings, state, onToggleSet)
                SelectionRow("achievements", strings, state, onToggleSet)
                SelectionRow("plans", strings, state, onToggleSet)
                SelectionRow("settings", strings, state, onToggleSet)
            }
        }

        NxSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(NxSpacing.lg)) {
                OutlinedTextField(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = state.validationKey == "vault.export.passwordShort",
                    label = { Text(strings.raw("vault.export.password")) },
                    supportingText = { Text(strings.raw("vault.export.passwordHint")) },
                )
                Spacer(Modifier.height(NxSpacing.sm))
                OutlinedTextField(
                    value = state.passwordConfirm,
                    onValueChange = onConfirmChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = state.validationKey == "vault.export.passwordMismatch",
                    label = { Text(strings.raw("vault.export.passwordConfirm")) },
                    supportingText = {
                        val key = state.validationKey
                        if (key != null) {
                            Text(strings.raw(key), color = NxTheme.colors.error)
                        }
                    },
                )
                Spacer(Modifier.height(NxSpacing.md))
                Text(
                    text = strings.raw("vault.export.warning"),
                    style = NxTypography.caption,
                    color = NxTheme.colors.warning,
                )
            }
        }

        if (state.done) {
            NxSurface(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(NxSpacing.lg)) {
                    Text(
                        text = strings.raw("vault.export.success"),
                        style = NxTypography.title,
                        color = NxTheme.colors.success,
                    )
                    Text(
                        text = strings.raw("vault.export.successBody"),
                        style = NxTypography.caption,
                        color = NxTheme.colors.textSecondary,
                    )
                    Text(
                        text = strings.format("unit.sessions", state.bytes),
                        style = NxTypography.caption,
                        color = NxTheme.colors.textTertiary,
                    )
                }
            }
        }

        state.errorKey?.let { key ->
            Text(text = strings.raw(key), style = NxTypography.body, color = NxTheme.colors.error)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(NxSpacing.sm)) {
            NxPrimaryButton(
                label = strings.raw("vault.export.action"),
                onClick = onExport,
                enabled = state.canExport,
                loading = state.busy,
                modifier = Modifier.weight(1f),
            )
            NxSecondaryButton(
                label = strings.raw("common.close"),
                onClick = onClose,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(NxSpacing.xxl))
    }
}

@Composable
private fun SelectionRow(
    set: String,
    strings: Strings,
    state: VaultExportState,
    onToggle: (String) -> Unit,
) {
    val selected = state.selection.contains(set)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = strings.raw("vault.export.choose") },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = selected, onCheckedChange = { onToggle(set) })
        Text(
            text = set,
            style = NxTypography.body,
            color = NxTheme.colors.textPrimary,
        )
    }
}

/**
 * Backup restore.
 *
 * The nine steps of the documented restore pipeline are shown to the user as
 * progress, not hidden. A restore that takes three seconds with no feedback feels
 * broken; a restore that shows "reading, checking, decrypting, validating, writing,
 * verifying" is a restore the user can trust.
 */
@Composable
fun VaultImportScreen(
    state: VaultImportState,
    strings: Strings,
    onPickFile: () -> Unit,
    onPasswordChange: (String) -> Unit,
    onRestore: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = NxSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(NxSpacing.lg),
    ) {
        Text(
            text = strings.raw("vault.import.title"),
            style = NxTypography.headline,
            color = NxTheme.colors.textPrimary,
            modifier = Modifier.padding(top = NxSpacing.lg),
        )

        NxSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(NxSpacing.lg)) {
                NxSectionHeader(title = strings.raw("vault.import.choose"))
                Text(
                    text = state.fileName ?: strings.raw("vault.import.choose"),
                    style = NxTypography.body,
                    color = NxTheme.colors.textSecondary,
                )
                Spacer(Modifier.height(NxSpacing.md))
                NxSecondaryButton(label = strings.raw("vault.import.choose"), onClick = onPickFile)
            }
        }

        state.header?.let { header -> ImportHeaderCard(header = header, strings = strings) }

        NxSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(NxSpacing.lg)) {
                OutlinedTextField(
                    value = state.password,
                    onValueChange = onPasswordChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    label = { Text(strings.raw("vault.export.password")) },
                )
            }
        }

        NxSurface(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(NxSpacing.lg)) {
                NxSectionHeader(title = strings.raw("vault.import.title"))
                listOf(
                    "vault.import.step.read",
                    "vault.import.step.envelope",
                    "vault.import.step.decrypt",
                    "vault.import.step.validate",
                    "vault.import.step.approve",
                    "vault.import.step.write",
                    "vault.import.step.verify",
                    "vault.import.step.done",
                ).forEachIndexed { index, key ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = strings.decimal((index + 1).toLong()),
                            style = NxTypography.caption,
                            color = NxTheme.colors.accent,
                            modifier = Modifier.size(NxSpacing.xl),
                        )
                        Text(text = strings.raw(key), style = NxTypography.caption, color = NxTheme.colors.textSecondary)
                    }
                }
            }
        }

        state.resultKey?.let { key ->
            Text(text = strings.raw(key), style = NxTypography.title, color = NxTheme.colors.success)
        }
        state.errorKey?.let { key ->
            Text(text = strings.raw(key), style = NxTypography.body, color = NxTheme.colors.error)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(NxSpacing.sm)) {
            NxPrimaryButton(
                label = strings.raw("vault.import.action"),
                onClick = onRestore,
                enabled = state.canStart,
                loading = state.busy,
                modifier = Modifier.weight(1f),
            )
            NxSecondaryButton(
                label = strings.raw("common.close"),
                onClick = onClose,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(NxSpacing.xxl))
    }
}

@Composable
private fun ImportHeaderCard(header: ImportHeader, strings: Strings) {
    NxSurface(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(NxSpacing.lg)) {
            NxSectionHeader(title = strings.raw("vault.import.preview"))
            Text(
                text = strings.format(
                    "vault.import.previewBody",
                    strings.dateTime(header.createdAtMillis),
                ),
                style = NxTypography.body,
                color = NxTheme.colors.textSecondary,
            )
            Text(
                text = strings.format("vault.import.privacyNote"),
                style = NxTypography.caption,
                color = NxTheme.colors.textTertiary,
            )
            Spacer(Modifier.height(NxSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(NxSpacing.xs)) {
                NxChip(text = header.formatVersion.toString(), tone = ChipTone.NEUTRAL)
                NxChip(text = header.kdfAlgorithm, tone = ChipTone.NEUTRAL)
                if (header.usesFallbackKdf) {
                    // Honest about the weaker KDF instead of hiding it.
                    NxChip(text = strings.raw("settings.about.limitations"), tone = ChipTone.WARNING)
                }
            }
        }
    }
}
