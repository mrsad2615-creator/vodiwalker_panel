package dev.mrsad.app.feature.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.mrsad.app.data.AppContainer
import dev.mrsad.app.data.settings.MrsadSettings
import dev.mrsad.app.ui.components.LocalBottomBarClearance
import dev.mrsad.app.ui.components.MetalIcon
import dev.mrsad.app.ui.components.MrsadChip
import dev.mrsad.app.ui.components.MrsadIcons
import dev.mrsad.app.ui.components.MrsadPrimaryButton
import dev.mrsad.app.ui.components.MrsadSurface
import dev.mrsad.app.ui.components.MrsadTokens
import dev.mrsad.app.ui.components.ObsidianCollapsingTopBar
import dev.mrsad.app.ui.theme.AmbientBackground
import dev.mrsad.app.ui.theme.MrsadTheme
import dev.mrsad.app.ui.theme.ObsidianSpectrum
import dev.mrsad.app.ui.theme.PageEnvironment

/**
 * OBSIDIAN SPECTRUM — MRSAD AI (Master Prompt §12).
 */
@Composable
fun AssistantScreen(
    container: AppContainer,
    settings: MrsadSettings,
) {
    val viewModel: AssistantViewModel = viewModel(key = "assistant", factory = AssistantViewModel.Factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val isCollapsed by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 28
        }
    }

    AmbientBackground(PageEnvironment.AI) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(bottom = LocalBottomBarClearance.current),
        ) {
            ObsidianCollapsingTopBar(
                title = "MRSAD AI",
                subtitle = "مربی محلی و آفلاین — بدون نیاز به اینترنت",
                collapsed = isCollapsed,
                spectralColor = ObsidianSpectrum.Lavender,
                leading = {
                    Box(
                        Modifier
                            .size(if (isCollapsed) 36.dp else 44.dp)
                            .clip(RoundedCornerShape(MrsadTokens.Radius.md))
                            .background(ObsidianSpectrum.DeepViolet.copy(alpha = 0.28f))
                            .border(1.dp, ObsidianSpectrum.Lavender.copy(alpha = 0.55f), RoundedCornerShape(MrsadTokens.Radius.md)),
                        contentAlignment = Alignment.Center,
                    ) {
                        MetalIcon(
                            MrsadIcons.Assistant,
                            accentOverride = ObsidianSpectrum.Lavender,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
                trailing = {
                    MrsadChip(text = "آفلاین", tint = ObsidianSpectrum.Aqua)
                },
            )

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = MrsadTokens.Spacing.md),
                verticalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.sm),
            ) {
                item { Spacer(Modifier.height(MrsadTokens.Spacing.xs)) }
                item { IntroBubble(state) }
                if (state.messages.isEmpty()) {
                    item { SuggestionRow(onSend = { viewModel.ask(it, settings.persianNumerals) }) }
                }
                items(state.messages) { message -> MessageBubble(message) }
                if (state.messages.isNotEmpty() && state.messages.last().role == Role.USER) {
                    item { Text("در حال تحلیل…", style = type.caption, color = colors.textMuted) }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MrsadTokens.Spacing.md, vertical = MrsadTokens.Spacing.sm),
            ) {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("سؤال خود را بنویسید…", style = type.body, color = colors.textMuted) },
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(MrsadTokens.Radius.lg))
                        .border(1.dp, ObsidianSpectrum.Lavender.copy(alpha = 0.45f), RoundedCornerShape(MrsadTokens.Radius.lg)),
                    textStyle = type.body,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (input.isNotBlank()) {
                            viewModel.ask(input.trim(), settings.persianNumerals)
                            input = ""
                        }
                    }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colors.surfaceHigh,
                        unfocusedContainerColor = colors.surface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    maxLines = 3,
                )
                Spacer(Modifier.width(MrsadTokens.Spacing.xs))
                MrsadPrimaryButton(
                    text = "ارسال",
                    icon = Icons.AutoMirrored.Outlined.Send,
                    accentColor = ObsidianSpectrum.Lavender,
                    onClick = {
                        if (input.isNotBlank()) {
                            viewModel.ask(input.trim(), settings.persianNumerals)
                            input = ""
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun IntroBubble(state: AssistantUiState) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    MrsadSurface(
        Modifier.fillMaxWidth(),
        accent = ObsidianSpectrum.Lavender,
    ) {
        Text("چطور می‌توانم کمک کنم؟", style = type.title, color = colors.textPrimary)
        Text(
            "من تحلیل‌های خودکار مرصاد را از روی داده‌های واقعی تمرین شما می‌سازم — کاملاً روی همین دستگاه. " +
                "می‌توانید دربارهٔ سرعت تایپ، دقت، برنامهٔ تمرین یا امکانات برنامه بپرسید.",
            style = type.body,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun SuggestionRow(onSend: (String) -> Unit) {
    val colors = MrsadTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.xs)) {
        listOf(
            "امروز چه تمرینی انجام بدهم؟",
            "وضعیت تایپ من چگونه است؟",
            "چگونه دقتم را بیشتر کنم؟",
            "مرصاد چه امکاناتی دارد؟",
        ).forEach { suggestion ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(MrsadTokens.Radius.pill))
                    .background(colors.surface)
                    .border(1.dp, ObsidianSpectrum.Lavender.copy(alpha = 0.42f), RoundedCornerShape(MrsadTokens.Radius.pill))
                    .clickable { onSend(suggestion) }
                    .padding(horizontal = MrsadTokens.Spacing.md, vertical = MrsadTokens.Spacing.xs),
            ) {
                Text(
                    suggestion,
                    style = MrsadTheme.type.label,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(message: Message) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    val isUser = message.role == Role.USER
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(MrsadTokens.Radius.md))
                .background(
                    if (isUser) {
                        ObsidianSpectrum.DeepViolet.copy(alpha = 0.28f)
                    } else {
                        colors.surfaceHigh
                    },
                )
                .border(
                    1.dp,
                    if (isUser) ObsidianSpectrum.Lavender else colors.border,
                    RoundedCornerShape(MrsadTokens.Radius.md),
                )
                .padding(MrsadTokens.Spacing.sm),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!isUser) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MetalIcon(
                            MrsadIcons.Assistant,
                            accentOverride = ObsidianSpectrum.Lavender,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "تحلیل محلی",
                            style = type.caption.copy(fontWeight = FontWeight.SemiBold),
                            color = ObsidianSpectrum.Lavender,
                        )
                    }
                }
                Text(message.text, style = type.body, color = colors.textPrimary)
            }
        }
    }
}
