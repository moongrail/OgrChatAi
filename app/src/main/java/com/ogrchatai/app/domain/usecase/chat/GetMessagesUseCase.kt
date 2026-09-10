package com.ogrchatai.app.domain.usecase.chat

import com.ogrchatai.app.domain.model.ChatMessage
import com.ogrchatai.app.domain.repository.ChatRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class GetMessagesUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(chatId: String): Result<List<ChatMessage>> {
        return runCatching {
            val id = chatId.toLongOrNull() ?: throw IllegalArgumentException("Invalid chat ID")
            chatRepository.getMessages(id).first()
        }
    }
}
