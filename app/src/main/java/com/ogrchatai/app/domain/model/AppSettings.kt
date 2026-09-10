package com.ogrchatai.app.domain.model

data class AppSettings(
    val language: Language = Language.ENGLISH,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val generationConfig: GenerationConfig = GenerationConfig()
)
