package com.ogrchatai.app.domain.usecase.chat

import com.ogrchatai.app.domain.repository.ChatRepository
import javax.inject.Inject

class DeleteChatUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(chatId: Long) {
        require(chatId > 0) { "Invalid chat ID" }
        chatRepository.deleteChat(chatId)
    }
}
