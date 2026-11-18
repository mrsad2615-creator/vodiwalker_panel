package ir.aegis.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class ChecklistEntityWithItems(
    @androidx.room.Embedded val checklist: ChecklistEntity,
    @androidx.room.Relation(parentColumn = "id", entityColumn = "checklistId")
    val items: List<ChecklistItemEntity>,
)

@Dao
interface AegisDao {
    @Query("SELECT * FROM security_profiles LIMIT 1")
    fun observeSecurityProfile(): Flow<SecurityProfileEntity?>

    @Query("SELECT * FROM security_profiles LIMIT 1")
    suspend fun getSecurityProfileOnce(): SecurityProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSecurityProfile(entity: SecurityProfileEntity)

    @Query("SELECT * FROM emergency_cards LIMIT 1")
    fun observeEmergencyCard(): Flow<EmergencyCardEntity?>

    @Query("SELECT * FROM emergency_cards LIMIT 1")
    suspend fun getEmergencyCardOnce(): EmergencyCardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEmergencyCard(entity: EmergencyCardEntity)

    @Query("SELECT * FROM vault_items ORDER BY updatedAtMillis DESC")
    fun observeVaultItems(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items")
    suspend fun getVaultItemsOnce(): List<VaultItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVaultItem(entity: VaultItemEntity)

    @Query("DELETE FROM vault_items WHERE id = :id")
    suspend fun deleteVaultItem(id: String)

    @Query("DELETE FROM vault_items")
    suspend fun clearVaultItems()

    @Query("SELECT * FROM trusted_contacts ORDER BY priorityRank ASC, createdAtMillis ASC")
    fun observeTrustedContacts(): Flow<List<TrustedContactEntity>>

    @Query("SELECT * FROM trusted_contacts")
    suspend fun getTrustedContactsOnce(): List<TrustedContactEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrustedContact(entity: TrustedContactEntity)

    @Query("DELETE FROM trusted_contacts WHERE id = :id")
    suspend fun deleteTrustedContact(id: String)

    @Query("SELECT * FROM safety_plans ORDER BY updatedAtMillis DESC")
    fun observeSafetyPlans(): Flow<List<SafetyPlanEntity>>

    @Query("SELECT * FROM safety_plans")
    suspend fun getSafetyPlansOnce(): List<SafetyPlanEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSafetyPlan(entity: SafetyPlanEntity)

    @Query("DELETE FROM safety_plans WHERE id = :id")
    suspend fun deleteSafetyPlan(id: String)

    @Query("SELECT * FROM security_reminders ORDER BY dueAtMillis ASC")
    fun observeReminders(): Flow<List<SecurityReminderEntity>>

    @Query("SELECT * FROM security_reminders")
    suspend fun getRemindersOnce(): List<SecurityReminderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReminder(entity: SecurityReminderEntity)

    @Query("DELETE FROM security_reminders WHERE id = :id")
    suspend fun deleteReminder(id: String)

    @Query("SELECT * FROM journal_entries ORDER BY updatedAtMillis DESC")
    fun observeJournalEntries(): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries")
    suspend fun getJournalEntriesOnce(): List<JournalEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertJournalEntry(entity: JournalEntryEntity)

    @Query("DELETE FROM journal_entries WHERE id = :id")
    suspend fun deleteJournalEntry(id: String)

    @Query("DELETE FROM journal_entries")
    suspend fun clearJournalEntries()

    @Query("SELECT * FROM incidents ORDER BY createdAtMillis DESC")
    fun observeAllIncidents(): Flow<List<IncidentEntity>>

    @Query("SELECT * FROM incidents WHERE isArchived = 0 ORDER BY createdAtMillis DESC")
    fun observeActiveIncidents(): Flow<List<IncidentEntity>>

    @Query("SELECT * FROM incidents")
    suspend fun getIncidentsOnce(): List<IncidentEntity>

    @Query("SELECT * FROM incidents WHERE id = :id LIMIT 1")
    suspend fun getIncidentById(id: String): IncidentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertIncident(entity: IncidentEntity)

    @Query("DELETE FROM incidents WHERE id = :id")
    suspend fun deleteIncident(id: String)

    @Query("SELECT * FROM incident_timeline WHERE incidentId = :incidentId ORDER BY createdAtMillis DESC")
    fun observeIncidentTimeline(incidentId: String): Flow<List<IncidentTimelineEntity>>

    @Query("SELECT * FROM incident_timeline")
    suspend fun getAllTimelineOnce(): List<IncidentTimelineEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTimelineEvent(entity: IncidentTimelineEntity)

    @Query("SELECT * FROM incident_attachments WHERE incidentId = :incidentId ORDER BY createdAtMillis DESC")
    fun observeIncidentAttachments(incidentId: String): Flow<List<IncidentAttachmentEntity>>

    @Query("SELECT * FROM incident_attachments")
    suspend fun getAllAttachmentsOnce(): List<IncidentAttachmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertIncidentAttachment(entity: IncidentAttachmentEntity)

    @Transaction
    @Query("SELECT * FROM checklists ORDER BY createdAtMillis DESC")
    fun observeChecklists(): Flow<List<ChecklistEntityWithItems>>

    @Transaction
    @Query("SELECT * FROM checklists")
    suspend fun getChecklistsOnce(): List<ChecklistEntityWithItems>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChecklist(entity: ChecklistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChecklistItems(items: List<ChecklistItemEntity>)

    @Query("UPDATE checklist_items SET isChecked = :checked WHERE id = :itemId")
    suspend fun toggleChecklistItem(itemId: String, checked: Boolean)

    @Query("DELETE FROM checklists WHERE id = :id")
    suspend fun deleteChecklist(id: String)

    @Query("SELECT * FROM map_packages ORDER BY isBundled DESC, regionName ASC")
    fun observeMapPackages(): Flow<List<MapPackageEntity>>

    @Query("SELECT COUNT(*) FROM map_packages")
    suspend fun countMapPackages(): Int

    @Query("SELECT * FROM map_packages")
    suspend fun getMapPackagesOnce(): List<MapPackageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMapPackage(entity: MapPackageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMapPackages(entities: List<MapPackageEntity>)

    @Query("SELECT * FROM map_markers ORDER BY isEmergencyResource DESC, createdAtMillis DESC")
    fun observeMapMarkers(): Flow<List<MapMarkerEntity>>

    @Query("SELECT * FROM map_markers")
    suspend fun getMapMarkersOnce(): List<MapMarkerEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMapMarker(entity: MapMarkerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMapMarkers(entities: List<MapMarkerEntity>)

    @Query("DELETE FROM map_markers WHERE id = :id")
    suspend fun deleteMapMarker(id: String)

    @Query("SELECT * FROM saved_routes ORDER BY createdAtMillis DESC")
    fun observeSavedRoutes(): Flow<List<SavedRouteEntity>>

    @Query("SELECT * FROM saved_routes")
    suspend fun getSavedRoutesOnce(): List<SavedRouteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSavedRoute(entity: SavedRouteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSavedRoutes(entities: List<SavedRouteEntity>)

    @Query("DELETE FROM saved_routes WHERE id = :id")
    suspend fun deleteSavedRoute(id: String)

    @Query("SELECT * FROM family_members ORDER BY updatedAtMillis DESC")
    fun observeFamilyMembers(): Flow<List<FamilyMemberEntity>>

    @Query("SELECT * FROM family_members")
    suspend fun getFamilyMembersOnce(): List<FamilyMemberEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFamilyMember(entity: FamilyMemberEntity)

    @Query("DELETE FROM family_members WHERE id = :id")
    suspend fun deleteFamilyMember(id: String)

    @Query("SELECT * FROM meeting_points ORDER BY isPrimary DESC, name ASC")
    fun observeMeetingPoints(): Flow<List<MeetingPointEntity>>

    @Query("SELECT * FROM meeting_points")
    suspend fun getMeetingPointsOnce(): List<MeetingPointEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeetingPoint(entity: MeetingPointEntity)

    @Query("DELETE FROM meeting_points WHERE id = :id")
    suspend fun deleteMeetingPoint(id: String)

    @Query("SELECT * FROM family_plans LIMIT 1")
    fun observeFamilyPlan(): Flow<FamilyPlanEntity?>

    @Query("SELECT * FROM family_plans LIMIT 1")
    suspend fun getFamilyPlanOnce(): FamilyPlanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFamilyPlan(entity: FamilyPlanEntity)

    @Query("SELECT * FROM academy_progress")
    fun observeAcademyProgress(): Flow<List<AcademyProgressEntity>>

    @Query("SELECT * FROM academy_progress")
    suspend fun getAcademyProgressOnce(): List<AcademyProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAcademyProgress(entity: AcademyProgressEntity)

    @Query("SELECT * FROM inventory_items ORDER BY updatedAtMillis DESC")
    fun observeInventoryItems(): Flow<List<InventoryItemEntity>>

    @Query("SELECT * FROM inventory_items")
    suspend fun getInventoryItemsOnce(): List<InventoryItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInventoryItem(entity: InventoryItemEntity)

    @Query("DELETE FROM inventory_items WHERE id = :id")
    suspend fun deleteInventoryItem(id: String)

    @Query("SELECT * FROM offline_notes ORDER BY isPinned DESC, updatedAtMillis DESC")
    fun observeOfflineNotes(): Flow<List<OfflineNoteEntity>>

    @Query("SELECT * FROM offline_notes")
    suspend fun getOfflineNotesOnce(): List<OfflineNoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOfflineNote(entity: OfflineNoteEntity)

    @Query("DELETE FROM offline_notes WHERE id = :id")
    suspend fun deleteOfflineNote(id: String)

    @Query("SELECT * FROM voice_notes ORDER BY createdAtMillis DESC")
    fun observeVoiceNotes(): Flow<List<VoiceNoteEntity>>

    @Query("SELECT * FROM voice_notes")
    suspend fun getVoiceNotesOnce(): List<VoiceNoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVoiceNote(entity: VoiceNoteEntity)

    @Query("DELETE FROM voice_notes WHERE id = :id")
    suspend fun deleteVoiceNote(id: String)

    @Query("SELECT * FROM audit_logs ORDER BY timestampMillis DESC LIMIT 200")
    fun observeAuditLogs(): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs ORDER BY timestampMillis DESC")
    suspend fun getAuditLogsOnce(): List<AuditLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(entity: AuditLogEntity)

    @Query("DELETE FROM audit_logs")
    suspend fun clearAuditLogs()
}
