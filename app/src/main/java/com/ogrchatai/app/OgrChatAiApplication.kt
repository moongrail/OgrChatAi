package com.ogrchatai.app

import android.app.Application
import com.ogrchatai.app.domain.repository.ChatRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class OgrChatAiApplication : Application() {

    @Inject
    lateinit var chatRepository: ChatRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        applicationScope.launch {
            runCatching { chatRepository.migrateLegacyMessages() }
        }
    }

    companion object {
        lateinit var instance: OgrChatAiApplication
            private set
    }
}
