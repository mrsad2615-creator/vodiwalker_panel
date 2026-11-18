package ir.aegis.core.ai

import ir.aegis.core.maps.GeoMath
import ir.aegis.core.model.ChecklistItem
import ir.aegis.core.model.EmergencyChecklist
import ir.aegis.core.model.FamilyMemberProfile
import ir.aegis.core.model.Incident
import ir.aegis.core.model.IncidentCategory
import ir.aegis.core.model.IncidentPriority
import ir.aegis.core.model.IncidentStatus
import ir.aegis.core.model.InventoryItem
import ir.aegis.core.model.AcademyProgressRecord
import java.util.UUID
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

data class KnowledgeSearchHit(
    val doc: ReviewedKnowledgeDoc,
    val score: Double,
    val matchedTerms: List<String>,
)

data class AiAssistantResponse(
    val query: String,
    val summaryAnswer: String,
    val suggestedSteps: List<String>,
    val citedSources: List<String>,
    val confidencePercent: Int,
    val safetyDisclaimer: String,
    val engineLabel: String,
)

data class IncidentClassificationResult(
    val suggestedCategory: IncidentCategory,
    val suggestedPriority: IncidentPriority,
    val confidencePercent: Int,
    val matchedSignals: List<String>,
    val safetyDisclaimer: String,
)

data class DuplicateCandidate(
    val incident: Incident,
    val similarityPercent: Int,
    val distanceMeters: Double?,
    val reasons: List<String>,
)

data class StatisticalInsightReport(
    val totalIncidents: Int,
    val openCount: Int,
    val criticalCount: Int,
    val resolutionRatePercent: Int,
    val topCategory: IncidentCategory?,
    val preparednessIndex: Int,
    val insights: List<String>,
    val safetyDisclaimer: String,
)

object LocalAiEngine {

    fun normalize(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text.lowercase()) {
            when (ch) {
                'ي', 'ى' -> sb.append('ی')
                'ك' -> sb.append('ک')
                '\u200c' -> sb.append(' ')
                in '۰'..'۹' -> sb.append(('0'.code + (ch.code - '۰'.code)).toChar())
                in '٠'..'٩' -> sb.append(('0'.code + (ch.code - '٠'.code)).toChar())
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun tokenize(text: String): List<String> = normalize(text)
        .split(Regex("[^\\p{L}\\p{Nd}]+"))
        .filter { it.length >= 2 }

    fun searchKnowledge(
        query: String,
        docs: List<ReviewedKnowledgeDoc> = LocalKnowledgeBase.knowledgeDocs,
        limit: Int = 5,
    ): List<KnowledgeSearchHit> {
        val qTokens = tokenize(query).distinct()
        if (qTokens.isEmpty()) return docs.take(limit).map { KnowledgeSearchHit(it, 1.0, emptyList()) }

        val nDocs = docs.size.coerceAtLeast(1)
        val docFreq = mutableMapOf<String, Int>()
        val docTokensList = docs.map { doc ->
            val tokens = tokenize("${doc.title} ${doc.domain} ${doc.tags.joinToString(" ")} ${doc.content}")
            tokens.distinct().forEach { t -> docFreq[t] = (docFreq[t] ?: 0) + 1 }
            tokens
        }

        val avgLen = docTokensList.map { it.size }.average().coerceAtLeast(1.0)
        val k1 = 1.5
        val b = 0.75

        return docs.mapIndexedNotNull { idx, doc ->
            val docTokens = docTokensList[idx]
            val titleNorm = normalize(doc.title)
            val tagNorm = normalize(doc.tags.joinToString(" "))
            val freqs = docTokens.groupingBy { it }.eachCount()
            var score = 0.0
            val matched = mutableListOf<String>()

            for (qt in qTokens) {
                val df = docFreq[qt] ?: 0
                val tf = freqs[qt] ?: 0
                if (tf > 0) {
                    val idf = ln(1.0 + (nDocs - df + 0.5) / (df + 0.5))
                    val tfNorm = (tf * (k1 + 1.0)) / (tf + k1 * (1.0 - b + b * (docTokens.size / avgLen)))
                    score += idf * tfNorm
                    matched += qt
                } else {
                    // Partial/prefix match for inflected English/Persian terms
                    val partial = docTokens.any { it.startsWith(qt) || (qt.length >= 4 && it.contains(qt)) }
                    if (partial) {
                        score += 0.65
                        matched += qt
                    }
                }
                if (titleNorm.contains(qt)) score += 1.6
                if (tagNorm.contains(qt)) score += 1.1
            }

            if (score > 0.0) KnowledgeSearchHit(doc, score, matched.distinct()) else null
        }.sortedByDescending { it.score }.take(limit)
    }

    fun answerQuery(
        query: String,
        localModelName: String = "",
        isPersian: Boolean = false,
    ): AiAssistantResponse {
        val hits = searchKnowledge(query, limit = 3)
        val disclaimer = if (isPersian) LocalKnowledgeBase.AI_SAFETY_NOTICE_FA else LocalKnowledgeBase.AI_SAFETY_NOTICE_EN
        val engineName = if (localModelName.isNotBlank()) {
            "Offline Hybrid Engine + Local Model ($localModelName)"
        } else {
            "Built-in Deterministic Offline Engine"
        }

        if (hits.isEmpty()) {
            return AiAssistantResponse(
                query = query,
                summaryAnswer = "No close match found in the offline reviewed library for \"$query\". Try keywords like bleeding, CPR, earthquake, fire, flood, blackout, gas leak, or go-bag.",
                suggestedSteps = listOf(
                    "Ensure personal immediate safety first.",
                    "Open Emergency Response Center to access Emergency Dialer or SOS Beacon.",
                    "Browse Preparedness Academy for full reviewed courses.",
                ),
                citedSources = emptyList(),
                confidencePercent = 18,
                safetyDisclaimer = disclaimer,
                engineLabel = engineName,
            )
        }

        val top = hits.first()
        val steps = hits.flatMap { it.doc.actionChecklist }.distinct().take(6)
        val sources = hits.map { "${it.doc.title} — ${it.doc.reviewedSource}" }.distinct()
        val confidence = ((top.score / (top.score + 2.5)) * 100.0).roundToInt().coerceIn(35, 94)
        val summary = buildString {
            append("Based on locally stored reviewed material (${top.doc.title}): ")
            append(top.doc.content.take(280))
            if (top.doc.content.length > 280) append("...")
        }

        return AiAssistantResponse(
            query = query,
            summaryAnswer = summary,
            suggestedSteps = steps,
            citedSources = sources,
            confidencePercent = confidence,
            safetyDisclaimer = disclaimer,
            engineLabel = engineName,
        )
    }

    private val categoryKeywords: Map<IncidentCategory, List<String>> = mapOf(
        IncidentCategory.MEDICAL to listOf(
            "bleed", "blood", "cpr", "breath", "unconscious", "fracture", "burn", "injury", "heart", "chok",
            "medical", "ambulance", "shock", "pain", "خونریزی", "بیهوش", "تنفس", "شکستگی", "سوختگی", "اورژانس", "مصدوم",
        ),
        IncidentCategory.FIRE to listOf(
            "fire", "smoke", "flame", "burn", "explosion", "spark", "extinguish", "آتش", "دود", "حریق", "انفجار", "شعله",
        ),
        IncidentCategory.EARTHQUAKE to listOf(
            "earthquake", "quake", "tremor", "aftershock", "seismic", "collapse", "rubble", "زلزله", "لرزش", "پس لرزه", "آوار",
        ),
        IncidentCategory.FLOOD to listOf(
            "flood", "water", "river", "rain", "inundation", "mudslide", "drown", "سیل", "آبگرفتگی", "طغیان", "باران",
        ),
        IncidentCategory.SECURITY_THREAT to listOf(
            "intruder", "theft", "robbery", "threat", "assault", "stalk", "breach", "suspicious", "weapon",
            "تهدید", "سرقت", "مزاحمت", "امنیتی", "نفوذ", "مشکوک",
        ),
        IncidentCategory.TRAFFIC_ACCIDENT to listOf(
            "car", "crash", "collision", "traffic", "vehicle", "highway", "motorcycle", "road",
            "تصادف", "خودرو", "جاده", "واژگونی", "ترافیک",
        ),
        IncidentCategory.UTILITY_OUTAGE to listOf(
            "power", "blackout", "electric", "gas", "leak", "outage", "internet", "pipe", "water cut",
            "برق", "قطعی", "گاز", "نشت", "آب", "تاسیسات",
        ),
        IncidentCategory.MISSING_PERSON to listOf(
            "missing", "lost", "child", "elder", "disappeared", "last seen", "search",
            "مفقود", "گمشده", "ناپدید", "جستجو",
        ),
    )

    private val criticalBoostWords = listOf(
        "critical", "severe", "unconscious", "not breathing", "heavy bleeding", "trapped", "collapse",
        "armed", "life-threatening", "explosion", "gas leak", "چند مصدوم", "بحرانی", "شدید", "خونریزی شدید", "گرفتار",
    )

    private val highBoostWords = listOf(
        "urgent", "fire", "smoke", "flood", "injured", "missing", "broken", "threat", "فوری", "آتش", "سیل", "مصدوم",
    )

    fun classifyIncident(
        title: String,
        description: String,
        isPersian: Boolean = false,
    ): IncidentClassificationResult {
        val norm = normalize("$title $description")
        val tokens = tokenize(norm)
        val scores = mutableMapOf<IncidentCategory, Int>()
        val signals = mutableListOf<String>()

        for ((cat, kws) in categoryKeywords) {
            var score = 0
            for (kw in kws) {
                if (norm.contains(kw) || tokens.any { it.startsWith(kw) }) {
                    score += if (kw.length >= 6) 3 else 2
                    signals += "${cat.name}: $kw"
                }
            }
            scores[cat] = score
        }

        val bestEntry = scores.maxByOrNull { it.value }
        val bestCategory = if (bestEntry != null && bestEntry.value > 0) bestEntry.key else IncidentCategory.SECURITY_THREAT
        val bestScore = bestEntry?.value ?: 0

        val priority = when {
            criticalBoostWords.any { norm.contains(it) } || bestScore >= 9 -> IncidentPriority.CRITICAL
            highBoostWords.any { norm.contains(it) } || bestScore >= 5 -> IncidentPriority.HIGH
            bestScore >= 2 -> IncidentPriority.MEDIUM
            else -> IncidentPriority.LOW
        }

        val totalScore = scores.values.sum().coerceAtLeast(1)
        val confidence = (((bestScore.toDouble() / totalScore) * 70.0) + (bestScore.coerceAtMost(6) * 5.0))
            .roundToInt()
            .coerceIn(25, 95)

        return IncidentClassificationResult(
            suggestedCategory = bestCategory,
            suggestedPriority = priority,
            confidencePercent = confidence,
            matchedSignals = signals.distinct().take(6),
            safetyDisclaimer = if (isPersian) LocalKnowledgeBase.AI_SAFETY_NOTICE_FA else LocalKnowledgeBase.AI_SAFETY_NOTICE_EN,
        )
    }

    fun findPotentialDuplicates(
        title: String,
        description: String,
        category: IncidentCategory,
        latitude: Double?,
        longitude: Double?,
        existingIncidents: List<Incident>,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<DuplicateCandidate> {
        val candText = normalize("$title $description")
        val candTrigrams = trigrams(candText)
        val candTokens = tokenize(candText).toSet()

        return existingIncidents
            .filter { !it.isArchived }
            .mapNotNull { inc ->
                val incText = normalize("${inc.title} ${inc.description}")
                val triSim = jaccard(candTrigrams, trigrams(incText))
                val tokSim = jaccard(candTokens, tokenize(incText).toSet())
                var combined = triSim * 0.55 + tokSim * 0.45
                val reasons = mutableListOf<String>()

                if (tokSim >= 0.25) reasons += "Shared keywords (${(tokSim * 100).roundToInt()}%)"
                if (inc.category == category) {
                    combined += 0.10
                    reasons += "Same category (${category.name})"
                }

                var distMeters: Double? = null
                val incLat = inc.latitude
                val incLon = inc.longitude
                if (latitude != null && longitude != null && incLat != null && incLon != null) {
                    val d = GeoMath.haversineMeters(latitude, longitude, incLat, incLon)
                    distMeters = d
                    if (d <= 500.0) {
                        combined += 0.18
                        reasons += "Nearby coordinates (${d.roundToInt()} m)"
                    }
                }

                if (abs(nowMillis - inc.createdAtMillis) <= 86_400_000L) {
                    combined += 0.05
                    reasons += "Within 24h window"
                }

                val pct = (combined.coerceIn(0.0, 1.0) * 100.0).roundToInt()
                if (pct >= 32) DuplicateCandidate(inc, pct, distMeters, reasons) else null
            }
            .sortedByDescending { it.similarityPercent }
            .take(5)
    }

    fun summarizeText(title: String, body: String, maxSentences: Int = 3): String {
        val rawSentences = body
            .split(Regex("(?<=[.!؟\\n])\\s+"))
            .map { it.trim() }
            .filter { it.length >= 12 }
        if (rawSentences.isEmpty()) return body.trim().ifBlank { title }

        val importantWords = criticalBoostWords + highBoostWords + listOf("location", "contact", "injured", "safe", "water", "shelter")
        val scored = rawSentences.mapIndexed { index, sentence ->
            val norm = normalize(sentence)
            var s = if (index == 0) 2.0 else 0.5
            s += importantWords.count { norm.contains(it) } * 1.8
            s += if (Regex("\\d").containsMatchIn(norm)) 1.0 else 0.0
            Triple(index, sentence, s)
        }.sortedByDescending { it.third }
            .take(maxSentences)
            .sortedBy { it.first }
            .map { it.second }

        return "$title — ${scored.joinToString(" ")}"
    }

    fun generateChecklist(
        category: IncidentCategory,
        familyMembers: List<FamilyMemberProfile>,
        inventoryItems: List<InventoryItem>,
        checklistId: String = "ai-chk-${UUID.randomUUID().toString().take(8)}",
        nowMillis: Long = System.currentTimeMillis(),
    ): Pair<EmergencyChecklist, List<ChecklistItem>> {
        val baseSteps = when (category) {
            IncidentCategory.EARTHQUAKE -> listOf(
                "Drop, Cover, Hold On until shaking stops",
                "Inspect structure & shut gas valve ONLY if leak smelled",
                "Put on sturdy shoes & grab 72h Go-Bag",
                "Proceed to open-ground assembly point on Offline Map",
            )
            IncidentCategory.FIRE -> listOf(
                "Alert occupants & crawl low below smoke",
                "Feel doors with back of hand before opening",
                "Evacuate without re-entering; meet at outside rally point",
                "Call fire services (125 / 112 / 911)",
            )
            IncidentCategory.FLOOD -> listOf(
                "Move to upper floor or high-ground assembly zone immediately",
                "Do not walk or drive through moving floodwater",
                "Grab waterproof document pouch & clean drinking water",
                "Turn off main breaker ONLY if panel area is dry",
            )
            IncidentCategory.MEDICAL -> listOf(
                "Check scene safety & Airway-Breathing-Circulation",
                "Apply direct pressure to severe bleeding",
                "Show Emergency Identity Card (blood type/allergies) to medics",
                "Contact primary ICE trusted contact via dialer/SMS",
            )
            IncidentCategory.SECURITY_THREAT -> listOf(
                "Move to locked interior safe room or pre-planned safe zone",
                "Activate privacy lock & silent emergency message template",
                "Record timeline observations & evidence checksums locally",
            )
            IncidentCategory.TRAFFIC_ACCIDENT -> listOf(
                "Turn on hazard warning & move to safe roadside if possible",
                "Do not move casualties with suspected spine injury unless fire threat",
                "Log coordinates & capture evidence attachments offline",
            )
            IncidentCategory.UTILITY_OUTAGE -> listOf(
                "Preserve phone battery; disable unused radios",
                "Never use fuel/charcoal heaters or generators indoors (CO risk)",
                "Track stored drinking water (3L/person/day) & medication cooling",
            )
            IncidentCategory.MISSING_PERSON -> listOf(
                "Record exact last-seen coordinates, time & clothing description",
                "Check family meeting points & notify emergency responders",
                "Share verified Family Emergency Card details locally",
            )
        }.toMutableList()

        if (familyMembers.any { it.medications.isNotBlank() }) {
            baseSteps += "Pack essential medications for: ${
                familyMembers.filter { it.medications.isNotBlank() }.joinToString(", ") { it.fullName }
            }"
        }
        if (familyMembers.any { it.specialNeeds.isNotBlank() || it.ageGroup.contains("Child", true) || it.ageGroup.contains("Elder", true) }) {
            baseSteps += "Verify mobility/infant/elder support gear for household members"
        }

        val invNames = inventoryItems.map { normalize(it.name) }
        val essentialChecks = listOf("water", "flashlight", "first aid", "power bank")
        val missing = essentialChecks.filter { req -> invNames.none { it.contains(req) } }
        if (missing.isNotEmpty()) {
            baseSteps += "Inventory gap alert — acquire or confirm: ${missing.joinToString(", ")}"
        }

        val checklist = EmergencyChecklist(
            id = checklistId,
            title = "AI Suggested: ${category.name.replace('_', ' ')} Response",
            category = category.name,
            isAiGenerated = true,
            createdAtMillis = nowMillis,
        )
        val items = baseSteps.mapIndexed { idx, label ->
            ChecklistItem(
                id = "${checklistId}-item-$idx",
                checklistId = checklistId,
                label = label,
                isChecked = false,
                sortOrder = idx,
            )
        }
        return checklist to items
    }

    fun analyzeStatistics(
        incidents: List<Incident>,
        inventory: List<InventoryItem>,
        familyMembers: List<FamilyMemberProfile>,
        academyProgress: List<AcademyProgressRecord>,
        nowMillis: Long = System.currentTimeMillis(),
        isPersian: Boolean = false,
    ): StatisticalInsightReport {
        val total = incidents.size
        val open = incidents.count { it.status == IncidentStatus.OPEN || it.status == IncidentStatus.INVESTIGATING }
        val critical = incidents.count { it.priority == IncidentPriority.CRITICAL && !it.isArchived }
        val resolved = incidents.count { it.status == IncidentStatus.RESOLVED || it.status == IncidentStatus.ARCHIVED }
        val resolutionRate = if (total == 0) 100 else ((resolved * 100.0) / total).roundToInt()
        val topCategory = incidents.groupingBy { it.category }.eachCount().maxByOrNull { it.value }?.key

        val lowStock = inventory.count { it.quantity < it.minimumThreshold }
        val expiringSoon = inventory.count { item -> item.expiryAtMillis?.let { exp -> exp - nowMillis < 30L * 86_400_000L } == true }
        val invScore = if (inventory.isEmpty()) 40 else (((inventory.size - lowStock).coerceAtLeast(0).toDouble() / inventory.size) * 35.0).roundToInt()
        val familyScore = if (familyMembers.isEmpty()) 10 else 25
        val academyScore = ((academyProgress.count { it.badgeEarned } / 6.0) * 25.0).roundToInt().coerceAtMost(25)
        val crisisPenalty = (critical * 7 + open * 3).coerceAtMost(20)
        val preparednessIndex = (15 + invScore + familyScore + academyScore - crisisPenalty).coerceIn(10, 100)

        val insights = buildList {
            if (total == 0) {
                add("No incidents recorded yet. Use Preparedness Academy and Emergency Inventory to maintain high readiness.")
            } else {
                add("Dominant incident category: ${topCategory?.name ?: "N/A"} across $total total local records ($open currently active).")
            }
            if (critical > 0) add("Attention: $critical CRITICAL priority incident(s) require active follow-up.")
            if (lowStock > 0) add("$lowStock inventory item(s) are below minimum readiness threshold.")
            if (expiringSoon > 0) add("$expiringSoon emergency supply item(s) expire within 30 days — schedule rotation.")
            if (academyProgress.count { it.badgeEarned } < 3) add("Complete at least 3 Preparedness Academy courses to raise readiness score.")
        }

        return StatisticalInsightReport(
            totalIncidents = total,
            openCount = open,
            criticalCount = critical,
            resolutionRatePercent = resolutionRate,
            topCategory = topCategory,
            preparednessIndex = preparednessIndex,
            insights = insights,
            safetyDisclaimer = if (isPersian) LocalKnowledgeBase.AI_SAFETY_NOTICE_FA else LocalKnowledgeBase.AI_SAFETY_NOTICE_EN,
        )
    }

    private fun trigrams(text: String): Set<String> {
        val padded = "  $text  "
        if (padded.length < 3) return setOf(padded)
        val out = HashSet<String>(padded.length)
        for (i in 0..padded.length - 3) {
            out += padded.substring(i, i + 3)
        }
        return out
    }

    private fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val inter = a.count { it in b }
        val union = a.size + b.size - inter
        return if (union == 0) 0.0 else inter.toDouble() / union.toDouble()
    }
}
