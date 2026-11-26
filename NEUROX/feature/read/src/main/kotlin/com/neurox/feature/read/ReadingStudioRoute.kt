package com.neurox.feature.read

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.neurox.domain.prefs.SettingsKeys
import com.neurox.games.content.ReadingLibrary
import com.neurox.games.content.ReadingText
import com.neurox.games.read.FocusGuide
import com.neurox.presentation.di.AppServices
import com.neurox.domain.repository.first
import com.neurox.presentation.i18n.Strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Reading Studio.
 *
 * Reading preferences are per profile and per language: a Persian reader wants RTL and
 * a different comfortable font size than the same person reading English. The screen
 * therefore stores both the numeric settings and the selected passage language, and
 * the passage list is filtered to the active language so RSVP never mixes scripts.
 */
@Composable
fun ReadingStudioRoute(
    strings: Strings,
    services: AppServices? = null,
    onClose: () -> Unit,
) {
    var rtl by remember { mutableStateOf(strings.isRightToLeft) }
    var wpm by remember { mutableStateOf(240) }
    var fontSize by remember { mutableStateOf(19) }
    var lineHeight by remember { mutableStateOf(1.6f) }
    var guide by remember { mutableStateOf(FocusGuide.NONE) }
    var selected by remember { mutableStateOf<ReadingText?>(null) }
    var profileId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val svc = services ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val active = svc.profileRepository.getActiveProfile() ?: return@withContext
            profileId = active.id
            // A snapshot, not a subscription: this screen only needs the stored values
            // once, and the stream never ends on its own.
            val stored = svc.settingsRepository.observeSettings(active.id).first().orEmpty()
            wpm = (stored[SettingsKeys.READING_WPM] ?: "240").toIntOrNull() ?: 240
            fontSize = (stored[SettingsKeys.READING_FONT_SIZE] ?: "19").toIntOrNull() ?: 19
            lineHeight = (stored[SettingsKeys.READING_LINE_HEIGHT] ?: "1.6").toFloatOrNull() ?: 1.6f
            rtl = stored[SettingsKeys.READING_RTL]?.toBooleanStrictOrNull() ?: strings.isRightToLeft
            guide = runCatching { FocusGuide.valueOf(stored[SettingsKeys.READING_FOCUS_GUIDE].orEmpty()) }
                .getOrDefault(FocusGuide.NONE)
        }
    }

    // Passage list is filtered to the script the reader is actually reading.
    val passages: List<ReadingText> = remember(rtl) {
        ReadingLibrary.texts.filter { it.languageTag == if (rtl) "fa" else "en" }
    }
    LaunchedEffect(passages) {
        if (selected == null || passages.none { it.id == selected?.id }) {
            selected = passages.firstOrNull()
        }
    }

    fun persist(key: String, value: String) {
        val id = profileId ?: return
        scope.launch {
            withContext(Dispatchers.IO) { services?.settingsRepository?.put(id, key, value) }
        }
    }

    ReadingStudioScreen(
        passages = passages,
        selected = selected,
        strings = strings,
        wpm = wpm,
        fontSize = fontSize,
        lineHeight = lineHeight,
        guide = guide,
        rtl = rtl,
        positionFraction = 0.0,
        onSelectPassage = { selected = it },
        onWpmChange = {
            wpm = it
            persist(SettingsKeys.READING_WPM, it.toString())
        },
        onFontSizeChange = {
            fontSize = it
            persist(SettingsKeys.READING_FONT_SIZE, it.toString())
        },
        onLineHeightChange = {
            lineHeight = it
            persist(SettingsKeys.READING_LINE_HEIGHT, it.toString())
        },
        onGuideChange = {
            guide = it
            persist(SettingsKeys.READING_FOCUS_GUIDE, it.name)
        },
        onToggleRtl = {
            rtl = it
            persist(SettingsKeys.READING_RTL, it.toString())
        },
        onStart = { onClose() },
    )
}
