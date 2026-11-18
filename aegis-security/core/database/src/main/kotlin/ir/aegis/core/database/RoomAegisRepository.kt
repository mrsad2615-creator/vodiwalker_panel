package ir.aegis.core.database

import ir.aegis.core.maps.OfflineMapSeeds
import ir.aegis.core.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class RoomAegisRepository(private val db: AegisDatabase) : AegisRepository {
    private val dao = db.dao()

    override fun observeSecurityProfile(): Flow<SecurityProfile?> = dao.observeSecurityProfile().map { it?.toDomain() }
    override suspend fun upsertSecurityProfile(profile: SecurityProfile) = dao.upsertSecurityProfile(SecurityProfileEntity.fromDomain(profile))

    override fun observeEmergencyCard(): Flow<EmergencyIdentityCard?> = dao.observeEmergencyCard().map { it?.toDomain() }
    override suspend fun upsertEmergencyCard(card: EmergencyIdentityCard) = dao.upsertEmergencyCard(EmergencyCardEntity.fromDomain(card))

    override fun observeVaultItems(): Flow<List<VaultItem>> = dao.observeVaultItems().map { list -> list.map { it.toDomain() } }
    override suspend fun upsertVaultItem(item: VaultItem) = dao.upsertVaultItem(VaultItemEntity.fromDomain(item))
    override suspend fun deleteVaultItem(id: String) = dao.deleteVaultItem(id)

    override fun observeTrustedContacts(): Flow<List<TrustedContact>> = dao.observeTrustedContacts().map { list -> list.map { it.toDomain() } }
    override suspend fun upsertTrustedContact(contact: TrustedContact) = dao.upsertTrustedContact(TrustedContactEntity.fromDomain(contact))
    override suspend fun deleteTrustedContact(id: String) = dao.deleteTrustedContact(id)

    override fun observeSafetyPlans(): Flow<List<PersonalSafetyPlan>> = dao.observeSafetyPlans().map { list -> list.map { it.toDomain() } }
    override suspend fun upsertSafetyPlan(plan: PersonalSafetyPlan) = dao.upsertSafetyPlan(SafetyPlanEntity.fromDomain(plan))
    override suspend fun deleteSafetyPlan(id: String) = dao.deleteSafetyPlan(id)

    override fun observeReminders(): Flow<List<SecurityReminder>> = dao.observeReminders().map { list -> list.map { it.toDomain() } }
    override suspend fun upsertReminder(reminder: SecurityReminder) = dao.upsertReminder(SecurityReminderEntity.fromDomain(reminder))
    override suspend fun deleteReminder(id: String) = dao.deleteReminder(id)

    override fun observeJournalEntries(): Flow<List<PrivateJournalEntry>> = dao.observeJournalEntries().map { list -> list.map { it.toDomain() } }
    override suspend fun upsertJournalEntry(entry: PrivateJournalEntry) = dao.upsertJournalEntry(JournalEntryEntity.fromDomain(entry))
    override suspend fun deleteJournalEntry(id: String) = dao.deleteJournalEntry(id)

    override fun observeIncidents(includeArchived: Boolean): Flow<List<Incident>> =
        (if (includeArchived) dao.observeAllIncidents() else dao.observeActiveIncidents())
            .map { list -> list.map { it.toDomain() } }

    override suspend fun getIncidentById(id: String): Incident? = dao.getIncidentById(id)?.toDomain()

    override suspend fun createIncident(incident: Incident, initialNote: String) {
        db.runInTransaction {
            // Executed inside transaction via Room blocking bridge not needed; use sequential inside suspend
        }
        dao.upsertIncident(IncidentEntity.fromDomain(incident))
        dao.upsertTimelineEvent(
            IncidentTimelineEntity(
                id = "tl-${UUID.randomUUID().toString().take(8)}",
                incidentId = incident.id,
                eventType = "REGISTERED",
                summary = initialNote,
                createdAtMillis = incident.createdAtMillis,
            ),
        )
    }

    override suspend fun updateIncident(incident: Incident, timelineNote: String?) {
        dao.upsertIncident(IncidentEntity.fromDomain(incident))
        if (!timelineNote.isNullOrBlank()) {
            dao.upsertTimelineEvent(
                IncidentTimelineEntity(
                    id = "tl-${UUID.randomUUID().toString().take(8)}",
                    incidentId = incident.id,
                    eventType = incident.status.name,
                    summary = timelineNote,
                    createdAtMillis = System.currentTimeMillis(),
                ),
            )
        }
    }

    override suspend fun deleteIncident(id: String) = dao.deleteIncident(id)

    override fun observeIncidentTimeline(incidentId: String): Flow<List<IncidentTimelineEvent>> =
        dao.observeIncidentTimeline(incidentId).map { list -> list.map { it.toDomain() } }

    override suspend fun addTimelineEvent(event: IncidentTimelineEvent) =
        dao.upsertTimelineEvent(IncidentTimelineEntity.fromDomain(event))

    override fun observeIncidentAttachments(incidentId: String): Flow<List<IncidentAttachment>> =
        dao.observeIncidentAttachments(incidentId).map { list -> list.map { it.toDomain() } }

    override suspend fun addIncidentAttachment(attachment: IncidentAttachment) =
        dao.upsertIncidentAttachment(IncidentAttachmentEntity.fromDomain(attachment))

    override fun observeChecklists(): Flow<List<ChecklistWithItems>> =
        dao.observeChecklists().map { rows ->
            rows.map { row ->
                ChecklistWithItems(
                    checklist = row.checklist.toDomain(),
                    items = row.items.sortedBy { it.sortOrder }.map { it.toDomain() },
                )
            }
        }

    override suspend fun upsertChecklistWithItems(checklist: EmergencyChecklist, items: List<ChecklistItem>) {
        dao.upsertChecklist(ChecklistEntity.fromDomain(checklist))
        dao.upsertChecklistItems(items.map { ChecklistItemEntity.fromDomain(it) })
    }

    override suspend fun toggleChecklistItem(itemId: String, checked: Boolean) = dao.toggleChecklistItem(itemId, checked)
    override suspend fun deleteChecklist(checklistId: String) = dao.deleteChecklist(checklistId)

    override fun observeMapPackages(): Flow<List<OfflineMapPackage>> = dao.observeMapPackages().map { it.map { e -> e.toDomain() } }
    override suspend fun upsertMapPackage(pkg: OfflineMapPackage) = dao.upsertMapPackage(MapPackageEntity.fromDomain(pkg))

    override fun observeMapMarkers(): Flow<List<MapMarker>> = dao.observeMapMarkers().map { it.map { e -> e.toDomain() } }
    override suspend fun upsertMapMarker(marker: MapMarker) = dao.upsertMapMarker(MapMarkerEntity.fromDomain(marker))
    override suspend fun deleteMapMarker(id: String) = dao.deleteMapMarker(id)

    override fun observeSavedRoutes(): Flow<List<SavedRoute>> = dao.observeSavedRoutes().map { it.map { e -> e.toDomain() } }
    override suspend fun upsertSavedRoute(route: SavedRoute) = dao.upsertSavedRoute(SavedRouteEntity.fromDomain(route))
    override suspend fun deleteSavedRoute(id: String) = dao.deleteSavedRoute(id)

    override fun observeFamilyMembers(): Flow<List<FamilyMemberProfile>> = dao.observeFamilyMembers().map { it.map { e -> e.toDomain() } }
    override suspend fun upsertFamilyMember(member: FamilyMemberProfile) = dao.upsertFamilyMember(FamilyMemberEntity.fromDomain(member))
    override suspend fun deleteFamilyMember(id: String) = dao.deleteFamilyMember(id)

    override fun observeMeetingPoints(): Flow<List<FamilyMeetingPoint>> = dao.observeMeetingPoints().map { it.map { e -> e.toDomain() } }
    override suspend fun upsertMeetingPoint(point: FamilyMeetingPoint) = dao.upsertMeetingPoint(MeetingPointEntity.fromDomain(point))
    override suspend fun deleteMeetingPoint(id: String) = dao.deleteMeetingPoint(id)

    override fun observeFamilyPlan(): Flow<FamilyPlan?> = dao.observeFamilyPlan().map { it?.toDomain() }
    override suspend fun upsertFamilyPlan(plan: FamilyPlan) = dao.upsertFamilyPlan(FamilyPlanEntity.fromDomain(plan))

    override fun observeAcademyProgress(): Flow<List<AcademyProgressRecord>> = dao.observeAcademyProgress().map { it.map { e -> e.toDomain() } }
    override suspend fun upsertAcademyProgress(progress: AcademyProgressRecord) = dao.upsertAcademyProgress(AcademyProgressEntity.fromDomain(progress))

    override fun observeInventoryItems(): Flow<List<InventoryItem>> = dao.observeInventoryItems().map { it.map { e -> e.toDomain() } }
    override suspend fun upsertInventoryItem(item: InventoryItem) = dao.upsertInventoryItem(InventoryItemEntity.fromDomain(item))
    override suspend fun deleteInventoryItem(id: String) = dao.deleteInventoryItem(id)

    override fun observeOfflineNotes(): Flow<List<OfflineNote>> = dao.observeOfflineNotes().map { it.map { e -> e.toDomain() } }
    override suspend fun upsertOfflineNote(note: OfflineNote) = dao.upsertOfflineNote(OfflineNoteEntity.fromDomain(note))
    override suspend fun deleteOfflineNote(id: String) = dao.deleteOfflineNote(id)

    override fun observeVoiceNotes(): Flow<List<VoiceNote>> = dao.observeVoiceNotes().map { it.map { e -> e.toDomain() } }
    override suspend fun upsertVoiceNote(note: VoiceNote) = dao.upsertVoiceNote(VoiceNoteEntity.fromDomain(note))
    override suspend fun deleteVoiceNote(id: String) = dao.deleteVoiceNote(id)

    override fun observeAuditLogs(): Flow<List<ActivityAuditLog>> = dao.observeAuditLogs().map { it.map { e -> e.toDomain() } }

    override suspend fun logActivity(actionType: String, moduleName: String, summary: String) {
        dao.insertAuditLog(
            AuditLogEntity(
                id = "log-${UUID.randomUUID().toString().take(10)}",
                actionType = actionType,
                moduleName = moduleName,
                summary = summary,
                timestampMillis = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun clearAuditLogs() = dao.clearAuditLogs()

    override suspend fun exportFullSnapshot(preferences: AppPreferences): FullBackupSnapshot {
        return FullBackupSnapshot(
            exportedAtMillis = System.currentTimeMillis(),
            preferences = preferences,
            securityProfile = dao.getSecurityProfileOnce()?.toDomain(),
            emergencyCard = dao.getEmergencyCardOnce()?.toDomain(),
            vaultItems = dao.getVaultItemsOnce().map { it.toDomain() },
            trustedContacts = dao.getTrustedContactsOnce().map { it.toDomain() },
            safetyPlans = dao.getSafetyPlansOnce().map { it.toDomain() },
            reminders = dao.getRemindersOnce().map { it.toDomain() },
            journalEntries = dao.getJournalEntriesOnce().map { it.toDomain() },
            incidents = dao.getIncidentsOnce().map { it.toDomain() },
            timelineEvents = dao.getAllTimelineOnce().map { it.toDomain() },
            attachments = dao.getAllAttachmentsOnce().map { it.toDomain() },
            checklists = dao.getChecklistsOnce().map { ChecklistWithItems(it.checklist.toDomain(), it.items.map { i -> i.toDomain() }) },
            mapPackages = dao.getMapPackagesOnce().map { it.toDomain() },
            mapMarkers = dao.getMapMarkersOnce().map { it.toDomain() },
            savedRoutes = dao.getSavedRoutesOnce().map { it.toDomain() },
            familyMembers = dao.getFamilyMembersOnce().map { it.toDomain() },
            meetingPoints = dao.getMeetingPointsOnce().map { it.toDomain() },
            familyPlan = dao.getFamilyPlanOnce()?.toDomain(),
            academyProgress = dao.getAcademyProgressOnce().map { it.toDomain() },
            inventoryItems = dao.getInventoryItemsOnce().map { it.toDomain() },
            offlineNotes = dao.getOfflineNotesOnce().map { it.toDomain() },
            voiceNotes = dao.getVoiceNotesOnce().map { it.toDomain() },
            auditLogs = dao.getAuditLogsOnce().map { it.toDomain() },
        )
    }

    override suspend fun restoreFullSnapshot(snapshot: FullBackupSnapshot) {
        db.runInTransaction {
            db.clearAllTables()
        }
        snapshot.securityProfile?.let { dao.upsertSecurityProfile(SecurityProfileEntity.fromDomain(it)) }
        snapshot.emergencyCard?.let { dao.upsertEmergencyCard(EmergencyCardEntity.fromDomain(it)) }
        snapshot.vaultItems.forEach { dao.upsertVaultItem(VaultItemEntity.fromDomain(it)) }
        snapshot.trustedContacts.forEach { dao.upsertTrustedContact(TrustedContactEntity.fromDomain(it)) }
        snapshot.safetyPlans.forEach { dao.upsertSafetyPlan(SafetyPlanEntity.fromDomain(it)) }
        snapshot.reminders.forEach { dao.upsertReminder(SecurityReminderEntity.fromDomain(it)) }
        snapshot.journalEntries.forEach { dao.upsertJournalEntry(JournalEntryEntity.fromDomain(it)) }
        snapshot.incidents.forEach { dao.upsertIncident(IncidentEntity.fromDomain(it)) }
        snapshot.timelineEvents.forEach { dao.upsertTimelineEvent(IncidentTimelineEntity.fromDomain(it)) }
        snapshot.attachments.forEach { dao.upsertIncidentAttachment(IncidentAttachmentEntity.fromDomain(it)) }
        snapshot.checklists.forEach { pair ->
            dao.upsertChecklist(ChecklistEntity.fromDomain(pair.checklist))
            dao.upsertChecklistItems(pair.items.map { ChecklistItemEntity.fromDomain(it) })
        }
        snapshot.mapPackages.forEach { dao.upsertMapPackage(MapPackageEntity.fromDomain(it)) }
        snapshot.mapMarkers.forEach { dao.upsertMapMarker(MapMarkerEntity.fromDomain(it)) }
        snapshot.savedRoutes.forEach { dao.upsertSavedRoute(SavedRouteEntity.fromDomain(it)) }
        snapshot.familyMembers.forEach { dao.upsertFamilyMember(FamilyMemberEntity.fromDomain(it)) }
        snapshot.meetingPoints.forEach { dao.upsertMeetingPoint(MeetingPointEntity.fromDomain(it)) }
        snapshot.familyPlan?.let { dao.upsertFamilyPlan(FamilyPlanEntity.fromDomain(it)) }
        snapshot.academyProgress.forEach { dao.upsertAcademyProgress(AcademyProgressEntity.fromDomain(it)) }
        snapshot.inventoryItems.forEach { dao.upsertInventoryItem(InventoryItemEntity.fromDomain(it)) }
        snapshot.offlineNotes.forEach { dao.upsertOfflineNote(OfflineNoteEntity.fromDomain(it)) }
        snapshot.voiceNotes.forEach { dao.upsertVoiceNote(VoiceNoteEntity.fromDomain(it)) }
        snapshot.auditLogs.forEach { dao.insertAuditLog(AuditLogEntity.fromDomain(it)) }
        logActivity("RESTORE", "Privacy & Backup", "Restored encrypted offline snapshot (${snapshot.incidents.size} incidents, ${snapshot.vaultItems.size} vault items)")
    }

    override suspend fun panicWipeSensitiveData() {
        dao.clearVaultItems()
        dao.clearJournalEntries()
        logActivity("PANIC_WIPE", "Privacy", "Sensitive encrypted vault and private journal wiped locally")
    }

    suspend fun ensureSeeded() {
        if (dao.countMapPackages() > 0) return
        val now = System.currentTimeMillis()
        dao.upsertMapPackages(OfflineMapSeeds.bundledPackages().map { MapPackageEntity.fromDomain(it) })
        dao.upsertMapMarkers(OfflineMapSeeds.bundledMarkers().map { MapMarkerEntity.fromDomain(it) })
        dao.upsertSavedRoutes(OfflineMapSeeds.bundledRoutes().map { SavedRouteEntity.fromDomain(it) })

        dao.upsertSecurityProfile(
            SecurityProfileEntity.fromDomain(
                SecurityProfile(
                    id = "primary",
                    fullName = "Aegis Local Guardian",
                    aliasOrCallsign = "UNIT-01",
                    homeRegionLabel = "Central District (Offline Anchor)",
                    homeLatitude = 35.6892,
                    homeLongitude = 51.3890,
                    safeCheckInCode = "GREEN-7",
                    duressCode = "AMBER-9",
                    emergencyNote = "Offline Emergency Profile — review & customize with your personal details.",
                    updatedAtMillis = now,
                ),
            ),
        )
        dao.upsertEmergencyCard(
            EmergencyCardEntity.fromDomain(
                EmergencyIdentityCard(
                    id = "primary-card",
                    fullName = "Aegis Local Guardian",
                    dateOfBirth = "1994-05-12",
                    bloodType = "O+",
                    allergies = "None reported (Edit in Personal Security)",
                    medications = "None",
                    medicalConditions = "None",
                    primaryIceContactName = "Emergency Coordinator",
                    primaryIceContactPhone = "112",
                    organDonor = false,
                    languagesSpoken = "English, Persian",
                    insuranceOrIdNumber = "LOCAL-ICE-001",
                    updatedAtMillis = now,
                ),
            ),
        )
        dao.upsertTrustedContact(
            TrustedContactEntity.fromDomain(
                TrustedContact(
                    id = "tc-starter-1",
                    name = "Civil Emergency Dispatch",
                    relation = "Public Emergency",
                    phone = "112",
                    alternateChannel = "Local UHF / SMS",
                    addressOrZone = "Regional Hub",
                    priorityRank = 1,
                    canReceiveSosMessage = true,
                    notes = "Standard universal emergency number (offline dialer)",
                    createdAtMillis = now,
                ),
            ),
        )
        dao.upsertSafetyPlan(
            SafetyPlanEntity.fromDomain(
                PersonalSafetyPlan(
                    id = "sp-starter-eq",
                    title = "72-Hour Urban Seismic Response Plan",
                    scenarioType = "EARTHQUAKE",
                    triggerConditions = "Strong ground shaking > 5 seconds or structural alarm",
                    actionSteps = listOf(
                        "Drop, Cover under sturdy desk, Hold On until shaking stops",
                        "Wear sturdy shoes & grab flashlight + Go-Bag beside exit",
                        "Avoid elevators; check gas odor before shutting main valve",
                        "Walk via saved Primary Evacuation Corridor to Open Assembly Point",
                    ),
                    primarySafeLocation = "Laleh Park Open Assembly Shelter",
                    backupSafeLocation = "South Underground Reinforced Shelter",
                    updatedAtMillis = now,
                ),
            ),
        )
        dao.upsertFamilyPlan(
            FamilyPlanEntity.fromDomain(
                FamilyPlan(
                    id = "family-plan-main",
                    planTitle = "Household Reunification & Offline Comms Plan",
                    evacuationProtocol = "If separated during an urban emergency, walk to Primary Meeting Point within 45 minutes or wait at Secondary Point if corridor blocked.",
                    communicationProtocol = "Use concise SMS with coordinates + check-in status code; preserve phone battery in Airplane Mode between check-ins.",
                    outOfAreaContactName = "Out-of-Region Relay Contact",
                    outOfAreaContactPhone = "+49 911 000000",
                    rallyWindowMinutes = 45,
                    updatedAtMillis = now,
                ),
            ),
        )
        dao.upsertMeetingPoint(
            MeetingPointEntity.fromDomain(
                FamilyMeetingPoint(
                    id = "mp-primary",
                    name = "Primary Park Open Assembly Zone",
                    isPrimary = true,
                    latitude = 35.7106,
                    longitude = 51.3919,
                    addressNote = "Central Boulevard North Gate",
                    landmarkNote = "Open plaza away from overhead wires & glass towers",
                    instructions = "Wait near main fountain north side; leave chalk note if moving to backup.",
                ),
            ),
        )
        dao.upsertMeetingPoint(
            MeetingPointEntity.fromDomain(
                FamilyMeetingPoint(
                    id = "mp-backup",
                    name = "Secondary Reinforced Shelter Hub",
                    isPrimary = false,
                    latitude = 35.6762,
                    longitude = 51.3678,
                    addressNote = "South Sector Community Hall",
                    landmarkNote = "Concrete underground entrance",
                    instructions = "Use if primary assembly zone is obstructed or severe storm hits.",
                ),
            ),
        )

        val starterChecklists = listOf(
            EmergencyChecklist("chk-72h", "72-Hour Go-Bag Readiness Checklist", "EVACUATION", null, false, now) to listOf(
                ChecklistItem("ci-1", "chk-72h", "Drinking water (3L/person/day) — verified", true, 0),
                ChecklistItem("ci-2", "chk-72h", "Trauma first-aid kit & pressure dressings", true, 1),
                ChecklistItem("ci-3", "chk-72h", "Headlamp + spare lithium cells", false, 2),
                ChecklistItem("ci-4", "chk-72h", "Encrypted ID copies & emergency cash", false, 3),
                ChecklistItem("ci-5", "chk-72h", "Power bank 20,000 mAh charged to 100%", true, 4),
            ),
            EmergencyChecklist("chk-fire", "Home Fire & Gas Immediate Check", "FIRE", null, false, now) to listOf(
                ChecklistItem("ci-6", "chk-fire", "Test bedroom & corridor smoke alarms", true, 0),
                ChecklistItem("ci-7", "chk-fire", "Verify ABC extinguisher pressure gauge in green", false, 1),
                ChecklistItem("ci-8", "chk-fire", "Keep main gas shutoff wrench accessible", true, 2),
            ),
        )
        starterChecklists.forEach { (chk, items) ->
            dao.upsertChecklist(ChecklistEntity.fromDomain(chk))
            dao.upsertChecklistItems(items.map { ChecklistItemEntity.fromDomain(it) })
        }

        val starterInventory = listOf(
            InventoryItem("inv-water", "Potable Drinking Water", "Water", 18.0, "Liters", 12.0, now + 180L * 86_400_000L, "Hallway Closet", now),
            InventoryItem("inv-kit", "Trauma First Aid + Tourniquet", "Medical", 1.0, "Kit", 1.0, now + 360L * 86_400_000L, "Go-Bag Front Pocket", now),
            InventoryItem("inv-light", "LED Tactical Flashlight + Spare Cells", "Power & Light", 2.0, "Units", 1.0, null, "Bedside & Go-Bag", now),
            InventoryItem("inv-bank", "20,000 mAh Power Bank", "Power & Light", 1.0, "Unit", 1.0, null, "Entryway Shelf", now),
        )
        starterInventory.forEach { dao.upsertInventoryItem(InventoryItemEntity.fromDomain(it)) }

        val starterIncidents = listOf(
            Incident(
                id = "inc-sample-1",
                title = "Grid Outage Drill — Sector 2 Transformer",
                description = "Sample offline readiness incident: simulated 6-hour evening power outage drill to test backup lighting and offline emergency navigation.",
                category = IncidentCategory.UTILITY_OUTAGE,
                priority = IncidentPriority.MEDIUM,
                status = IncidentStatus.CONTAINED,
                latitude = 35.6941,
                longitude = 51.3755,
                locationLabel = "Sector 2 Drill Zone",
                aiSuggestedCategory = "UTILITY_OUTAGE",
                createdAtMillis = now - 86_400_000L * 3,
                updatedAtMillis = now - 86_400_000L * 2,
            ),
            Incident(
                id = "inc-sample-2",
                title = "Stairwell Smoke Alarm False Trigger Inspection",
                description = "Sample historical log: inspected stairwell detector battery and cleared dust sensor; documented evacuation corridor timing.",
                category = IncidentCategory.FIRE,
                priority = IncidentPriority.LOW,
                status = IncidentStatus.RESOLVED,
                latitude = 35.7024,
                longitude = 51.3912,
                locationLabel = "North Stairwell",
                aiSuggestedCategory = "FIRE",
                createdAtMillis = now - 86_400_000L * 8,
                updatedAtMillis = now - 86_400_000L * 7,
            ),
        )
        starterIncidents.forEach { inc ->
            dao.upsertIncident(IncidentEntity.fromDomain(inc))
            dao.upsertTimelineEvent(
                IncidentTimelineEntity(
                    id = "tl-seed-${inc.id}",
                    incidentId = inc.id,
                    eventType = "SEEDED_DRILL",
                    summary = "Offline starter drill record initialized",
                    createdAtMillis = inc.createdAtMillis,
                ),
            )
        }

        dao.upsertOfflineNote(
            OfflineNoteEntity(
                id = "note-seed-1",
                title = "Legacy Alert Baseline → Full Security Ecosystem",
                body = "Quick SOS Alert & Emergency Beacon remain one tap away while Personal Security, Crisis Management, Offline Maps, Local AI, Family Safety, Preparedness Academy, Analytics, Privacy Vault & Utilities all operate 100% offline.",
                category = "System",
                isPinned = true,
                updatedAtMillis = now,
            ),
        )
        logActivity("SYSTEM_INIT", "Core", "Initialized 100% offline database & bundled emergency packs")
    }
}
