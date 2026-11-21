package dev.hgallery.app.ui.placeholder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.hgallery.app.ui.theme.hgPalette

/**
 * Explicit, honest placeholder.
 *
 * A tab that is not implemented yet says so. It never pretends to be finished,
 * and it never shows invented content — both of which would make the build look
 * more complete than it is.
 */
@Composable
fun PlaceholderScreen(name: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "$name is not implemented yet",
            style = MaterialTheme.typography.bodyMedium,
            color = hgPalette.textSecondary,
        )
    }
}
