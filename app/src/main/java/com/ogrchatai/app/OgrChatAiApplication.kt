package com.ogrchatai.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class OgrChatAiApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: OgrChatAiApplication
            private set
    }
}