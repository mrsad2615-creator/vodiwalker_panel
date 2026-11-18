package ai.dana.domain

import kotlinx.coroutines.flow.Flow

data class DanaNote(val id: Long, val text: String, val updatedAtMs: Long)

interface NotesRepository {
    val notes: Flow<List<DanaNote>>
    suspend fun save(id: Long?, text: String): Long
    suspend fun delete(id: Long)
}

data class Conversation(val id: Long, val title: String, val updatedAtMs: Long)

enum class MessageRole { USER, ASSISTANT }

data class ConversationMessage(
    val id: Long,
    val conversationId: Long,
    val role: MessageRole,
    val body: String,
    val createdAtMs: Long,
)

interface HistoryRepository {
    val conversations: Flow<List<Conversation>>
    fun messages(conversationId: Long): Flow<List<ConversationMessage>>
    suspend fun create(title: String): Long
    suspend fun append(conversationId: Long, role: MessageRole, body: String): Long
    suspend fun saveExchange(conversationId: Long?, title: String, user: String, answer: String): Long
    suspend fun rename(conversationId: Long, title: String)
    suspend fun delete(conversationId: Long)
    suspend fun clear()
    fun search(text: String): Flow<List<Conversation>>
}

data class UserMemory(val id: Long, val fact: String, val updatedAtMs: Long)

/** Only explicit local writes. Automatic extraction, opt-in and injection are not part of this port. */
interface MemoryRepository {
    val facts: Flow<List<UserMemory>>
    suspend fun save(id: Long?, fact: String): Long
    suspend fun delete(id: Long)
    suspend fun clear()
}
