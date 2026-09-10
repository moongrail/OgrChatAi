package com.ogrchatai.app.domain.repository

import com.ogrchatai.app.domain.model.Chat
import com.ogrchatai.app.domain.model.ChatMessage
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun getChats(): Flow<List<Chat>>
    fun getChatById(id: Long): Flow<Chat?>
    fun getMessages(chatId: Long): Flow<List<ChatMessage>>
    fun getMessagesByModelId(modelId: String): Flow<List<ChatMessage>>
    suspend fun createChat(title: String, modelId: String): Long
    suspend fun updateChat(chat: Chat)
    suspend fun deleteChat(chatId: Long)
    suspend fun deleteAllChats()
    suspend fun insertMessage(message: ChatMessage): Long
    suspend fun updateMessage(message: ChatMessage)
    suspend fun deleteMessage(messageId: Long)
    suspend fun deleteMessagesByChatId(chatId: Long)
    suspend fun getChatCount(): Int
    suspend fun getMessageCount(chatId: Long): Int
    suspend fun updateChatTitle(chatId: Long, title: String)
    suspend fun searchChats(query: String): List<Chat>
}
