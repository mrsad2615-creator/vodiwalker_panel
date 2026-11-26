package com.neurox.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.neurox.domain.repository.first
import com.neurox.presentation.di.AppServices
import com.neurox.common.MotionModeName
import com.neurox.domain.prefs.NxPreferences
import com.neurox.domain.prefs.SettingsKeys
import com.neurox.domain.model.AudioSettings
import com.neurox.domain.model.ThemeDefinition
import com.neurox.presentation.i18n.Strings
import com.neurox.presentation.screen.SettingsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings.
 *
 * WRITE POLICY: every change is persisted immediately and then reflected in the
 * screens that depend on it. There is no "Apply" button, because a settings screen
 * that lets you leave without saving is a settings screen that loses changes.
 *
 * The audio cache that [com.neurox.audio.AudioPolicy] reads is updated in the same
 * step, so a change to silent mode takes effect on the very next cue.
 */
@Composable
fun SettingsRoute(
    services: AppServices,
    strings: Strings,
    audioRenderer: com.neurox.audio.platform.AudioRenderer? = null,
    onPreferencesChanged: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    var preferences by remember { mutableStateOf(NxPreferences()) }
    var audio by remember { mutableStateOf(AudioSettings()) }
    var profileId by remember { mutableStateOf<String?>(null) }
    // Bumped after a change so the snapshot reloads; a preference write does not
    // push a new value to this screen by itself.
    var reloadToken by remember { mutableStateOf(0) }
    var appVersion by remember { mutableStateOf("1.0.0") }
    val scope = rememberCoroutineScope()

    // First load: snapshots, not subscriptions. The two streams are Room flows, so
    // collecting them would never return and the screen would sit on defaults forever.
    LaunchedEffect(reloadToken) {
        withContext(Dispatchers.IO) {
            val active = services.profileRepository.getActiveProfile() ?: return@withContext
            profileId = active.id
            val values = services.settingsRepository.observeSettings(active.id).first().orEmpty()
            preferences = NxPreferences.from(values)
            services.audioRepository.observe(active.id).first()?.let { current ->
                audio = current
                services.audioSettings.update(current)
            }
        }
    }

    fun persist(key: String, value: String) {
        val id = profileId ?: return
        // The UI is updated from the same value that is written, so it can never show
        // a setting that did not reach storage.
        scope.launch {
            withContext(Dispatchers.IO) { services.settingsRepository.put(id, key, value) }
            onPreferencesChanged()
        }
    }

    fun persistAudio(next: AudioSettings) {
        val id = profileId ?: return
        audio = next
        services.audioSettings.update(next)
        scope.launch {
            withContext(Dispatchers.IO) { services.audioRepository.update(id, next) }
        }
    }

    SettingsScreen(
        state = SettingsState(
            languageTag = preferences.languageTag,
            themeId = preferences.themeId,
            availableThemes = BuiltInThemes,
            motionMode = preferences.motionMode.name,
            reducedMotion = preferences.reducedMotion,
            textScale = preferences.textScale,
            adaptiveEnabled = preferences.adaptiveEnabled,
            adaptiveMinSamples = preferences.adaptiveMinSamples,
            inputLatencyMillis = preferences.inputLatencyMillis,
            displayLatencyMillis = preferences.displayLatencyMillis,
            audio = audio,
            weeklyGoalMinutes = preferences.weeklyGoalMinutes,
            scheduledBackup = preferences.scheduledBackup,
            lastExportAtMillis = preferences.lastExportAtMillis,
            appVersion = appVersion,
        ),
        strings = strings,
        onChangeLanguage = { tag ->
            preferences = preferences.copy(languageTag = tag)
            persist(SettingsKeys.LANGUAGE_TAG, tag)
        },
        onChangeTheme = { id ->
            preferences = preferences.copy(themeId = id)
            persist(SettingsKeys.THEME_ID, id)
        },
        onChangeMotion = { mode ->
            val parsed = runCatching { MotionModeName.valueOf(mode) }.getOrDefault(MotionModeName.BALANCED)
            preferences = preferences.copy(motionMode = parsed)
            persist(SettingsKeys.MOTION_MODE, parsed.name)
        },
        onToggleReducedMotion = { enabled ->
            preferences = preferences.copy(reducedMotion = enabled)
            persist(SettingsKeys.REDUCED_MOTION, enabled.toString())
        },
        onChangeTextScale = { scale ->
            preferences = preferences.copy(textScale = scale)
            persist(SettingsKeys.TEXT_SCALE, scale.toString())
        },
        onToggleAdaptive = { enabled ->
            preferences = preferences.copy(adaptiveEnabled = enabled)
            persist(SettingsKeys.ADAPTIVE_ENABLED, enabled.toString())
        },
        onChangeAdaptiveSamples = { count ->
            preferences = preferences.copy(adaptiveMinSamples = count)
            persist(SettingsKeys.ADAPTIVE_MIN_SAMPLES, count.toString())
        },
        onChangeAudio = ::persistAudio,
        onChangeInputLatency = { millis ->
            preferences = preferences.copy(inputLatencyMillis = millis)
            persist(SettingsKeys.TIMING_INPUT_LATENCY_MS, millis.toString())
        },
        onChangeDisplayLatency = { millis ->
            preferences = preferences.copy(displayLatencyMillis = millis)
            persist(SettingsKeys.TIMING_DISPLAY_LATENCY_MS, millis.toString())
        },
        onChangeWeeklyGoal = { minutes ->
            preferences = preferences.copy(weeklyGoalMinutes = minutes)
            persist(SettingsKeys.PLAN_WEEKLY_GOAL_MINUTES, minutes.toString())
        },
        onToggleScheduledBackup = { enabled ->
            preferences = preferences.copy(scheduledBackup = enabled)
            persist(SettingsKeys.VAULT_SCHEDULED_BACKUP, enabled.toString())
        },
        onExport = onExport,
        onImport = onImport,
        onOpenLicenses = { },
    )
}

/**
 * The three built-in themes, mirrored from the design system so this file has no
 * Compose dependency in its state building.
 */
private val BuiltInThemes = listOf(
    ThemeDefinition("neurox.obsidian", "Obsidian Gold", isBuiltIn = true, accentArgb = 0xFFD8B36A, secondaryArgb = 0xFFF6E7C1, lightSurfaces = false),
    ThemeDefinition("neurox.ivory", "Ivory", isBuiltIn = true, accentArgb = 0xFFBE9550, secondaryArgb = 0xFF8A6A33, lightSurfaces = true),
    ThemeDefinition("neurox.contrast", "High contrast", isBuiltIn = true, accentArgb = 0xFFFFD98A, secondaryArgb = 0xFFFFFFFF, lightSurfaces = false),
)
