package com.mindforge.app.domain.usecase.chat

import com.mindforge.app.domain.model.ChatMessage
import com.mindforge.app.domain.repository.ChatRepository
import javax.inject.Inject

class GetMessagesUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(chatId: String): Result<List<ChatMessage>> {
        return runCatching {
            val id = chatId.toLongOrNull() ?: throw IllegalArgumentException("Invalid chat ID")
            chatRepository.getMessages(id)
                .let { flow ->
                    kotlinx.coroutines.flow.first(flow) { true }
                }
        }
    }
}
