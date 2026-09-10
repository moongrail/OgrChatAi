package com.ogrchatai.app.domain.usecase.chat

import com.ogrchatai.app.domain.repository.ChatRepository
import com.ogrchatai.app.util.ChatNamingUtil
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class AutoNameChatUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(chatId: Long): Result<Unit> {
        return try {
            val messages = chatRepository.getMessages(chatId).first()
            ChatNamingUtil.generateChatTitle(messages)?.let { title ->
                chatRepository.updateChatTitle(chatId, title)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
