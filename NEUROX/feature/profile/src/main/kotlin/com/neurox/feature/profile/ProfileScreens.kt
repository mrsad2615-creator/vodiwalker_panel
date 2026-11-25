package com.neurox.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.neurox.designsystem.components.NxChip
import com.neurox.designsystem.components.ChipTone
import com.neurox.designsystem.components.NxPrimaryButton
import com.neurox.designsystem.components.NxSecondaryButton
import com.neurox.designsystem.tokens.NxRadius
import com.neurox.designsystem.components.NxSectionHeader
import com.neurox.designsystem.tokens.NxSpacing
import com.neurox.designsystem.components.NxSurface
import com.neurox.designsystem.theme.NxTheme
import com.neurox.designsystem.theme.NxTypography
import com.neurox.designsystem.components.nxAccentBrush
import com.neurox.domain.model.ProfileSummary
import com.neurox.presentation.i18n.Strings
import com.neurox.presentation.screen.ProfileEditState
import com.neurox.presentation.screen.ProfileListState

/**
 * Profiles.
 *
 * ISOLATION IS NOT A UI FEATURE. This screen only *requests* a switch; the
 * repository enforces the PIN and the domain keeps exactly one active profile. The
 * copy on this screen says so, because a user who believes isolation is only
 * cosmetic will not trust the app with two people's data.
 */
@Composable
fun ProfileListScreen(
    state: ProfileListState,
    strings: Strings,
    onSelect: (ProfileSummary) -> Unit,
    onCreate: () -> Unit,
    onCreateGuest: () -> Unit,
    onEdit: (ProfileSummary) -> Unit,
    onDelete: (ProfileSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = NxSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(NxSpacing.md),
    ) {
        item {
            Column(modifier = Modifier.padding(top = NxSpacing.lg)) {
                Text(
                    text = strings.raw("profile.title"),
                    style = NxTypography.headline,
                    color = NxTheme.colors.textPrimary,
                )
                Text(
                    text = strings.raw("profile.choose"),
                    style = NxTypography.caption,
                    color = NxTheme.colors.textTertiary,
                )
            }
        }

        items(state.profiles) { profile ->
            ProfileCard(
                profile = profile,
                isActive = profile.id == state.activeId,
                strings = strings,
                onSelect = { onSelect(profile) },
                onEdit = { onEdit(profile) },
                onDelete = { onDelete(profile) },
                canDelete = state.profiles.size > 1,
            )
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(NxSpacing.sm)) {
                if (state.canCreate) {
                    NxPrimaryButton(
                        label = strings.raw("profile.create"),
                        onClick = onCreate,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    NxSecondaryButton(
                        label = strings.raw("profile.createGuest"),
                        onClick = onCreateGuest,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    NxSurface(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = strings.raw("profile.limit"),
                            style = NxTypography.body,
                            color = NxTheme.colors.warning,
                            modifier = Modifier.padding(NxSpacing.lg),
                        )
                    }
                }
                Spacer(Modifier.height(NxSpacing.sm))
                Text(
                    text = strings.raw("profile.privacyNote"),
                    style = NxTypography.caption,
                    color = NxTheme.colors.textTertiary,
                )
            }
        }
        item { Spacer(Modifier.height(NxSpacing.xxl)) }
    }
}

@Composable
private fun ProfileCard(
    profile: ProfileSummary,
    isActive: Boolean,
    strings: Strings,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean,
) {
    NxSurface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isActive) 1.5.dp else 0.dp,
                color = if (isActive) NxTheme.colors.accent else NxTheme.colors.border,
                shape = RoundedCornerShape(NxRadius.md),
            )
            .semantics {
                contentDescription = strings.raw("profile.current")
            },
    ) {
        Column(modifier = Modifier.padding(NxSpacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(nxAccentBrush()),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = profile.displayName.take(1).uppercase(),
                        style = NxTypography.label,
                        color = NxTheme.colors.onAccent,
                    )
                }
                Spacer(Modifier.size(NxSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.displayName,
                        style = NxTypography.title,
                        color = NxTheme.colors.textPrimary,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(NxSpacing.xs)) {
                        if (isActive) {
                            NxChip(text = strings.raw("profile.current"), tone = ChipTone.ACCENT)
                        }
                        if (profile.isGuest) {
                            NxChip(text = strings.raw("profile.guest"))
                        }
                        NxChip(
                            text = strings.raw(if (profile.hasPin) "profile.pinSet" else "profile.pinNotSet"),
                            tone = if (profile.hasPin) ChipTone.SUCCESS else ChipTone.NEUTRAL,
                        )
                    }
                }
            }
            Spacer(Modifier.height(NxSpacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(NxSpacing.sm)) {
                if (!isActive) {
                    NxPrimaryButton(label = strings.raw("common.continue"), onClick = onSelect)
                }
                NxSecondaryButton(label = strings.raw("common.edit"), onClick = onEdit)
                if (canDelete) {
                    NxSecondaryButton(
                        label = strings.raw("common.delete"),
                        onClick = onDelete,
                        destructive = true,
                    )
                }
            }
            if (!canDelete) {
                Spacer(Modifier.height(NxSpacing.xs))
                Text(
                    text = strings.raw("profile.deleteLast"),
                    style = NxTypography.caption,
                    color = NxTheme.colors.textTertiary,
                )
            }
        }
    }
}

/**
 * Create / edit / PIN / delete confirmation for one profile.
 *
 * The PIN is entered twice to set it; a switch only needs it once. Digits are the
 * only accepted input, and the field never shows more than 8 characters because the
 * domain rejects anything longer - telling the user before they type is kinder than
 * an error afterwards.
 */
@Composable
fun ProfileEditScreen(
    state: ProfileEditState,
    strings: Strings,
    onNameChange: (String) -> Unit,
    onAvatarChange: (Int) -> Unit,
    onLanguageChange: (String) -> Unit,
    onThemeChange: (String) -> Unit,
    onPinChange: (String) -> Unit,
    onPinConfirmChange: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = NxSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(NxSpacing.lg),
    ) {
        item {
            Text(
                text = strings.raw(if (state.isNew) "profile.create" else "profile.rename"),
                style = NxTypography.headline,
                color = NxTheme.colors.textPrimary,
                modifier = Modifier.padding(top = NxSpacing.lg),
            )
        }
        item {
            NxSurface(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(NxSpacing.lg)) {
                    OutlinedTextField(
                        value = state.draftName,
                        onValueChange = onNameChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = state.errorKey != null,
                        label = { Text(strings.raw("profile.name")) },
                        supportingText = {
                            Text(
                                text = state.errorKey?.let { strings.raw(it) }
                                    ?: strings.raw("profile.nameHint"),
                            )
                        },
                    )
                    Spacer(Modifier.height(NxSpacing.md))
                    NxSectionHeader(title = strings.raw("profile.avatar"))
                    Row(horizontalArrangement = Arrangement.spacedBy(NxSpacing.sm)) {
                        (0..7).forEach { avatar ->
                            AvatarOption(
                                index = avatar,
                                selected = state.draftAvatar == avatar,
                                strings = strings,
                                onClick = { onAvatarChange(avatar) },
                            )
                        }
                    }
                    Spacer(Modifier.height(NxSpacing.md))
                    Row(horizontalArrangement = Arrangement.spacedBy(NxSpacing.sm)) {
                        Strings.SUPPORTED_LANGUAGES.forEach { tag ->
                            NxChip(
                                text = strings.raw("language.$tag"),
                                selected = state.draftLanguage == tag,
                                tone = if (state.draftLanguage == tag) ChipTone.ACCENT else ChipTone.NEUTRAL,
                                onClick = { onLanguageChange(tag) },
                            )
                        }
                    }
                    Spacer(Modifier.height(NxSpacing.md))
                    Row(horizontalArrangement = Arrangement.spacedBy(NxSpacing.sm)) {
                        listOf("neurox.obsidian", "neurox.ivory", "neurox.contrast").forEach { themeId ->
                            NxChip(
                                text = strings.raw("settings.theme.${themeId.substringAfterLast('.')}"),
                                selected = state.draftThemeId == themeId,
                                tone = if (state.draftThemeId == themeId) ChipTone.ACCENT else ChipTone.NEUTRAL,
                                onClick = { onThemeChange(themeId) },
                            )
                        }
                    }
                }
            }
        }
        item {
            NxSurface(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(NxSpacing.lg)) {
                    NxSectionHeader(title = strings.raw("profile.pin"))
                    Text(
                        text = strings.raw("profile.pinHint"),
                        style = NxTypography.caption,
                        color = NxTheme.colors.textTertiary,
                    )
                    Spacer(Modifier.height(NxSpacing.sm))
                    OutlinedTextField(
                        value = state.pinEntered,
                        onValueChange = onPinChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        label = { Text(strings.raw("profile.pinEnter")) },
                    )
                    Spacer(Modifier.height(NxSpacing.sm))
                    OutlinedTextField(
                        value = state.pinConfirm,
                        onValueChange = onPinConfirmChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        isError = state.pinEntered.isNotEmpty() && !state.pinValid,
                        label = { Text(strings.raw("vault.export.passwordConfirm")) },
                        supportingText = {
                            if (state.pinEntered.isNotEmpty() && !state.pinValid) {
                                Text(strings.raw("profile.pinHint"), color = NxTheme.colors.error)
                            }
                        },
                    )
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(NxSpacing.sm)) {
                NxPrimaryButton(
                    label = strings.raw("common.save"),
                    onClick = onSave,
                    enabled = state.draftName.isNotBlank() && state.pinValid,
                    modifier = Modifier.fillMaxWidth(),
                )
                NxSecondaryButton(
                    label = strings.raw("common.cancel"),
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                )
                onDelete?.let { delete ->
                    if (!state.isNew) {
                        NxSecondaryButton(
                            label = strings.raw("profile.delete"),
                            onClick = delete,
                            destructive = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(NxSpacing.xxl)) }
    }
}

@Composable
private fun AvatarOption(index: Int, selected: Boolean, strings: Strings, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (selected) NxTheme.colors.accentMuted else NxTheme.colors.surfaceElevated)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) NxTheme.colors.accent else NxTheme.colors.border,
                shape = CircleShape,
            )
            .semantics { contentDescription = strings.format("a11y.avatar", index) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = strings.decimal((index + 1).toLong()),
            style = NxTypography.caption,
            color = NxTheme.colors.textPrimary,
        )
    }
}

/**
 * Confirmation dialog for a destructive profile action.
 *
 * A plain AlertDialog is used instead of a bespoke sheet: destructive confirmation is
 * exactly the case where platform behaviour (back button, scrim tap, screen reader
 * focus) should not be re-invented.
 */
@Composable
fun ProfileConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    cancelLabel: String,
    destructive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = NxTypography.title, color = NxTheme.colors.textPrimary) },
        text = { Text(body, style = NxTypography.body, color = NxTheme.colors.textSecondary) },
        confirmButton = {
            NxPrimaryButton(label = confirmLabel, onClick = onConfirm)
        },
        dismissButton = {
            NxSecondaryButton(
                label = cancelLabel,
                onClick = onDismiss,
                destructive = destructive.not(),
            )
        },
        containerColor = NxTheme.colors.surfaceElevated,
    )
}

/** PIN prompt shown before switching into a protected profile. */
@Composable
fun ProfilePinDialog(
    strings: Strings,
    profileName: String,
    pin: String,
    errorKey: String?,
    onPinChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(profileName, style = NxTypography.title, color = NxTheme.colors.textPrimary) },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = onPinChange,
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    label = { Text(strings.raw("profile.pinEnter")) },
                )
                errorKey?.let { key ->
                    Text(text = strings.raw(key), style = NxTypography.caption, color = NxTheme.colors.error)
                }
            }
        },
        confirmButton = { NxPrimaryButton(label = strings.raw("common.continue"), onClick = onConfirm) },
        dismissButton = {
            NxSecondaryButton(label = strings.raw("common.cancel"), onClick = onDismiss)
        },
        containerColor = NxTheme.colors.surfaceElevated,
    )
}

/** Non-modal error line, used when no dialog owns the error. */
@Composable
fun ProfileErrorBanner(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(NxSpacing.lg),
    ) {
        Text(text = message, style = NxTypography.body, color = NxTheme.colors.error)
    }
}
