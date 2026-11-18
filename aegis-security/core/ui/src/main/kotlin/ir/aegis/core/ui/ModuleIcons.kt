package ir.aegis.core.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.aegis.core.model.AegisModule

fun moduleVectorIcon(module: AegisModule): ImageVector = when (module) {
    AegisModule.HOME -> Icons.Rounded.Shield
    AegisModule.PERSONAL -> Icons.Rounded.Key
    AegisModule.CRISIS -> Icons.Rounded.ReportProblem
    AegisModule.MAPS -> Icons.Rounded.Explore
    AegisModule.EMERGENCY -> Icons.Rounded.GppGood
    AegisModule.AI -> Icons.Rounded.AutoAwesome
    AegisModule.FAMILY -> Icons.Rounded.Devices
    AegisModule.ACADEMY -> Icons.Rounded.MenuBook
    AegisModule.ANALYTICS -> Icons.Rounded.Insights
    AegisModule.PRIVACY -> Icons.Rounded.EnhancedEncryption
    AegisModule.UTILITIES -> Icons.Rounded.Radar
}

@Composable
fun AegisModuleIcon(
    module: AegisModule,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    Icon(
        imageVector = moduleVectorIcon(module),
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size),
    )
}
