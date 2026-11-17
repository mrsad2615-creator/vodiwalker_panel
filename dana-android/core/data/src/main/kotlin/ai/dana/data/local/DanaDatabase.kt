package ai.dana.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "notes")
internal data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    @ColumnInfo(name = "updated_at_ms") val updatedAtMs: Long,
)

@Entity(tableName = "conversations")
internal data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    @ColumnInfo(name = "updated_at_ms") val updatedAtMs: Long,
)

@Entity(
    tableName = "messages",
    foreignKeys = [ForeignKey(
        entity = ConversationEntity::class,
        parentColumns = ["id"],
        childColumns = ["conversation_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["conversation_id", "id"])],
)
internal data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "conversation_id") val conversationId: Long,
    val role: String,
    val body: String,
    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long,
)

@Entity(tableName = "user_memory")
internal data class UserMemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fact: String,
    @ColumnInfo(name = "updated_at_ms") val updatedAtMs: Long,
)

@Entity(tableName = "engine_profiles")
internal data class EngineEntity(
    @PrimaryKey val id: String,
    val name: String,
    val protocol: String,
    val network: String,
    @ColumnInfo(name = "base_url") val baseUrl: String,
    val model: String,
    @ColumnInfo(name = "sealed_key") val sealedKey: String?,
) {
    // Do not include the envelope in diagnostics (even though it isn't a plaintext credential).
    override fun toString(): String = "EngineEntity(id=$id)"
}

internal data class EngineSummary(
    val id: String,
    val name: String,
    val protocol: String,
    val network: String,
    @ColumnInfo(name = "base_url") val baseUrl: String,
    val model: String,
    @ColumnInfo(name = "has_credential") val hasCredential: Boolean,
)

@Dao
internal interface NotesDao {
    @Query("SELECT * FROM notes ORDER BY updated_at_ms DESC, id DESC")
    fun observe(): Flow<List<NoteEntity>>

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Query("UPDATE notes SET text = :text, updated_at_ms = :now WHERE id = :id")
    suspend fun update(id: Long, text: String, now: Long): Int

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: Long): Int
}

@Dao
internal interface HistoryDao {
    @Query("SELECT * FROM conversations ORDER BY updated_at_ms DESC, id DESC")
    fun observe(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY id ASC")
    fun observeMessages(conversationId: Long): Flow<List<MessageEntity>>

    @Insert
    suspend fun insertConversation(conversation: ConversationEntity): Long

    @Insert
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("UPDATE conversations SET updated_at_ms = :now WHERE id = :id")
    suspend fun touchConversation(id: Long, now: Long): Int

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: Long): Int
}

@Dao
internal interface MemoryDao {
    @Query("SELECT * FROM user_memory ORDER BY updated_at_ms DESC, id DESC")
    fun observe(): Flow<List<UserMemoryEntity>>

    @Insert
    suspend fun insert(memory: UserMemoryEntity): Long

    @Query("UPDATE user_memory SET fact = :fact, updated_at_ms = :now WHERE id = :id")
    suspend fun update(id: Long, fact: String, now: Long): Int

    @Query("DELETE FROM user_memory WHERE id = :id")
    suspend fun delete(id: Long): Int

    @Query("DELETE FROM user_memory")
    suspend fun clear()
}

@Dao
internal interface EngineDao {
    @Query(
        """
        SELECT id, name, protocol, network, base_url, model,
               CASE WHEN sealed_key IS NULL THEN 0 ELSE 1 END AS has_credential
        FROM engine_profiles ORDER BY name COLLATE NOCASE, id
        """,
    )
    fun observe(): Flow<List<EngineSummary>>

    @Query("SELECT * FROM engine_profiles WHERE id = :id")
    suspend fun find(id: String): EngineEntity?

    @Upsert
    suspend fun upsert(engine: EngineEntity)

    @Query("DELETE FROM engine_profiles WHERE id = :id")
    suspend fun delete(id: String): Int
}

@Database(
    entities = [NoteEntity::class, ConversationEntity::class, MessageEntity::class,
        UserMemoryEntity::class, EngineEntity::class],
    version = 1,
    exportSchema = true,
)
internal abstract class DanaDatabase : RoomDatabase() {
    abstract fun notes(): NotesDao
    abstract fun history(): HistoryDao
    abstract fun memory(): MemoryDao
    abstract fun engines(): EngineDao
}
