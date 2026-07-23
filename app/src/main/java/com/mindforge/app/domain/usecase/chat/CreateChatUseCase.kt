package com.mindforge.app.domain.usecase.chat

import com.mindforge.app.domain.repository.ChatRepository
import javax.inject.Inject

class CreateChatUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    suspend operator fun invoke(title: String, modelId: String): Long {
        require(title.isNotBlank()) { "Chat title cannot be blank" }
        require(modelId.isNotBlank()) { "Model ID cannot be blank" }
        return chatRepository.createChat(title, modelId)
    }
}
