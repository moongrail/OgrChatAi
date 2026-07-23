package com.mindforge.app.domain.usecase.chat

import com.mindforge.app.domain.repository.ChatRepository
import javax.inject.Inject

class DeleteChatUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(chatId: Long) {
        require(chatId > 0) { "Invalid chat ID" }
        chatRepository.deleteChat(chatId)
    }
}
