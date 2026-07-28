package com.mindforge.app.data.repository

import com.mindforge.app.data.local.ChatDao
import com.mindforge.app.data.local.entity.ChatEntity
import com.mindforge.app.data.local.entity.ChatMessageEntity
import com.mindforge.app.domain.model.Chat
import com.mindforge.app.domain.model.ChatMessage
import com.mindforge.app.domain.model.MessageRole
import com.mindforge.app.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val chatDao: ChatDao
) : ChatRepository {

    override fun getChats(): Flow<List<Chat>> {
        return chatDao.getAllChats().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getChatById(id: Long): Flow<Chat?> {
        return chatDao.getChatById(id).map { it?.toDomain() }
    }

    override fun getMessages(chatId: Long): Flow<List<ChatMessage>> {
        return chatDao.getMessagesByChatId(chatId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getMessagesByModelId(modelId: String): Flow<List<ChatMessage>> {
        return chatDao.getAllChats().map { chatEntities ->
            chatEntities.filter { it.modelId == modelId }.flatMap { chat ->
                chatDao.getMessagesByChatIdList(chat.id).map { it.toDomain() }
            }
        }
    }

    override suspend fun createChat(title: String, modelId: String): Long {
        val chat = ChatEntity(title = title, modelId = modelId)
        return chatDao.insertChat(chat)
    }

    override suspend fun updateChat(chat: Chat) {
        chatDao.updateChat(chat.toEntity())
    }

    override suspend fun deleteChat(chatId: Long) {
        chatDao.deleteChatById(chatId)
    }

    override suspend fun deleteAllChats() {
        chatDao.deleteAllChats()
    }

    override suspend fun insertMessage(message: ChatMessage): Long {
        return chatDao.insertMessage(message.toEntity())
    }

    override suspend fun updateMessage(message: ChatMessage) {
        chatDao.updateMessage(message.toEntity())
    }

    override suspend fun deleteMessage(messageId: Long) {
        chatDao.deleteMessageById(messageId)
    }

    override suspend fun deleteMessagesByChatId(chatId: Long) {
        chatDao.deleteMessagesByChatId(chatId)
    }

    override suspend fun getChatCount(): Int {
        return chatDao.getAllChats().map { it.size }.let { flow ->
            kotlinx.coroutines.flow.first(flow) { true }
        }
    }

    override suspend fun getMessageCount(chatId: Long): Int {
        return chatDao.getMessageCount(chatId)
    }

    override suspend fun updateChatTitle(chatId: Long, title: String) {
        chatDao.updateChatTimestamp(chatId)
    }

    override suspend fun searchChats(query: String): List<Chat> {
        return chatDao.getAllChats().map { entities ->
            entities.filter {
                it.title.contains(query, ignoreCase = true)
            }.map { it.toDomain() }
        }.let { flow ->
            kotlinx.coroutines.flow.first(flow) { true }
        }
    }

    private fun ChatEntity.toDomain(): Chat = Chat(
        id = id,
        title = title,
        modelId = modelId,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun Chat.toEntity(): ChatEntity = ChatEntity(
        id = id,
        title = title,
        modelId = modelId,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun ChatMessageEntity.toDomain(): ChatMessage = ChatMessage(
        id = id,
        chatId = chatId,
        role = try { MessageRole.valueOf(role) } catch (_: Exception) { MessageRole.USER },
        content = content,
        timestamp = timestamp
    )

    private fun ChatMessage.toEntity(): ChatMessageEntity = ChatMessageEntity(
        id = id,
        chatId = chatId,
        role = role.name,
        content = content,
        timestamp = timestamp
    )
}
