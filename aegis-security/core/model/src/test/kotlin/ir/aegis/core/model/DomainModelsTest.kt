package ir.aegis.core.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainModelsTest {

    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    @Test
    fun appPreferencesDefaultsProtectPrivacyOffline() {
        val prefs = AppPreferences()
        assertTrue(prefs.flagSecureEnabled)
        assertTrue(prefs.sensitiveNotificationRedacted)
        assertFalse(prefs.localDeviceSharingConsent)
        assertEquals(ThemeMode.DARK, prefs.themeMode)
        assertEquals(AppLanguage.EN, prefs.language)
    }

    @Test
    fun appPreferencesSerializationRoundTripPreservesWidgetsAndFallback() {
        val original = AppPreferences(
            themeMode = ThemeMode.LIGHT,
            language = AppLanguage.FA,
            pinEnabled = true,
            disabledWidgets = setOf(DashboardWidgetId.INCIDENT_SPARKLINE),
            fallbackLatitude = 32.6546,
            fallbackLongitude = 51.6680,
            localDeviceSharingConsent = true,
        )
        val decoded = json.decodeFromString<AppPreferences>(json.encodeToString(original))
        assertEquals(original, decoded)
    }

    @Test
    fun moduleRegistryCoversHomeAndTenSecurityModules() {
        assertEquals(11, AegisModule.entries.size)
        assertEquals(10, AegisModule.entries.count { it != AegisModule.HOME })
        assertEquals(AegisModule.entries.size, AegisModule.entries.map { it.route }.distinct().size)
    }

    @Test
    fun emergencyIncidentSupportsNullableCoordinatesAndArchiveFlag() {
        val inc = Incident(
            id = "inc-offline-1",
            title = "Road blockage",
            description = "Fallen tree blocking west exit",
            category = IncidentCategory.TRAFFIC_ACCIDENT,
            priority = IncidentPriority.HIGH,
            status = IncidentStatus.OPEN,
            latitude = null,
            longitude = null,
            locationLabel = "West Gate",
            createdAtMillis = 1000L,
            updatedAtMillis = 1000L,
        )
        assertFalse(inc.isArchived)
        val encoded = json.encodeToString(inc)
        val decoded = json.decodeFromString<Incident>(encoded)
        assertEquals(inc, decoded)
    }
}
