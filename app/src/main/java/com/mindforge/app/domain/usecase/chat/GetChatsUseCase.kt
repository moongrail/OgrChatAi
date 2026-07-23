package com.mindforge.app.domain.usecase.chat

import com.mindforge.app.domain.model.Chat
import com.mindforge.app.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetChatsUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    operator fun invoke(): Flow<List<Chat>> {
        return chatRepository.getChats()
    }
}
