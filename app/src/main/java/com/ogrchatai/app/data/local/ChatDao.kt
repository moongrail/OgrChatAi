package com.ogrchatai.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ogrchatai.app.data.local.entity.ChatEntity
import com.ogrchatai.app.data.local.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Query("SELECT * FROM chats ORDER BY updated_at DESC")
    fun getAllChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE id = :chatId")
    fun getChatById(chatId: Long): Flow<ChatEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatEntity): Long

    @Update
    suspend fun updateChat(chat: ChatEntity)

    @Query("DELETE FROM chats WHERE id = :chatId")
    suspend fun deleteChatById(chatId: Long)

    @Query("DELETE FROM chats")
    suspend fun deleteAllChats()

    @Query("SELECT * FROM chat_messages WHERE chat_id = :chatId ORDER BY timestamp ASC")
    fun getMessagesByChatId(chatId: Long): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE chat_id = :chatId ORDER BY timestamp ASC")
    suspend fun getMessagesByChatIdList(chatId: Long): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: Long): ChatMessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessageEntity>)

    @Update
    suspend fun updateMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: Long)

    @Query("DELETE FROM chat_messages WHERE chat_id = :chatId")
    suspend fun deleteMessagesByChatId(chatId: Long)

    @Query("SELECT COUNT(*) FROM chat_messages WHERE chat_id = :chatId")
    suspend fun getMessageCount(chatId: Long): Int

    @Query("UPDATE chats SET updated_at = :timestamp WHERE id = :chatId")
    suspend fun updateChatTimestamp(chatId: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE chats SET title = :title, updated_at = :timestamp WHERE id = :chatId")
    suspend fun updateChatTitle(chatId: Long, title: String, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT * FROM chat_messages WHERE chat_id = :chatId ORDER BY timestamp DESC LIMIT 1")
    fun getLastMessage(chatId: Long): Flow<ChatMessageEntity?>

    @Query("SELECT * FROM chats WHERE model_id = :modelId ORDER BY updated_at DESC")
    fun getChatsByModelId(modelId: String): Flow<List<ChatEntity>>
}
