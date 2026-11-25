package com.neurox.feature.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.neurox.presentation.di.AppServices
import com.neurox.common.result.Outcome
import com.neurox.domain.model.ProfileSummary
import com.neurox.presentation.i18n.Strings
import com.neurox.presentation.screen.ProfileEditState
import com.neurox.presentation.screen.ProfileListState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Profiles: list, create, edit, switch, delete.
 *
 * THE SCREEN NEVER DECIDES WHETHER A SWITCH IS ALLOWED. It asks the repository, which
 * verifies the PIN and keeps exactly one active row. If the repository refuses, the
 * screen shows the code it returned; it has no second opinion, so the two can never
 * disagree about who is allowed in.
 *
 * DRAFT STATE: the editor keeps a real draft (name, avatar, language, theme, PIN) and
 * only writes on Save. A partially typed profile name is never persisted, and Cancel
 * is a genuine cancel.
 */
@Composable
fun ProfileRoute(
    services: AppServices,
    strings: Strings,
    onProfileSwitched: () -> Unit,
) {
    var profiles by remember { mutableStateOf<List<ProfileSummary>>(emptyList()) }
    var activeId by remember { mutableStateOf<String?>(null) }
    var maxReached by remember { mutableStateOf(false) }
    var errorKey by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    // Editing session: null means "list mode".
    var editingProfile by remember { mutableStateOf<ProfileSummary?>(null) }
    var isCreating by remember { mutableStateOf(false) }
    var draftName by remember { mutableStateOf("") }
    var draftAvatar by remember { mutableStateOf(0) }
    var draftLanguage by remember { mutableStateOf("fa") }
    var draftTheme by remember { mutableStateOf("neurox.obsidian") }
    var pin by remember { mutableStateOf("") }
    var pinConfirm by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<ProfileSummary?>(null) }
    var switchTarget by remember { mutableStateOf<ProfileSummary?>(null) }
    var switchPin by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    LaunchedEffect(reload) {
        withContext(Dispatchers.IO) {
            val all = mutableListOf<ProfileSummary>()
            services.profileRepository.observeProfiles().collect { list ->
                all.clear()
                all.addAll(list)
            }
            profiles = all
            activeId = all.firstOrNull { it.active }?.id
            maxReached = all.size >= MAX_PROFILES
        }
    }

    fun openCreate() {
        editingProfile = null
        isCreating = true
        draftName = ""
        draftAvatar = 0
        draftLanguage = strings.languageTag
        draftTheme = "neurox.obsidian"
        pin = ""
        pinConfirm = ""
        errorKey = null
    }

    fun openEdit(profile: ProfileSummary) {
        editingProfile = profile
        isCreating = false
        draftName = profile.displayName
        draftAvatar = profile.avatarId
        draftLanguage = profile.languageTag
        draftTheme = profile.themeId
        pin = ""
        pinConfirm = ""
        errorKey = null
    }

    fun closeEditor() {
        editingProfile = null
        isCreating = false
        errorKey = null
    }

    fun save() {
        val name = draftName.trim()
        scope.launch {
            val outcome: Outcome<ProfileSummary> = withContext(Dispatchers.IO) {
                val target = editingProfile
                when {
                    target == null -> services.profileRepository.createProfile(
                        displayName = name,
                    ).also { created ->
                        // Apply the rest of the draft only after the row exists.
                        if (created is Outcome.Ok) {
                            val id = created.value.id
                            services.profileRepository.updateAvatar(id, draftAvatar)
                            services.profileRepository.updateSettingsPatch(id, draftTheme, draftLanguage)
                            if (pin.isNotEmpty()) services.profileRepository.setPin(id, pin)
                        }
                    }
                    else -> {
                        val renamed = services.profileRepository.renameProfile(target.id, name)
                        if (renamed is Outcome.Ok) {
                            services.profileRepository.updateAvatar(target.id, draftAvatar)
                            services.profileRepository.updateSettingsPatch(target.id, draftTheme, draftLanguage)
                            // Only change the PIN when one was actually entered, so a
                            // rename cannot silently remove protection.
                            if (pin.isNotEmpty()) services.profileRepository.setPin(target.id, pin)
                        }
                        renamed
                    }
                }
            }
            when (outcome) {
                is Outcome.Err -> errorKey = "error.${outcome.error.code}"
                is Outcome.Ok -> {
                    closeEditor()
                    reload += 1
                }
            }
        }
    }

    fun requestSwitch(profile: ProfileSummary) {
        if (profile.hasPin) {
            switchTarget = profile
            switchPin = ""
        } else {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    services.profileRepository.switchProfile(profile.id, pin = null)
                }
                when (result) {
                    is Outcome.Err -> errorKey = "error.${result.error.code}"
                    is Outcome.Ok -> {
                        errorKey = null
                        reload += 1
                        onProfileSwitched()
                    }
                }
            }
        }
    }

    fun confirmSwitch() {
        val target = switchTarget ?: return
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                services.profileRepository.switchProfile(target.id, switchPin)
            }
            when (result) {
                is Outcome.Err -> errorKey = "error.${result.error.code}"
                is Outcome.Ok -> {
                    errorKey = null
                    switchTarget = null
                    switchPin = ""
                    reload += 1
                    onProfileSwitched()
                }
            }
        }
    }

    if (isCreating || editingProfile != null) {
        ProfileEditScreen(
            state = ProfileEditState(
                profile = editingProfile,
                draftName = draftName,
                draftAvatar = draftAvatar,
                draftLanguage = draftLanguage,
                draftThemeId = draftTheme,
                pinEntered = pin,
                pinConfirm = pinConfirm,
                errorKey = errorKey,
                saved = false,
            ),
            strings = strings,
            onNameChange = { if (it.length <= MAX_NAME_LENGTH) draftName = it },
            onAvatarChange = { draftAvatar = it },
            onLanguageChange = { draftLanguage = it },
            onThemeChange = { draftTheme = it },
            onPinChange = { input ->
                // Digits only, max 8: the domain rejects anything else, and telling the
                // user before they type beats an error afterwards.
                pin = input.filter { char -> char.isDigit() }.take(MAX_PIN_LENGTH)
            },
            onPinConfirmChange = { input ->
                pinConfirm = input.filter { char -> char.isDigit() }.take(MAX_PIN_LENGTH)
            },
            onSave = ::save,
            onDelete = editingProfile?.let { profile ->
                {
                    deleteTarget = profile
                }
            },
            onCancel = ::closeEditor,
        )
        return
    }

    ProfileListScreen(
        state = ProfileListState(
            profiles = profiles,
            activeId = activeId,
            maxReached = maxReached,
            pinPromptProfileId = switchTarget?.id,
        ),
        strings = strings,
        onSelect = ::requestSwitch,
        onCreate = ::openCreate,
        onCreateGuest = {
            scope.launch {
                withContext(Dispatchers.IO) {
                    services.profileRepository.createProfile(displayName = strings.raw("profile.guest"), isGuest = true)
                }
                reload += 1
                onProfileSwitched()
            }
        },
        onEdit = ::openEdit,
        onDelete = { profile ->
            // Deleting the last profile is refused by the repository; the button is
            // hidden for it above, and the code is still surfaced if it races.
            deleteTarget = profile
        },
    )

    // Two confirmations live outside the list so they cannot be triggered by a
    // mis-tap on the card itself.
    deleteTarget?.let { profile ->
        ProfileConfirmDialog(
            title = strings.format("profile.deleteConfirm", profile.displayName),
            body = strings.raw("profile.deleteBody"),
            confirmLabel = strings.raw("profile.delete"),
            cancelLabel = strings.raw("common.cancel"),
            destructive = true,
            onConfirm = {
                deleteTarget = null
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        services.profileRepository.deleteProfile(profile.id)
                    }
                    if (result is Outcome.Err) errorKey = "error.${result.error.code}"
                    reload += 1
                    onProfileSwitched()
                }
            },
            onDismiss = { deleteTarget = null },
        )
    }

    switchTarget?.let { profile ->
        ProfilePinDialog(
            strings = strings,
            profileName = profile.displayName,
            pin = switchPin,
            errorKey = errorKey,
            onPinChange = { switchPin = it.filter { c -> c.isDigit() }.take(MAX_PIN_LENGTH) },
            onConfirm = ::confirmSwitch,
            onDismiss = {
                switchTarget = null
                switchPin = ""
                errorKey = null
            },
        )
    }

    errorKey?.takeIf { switchTarget == null && deleteTarget == null }?.let { key ->
        // A non-modal error (for example switching to a profile that was deleted from
        // another device) is shown once and cleared by the next action.
        ProfileErrorBanner(message = strings.raw(key))
    }
}

private const val MAX_PROFILES = 8
private const val MAX_NAME_LENGTH = 32
private const val MAX_PIN_LENGTH = 8
