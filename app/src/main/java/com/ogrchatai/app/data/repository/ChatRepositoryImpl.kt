package com.ogrchatai.app.data.repository

import android.content.Context
import android.util.Log
import com.ogrchatai.app.data.files.AttachmentProcessor
import com.ogrchatai.app.data.local.ChatDao
import com.ogrchatai.app.data.local.entity.ChatEntity
import com.ogrchatai.app.data.local.entity.ChatMessageEntity
import com.ogrchatai.app.domain.model.Attachment
import com.ogrchatai.app.domain.model.Chat
import com.ogrchatai.app.domain.model.ChatMessage
import com.ogrchatai.app.domain.model.MessageRole
import com.ogrchatai.app.domain.repository.ChatRepository
import com.ogrchatai.app.util.MessageCrypto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val chatDao: ChatDao,
    private val attachmentProcessor: AttachmentProcessor,
    @ApplicationContext private val context: Context
) : ChatRepository {

    private val crypto = MessageCrypto
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val attachmentListSerializer = ListSerializer(Attachment.serializer())

    override fun getChats(): Flow<List<Chat>> {
        return chatDao.getAllChats().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getChatById(id: Long): Flow<Chat?> {
        return chatDao.getChatById(id).map { it?.toDomain() }
    }

    override fun getMessages(chatId: Long): Flow<List<ChatMessage>> {
        return chatDao.getMessagesByChatId(chatId)
            .map { entities -> entities.map { decryptMessage(it) } }
            .flowOn(Dispatchers.IO)
    }

    override fun getMessagesByModelId(modelId: String): Flow<List<ChatMessage>> {
        return chatDao.getAllChats()
            .map { chatEntities ->
                chatEntities.filter { it.modelId == modelId }.flatMap { chat ->
                    chatDao.getMessagesByChatIdList(chat.id).map { decryptMessage(it) }
                }
            }
            .flowOn(Dispatchers.IO)
    }

    override suspend fun createChat(title: String, modelId: String): Long {
        val chat = ChatEntity(title = title, modelId = modelId)
        return chatDao.insertChat(chat)
    }

    override suspend fun updateChat(chat: Chat) {
        chatDao.updateChat(chat.toEntity())
    }

    override suspend fun deleteChat(chatId: Long) {
        val messages = chatDao.getMessagesByChatIdList(chatId)
        deleteAttachmentFiles(messages)
        chatDao.deleteChatById(chatId)
    }

    override suspend fun deleteAllChats() {
        val messages = chatDao.getAllMessages()
        deleteAttachmentFiles(messages)
        chatDao.deleteAllChats()
    }

    override suspend fun insertMessage(message: ChatMessage): Long =
        withContext(Dispatchers.IO) {
            val encrypted = encryptMessageText(message.content)
            // The plaintext column is intentionally left empty. The encrypted copy is the
            // only source of the message text, so a database dump cannot reveal it.
            val entity = message.toEntity().copy(
                content = "",
                encryptedContent = encrypted.ciphertext,
                contentNonce = encrypted.nonce
            )
            chatDao.insertMessage(entity)
        }

    override suspend fun updateMessage(message: ChatMessage) =
        withContext(Dispatchers.IO) {
            val encrypted = encryptMessageText(message.content)
            val entity = message.toEntity().copy(
                content = "",
                encryptedContent = encrypted.ciphertext,
                contentNonce = encrypted.nonce
            )
            chatDao.updateMessage(entity)
        }

    override suspend fun deleteMessage(messageId: Long) {
        chatDao.getMessageById(messageId)?.let { deleteAttachmentFiles(listOf(it)) }
        chatDao.deleteMessageById(messageId)
    }

    override suspend fun deleteMessagesByChatId(chatId: Long) {
        val messages = chatDao.getMessagesByChatIdList(chatId)
        deleteAttachmentFiles(messages)
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

    override suspend fun migrateLegacyMessages() = withContext(Dispatchers.IO) {
        val messages = chatDao.getAllMessages()
        var migrated = 0

        messages.forEach { entity ->
            try {
                val hasEncryptedText =
                    !entity.encryptedContent.isNullOrBlank() && !entity.contentNonce.isNullOrBlank()

                val encryptedText = if (hasEncryptedText) {
                    MessageCrypto.EncryptedResult(
                        ciphertext = entity.encryptedContent!!,
                        nonce = entity.contentNonce!!
                    )
                } else {
                    encryptMessageText(entity.content)
                }

                val attachments = when {
                    entity.attachments.isBlank() || entity.attachments == "[]" -> "[]"
                    entity.attachments.startsWith(ATTACHMENT_ENCRYPTION_PREFIX) -> entity.attachments
                    else -> encryptRawAttachments(entity.attachments)
                }

                if (entity.content.isNotEmpty() ||
                    !hasEncryptedText ||
                    attachments != entity.attachments
                ) {
                    chatDao.updateMessage(
                        entity.copy(
                            content = "",
                            encryptedContent = encryptedText.ciphertext,
                            contentNonce = encryptedText.nonce,
                            attachments = attachments
                        )
                    )
                    migrated++
                }
            } catch (e: Exception) {
                // Do not delete data that could not be encrypted. It remains readable and the
                // migration will be retried on the next app start.
                Log.e(TAG, "Failed to encrypt legacy message ${entity.id}", e)
            }
        }

        if (migrated > 0) {
            Log.i(TAG, "Encrypted $migrated legacy chat message(s)")
        }
    }

    private fun encryptMessageText(plaintext: String): MessageCrypto.EncryptedResult {
        return try {
            crypto.encrypt(context, plaintext)
        } catch (e: Exception) {
            throw IllegalStateException("Unable to encrypt message on this device", e)
        }
    }

    private fun decryptMessage(entity: ChatMessageEntity): ChatMessage {
        val decryptedContent = if (entity.encryptedContent != null && entity.contentNonce != null) {
            try {
                crypto.decrypt(context, MessageCrypto.EncryptedResult(
                    ciphertext = entity.encryptedContent!!,
                    nonce = entity.contentNonce!!
                ))
            } catch (e: Exception) {
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
            timestamp = entity.timestamp,
            attachments = decodeAttachments(entity.attachments)
        )
    }

    private suspend fun deleteAttachmentFiles(entities: List<ChatMessageEntity>) {
        val attachments = entities.flatMap { decodeAttachments(it.attachments) }
        if (attachments.isNotEmpty()) {
            attachmentProcessor.deleteAttachments(attachments)
        }
    }

    private fun decodeAttachments(raw: String?): List<Attachment> {
        if (raw.isNullOrBlank() || raw == "[]") return emptyList()
        return try {
            val plain = if (raw.startsWith(ATTACHMENT_ENCRYPTION_PREFIX)) {
                decryptRawAttachments(raw)
            } else {
                raw
            }
            json.decodeFromString(attachmentListSerializer, plain)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode attachments", e)
            emptyList()
        }
    }

    private fun encodeAttachments(attachments: List<Attachment>): String {
        if (attachments.isEmpty()) return "[]"
        val raw = json.encodeToString(attachmentListSerializer, attachments)
        return encryptRawAttachments(raw)
    }

    private fun encryptRawAttachments(raw: String): String {
        val encrypted = try {
            crypto.encrypt(context, raw)
        } catch (e: Exception) {
            throw IllegalStateException("Unable to encrypt attachment metadata", e)
        }
        return ATTACHMENT_ENCRYPTION_PREFIX + encrypted.nonce + ":" + encrypted.ciphertext
    }

    private fun decryptRawAttachments(raw: String): String {
        val payload = raw.removePrefix(ATTACHMENT_ENCRYPTION_PREFIX)
        val separator = payload.indexOf(':')
        require(separator > 0 && separator < payload.lastIndex) {
            "Invalid encrypted attachment payload"
        }
        val nonce = payload.substring(0, separator)
        val ciphertext = payload.substring(separator + 1)
        return crypto.decrypt(
            context,
            MessageCrypto.EncryptedResult(ciphertext = ciphertext, nonce = nonce)
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
        timestamp = timestamp,
        attachments = encodeAttachments(attachments)
    )

    companion object {
        private const val TAG = "ChatRepositoryImpl"
        private const val ATTACHMENT_ENCRYPTION_PREFIX = "enc:v1:"
    }
}
