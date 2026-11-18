package ir.aegis.core.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.aegis.core.model.AegisModule

@Composable
fun ObsidianBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalObsidianColors.current
    val pulse by rememberInfiniteTransition(label = "bg-orb").animateFloat(
        initialValue = 0.90f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orb-pulse",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(colors.canvasTop, colors.canvasMid, colors.canvasBottom),
                ),
            )
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(colors.orbCyan, Color.Transparent),
                        center = Offset(size.width * 0.18f, size.height * 0.10f),
                        radius = size.width * 0.72f * pulse,
                    ),
                    center = Offset(size.width * 0.18f, size.height * 0.10f),
                    radius = size.width * 0.72f * pulse,
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(colors.orbViolet, Color.Transparent),
                        center = Offset(size.width * 0.86f, size.height * 0.84f),
                        radius = size.width * 0.65f * pulse,
                    ),
                    center = Offset(size.width * 0.86f, size.height * 0.84f),
                    radius = size.width * 0.65f * pulse,
                )
            },
        content = content,
    )
}

@Composable
fun ObsidianGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    elevated: Boolean = false,
    enable3DTilt: Boolean = false,
    accentBorderColor: Color? = null,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalObsidianColors.current
    val shape = RoundedCornerShape(cornerRadius)
    val rimBrush = Brush.linearGradient(
        colors = listOf(
            accentBorderColor?.copy(alpha = 0.52f) ?: colors.glassRimStart,
            colors.glassRimEnd,
            accentBorderColor?.copy(alpha = 0.20f) ?: colors.glassRimEnd.copy(alpha = 0.30f),
        ),
    )

    val fillBrush = Brush.linearGradient(
        colors = listOf(
            (if (elevated) colors.glassElevated else colors.glassSurface),
            colors.glassSurface.copy(alpha = if (colors.isDark) 0.58f else 0.82f),
        ),
    )

    val baseModifier = modifier
        .clip(shape)
        .background(fillBrush)
        .border(width = 0.9.dp, brush = rimBrush, shape = shape)
        .drawBehind {
            drawLine(
                color = colors.glassTopSheen,
                start = Offset(size.width * 0.08f, 1.2f),
                end = Offset(size.width * 0.92f, 1.2f),
                strokeWidth = 1.5f,
            )
        }
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }

    Column(
        modifier = baseModifier.padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
fun Hero3DSecurityShield(
    readinessScore: Int,
    activeAlertsCount: Int,
    modifier: Modifier = Modifier,
) {
    val colors = LocalObsidianColors.current
    val ringColor = when {
        activeAlertsCount > 0 -> colors.accentCrimson
        readinessScore >= 75 -> colors.accentEmerald
        readinessScore >= 50 -> colors.accentCyan
        else -> colors.accentAmber
    }
    val sweep = (readinessScore.coerceIn(0, 100) / 100f) * 360f

    Box(
        modifier = modifier.size(112.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeW = 10.dp.toPx()
            val pad = strokeW / 2f + 4f
            val arcSize = Size(size.width - pad * 2f, size.height - pad * 2f)
            drawArc(
                color = ringColor.copy(alpha = 0.16f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(pad, pad),
                size = arcSize,
                style = Stroke(width = strokeW, cap = StrokeCap.Round),
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(ringColor, colors.accentCyan, ringColor)),
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(pad, pad),
                size = arcSize,
                style = Stroke(width = strokeW, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "$readinessScore",
                    style = TextStyle(
                        fontFamily = AegisFontFamily,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFeatureSettings = "tnum",
                    ),
                    color = colors.textPrimary,
                )
                Text(
                    text = "%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ringColor,
                    modifier = Modifier.padding(bottom = 5.dp, start = 1.dp),
                )
            }
            Text(
                text = tr("SHIELD", "شاخص امنیت"),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
fun BentoMetricTile(
    value: String,
    label: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalObsidianColors.current
    ObsidianGlassCard(
        modifier = modifier,
        cornerRadius = 22.dp,
        elevated = true,
        accentBorderColor = accent,
        onClick = onClick,
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = value,
            style = TextStyle(
                fontFamily = AegisFontFamily,
                fontSize = 30.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFeatureSettings = "tnum",
            ),
            color = colors.textPrimary,
            maxLines = 1,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun OfflineStatusPill(
    text: String = tr("OFFLINE · ZERO NETWORK", "آفلاین · بدون دسترسی شبکه"),
    isWarning: Boolean = false,
) {
    val colors = LocalObsidianColors.current
    val tone = if (isWarning) colors.accentAmber else colors.accentEmerald
    Surface(
        shape = RoundedCornerShape(50),
        color = tone.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.42f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(tone),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = tone,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    badge: String? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalObsidianColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        if (!badge.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = colors.accentCyan.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, colors.accentCyan.copy(alpha = 0.35f)),
            ) {
                Text(
                    text = badge,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.accentCyan,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipFlowGroup(
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = 8.dp,
    verticalSpacing: Dp = 8.dp,
    content: @Composable FlowRowScope.() -> Unit,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        content = content,
    )
}

@Composable
fun AegisFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = LocalObsidianColors.current.accentCyan,
) {
    val colors = LocalObsidianColors.current
    Surface(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 36.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) accent.copy(alpha = 0.22f) else colors.glassElevated.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, if (selected) accent else colors.divider),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) accent else colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun AiSafetyBanner(
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalObsidianColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = colors.accentAmber.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, colors.accentAmber.copy(alpha = 0.40f)),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = colors.accentAmber, modifier = Modifier.size(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = tr("LOCAL SECURITY ANALYSIS", "تحلیل امنیتی آفلاین"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.accentAmber,
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textPrimary,
                )
            }
        }
    }
}

@Composable
fun ModuleTopBar(
    module: AegisModule,
    onBack: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    extraBadge: String? = null,
    showBackButton: Boolean = true,
) {
    val colors = LocalObsidianColors.current
    val lang = LocalAppLanguage.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (showBackButton) {
            Surface(
                onClick = onBack,
                shape = RoundedCornerShape(14.dp),
                color = colors.glassElevated,
                border = BorderStroke(1.dp, colors.glassRimEnd),
            ) {
                Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(colors.accentCyan.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            AegisModuleIcon(module = module, tint = colors.accentCyan, size = 22.dp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = moduleTitle(module, lang),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = extraBadge ?: moduleSubtitle(module, lang),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Surface(
            onClick = onToggleFavorite,
            shape = RoundedCornerShape(14.dp),
            color = if (isFavorite) colors.accentAmber.copy(alpha = 0.18f) else colors.glassSurface,
            border = BorderStroke(1.dp, if (isFavorite) colors.accentAmber else colors.glassRimEnd),
        ) {
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = "Favorite",
                    tint = if (isFavorite) colors.accentAmber else colors.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
fun GlassTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalObsidianColors.current
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(tabs.size) { idx ->
            val selected = idx == selectedIndex
            Surface(
                onClick = { onSelect(idx) },
                modifier = Modifier.defaultMinSize(minHeight = 38.dp),
                shape = RoundedCornerShape(12.dp),
                color = if (selected) colors.accentCyan.copy(alpha = 0.22f) else colors.glassSurface,
                border = BorderStroke(1.dp, if (selected) colors.accentCyan else colors.glassRimEnd),
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = tabs[idx],
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) colors.accentCyan else colors.textSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun GlassButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = LocalObsidianColors.current.accentCyan,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = 42.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = accent.copy(alpha = 0.20f),
            contentColor = accent,
            disabledContainerColor = accent.copy(alpha = 0.08f),
            disabledContentColor = accent.copy(alpha = 0.45f),
        ),
        border = BorderStroke(1.dp, accent.copy(alpha = if (enabled) 0.58f else 0.2f)),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    val colors = LocalObsidianColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = colors.textSecondary) },
        singleLine = singleLine,
        minLines = minLines,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.accentCyan,
            unfocusedBorderColor = colors.divider,
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
            focusedContainerColor = colors.glassSurface.copy(alpha = 0.65f),
            unfocusedContainerColor = colors.glassSurface.copy(alpha = 0.45f),
        ),
    )
}
