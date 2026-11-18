package ir.aegis.feature.utilities

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.aegis.core.model.*
import ir.aegis.core.ui.*

@Composable
fun AdvancedUtilitiesScreen(
    repository: AegisRepository,
    reminderScheduler: ReminderScheduler,
    preferences: AppPreferences,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
    showBackButton: Boolean = true,
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        ModuleTopBar(
            module = AegisModule.UTILITIES,
            onBack = onBack,
            isFavorite = isFavorite,
            onToggleFavorite = onToggleFavorite,
            extraBadge = tr("12 OFFLINE SURVIVAL & FIELD TOOLS", "۱۲ ابزار تخصصی آفلاین"),
            showBackButton = showBackButton,
        )
        GlassTabRow(
            tabs = listOf(
                tr("Torch · Compass · GPS", "نور · قطب‌نما · GPS"),
                tr("Voice · Scanner", "صدا · اسکنر"),
                tr("Notes · Inventory", "یادداشت · انبار"),
                tr("Calc · Cal · Timer · Units", "محاسبه · تقویم · تایمر · تبدیل"),
            ),
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
        )
        Spacer(Modifier.height(10.dp))

        when (selectedTab) {
            0 -> SensorsAndFieldTab(repository = repository)
            1 -> VoiceAndScannerTab(repository = repository)
            2 -> NotesAndInventoryTab(repository = repository)
            3 -> CalculatorCalendarTimerUnitsTab(
                repository = repository,
                reminderScheduler = reminderScheduler,
                preferences = preferences,
            )
        }
    }
}
