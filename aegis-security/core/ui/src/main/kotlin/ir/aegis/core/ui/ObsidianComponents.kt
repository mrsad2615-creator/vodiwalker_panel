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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.aegis.core.model.AegisModule
import ir.aegis.core.model.AppLanguage
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ObsidianBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalObsidianColors.current
    val pulse by rememberInfiniteTransition(label = "bg-orb").animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000, easing = FastOutSlowInEasing),
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
                        center = Offset(size.width * 0.20f, size.height * 0.12f),
                        radius = size.width * 0.72f * pulse,
                    ),
                    center = Offset(size.width * 0.20f, size.height * 0.12f),
                    radius = size.width * 0.72f * pulse,
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(colors.orbViolet, Color.Transparent),
                        center = Offset(size.width * 0.84f, size.height * 0.82f),
                        radius = size.width * 0.68f * pulse,
                    ),
                    center = Offset(size.width * 0.84f, size.height * 0.82f),
                    radius = size.width * 0.68f * pulse,
                )
            },
        content = content,
    )
}

@Composable
fun ObsidianGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
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
            accentBorderColor?.copy(alpha = 0.65f) ?: colors.glassRimStart,
            colors.glassRimEnd,
            accentBorderColor?.copy(alpha = 0.24f) ?: colors.glassRimEnd.copy(alpha = 0.35f),
        ),
    )

    val fillBrush = Brush.linearGradient(
        colors = listOf(
            (if (elevated) colors.glassElevated else colors.glassSurface),
            colors.glassSurface.copy(alpha = if (colors.isDark) 0.56f else 0.78f),
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
                strokeWidth = 1.6f,
            )
            drawLine(
                color = colors.divider.copy(alpha = 0.25f),
                start = Offset(size.width * 0.12f, size.height - 1.2f),
                end = Offset(size.width * 0.88f, size.height - 1.2f),
                strokeWidth = 1.0f,
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
    val orbitAngle by rememberInfiniteTransition(label = "hero-3d").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(16_000), repeatMode = RepeatMode.Restart),
        label = "orbit",
    )

    Box(
        modifier = modifier.size(100.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension * 0.42f

            drawCircle(
                color = colors.accentCyan.copy(alpha = 0.22f),
                radius = r * 1.06f,
                center = c,
                style = Stroke(width = 2.5f),
            )
            drawArc(
                color = if (activeAlertsCount > 0) colors.accentCrimson else colors.accentTeal,
                startAngle = orbitAngle,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = Offset(c.x - r * 1.06f, c.y - r * 1.06f),
                size = androidx.compose.ui.geometry.Size(r * 2.12f, r * 2.12f),
                style = Stroke(width = 4.5f, cap = StrokeCap.Round),
            )

            val shield = Path().apply {
                moveTo(c.x, c.y - r * 0.78f)
                lineTo(c.x + r * 0.64f, c.y - r * 0.48f)
                lineTo(c.x + r * 0.60f, c.y + r * 0.14f)
                quadraticTo(c.x + r * 0.48f, c.y + r * 0.62f, c.x, c.y + r * 0.84f)
                quadraticTo(c.x - r * 0.48f, c.y + r * 0.62f, c.x - r * 0.60f, c.y + r * 0.14f)
                lineTo(c.x - r * 0.64f, c.y - r * 0.48f)
                close()
            }
            drawPath(
                path = shield,
                brush = Brush.verticalGradient(
                    listOf(
                        colors.accentCyan.copy(alpha = 0.34f),
                        colors.accentViolet.copy(alpha = 0.16f),
                    ),
                ),
            )
            drawPath(
                path = shield,
                color = colors.accentCyan.copy(alpha = 0.85f),
                style = Stroke(width = 3f),
            )

            val rad = Math.toRadians(orbitAngle.toDouble())
            drawCircle(
                color = colors.accentEmerald,
                radius = 5f,
                center = Offset(
                    c.x + (r * 1.06f * cos(rad)).toFloat(),
                    c.y + (r * 1.06f * sin(rad)).toFloat(),
                ),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$readinessScore%",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = colors.textPrimary,
            )
            Text(
                text = tr("SECURE", "امنیت"),
                style = MaterialTheme.typography.labelSmall,
                color = colors.accentCyan,
            )
        }
    }
}

@Composable
fun OfflineStatusPill(
    text: String = tr("100% OFFLINE · ZERO NETWORK", "۱۰۰٪ آفلاین · بدون اینترنت"),
    isWarning: Boolean = false,
) {
    val colors = LocalObsidianColors.current
    val tone = if (isWarning) colors.accentAmber else colors.accentEmerald
    Surface(
        shape = RoundedCornerShape(50),
        color = tone.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, tone.copy(alpha = 0.48f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
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
        shape = RoundedCornerShape(14.dp),
        color = colors.accentAmber.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, colors.accentAmber.copy(alpha = 0.45f)),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = tr("AI SUGGESTION NOTICE · REVIEW REQUIRED", "هشدار پیشنهاد هوش مصنوعی · نیاز به بررسی"),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = colors.accentAmber,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
            )
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
                shape = RoundedCornerShape(12.dp),
                color = colors.glassElevated,
                border = BorderStroke(1.dp, colors.glassRimEnd),
            ) {
                Text(
                    text = if (lang == AppLanguage.FA) "→" else "←",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
            }
        }
        AegisModuleIcon(module = module, tint = colors.accentCyan, size = 26.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = moduleTitle(module, lang),
                style = MaterialTheme.typography.titleLarge,
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
            shape = RoundedCornerShape(12.dp),
            color = if (isFavorite) colors.accentAmber.copy(alpha = 0.2f) else colors.glassSurface,
            border = BorderStroke(1.dp, if (isFavorite) colors.accentAmber else colors.glassRimEnd),
        ) {
            Text(
                text = if (isFavorite) "★" else "☆",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                style = MaterialTheme.typography.titleMedium,
                color = if (isFavorite) colors.accentAmber else colors.textSecondary,
            )
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
                modifier = Modifier.defaultMinSize(minHeight = 40.dp),
                shape = RoundedCornerShape(12.dp),
                color = if (selected) colors.accentCyan.copy(alpha = 0.24f) else colors.glassSurface,
                border = BorderStroke(
                    1.dp,
                    if (selected) colors.accentCyan else colors.glassRimEnd,
                ),
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
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
        modifier = modifier.defaultMinSize(minHeight = 44.dp),
        shape = RoundedCornerShape(13.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = accent.copy(alpha = 0.22f),
            contentColor = accent,
            disabledContainerColor = accent.copy(alpha = 0.08f),
            disabledContentColor = accent.copy(alpha = 0.45f),
        ),
        border = BorderStroke(1.dp, accent.copy(alpha = if (enabled) 0.65f else 0.2f)),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
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
        shape = RoundedCornerShape(14.dp),
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
