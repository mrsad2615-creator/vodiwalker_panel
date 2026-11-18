package ir.aegis.core.ai

import ir.aegis.core.model.DisasterGuide
import ir.aegis.core.model.FirstAidGuide

data class ReviewedKnowledgeDoc(
    val id: String,
    val title: String,
    val domain: String,
    val tags: List<String>,
    val content: String,
    val actionChecklist: List<String>,
    val reviewedSource: String,
)

object LocalKnowledgeBase {

    const val AI_SAFETY_NOTICE_EN =
        "AI Suggestion Only — Not verified emergency dispatch instructions. Cross-check critical actions against reviewed offline guides or local emergency responders."
    const val AI_SAFETY_NOTICE_FA =
        "صرفاً پیشنهاد هوش مصنوعی محلی — جایگزین دستورالعمل رسمی اورژانس نیست. در شرایط بحرانی به راهنماهای بازبینی‌شده و نیروهای امدادی تکیه کنید."

    val firstAidGuides: List<FirstAidGuide> = listOf(
        FirstAidGuide(
            id = "fa-bleeding",
            title = "Severe External Bleeding Control",
            category = "Trauma",
            urgency = "CRITICAL",
            summary = "Stop life-threatening arterial or venous bleeding rapidly using direct firm pressure.",
            steps = listOf(
                "Ensure personal scene safety and wear barrier gloves if available.",
                "Expose the wound and apply firm, continuous direct pressure with sterile gauze or clean cloth.",
                "If blood soaks through, stack fresh dressings on top — do not remove the first saturated pad.",
                "For severe limb hemorrhage uncontrolled by direct pressure, apply a commercial tourniquet 5–7 cm above the wound (not over a joint) and record exact application time.",
                "Keep the casualty warm, flat, and calm while activating emergency medical services.",
            ),
            doNotDo = listOf(
                "Do not remove embedded objects (stabilize around them instead).",
                "Do not loosen a properly placed tourniquet in the field.",
            ),
            reviewedSource = "Reviewed Offline Material · Red Cross / Stop The Bleed Field Standard (Bundled v1.0)",
        ),
        FirstAidGuide(
            id = "fa-cpr",
            title = "Adult CPR & Unresponsive Casualty",
            category = "Cardiac / Airway",
            urgency = "CRITICAL",
            summary = "Immediate compression-first resuscitation when an adult is unresponsive and not breathing normally.",
            steps = listOf(
                "Check scene safety, tap shoulders firmly, and shout to confirm responsiveness.",
                "Look for normal breathing for no more than 10 seconds (occasional gasping is not normal breathing).",
                "Call local emergency services or send a bystander with an AED immediately.",
                "Place heel of one hand on the center of the chest, interlock the other hand, and push hard and fast 5–6 cm deep at 100–120 compressions/min.",
                "Allow full chest recoil between compressions; switch rescuers every 2 minutes if available.",
            ),
            doNotDo = listOf(
                "Do not interrupt compressions for more than 10 seconds.",
                "Do not give food or water to an unresponsive casualty.",
            ),
            reviewedSource = "Reviewed Offline Material · ERC / AHA Basic Life Support Guidelines (Bundled v1.0)",
        ),
        FirstAidGuide(
            id = "fa-choking",
            title = "Severe Airway Obstruction (Choking)",
            category = "Airway",
            urgency = "CRITICAL",
            summary = "Alternate 5 sharp back blows and 5 abdominal thrusts for conscious choking adults.",
            steps = listOf(
                "Ask 'Are you choking?' If the person cannot speak, cough forcefully, or breathe, intervene immediately.",
                "Lean the person forward and deliver up to 5 firm back blows between the shoulder blades using the heel of your hand.",
                "If airway remains blocked, stand behind and perform up to 5 inward-and-upward abdominal thrusts above the navel.",
                "Repeat cycles of 5 back blows and 5 abdominal thrusts until cleared or casualty becomes unresponsive (start CPR if unresponsive).",
            ),
            doNotDo = listOf(
                "Do not perform blind finger sweeps in the mouth.",
                "Do not use abdominal thrusts on infants under 1 year (use infant back blows & chest thrusts).",
            ),
            reviewedSource = "Reviewed Offline Material · St John / IFRC First Aid Protocol (Bundled v1.0)",
        ),
        FirstAidGuide(
            id = "fa-burns",
            title = "Thermal & Scald Burns Care",
            category = "Burns",
            urgency = "HIGH",
            summary = "Cool thermal burns with clean running cool water for 20 minutes and cover loosely.",
            steps = listOf(
                "Stop the burning source and safely remove non-sticking jewelry or watches before swelling begins.",
                "Cool the burn immediately under clean cool running water (never ice) for a full 20 minutes.",
                "Cover loosely lengthwise with clean non-fluffy cling wrap or sterile non-adherent dressing.",
                "Seek urgent care for face, airway, hands, feet, groin, circumferential, chemical, or deep burns.",
            ),
            doNotDo = listOf(
                "Do not apply butter, toothpaste, oil, or ice directly to burned skin.",
                "Do not burst blisters or peel adhered clothing.",
            ),
            reviewedSource = "Reviewed Offline Material · WHO Emergency Burn Care Reference (Bundled v1.0)",
        ),
        FirstAidGuide(
            id = "fa-shock",
            title = "Hypovolemic / Trauma Shock Management",
            category = "Trauma",
            urgency = "HIGH",
            summary = "Preserve core perfusion and body heat while controlling underlying bleeding.",
            steps = listOf(
                "Control any external hemorrhage first.",
                "Lay the casualty flat on their back if no spinal, airway, or breathing contraindication exists.",
                "Insulate from cold ground underneath and cover with a thermal foil blanket or coat.",
                "Monitor breathing and consciousness continuously until medical handover.",
            ),
            doNotDo = listOf(
                "Do not give fluids by mouth if trauma surgery may be required or consciousness is reduced.",
            ),
            reviewedSource = "Reviewed Offline Material · IFRC Emergency Care Manual (Bundled v1.0)",
        ),
        FirstAidGuide(
            id = "fa-fracture",
            title = "Suspected Fracture & Immobilization",
            category = "Orthopedic",
            urgency = "MEDIUM",
            summary = "Stabilize the injured bone and joints above and below without forcing alignment.",
            steps = listOf(
                "Support the limb manually above and below the injury site to prevent movement.",
                "Pad and splint in the position found using rigid boards, rolled magazines, or triangular bandages.",
                "Check distal circulation (pulse, warmth, capillary refill) before and after splinting.",
                "If open bone protrudes, cover gently with sterile moist dressing without pushing bone back in.",
            ),
            doNotDo = listOf(
                "Do not attempt to straighten angulated fractures or push exposed bone back in.",
            ),
            reviewedSource = "Reviewed Offline Material · Wilderness & Urban First Aid Standard (Bundled v1.0)",
        ),
    )

    val disasterGuides: List<DisasterGuide> = listOf(
        DisasterGuide(
            id = "dg-earthquake",
            hazardType = "EARTHQUAKE",
            title = "Earthquake Response & Structural Safety",
            beforeSteps = listOf(
                "Anchor heavy bookshelves, water heaters, and tall cabinets to wall studs.",
                "Identify structural safe spots in each room away from windows and mirrors.",
                "Keep sturdy shoes, flashlight, whistle, and 72-hour go-bag next to beds.",
            ),
            duringSteps = listOf(
                "DROP onto hands and knees so shaking cannot knock you down.",
                "COVER head and neck beneath a sturdy table or against an interior load-bearing wall away from glass.",
                "HOLD ON until shaking stops completely; stay inside until falling debris risk passes.",
                "If outdoors, move away from buildings, overhead power lines, and brick walls.",
            ),
            afterSteps = listOf(
                "Expect strong aftershocks; wear sturdy shoes to protect against shattered glass.",
                "Check for gas leaks (smell/hiss) and shut off main gas valve only if leak suspected; do NOT use open flame.",
                "Use Offline Map to locate open-ground assembly points and avoid damaged bridges or elevators.",
            ),
            reviewedSource = "Reviewed Offline Material · FEMA / IIEES Seismic Safety Guide (Bundled v1.0)",
        ),
        DisasterGuide(
            id = "dg-fire",
            hazardType = "FIRE",
            title = "Structure Fire & Smoke Evacuation",
            beforeSteps = listOf(
                "Verify smoke detectors monthly and maintain ABC dry-chemical extinguishers along exit corridors.",
                "Map two exit paths from every bedroom and set a family outside rally point.",
            ),
            duringSteps = listOf(
                "Stay low beneath the toxic smoke layer where cooler breathable air remains.",
                "Test closed doors with the back of your hand before opening; if hot, use alternate exit or seal door gaps with wet cloth.",
                "Evacuate immediately without collecting valuables; close doors behind you to slow flame spread.",
                "If clothes catch fire: STOP, DROP to the ground, and ROLL covering your face.",
            ),
            afterSteps = listOf(
                "Account for every family member at the outside meeting point and never re-enter a burning structure.",
                "Seek medical evaluation for any soot around mouth/nose or smoke inhalation.",
            ),
            reviewedSource = "Reviewed Offline Material · NFPA Civilian Fire Survival Standard (Bundled v1.0)",
        ),
        DisasterGuide(
            id = "dg-flood",
            hazardType = "FLOOD",
            title = "Flash Flood & Urban Inundation",
            beforeSteps = listOf(
                "Store important documents in waterproof pouches and identify high-ground evacuation routes on Offline Map.",
                "Prepare clean drinking water reserves and backflow prevention if in basement zones.",
            ),
            duringSteps = listOf(
                "Move immediately to higher ground; never wait for water to rise around exits.",
                "Turn Around, Don't Drown: never walk through >15 cm of moving water or drive into flooded underpasses.",
                "Disconnect main electrical breakers only if dry and safe to reach; avoid downed lines near puddles.",
            ),
            afterSteps = listOf(
                "Treat all floodwater as biologically/chemically contaminated; boil or disinfect water before use.",
                "Check structural foundations and electrical panels before restoring power.",
            ),
            reviewedSource = "Reviewed Offline Material · UNDRR / Civil Defense Flood Protocol (Bundled v1.0)",
        ),
        DisasterGuide(
            id = "dg-blackout",
            hazardType = "UTILITY_OUTAGE",
            title = "Extended Power, Telecom & Water Outage",
            beforeSteps = listOf(
                "Maintain charged power banks, solar crank lantern, and 3L water per person per day.",
                "Store offline maps, emergency ID cards, and family rally protocols locally on device.",
            ),
            duringSteps = listOf(
                "Keep refrigerator/freezer doors closed (unopened fridge preserves food ~4 hours, full freezer ~48 hours).",
                "Never operate portable generators, charcoal grills, or camp stoves indoors due to lethal Carbon Monoxide.",
                "Switch phone to battery-saver + Airplane Mode when cell towers are down to preserve battery for SOS beacon & offline tools.",
            ),
            afterSteps = listOf(
                "Unplug sensitive electronics prior to surge restoration; discard perishable food held above 4°C over 2 hours.",
            ),
            reviewedSource = "Reviewed Offline Material · Civil Preparedness Grid Outage Handbook (Bundled v1.0)",
        ),
    )

    val knowledgeDocs: List<ReviewedKnowledgeDoc> by lazy {
        CyberKnowledgeBase.playbooks.map { p ->
            ReviewedKnowledgeDoc(
                id = p.id,
                title = "${p.titleFa} (${p.titleEn})",
                domain = "Cybersecurity & Anti-Hacking",
                tags = listOf("cyber", "security", "hack", "virus", "malware", "phishing", "trojan", "هک", "ویروس", "بدافزار", "فیشینگ", "جاسوسی", "امنیت", p.mitreTechnique),
                content = "${p.summaryFa}\n${p.summaryEn}\nIndicators: ${(p.indicatorsFa + p.indicatorsEn).joinToString(" | ")}",
                actionChecklist = p.stepsFa + p.stepsEn,
                reviewedSource = p.standardRef,
            )
        } + firstAidGuides.map { g ->
        val extraFaTags = when (g.id) {
            "fa-bleeding" -> listOf("bleeding", "tourniquet", "wound", "hemorrhage", "خونریزی", "پانسمان", "تورنیکت", "زخم")
            "fa-cpr" -> listOf("cpr", "cardiac", "resuscitation", "breathing", "احیا", "قلبی", "تنفس")
            "fa-choking" -> listOf("choking", "airway", "heimlich", "خفگی", "راه هوایی")
            "fa-burns" -> listOf("burn", "scald", "thermal", "سوختگی", "حرارتی")
            "fa-shock" -> listOf("shock", "perfusion", "hypothermia", "شوک", "تروما")
            "fa-fracture" -> listOf("fracture", "splint", "bone", "شکستگی", "آتل")
            else -> emptyList()
        }
        ReviewedKnowledgeDoc(
            id = g.id,
            title = g.title,
            domain = "First Aid · ${g.category}",
            tags = (listOf("first aid", g.category.lowercase(), g.urgency.lowercase(), "کمک های اولیه", "امداد") + extraFaTags).distinct(),
            content = "${g.summary} Steps: ${g.steps.joinToString(" ")} Avoid: ${g.doNotDo.joinToString(" ")}",
            actionChecklist = g.steps,
            reviewedSource = g.reviewedSource,
        )
    } + disasterGuides.map { d ->
        val hazardTags = when (d.id) {
            "dg-earthquake" -> listOf("earthquake", "seismic", "aftershock", "drop cover hold", "زلزله", "پس لرزه", "آوار")
            "dg-fire" -> listOf("fire", "smoke", "evacuation", "extinguisher", "آتش", "آتش سوزی", "حریق", "دود")
            "dg-flood" -> listOf("flood", "inundation", "water", "high ground", "سیل", "آبگرفتگی")
            "dg-blackout" -> listOf("blackout", "outage", "power", "generator", "قطع برق", "قطعی برق", "تاسیسات")
            else -> emptyList()
        }
        ReviewedKnowledgeDoc(
            id = d.id,
            title = d.title,
            domain = "Disaster Guide · ${d.hazardType}",
            tags = (listOf("disaster", d.hazardType.lowercase(), "بحران") + hazardTags).distinct(),
            content = "Before: ${d.beforeSteps.joinToString(" ")} During: ${d.duringSteps.joinToString(" ")} After: ${d.afterSteps.joinToString(" ")}",
            actionChecklist = d.duringSteps,
            reviewedSource = d.reviewedSource,
        )
    } + listOf(
        ReviewedKnowledgeDoc(
            id = "kb-evac-bag",
            title = "72-Hour Evacuation Go-Bag & Document Readiness",
            domain = "Evacuation Preparedness",
            tags = listOf("evacuation", "go bag", "kit", "inventory", "کیف نجات", "تخلیه اضطراری"),
            content = "Pack 3 liters of water per person per day, calorie-dense non-perishable food, trauma first aid kit, headlamp + spare batteries, N95 dust masks, emergency whistle, power bank, offline encrypted ID copies, cash in small bills, and essential prescriptions for 7 days.",
            actionChecklist = listOf(
                "Water: 3L per person per day (minimum 3 days)",
                "Trauma kit + tourniquet + personal 7-day medications",
                "Headlamp, whistle, power bank & spare charging cable",
                "Waterproof pouch with physical ID copies & emergency cash",
                "Sturdy walking shoes, thermal blanket & dust masks",
            ),
            reviewedSource = "Reviewed Offline Material · International 72-Hour Preparedness Standard (Bundled v1.0)",
        ),
        ReviewedKnowledgeDoc(
            id = "kb-digital-opsec",
            title = "Emergency Digital Safety & Offline Device Security",
            domain = "Digital Safety",
            tags = listOf("security", "pin", "encryption", "backup", "privacy", "امنیت دیجیتال", "رمزنگاری"),
            content = "Use a strong non-guessable PIN, disable lockscreen notification content previews, enable screenshot protection on sensitive vaults, maintain offline AES-256 encrypted backups with a separate recovery passphrase, and verify checksums before restoring.",
            actionChecklist = listOf(
                "Enable strong 6+ digit PIN and screen auto-lock",
                "Enable Sensitive Notification Redaction & FLAG_SECURE",
                "Export encrypted .aegisbackup copy to offline removable storage",
            ),
            reviewedSource = "Reviewed Offline Material · Mobile Operational Security Baseline (Bundled v1.0)",
        ),
        ReviewedKnowledgeDoc(
            id = "kb-co-poisoning",
            title = "Carbon Monoxide & Gas Leak Emergency",
            domain = "Toxic Hazard",
            tags = listOf("gas", "carbon monoxide", "leak", "smoke", "گاز", "گازگرفتگی", "نشت گاز"),
            content = "Symptoms of carbon monoxide include headache, dizziness, nausea, and confusion affecting multiple occupants simultaneously. Evacuate immediately to fresh outdoor air, never operate electrical switches or flames if natural gas smell is present, and call emergency responders from outside.",
            actionChecklist = listOf(
                "Evacuate all people & pets immediately to fresh outdoor air",
                "Do NOT flip light switches or ignite flames if natural gas odor exists",
                "Shut off main external valve only if safe without entering dense fumes",
                "Seek medical oxygen assessment for anyone with dizziness or headache",
            ),
            reviewedSource = "Reviewed Offline Material · Poison & Gas Emergency Protocol (Bundled v1.0)",
        ),
    ) }
}
