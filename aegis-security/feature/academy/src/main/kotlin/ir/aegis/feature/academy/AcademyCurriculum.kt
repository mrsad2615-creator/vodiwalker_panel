package ir.aegis.feature.academy

import ir.aegis.core.model.*

object AcademyCurriculum {

    val courses: List<AcademyCourse> = listOf(
        AcademyCourse(
            id = "course-first-aid",
            domain = AcademyDomain.FIRST_AID,
            title = "Trauma & Emergency First Aid",
            subtitle = "Reviewed severe bleeding control, airway recovery, shock prevention, and burn cooling.",
            estimatedMinutes = 16,
            lessons = listOf(
                AcademyLesson(
                    id = "fa-1",
                    title = "Direct Pressure & Wound Packing",
                    durationMinutes = 6,
                    summary = "Stop life-threatening external hemorrhage rapidly using continuous direct pressure.",
                    keySteps = listOf("Ensure scene safety.", "Apply firm continuous direct pressure with sterile gauze.", "Stack fresh dressings on top without lifting the first layer."),
                    warningNote = "Never remove embedded foreign objects in the field.",
                    reviewedSource = "Red Cross / Stop The Bleed Bundled Standard",
                ),
                AcademyLesson(
                    id = "fa-2",
                    title = "Recovery Position & Airway Check",
                    durationMinutes = 5,
                    summary = "Keep the airway open in unresponsive, breathing casualties without spinal trauma.",
                    keySteps = listOf("Check responsiveness and breathing for up to 10 seconds.", "Roll gently onto side keeping airway angled downward.", "Monitor breathing until handover."),
                    warningNote = "Avoid unnecessary movement if spinal trauma is suspected.",
                    reviewedSource = "ERC / IFRC First Aid Reference",
                ),
                AcademyLesson(
                    id = "fa-3",
                    title = "Thermal Burn First Response",
                    durationMinutes = 5,
                    summary = "Cool thermal burns with clean cool running water for 20 minutes.",
                    keySteps = listOf("Remove hot non-adhered items safely.", "Cool under running cool water for 20 minutes.", "Cover loosely with sterile non-stick wrap."),
                    warningNote = "Do not apply ice, butter, or toothpaste.",
                    reviewedSource = "WHO Emergency Burn Care Standard",
                ),
            ),
            quiz = listOf(
                AcademyQuizQuestion("faq-1", "What should you do if a pressure bandage becomes soaked with blood?", listOf("Remove it completely and wash", "Add fresh dressings on top while keeping firm pressure", "Apply butter or ointment"), 1, "Removing the first layer disrupts clotting; stack fresh gauze on top."),
                AcademyQuizQuestion("faq-2", "How long should a thermal burn be cooled under cool running water?", listOf("2 minutes", "20 minutes", "Apply ice directly for 1 minute"), 1, "Standard medical guidance specifies 20 minutes of cool running water."),
                AcademyQuizQuestion("faq-3", "For suspected spinal trauma in an unresponsive breathing person, what is the priority?", listOf("Keep head/neck aligned and avoid unnecessary movement", "Sit them upright immediately", "Give water to drink"), 0, "Preventing spinal movement avoids secondary neurological injury."),
            ),
            scenario = AcademyScenario(
                id = "fa-sc",
                title = "Broken Glass Laceration Drill",
                startNodeId = "n1",
                nodes = listOf(
                    AcademyScenarioNode(
                        id = "n1",
                        prompt = "A teammate suffers a deep forearm cut after shattered glass falls. Blood is flowing steadily and the scene is now safe.",
                        choices = listOf(
                            AcademyScenarioChoice("Apply immediate firm direct pressure with clean folded cloth and elevate slightly", "Correct. Direct pressure stops most extremity bleeding safely.", 100, null),
                            AcademyScenarioChoice("Wash the wound for 10 minutes before applying pressure", "Controlling active bleeding comes before irrigation.", 25, null),
                            AcademyScenarioChoice("Leave the wound uncovered so air clots it", "Delayed hemostasis risks hypovolemic shock.", 0, null),
                        ),
                    ),
                ),
            ),
        ),
        AcademyCourse(
            id = "course-fire",
            domain = AcademyDomain.FIRE_SAFETY,
            title = "Structural Fire & Smoke Survival",
            subtitle = "Smoke visibility survival, door thermal checks, grease fires, and egress discipline.",
            estimatedMinutes = 10,
            lessons = listOf(
                AcademyLesson("fs-1", "Low Crawl & Door Heat Test", 5, "Superheated smoke rises toward ceilings.", listOf("Crawl low below smoke.", "Check door heat with back of your hand.", "Close doors behind you."), "Never open a hot door.", "NFPA Fire Survival Standard"),
                AcademyLesson("fs-2", "Grease Fires & Stairwells", 5, "Never pour water onto burning kitchen oil or ride elevators.", listOf("Slide metal lid over pan fire.", "Turn off burner.", "Use enclosed stairwell only."), "Water causes explosive grease boil-over.", "Civil Defense Fire Manual"),
            ),
            quiz = listOf(
                AcademyQuizQuestion("fsq-1", "How do you test a closed door during a building fire?", listOf("Grip the knob tightly", "Touch the door lightly with the back of your hand", "Kick it open fast"), 1, "Back of hand detects heat without clutching spasms."),
                AcademyQuizQuestion("fsq-2", "What extinguishes a kitchen oil pan fire safely?", listOf("Cold water", "Sliding a metal lid over the pan", "Fanning with towel"), 1, "Smothering cuts oxygen safely."),
            ),
            scenario = AcademyScenario(
                id = "fs-sc",
                title = "Corridor Smoke Egress",
                startNodeId = "n1",
                nodes = listOf(
                    AcademyScenarioNode(
                        id = "n1",
                        prompt = "Hallway smoke alarm triggers on Floor 4. Thick dark smoke fills the upper half of the corridor.",
                        choices = listOf(
                            AcademyScenarioChoice("Stay low below smoke layer, move to enclosed stairwell, never use elevator", "Optimal response. Low crawl preserves breathable air.", 100, null),
                            AcademyScenarioChoice("Take the elevator straight to the lobby", "Elevators can stall or open directly onto fire floor.", 0, null),
                        ),
                    ),
                ),
            ),
        ),
        AcademyCourse(
            id = "course-earthquake",
            domain = AcademyDomain.EARTHQUAKE,
            title = "Seismic Response & Aftershocks",
            subtitle = "Drop-Cover-Hold On mechanics and post-quake utility safety.",
            estimatedMinutes = 11,
            lessons = listOf(
                AcademyLesson("eq-1", "Drop, Cover, and Hold On", 5, "Immediate posture saves lives against falling debris.", listOf("Drop to hands and knees.", "Shelter head/neck under sturdy furniture away from glass.", "Hold on until shaking stops."), "Do not stand in doorways or run during shaking.", "FEMA / IIEES Guide"),
                AcademyLesson("eq-2", "Utility Inspection & Aftershocks", 6, "Post-quake secondary hazards require calm inspection.", listOf("Put on sturdy shoes.", "Shut off main gas ONLY if smell/hiss detected.", "Walk to open ground rally point."), "Do not flip switches near suspected gas leak.", "Civil Seismic Protocol"),
            ),
            quiz = listOf(
                AcademyQuizQuestion("eqq-1", "During strong indoor shaking, what is safest?", listOf("Run down stairs mid-shaking", "Stand in any doorway", "Drop, Cover under a sturdy desk/table, Hold On"), 2, "Falling glass and objects pose the main indoor hazard."),
                AcademyQuizQuestion("eqq-2", "After shaking stops, you smell gas inside. What should you NOT do?", listOf("Open windows and exit", "Flip light switches or strike a lighter", "Evacuate outdoors"), 1, "Electrical arcs can ignite leaked gas."),
            ),
            scenario = AcademyScenario(
                id = "eq-sc",
                title = "Indoor Tremor Drill",
                startNodeId = "n1",
                nodes = listOf(
                    AcademyScenarioNode(
                        id = "n1",
                        prompt = "You are indoors near a tall bookshelf and large window when sudden strong P-wave shaking starts.",
                        choices = listOf(
                            AcademyScenarioChoice("Drop beside sturdy desk away from window, shield head/neck, wait until shaking stops", "Correct. Staying clear of glass prevents laceration.", 100, null),
                            AcademyScenarioChoice("Sprint to stand beside the glass balcony door", "Glass shatters violently under structural shear.", 10, null),
                        ),
                    ),
                ),
            ),
        ),
        AcademyCourse(
            id = "course-flood",
            domain = AcademyDomain.FLOOD,
            title = "Flash Flood & Water Safety",
            subtitle = "Hydrodynamic vehicle hazards, electrical water risks, and safe water purification.",
            estimatedMinutes = 10,
            lessons = listOf(
                AcademyLesson("fl-1", "Turn Around, Don't Drown", 5, "Moving water carries enormous force.", listOf("Never walk across >15 cm flowing water.", "Never drive into flooded underpasses.", "Climb to upper terrain."), "Vehicles lose traction in 30–60 cm moving water.", "UNDRR Flood Guide"),
                AcademyLesson("fl-2", "Water Purification & Electrical Risks", 5, "Prevent post-flood electrocution and illness.", listOf("Stay out of puddles near downed power lines.", "Boil suspect water 1–3 minutes before consumption."), "Assume standing floodwater is contaminated.", "WHO Emergency Water Protocol"),
            ),
            quiz = listOf(
                AcademyQuizQuestion("flq-1", "How much swift floodwater can sweep most passenger cars off a road?", listOf("30–60 cm (1–2 feet)", "Only 2 meters deep", "Vehicles never float"), 0, "Buoyancy drastically reduces tire traction."),
            ),
            scenario = AcademyScenario(
                id = "fl-sc",
                title = "Flooded Underpass Approach",
                startNodeId = "n1",
                nodes = listOf(
                    AcademyScenarioNode(
                        id = "n1",
                        prompt = "Heavy rain causes muddy water to surge across the underpass ahead of your route; depth is unknown.",
                        choices = listOf(
                            AcademyScenarioChoice("Stop, turn around, and move uphill to higher ground using alternate route", "Wise choice. Unknown depth hides washed-out roadbed.", 100, null),
                            AcademyScenarioChoice("Accelerate through quickly before water rises", "Stalling inside an underpass traps occupants.", 0, null),
                        ),
                    ),
                ),
            ),
        ),
        AcademyCourse(
            id = "course-travel",
            domain = AcademyDomain.TRAVEL_SAFETY,
            title = "Transit & Field Travel Readiness",
            subtitle = "Offline packing, situational awareness, and vehicle breakdown protocols.",
            estimatedMinutes = 9,
            lessons = listOf(
                AcademyLesson("tr-1", "Offline Route Redundancy", 5, "Never depend solely on cellular towers.", listOf("Download offline map package before travel.", "Share ETA and rally window.", "Carry physical ICE card & spare flashlight."), "Dead zones occur rapidly in mountains & tunnels.", "Field Transit Safety Guide"),
            ),
            quiz = listOf(
                AcademyQuizQuestion("trq-1", "Before entering an off-grid mountain pass, what is essential?", listOf("Rely solely on cloud maps", "Download offline map package, pack water/flashlight, set check-in time", "Set screen brightness 100%"), 1, "Offline map redundancy protects you when cellular signal drops."),
            ),
            scenario = AcademyScenario(
                id = "tr-sc",
                title = "No-Signal Mountain Breakdown",
                startNodeId = "n1",
                nodes = listOf(
                    AcademyScenarioNode(
                        id = "n1",
                        prompt = "Your vehicle loses traction on an unlit mountain road with zero cellular service as dusk falls.",
                        choices = listOf(
                            AcademyScenarioChoice("Stay visible off roadway, log offline grid coordinates, conserve battery", "Excellent field discipline.", 100, null),
                            AcademyScenarioChoice("Abandon vehicle immediately in darkness without warm gear", "Walking blind at night raises hypothermia risk.", 20, null),
                        ),
                    ),
                ),
            ),
        ),
        AcademyCourse(
            id = "course-digital",
            domain = AcademyDomain.DIGITAL_SAFETY,
            title = "Offline Digital & Operational Privacy",
            subtitle = "Device locking, AES-256 backups, notification privacy, and consent-based sharing.",
            estimatedMinutes = 11,
            lessons = listOf(
                AcademyLesson("dg-1", "Zero-Cloud Vault & Backup Integrity", 6, "Encrypt documents locally with PBKDF2 + AES-256-GCM.", listOf("Set high-entropy PIN & Vault passphrase.", "Verify SHA-256 checksum on backups.", "Enable FLAG_SECURE & redacted notifications."), "Never store recovery passphrases inside plain notes.", "Mobile OpSec Baseline"),
            ),
            quiz = listOf(
                AcademyQuizQuestion("dgq-1", "Why should encrypted backup files include a verification checksum / GCM tag?", listOf("To make files look longer", "To detect tampering or wrong passphrase decryption immediately", "To upload automatically"), 1, "AEAD ensures confidentiality and tamper integrity."),
            ),
            scenario = AcademyScenario(
                id = "dg-sc",
                title = "Consent-Based Neighbor Share",
                startNodeId = "n1",
                nodes = listOf(
                    AcademyScenarioNode(
                        id = "n1",
                        prompt = "You need to share your family rally point with a trusted neighbor locally while offline.",
                        choices = listOf(
                            AcademyScenarioChoice("Review selected fields, export consent-signed local packet, share directly offline", "Best practice: minimal scope & checksum verification.", 100, null),
                            AcademyScenarioChoice("Leave sensitive vault keys unencrypted in public notes", "Exposes private credentials.", 0, null),
                        ),
                    ),
                ),
            ),
        ),
    )
}
