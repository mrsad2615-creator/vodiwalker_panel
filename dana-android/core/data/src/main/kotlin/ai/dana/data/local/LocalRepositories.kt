package ai.dana.data.local

import ai.dana.domain.Conversation
import ai.dana.domain.ConversationMessage
import ai.dana.domain.DanaNote
import ai.dana.domain.HistoryRepository
import ai.dana.domain.InputIssue
import ai.dana.domain.InputRejected
import ai.dana.domain.LocalText
import ai.dana.domain.MemoryRepository
import ai.dana.domain.MessageRole
import ai.dana.domain.NotesRepository
import ai.dana.domain.UserMemory
import androidx.room.withTransaction
import kotlinx.coroutines.flow.map

internal class RoomNotesRepository(private val dao: NotesDao) : NotesRepository {
    override val notes = dao.observe().map { entities ->
        entities.map { DanaNote(it.id, it.text, it.updatedAtMs) }
    }

    override suspend fun save(id: Long?, text: String): Long {
        val clean = LocalText.note(text)
        val now = System.currentTimeMillis()
        if (id == null) return dao.insert(NoteEntity(text = clean, updatedAtMs = now))
        requireChange(dao.update(id, clean, now))
        return id
    }

    override suspend fun delete(id: Long) = requireChange(dao.delete(id))
}

internal class RoomHistoryRepository(private val database: DanaDatabase) : HistoryRepository {
    private val dao = database.history()

    override val conversations = dao.observe().map { entities ->
        entities.map { Conversation(it.id, it.title, it.updatedAtMs) }
    }

    override fun search(text: String) = dao.search(text.trim().take(160)).map { entities ->
        entities.map { Conversation(it.id, it.title, it.updatedAtMs) }
    }

    override fun messages(conversationId: Long) = dao.observeMessages(conversationId).map { entities ->
        entities.map {
            ConversationMessage(it.id, it.conversationId, MessageRole.valueOf(it.role), it.body, it.createdAtMs)
        }
    }

    override suspend fun create(title: String): Long = dao.insertConversation(
        ConversationEntity(title = LocalText.title(title), updatedAtMs = System.currentTimeMillis()),
    )

    override suspend fun append(conversationId: Long, role: MessageRole, body: String): Long {
        val clean = LocalText.message(body)
        return database.withTransaction {
            val now = System.currentTimeMillis()
            requireChange(dao.touchConversation(conversationId, now))
            dao.insertMessage(
                MessageEntity(conversationId = conversationId, role = role.name, body = clean, createdAtMs = now),
            )
        }
    }

    override suspend fun saveExchange(conversationId: Long?, title: String, user: String, answer: String): Long {
        val question = LocalText.message(user)
        val response = LocalText.message(answer)
        return database.withTransaction {
            val now = System.currentTimeMillis()
            val id = conversationId ?: dao.insertConversation(ConversationEntity(title = LocalText.title(title), updatedAtMs = now))
            if (conversationId != null) requireChange(dao.touchConversation(id, now))
            dao.insertMessage(MessageEntity(conversationId = id, role = MessageRole.USER.name, body = question, createdAtMs = now))
            dao.insertMessage(MessageEntity(conversationId = id, role = MessageRole.ASSISTANT.name, body = response, createdAtMs = now))
            id
        }
    }

    override suspend fun rename(conversationId: Long, title: String) = requireChange(dao.rename(conversationId, LocalText.title(title)))

    override suspend fun clear() = dao.clear()

    override suspend fun delete(conversationId: Long) = requireChange(dao.deleteConversation(conversationId))
}

internal class RoomMemoryRepository(private val dao: MemoryDao) : MemoryRepository {
    override val facts = dao.observe().map { entities ->
        entities.map { UserMemory(it.id, it.fact, it.updatedAtMs) }
    }

    override suspend fun save(id: Long?, fact: String): Long {
        val clean = LocalText.memory(fact)
        val now = System.currentTimeMillis()
        if (id == null) return dao.insert(UserMemoryEntity(fact = clean, updatedAtMs = now))
        requireChange(dao.update(id, clean, now))
        return id
    }

    override suspend fun delete(id: Long) = requireChange(dao.delete(id))
    override suspend fun clear() = dao.clear()
}

internal fun requireChange(count: Int) {
    if (count != 1) throw InputRejected(InputIssue.RECORD_NOT_FOUND)
}
