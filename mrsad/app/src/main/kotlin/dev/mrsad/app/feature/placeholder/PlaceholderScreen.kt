package dev.mrsad.app.feature.placeholder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import dev.mrsad.app.ui.components.MrsadSecondaryButton
import dev.mrsad.app.ui.components.MrsadTokens
import dev.mrsad.app.ui.theme.AmbientBackground
import dev.mrsad.app.ui.theme.MrsadTheme
import dev.mrsad.app.ui.theme.PageEnvironment

/**
 * Honest roadmap placeholder for modules scheduled in a later phase.
 * It never pretends to be functional: it names the module and states what will
 * be implemented, with a real back action (spec §28, §31 rule 1–2).
 */
@Composable
fun PlaceholderScreen(
    title: String,
    environment: PageEnvironment,
    description: String,
    onBack: () -> Unit,
) {
    val colors = MrsadTheme.colors
    val type = MrsadTheme.type
    AmbientBackground(environment) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(MrsadTokens.Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(MrsadTokens.Spacing.sm, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = type.display, color = colors.textPrimary, textAlign = TextAlign.Center)
            Text(
                "این ماژول طبق نقشهٔ راه، در فاز بعدی توسعه داده می‌شود.",
                style = type.body,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            Text(description, style = type.body, color = colors.textMuted, textAlign = TextAlign.Center)
            MrsadSecondaryButton(
                text = "بازگشت",
                icon = Icons.Outlined.ArrowForward,
                onClick = onBack,
            )
        }
    }
}
