package com.ogrchatai.app.data.repository

import android.content.Context
import com.ogrchatai.app.data.local.ChatDao
import com.ogrchatai.app.data.local.entity.ChatEntity
import com.ogrchatai.app.data.local.entity.ChatMessageEntity
import com.ogrchatai.app.domain.model.Chat
import com.ogrchatai.app.domain.model.ChatMessage
import com.ogrchatai.app.domain.model.MessageRole
import com.ogrchatai.app.domain.repository.ChatRepository
import com.ogrchatai.app.util.MessageCrypto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val chatDao: ChatDao,
    @ApplicationContext private val context: Context
) : ChatRepository {

    private val crypto = MessageCrypto

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
            entities.map { decryptMessage(it) }
        }
    }

    override fun getMessagesByModelId(modelId: String): Flow<List<ChatMessage>> {
        return chatDao.getAllChats().map { chatEntities ->
            chatEntities.filter { it.modelId == modelId }.flatMap { chat ->
                chatDao.getMessagesByChatIdList(chat.id).map { decryptMessage(it) }
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
        android.util.Log.d("ChatRepo", "insertMessage: chatId=${message.chatId}, role=${message.role}")
        val encrypted = try {
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(3000L) {
                    crypto.encrypt(context, message.content)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatRepo", "encrypt failed: ${e.message}")
            null
        }
        android.util.Log.d("ChatRepo", "encrypt done, encrypted=${encrypted != null}")
        val entity = message.toEntity().copy(
            encryptedContent = encrypted?.ciphertext,
            contentNonce = encrypted?.nonce
        )
        val id = chatDao.insertMessage(entity)
        android.util.Log.d("ChatRepo", "insertMessage done, id=$id")
        return id
    }

    override suspend fun updateMessage(message: ChatMessage) {
        val encrypted = try {
            withContext(Dispatchers.IO) {
                withTimeoutOrNull(3000L) {
                    crypto.encrypt(context, message.content)
                }
            }
        } catch (e: Exception) {
            null
        }
        val entity = message.toEntity().copy(
            encryptedContent = encrypted?.ciphertext,
            contentNonce = encrypted?.nonce
        )
        chatDao.updateMessage(entity)
    }

    override suspend fun deleteMessage(messageId: Long) {
        chatDao.deleteMessageById(messageId)
    }

    override suspend fun deleteMessagesByChatId(chatId: Long) {
        chatDao.deleteMessagesByChatId(chatId)
    }

    override suspend fun getChatCount(): Int {
        return chatDao.getAllChats().map { it.size }.first()
    }

    override suspend fun getMessageCount(chatId: Long): Int {
        return chatDao.getMessageCount(chatId)
    }

    override suspend fun updateChatTitle(chatId: Long, title: String) {
        chatDao.updateChatTitle(chatId, title)
    }

    override suspend fun searchChats(query: String): List<Chat> {
        return chatDao.getAllChats().map { entities ->
            entities.filter {
                it.title.contains(query, ignoreCase = true)
            }.map { it.toDomain() }
        }.first()
    }

    private fun decryptMessage(entity: ChatMessageEntity): ChatMessage {
        val decryptedContent = if (entity.encryptedContent != null && entity.contentNonce != null) {
            try {
                crypto.decrypt(context, MessageCrypto.EncryptedResult(
                    ciphertext = entity.encryptedContent!!,
                    nonce = entity.contentNonce!!
                ))
            } catch (e: Exception) {
                android.util.Log.e("ChatRepo", "decrypt failed: ${e.message}")
                entity.content
            }
        } else {
            entity.content
        }
        return ChatMessage(
            id = entity.id,
            chatId = entity.chatId,
            role = try { MessageRole.valueOf(entity.role) } catch (_: Exception) { MessageRole.USER },
            content = decryptedContent,
            timestamp = entity.timestamp
        )
    }

    private fun ChatEntity.toDomain(): Chat = Chat(
        id = id,
        title = title,
        modelId = modelId,
        folderId = folderId,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun Chat.toEntity(): ChatEntity = ChatEntity(
        id = id,
        title = title,
        modelId = modelId,
        folderId = folderId,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun ChatMessage.toEntity(): ChatMessageEntity = ChatMessageEntity(
        id = id,
        chatId = chatId,
        role = role.name,
        content = content,
        timestamp = timestamp
    )
}
