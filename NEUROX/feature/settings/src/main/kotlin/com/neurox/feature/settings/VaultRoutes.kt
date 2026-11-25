package com.neurox.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.neurox.presentation.di.AppServices
import com.neurox.common.result.Outcome
import com.neurox.data.prefs.BackupRestoreCoordinator
import com.neurox.data.prefs.SettingsKeys
import com.neurox.domain.model.ProfileSummary
import com.neurox.presentation.i18n.Strings
import com.neurox.presentation.screen.ImportHeader
import com.neurox.presentation.screen.VaultExportState
import com.neurox.security.vault.VaultService
import com.neurox.presentation.screen.VaultImportState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Backup export.
 *
 * The password is never stored, never cached and never written to a log. It lives in
 * this composable's state for as long as the screen is open, and is dropped when the
 * screen leaves the composition.
 */
@Composable
fun VaultExportRoute(
    services: AppServices,
    strings: Strings,
    vaultService: VaultService,
    backupCoordinator: BackupRestoreCoordinator,
    onClose: () -> Unit,
    onExported: (() -> Unit)? = null,
) {
    var selection by remember { mutableStateOf(setOf("profiles", "sessions", "exercises", "achievements", "settings")) }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var errorKey by remember { mutableStateOf<String?>(null) }
    var bytes by remember { mutableStateOf(0) }
    var profile by remember { mutableStateOf<ProfileSummary?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        profile = withContext(Dispatchers.IO) { services.profileRepository.getActiveProfile() }
    }

    VaultExportScreen(
        state = VaultExportState(
            selection = selection,
            password = password,
            passwordConfirm = confirm,
            busy = busy,
            progressKey = null,
            done = done,
            bytes = bytes,
            errorKey = errorKey,
        ),
        strings = strings,
        onToggleSet = { set ->
            selection = if (selection.contains(set)) selection - set else selection + set
        },
        onPasswordChange = { password = it },
        onConfirmChange = { confirm = it },
        onExport = {
            val active = profile ?: return@VaultExportScreen
            busy = true
            errorKey = null
            scope.launch {
                val outcome = withContext(Dispatchers.IO) {
                    backupCoordinator.export(
                        service = vaultService,
                        profile = active,
                        password = password.toCharArray(),
                        selection = selection,
                    )
                }
                busy = false
                when (outcome) {
                    is Outcome.Ok -> {
                        // The document is returned to the caller, which owns file I/O
                        // (SAF). Writing files from a screen without the picker would be
                        // a permission bug.
                        bytes = outcome.value.length
                        done = true
                        withContext(Dispatchers.IO) {
                            services.settingsRepository.put(
                                active.id,
                                SettingsKeys.VAULT_LAST_EXPORT_AT,
                                System.currentTimeMillis().toString(),
                            )
                        }
                        onExported?.invoke()
                    }
                    is Outcome.Err -> errorKey = "error.${outcome.error.code}"
                }
            }
        },
        onClose = onClose,
    )
}

/**
 * Backup restore.
 *
 * ORDER: read the file (owned by the caller), validate the envelope with the vault
 * service, show the header *before* asking for a password, then decrypt, then apply
 * through the coordinator which validates, writes and verifies.
 */
@Composable
fun VaultImportRoute(
    services: AppServices,
    strings: Strings,
    vaultService: VaultService,
    backupCoordinator: BackupRestoreCoordinator,
    onClose: () -> Unit,
    onRestored: () -> Unit,
    pendingDocument: String? = null,
    fileName: String? = null,
) {
    var document by remember { mutableStateOf<String?>(pendingDocument) }
    var name by remember { mutableStateOf(fileName) }
    var header by remember { mutableStateOf<ImportHeader?>(null) }
    var password by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(VaultImportState.Step.CHOOSE_FILE) }
    var busy by remember { mutableStateOf(false) }
    var resultKey by remember { mutableStateOf<String?>(null) }
    var errorKey by remember { mutableStateOf<String?>(null) }
    var restoredSessions by remember { mutableStateOf(0) }
    var restoredSettings by remember { mutableStateOf(0) }
    var targetProfileId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        targetProfileId = withContext(Dispatchers.IO) {
            services.profileRepository.getActiveProfile()?.id
        }
    }

    // Describe the document as soon as it arrives: the header is readable without the
    // password, which is what lets the screen confirm "this is your file" first.
    LaunchedEffect(document) {
        val doc = document ?: return@LaunchedEffect
        val described = withContext(Dispatchers.IO) { vaultService.describe(doc) }
        when (described) {
            is Outcome.Ok -> {
                val value = described.value
                header = ImportHeader(
                    createdAtMillis = value.createdAtMillis,
                    formatVersion = value.version,
                    kdfAlgorithm = value.kdfAlgorithm,
                    kdfIterations = value.iterations,
                    // Argon2id was deferred in this build, so a file that names it is
                    // restored through the PBKDF2 band and the user is told so.
                    usesFallbackKdf = value.kdfAlgorithm == com.neurox.domain.vault.KdfParams.ARGON2ID,
                    selection = value.selection,
                )
                step = VaultImportState.Step.PASSWORD
            }
            is Outcome.Err -> {
                errorKey = "error.${described.error.code}"
                step = VaultImportState.Step.FAILED
            }
        }
    }

    VaultImportScreen(
        state = VaultImportState(
            fileName = name,
            header = header,
            password = password,
            step = step,
            busy = busy,
            resultKey = resultKey,
            errorKey = errorKey,
            restoredSessions = restoredSessions,
            restoredSettings = restoredSettings,
        ),
        strings = strings,
        onPickFile = { },
        onPasswordChange = { password = it },
        onRestore = {
            val doc = document ?: return@VaultImportScreen
            val active = targetProfileId ?: return@VaultImportScreen
            busy = true
            errorKey = null
            scope.launch {
                val outcome = withContext(Dispatchers.IO) {
                    backupCoordinator.import(
                        service = vaultService,
                        document = doc,
                        password = password.toCharArray(),
                        targetProfileId = active,
                    )
                }
                busy = false
                when (outcome) {
                    is Outcome.Ok -> {
                        restoredSessions = outcome.value.sessionsWritten
                        restoredSettings = outcome.value.settingsWritten
                        resultKey = "vault.import.success"
                        step = VaultImportState.Step.DONE
                        onRestored()
                    }
                    is Outcome.Err -> {
                        errorKey = "error.${outcome.error.code}"
                        step = VaultImportState.Step.FAILED
                    }
                }
            }
        },
        onClose = onClose,
    )
}

// The restore target is the active profile; a restore never silently creates one.
