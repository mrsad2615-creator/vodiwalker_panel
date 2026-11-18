package ir.aegis.core.ai

import ir.aegis.core.model.FamilyMemberProfile
import ir.aegis.core.model.Incident
import ir.aegis.core.model.IncidentCategory
import ir.aegis.core.model.IncidentPriority
import ir.aegis.core.model.IncidentStatus
import ir.aegis.core.model.InventoryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAiEngineTest {

    @Test
    fun knowledgeSearchRanksBleedingAndEarthquakeGuidesAccurately() {
        val hits = LocalAiEngine.searchKnowledge("severe bleeding tourniquet wound")
        assertTrue(hits.isNotEmpty())
        assertEquals("fa-bleeding", hits.first().doc.id)

        val persianHits = LocalAiEngine.searchKnowledge("زلزله و پس لرزه")
        assertTrue(persianHits.isNotEmpty())
        assertEquals("dg-earthquake", persianHits.first().doc.id)
    }

    @Test
    fun incidentClassifierDetectsCategoryPriorityAndIncludesDisclaimer() {
        val fireResult = LocalAiEngine.classifyIncident(
            title = "Heavy smoke and flame in electrical room",
            description = "Smoke spreading fast near exit corridor, urgent evacuation needed",
        )
        assertEquals(IncidentCategory.FIRE, fireResult.suggestedCategory)
        assertTrue(fireResult.suggestedPriority == IncidentPriority.HIGH || fireResult.suggestedPriority == IncidentPriority.CRITICAL)
        assertTrue(fireResult.safetyDisclaimer.contains("Not verified", ignoreCase = true))
    }

    @Test
    fun duplicateDetectorFlagsSimilarNearbyReport() {
        val now = 1763400000000L
        val existing = listOf(
            Incident(
                id = "inc-1",
                title = "Gas leak smell near Building B",
                description = "Strong gas odor in ground floor stairwell",
                category = IncidentCategory.UTILITY_OUTAGE,
                priority = IncidentPriority.CRITICAL,
                status = IncidentStatus.OPEN,
                latitude = 35.7000,
                longitude = 51.3900,
                locationLabel = "Building B",
                createdAtMillis = now - 3600_000L,
                updatedAtMillis = now - 3600_000L,
            ),
        )
        val candidates = LocalAiEngine.findPotentialDuplicates(
            title = "Gas leak odor Building B stairwell",
            description = "Strong smell of natural gas on ground floor",
            category = IncidentCategory.UTILITY_OUTAGE,
            latitude = 35.7002,
            longitude = 51.3901,
            existingIncidents = existing,
            nowMillis = now,
        )
        assertTrue(candidates.isNotEmpty())
        assertEquals("inc-1", candidates.first().incident.id)
        assertTrue(candidates.first().similarityPercent >= 50)
    }

    @Test
    fun checklistGeneratorIncorporatesFamilyMedsAndMissingInventory() {
        val family = listOf(
            FamilyMemberProfile(
                id = "f1",
                fullName = "Sara",
                relation = "Child",
                ageGroup = "Child (7)",
                bloodType = "A+",
                allergies = "Peanuts",
                medications = "EpiPen",
                specialNeeds = "",
                phone = "",
                emergencyNote = "",
                updatedAtMillis = 1000L,
            ),
        )
        val inventory = listOf(
            InventoryItem("i1", "Bottled Water", "Water", 12.0, "L", 9.0, null, "Closet", 1000L),
        )
        val (checklist, items) = LocalAiEngine.generateChecklist(IncidentCategory.EARTHQUAKE, family, inventory)
        assertTrue(checklist.isAiGenerated)
        assertTrue(items.any { it.label.contains("EpiPen") })
        assertTrue(items.any { it.label.contains("Inventory gap alert") })
    }
}
