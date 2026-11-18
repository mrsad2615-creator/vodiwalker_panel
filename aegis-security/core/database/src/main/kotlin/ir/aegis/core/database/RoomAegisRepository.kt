package ir.aegis.core.database

import ir.aegis.core.maps.OfflineMapSeeds
import ir.aegis.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
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
        withContext(Dispatchers.IO) {
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
        withContext(Dispatchers.IO) {
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
    }

    override suspend fun panicWipeSensitiveData() {
        dao.clearVaultItems()
        dao.clearJournalEntries()
        logActivity("PANIC_WIPE", "Privacy", "Sensitive encrypted vault and private journal wiped locally")
    }

    suspend fun ensureSeeded() {
        val existingPacks = dao.getMapPackagesOnce()
        val needsMapRefresh = existingPacks.none { it.versionTag == "2026.11-PRO-V3" }
        if (needsMapRefresh) {
            val installedById = existingPacks.associate { it.id to it.isInstalled }
            dao.upsertMapPackages(
                OfflineMapSeeds.bundledPackages().map { pkg ->
                    val preserved = installedById[pkg.id] ?: pkg.isInstalled
                    MapPackageEntity.fromDomain(pkg.copy(isInstalled = preserved))
                },
            )
            dao.upsertMapMarkers(OfflineMapSeeds.bundledMarkers().map { MapMarkerEntity.fromDomain(it) })
            dao.upsertSavedRoutes(OfflineMapSeeds.bundledRoutes().map { SavedRouteEntity.fromDomain(it) })
        }
        if (existingPacks.isNotEmpty()) return
        val now = System.currentTimeMillis()

        dao.upsertSecurityProfile(
            SecurityProfileEntity.fromDomain(
                SecurityProfile(
                    id = "primary",
                    fullName = "نگهبان امنیتی ایجیس",
                    aliasOrCallsign = "SHIELD-01",
                    homeRegionLabel = "تهران · مرکز آفلاین",
                    homeLatitude = 35.6892,
                    homeLongitude = 51.3890,
                    safeCheckInCode = "GREEN-7",
                    duressCode = "AMBER-9",
                    emergencyNote = "پروفیل امنیت موبایل و دفاع در برابر نفوذ — ۱۰۰٪ آفلاین",
                    updatedAtMillis = now,
                ),
            ),
        )
        dao.upsertEmergencyCard(
            EmergencyCardEntity.fromDomain(
                EmergencyIdentityCard(
                    id = "primary-card",
                    fullName = "کاربر اصلی AegisGuard",
                    dateOfBirth = "1373-02-22",
                    bloodType = "O+",
                    allergies = "بدون مورد خاص",
                    medications = "ندارد",
                    medicalConditions = "ندارد",
                    primaryIceContactName = "پلیس فتا / تماس اضطراری",
                    primaryIceContactPhone = "096380",
                    organDonor = false,
                    languagesSpoken = "فارسی، انگلیسی",
                    insuranceOrIdNumber = "AEGIS-SEC-01",
                    updatedAtMillis = now,
                ),
            ),
        )
        dao.upsertTrustedContact(
            TrustedContactEntity.fromDomain(
                TrustedContact(
                    id = "tc-starter-1",
                    name = "پلیس فتا (رسیدگی به جرایم سایبری)",
                    relation = "مرکز رسمی امنیت سایبری",
                    phone = "096380",
                    alternateChannel = "شماره تماس مستقیم",
                    addressOrZone = "سراسر کشور",
                    priorityRank = 1,
                    canReceiveSosMessage = true,
                    notes = "شماره رسمی گزارش فیشینگ، کلاهبرداری پیامکی و هک حساب",
                    createdAtMillis = now,
                ),
            ),
        )
        dao.upsertSafetyPlan(
            SafetyPlanEntity.fromDomain(
                PersonalSafetyPlan(
                    id = "sp-starter-eq",
                    title = "پروتکل مهار نفوذ و پاک‌سازی موبایل (NIST IR)",
                    scenarioType = "CYBER_INCIDENT",
                    triggerConditions = "فعالیت مشکوک Accessibility، پیامک OTP ناخواسته یا نصب APK جعلی",
                    actionSteps = listOf(
                        "قرار دادن فوری گوشی در حالت پرواز (Airplane Mode) برای قطع C2 مهاجم",
                        "اجرای اسکنر بدافزار و بازرس ضد هک در AegisGuard و ذخیره گزارش PDF",
                        "لغو دسترسی Accessibility و Device Admin برنامه مشکوک و حذف آن",
                        "تغییر رمزهای ایمیل، بانکی و بستن نشست‌های فعال از یک دستگاه پاک دیگر",
                    ),
                    primarySafeLocation = "قرنطینه آفلاین دستگاه",
                    backupSafeLocation = "پشتیبان‌گیری رمزنگاری AES-256",
                    updatedAtMillis = now,
                ),
            ),
        )
        dao.upsertFamilyPlan(
            FamilyPlanEntity.fromDomain(
                FamilyPlan(
                    id = "family-plan-main",
                    planTitle = "طرح ایمنی سایبری و تماس امن خانواده",
                    evacuationProtocol = "در صورت دریافت پیامک جعلی یا مشکوک شدن به هک، از نصب هرگونه فایل خودداری و موضوع را در AegisGuard بررسی کنید.",
                    communicationProtocol = "برای احراز هویت اضطراری بین اعضا از کد تایید اختصاصی استفاده شود تا فریب کلاهبرداری تلفنی نخورید.",
                    outOfAreaContactName = "پلیس فتا / تماس پشتیبان",
                    outOfAreaContactPhone = "096380",
                    rallyWindowMinutes = 30,
                    updatedAtMillis = now,
                ),
            ),
        )
        dao.upsertMeetingPoint(
            MeetingPointEntity.fromDomain(
                FamilyMeetingPoint(
                    id = "mp-primary",
                    name = "نقطه قرار امن پارک لاله",
                    isPrimary = true,
                    latitude = 35.7106,
                    longitude = 51.3919,
                    addressNote = "ضلع شمالی بلوار کشاورز",
                    landmarkNote = "فضای باز مرکزی روی نقشه آفلاین",
                    instructions = "مسیریابی مستقیم از بخش نقشه آفلاین ایجیس",
                ),
            ),
        )
        dao.upsertMeetingPoint(
            MeetingPointEntity.fromDomain(
                FamilyMeetingPoint(
                    id = "mp-backup",
                    name = "نقطه امن جایگزین بوستان ملت",
                    isPrimary = false,
                    latitude = 35.7785,
                    longitude = 51.4098,
                    addressNote = "خیابان ولیعصر، پارک ملت",
                    landmarkNote = "نقطه پشتیبان در شمال شهر",
                    instructions = "در صورت محدودیت مسیر مرکزی استفاده شود.",
                ),
            ),
        )

        val starterChecklists = listOf(
            EmergencyChecklist("chk-72h", "چک‌لیست ایمن‌سازی موبایل در برابر هک (OWASP MASVS)", "HARDENING", null, false, now) to listOf(
                ChecklistItem("ci-1", "chk-72h", "خاموش کردن USB Debugging (ADB) و Developer Options", true, 0),
                ChecklistItem("ci-2", "chk-72h", "بررسی صفر بودن گواهی کاربری ناشناس (User CA)", true, 1),
                ChecklistItem("ci-3", "chk-72h", "محدود کردن دسترسی Accessibility و Overlay به برنامه‌های سیستمی", false, 2),
                ChecklistItem("ci-4", "chk-72h", "فعال‌سازی قفل پین/اثرانگشت + FLAG_SECURE داخل سپر ایجیس", false, 3),
                ChecklistItem("ci-5", "chk-72h", "تهیه بکاپ رمزنگاری‌شده AES-256-GCM آفلاین", true, 4),
            ),
            EmergencyChecklist("chk-fire", "چک‌لیست فوری مقابله با فیشینگ پیامکی و سرقت OTP", "PHISHING", null, false, now) to listOf(
                ChecklistItem("ci-6", "chk-fire", "تحلیل لینک پیامکی مشکوک در آزمایشگاه ضد فیشینگ", true, 0),
                ChecklistItem("ci-7", "chk-fire", "پاک‌سازی Notification Listeners برای جلوگیری از سرقت OTP", false, 1),
                ChecklistItem("ci-8", "chk-fire", "بررسی بسته‌های نصب‌شده خارج از گوگل‌پلی و کافه‌بازار", true, 2),
            ),
        )
        starterChecklists.forEach { (chk, items) ->
            dao.upsertChecklist(ChecklistEntity.fromDomain(chk))
            dao.upsertChecklistItems(items.map { ChecklistItemEntity.fromDomain(it) })
        }

        val starterInventory = listOf(
            InventoryItem("inv-water", "کلیدهای بازیابی رمزنگاری‌شده", "Security", 1.0, "Vault", 1.0, now + 180L * 86_400_000L, "AES-256 Vault", now),
            InventoryItem("inv-kit", "بکاپ آفلاین .aegisbak", "Backup", 1.0, "Archive", 1.0, now + 90L * 86_400_000L, "Local Storage", now),
        )
        starterInventory.forEach { dao.upsertInventoryItem(InventoryItemEntity.fromDomain(it)) }

        val starterIncidents = listOf(
            Incident(
                id = "inc-sample-1",
                title = "نمونه ممیزی: تلاش فیشینگ پیامکی با لینک APK جعلی",
                description = "نمونه گزارش آموزشی برای جرم‌یابی: شناسایی پیامک با دامنه غیررسمی .xyz و پسوند دوگانه .pdf.apk — مهار‌شده به‌صورت کاملاً آفلاین.",
                category = IncidentCategory.SECURITY_THREAT,
                priority = IncidentPriority.HIGH,
                status = IncidentStatus.CONTAINED,
                latitude = 35.6941,
                longitude = 51.3755,
                locationLabel = "آزمایشگاه فیشینگ محلی",
                aiSuggestedCategory = "SECURITY_THREAT",
                createdAtMillis = now - 86_400_000L * 3,
                updatedAtMillis = now - 86_400_000L * 2,
            ),
            Incident(
                id = "inc-sample-2",
                title = "نمونه ممیزی: بررسی مجوز Accessibility و گواهی CA",
                description = "گزارش دوره‌ای سلامت سیستم: بررسی سرویس‌های Accessibility، سرپرست دستگاه و گواهی‌های کاربری مطابق OWASP MASVS.",
                category = IncidentCategory.UTILITY_OUTAGE,
                priority = IncidentPriority.LOW,
                status = IncidentStatus.RESOLVED,
                latitude = 35.7024,
                longitude = 51.3912,
                locationLabel = "بازرسی هسته اندروید",
                aiSuggestedCategory = "UTILITY_OUTAGE",
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
                    eventType = "CYBER_AUDIT",
                    summary = "گزارش نمونه امنیت موبایل ثبت شد",
                    createdAtMillis = inc.createdAtMillis,
                ),
            )
        }

        dao.upsertOfflineNote(
            OfflineNoteEntity(
                id = "note-seed-1",
                title = "سپر امنیتی ایجیس v3 — ۱۰۰٪ آفلاین و بدون دسترسی اینترنت",
                body = "تمام موتورهای ضد هک، آنتی‌ویروس بررسی بسته‌های APK، تحلیلگر فیشینگ و نقشه کامل جهان و ایران بدون هیچ مجوز شبکه‌ای درون دستگاه اجرا می‌شوند.",
                category = "Security",
                isPinned = true,
                updatedAtMillis = now,
            ),
        )
        logActivity("SYSTEM_INIT", "Core", "Initialized v3.0.0 offline cybersecurity & multi-scale map engine")
    }
}
