package ir.aegis.core.model

import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {
    val preferences: Flow<AppPreferences>
    suspend fun updateThemeMode(mode: ThemeMode)
    suspend fun updateLanguage(language: AppLanguage)
    suspend fun setPinCredential(saltBase64: String, hashBase64: String)
    suspend fun clearPinCredential()
    suspend fun setBiometricEnabled(enabled: Boolean)
    suspend fun setFlagSecure(enabled: Boolean)
    suspend fun setSensitiveNotificationRedacted(redacted: Boolean)
    suspend fun setAutoLockSeconds(seconds: Int)
    suspend fun toggleFavoriteModule(module: AegisModule)
    suspend fun updateDashboardWidgets(order: List<DashboardWidgetId>, disabled: Set<DashboardWidgetId>)
    suspend fun setFallbackLocation(latitude: Double, longitude: Double, label: String)
    suspend fun setActiveMapPackage(packageId: String)
    suspend fun setLocalModelInfo(path: String, name: String, sha256: String)
    suspend fun setLocalDeviceSharingConsent(consented: Boolean)
    suspend fun setLastBackupTimestamp(timestampMillis: Long)
    suspend fun replacePreferences(preferences: AppPreferences)
}

interface ReminderScheduler {
    fun scheduleReminder(reminder: SecurityReminder, redactContent: Boolean)
    fun cancelReminder(reminderId: String)
}

interface AegisRepository {
    fun observeSecurityProfile(): Flow<SecurityProfile?>
    suspend fun upsertSecurityProfile(profile: SecurityProfile)

    fun observeEmergencyCard(): Flow<EmergencyIdentityCard?>
    suspend fun upsertEmergencyCard(card: EmergencyIdentityCard)

    fun observeVaultItems(): Flow<List<VaultItem>>
    suspend fun upsertVaultItem(item: VaultItem)
    suspend fun deleteVaultItem(id: String)

    fun observeTrustedContacts(): Flow<List<TrustedContact>>
    suspend fun upsertTrustedContact(contact: TrustedContact)
    suspend fun deleteTrustedContact(id: String)

    fun observeSafetyPlans(): Flow<List<PersonalSafetyPlan>>
    suspend fun upsertSafetyPlan(plan: PersonalSafetyPlan)
    suspend fun deleteSafetyPlan(id: String)

    fun observeReminders(): Flow<List<SecurityReminder>>
    suspend fun upsertReminder(reminder: SecurityReminder)
    suspend fun deleteReminder(id: String)

    fun observeJournalEntries(): Flow<List<PrivateJournalEntry>>
    suspend fun upsertJournalEntry(entry: PrivateJournalEntry)
    suspend fun deleteJournalEntry(id: String)

    fun observeIncidents(includeArchived: Boolean = true): Flow<List<Incident>>
    suspend fun getIncidentById(id: String): Incident?
    suspend fun createIncident(incident: Incident, initialNote: String = "Incident registered")
    suspend fun updateIncident(incident: Incident, timelineNote: String? = null)
    suspend fun deleteIncident(id: String)

    fun observeIncidentTimeline(incidentId: String): Flow<List<IncidentTimelineEvent>>
    suspend fun addTimelineEvent(event: IncidentTimelineEvent)

    fun observeIncidentAttachments(incidentId: String): Flow<List<IncidentAttachment>>
    suspend fun addIncidentAttachment(attachment: IncidentAttachment)

    fun observeChecklists(): Flow<List<ChecklistWithItems>>
    suspend fun upsertChecklistWithItems(checklist: EmergencyChecklist, items: List<ChecklistItem>)
    suspend fun toggleChecklistItem(itemId: String, checked: Boolean)
    suspend fun deleteChecklist(checklistId: String)

    fun observeMapPackages(): Flow<List<OfflineMapPackage>>
    suspend fun upsertMapPackage(pkg: OfflineMapPackage)

    fun observeMapMarkers(): Flow<List<MapMarker>>
    suspend fun upsertMapMarker(marker: MapMarker)
    suspend fun deleteMapMarker(id: String)

    fun observeSavedRoutes(): Flow<List<SavedRoute>>
    suspend fun upsertSavedRoute(route: SavedRoute)
    suspend fun deleteSavedRoute(id: String)

    fun observeFamilyMembers(): Flow<List<FamilyMemberProfile>>
    suspend fun upsertFamilyMember(member: FamilyMemberProfile)
    suspend fun deleteFamilyMember(id: String)

    fun observeMeetingPoints(): Flow<List<FamilyMeetingPoint>>
    suspend fun upsertMeetingPoint(point: FamilyMeetingPoint)
    suspend fun deleteMeetingPoint(id: String)

    fun observeFamilyPlan(): Flow<FamilyPlan?>
    suspend fun upsertFamilyPlan(plan: FamilyPlan)

    fun observeAcademyProgress(): Flow<List<AcademyProgressRecord>>
    suspend fun upsertAcademyProgress(progress: AcademyProgressRecord)

    fun observeInventoryItems(): Flow<List<InventoryItem>>
    suspend fun upsertInventoryItem(item: InventoryItem)
    suspend fun deleteInventoryItem(id: String)

    fun observeOfflineNotes(): Flow<List<OfflineNote>>
    suspend fun upsertOfflineNote(note: OfflineNote)
    suspend fun deleteOfflineNote(id: String)

    fun observeVoiceNotes(): Flow<List<VoiceNote>>
    suspend fun upsertVoiceNote(note: VoiceNote)
    suspend fun deleteVoiceNote(id: String)

    fun observeAuditLogs(): Flow<List<ActivityAuditLog>>
    suspend fun logActivity(actionType: String, moduleName: String, summary: String)
    suspend fun clearAuditLogs()

    suspend fun exportFullSnapshot(preferences: AppPreferences): FullBackupSnapshot
    suspend fun restoreFullSnapshot(snapshot: FullBackupSnapshot)
    suspend fun panicWipeSensitiveData()
}
