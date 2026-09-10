package com.ogrchatai.app.util

object Constants {

    const val HF_API_BASE_URL = "https://huggingface.co/api/"
    const val HF_API_MODELS_ENDPOINT = "models"
    const val HF_API_TOKEN_ENDPOINT = "oauth/token"

    const val DATABASE_NAME = "chat_llm_database"
    const val DATASTORE_NAME = "chat_llm_preferences"

    const val DEFAULT_MAX_TOKENS = 1024
    const val DEFAULT_TEMPERATURE = 0.7f
    const val DEFAULT_TOP_P = 0.9f
    const val DEFAULT_TOP_K = 50
    const val DEFAULT_REPETITION_PENALTY = 1.1f

    const val MAX_CONVERSATION_HISTORY = 20
    const val MAX_MESSAGE_LENGTH = 10000
    const val TYPING_INDICATOR_DELAY = 1000L

    const val MODEL_CACHE_DIR = "models"
    const val CHAT_EXPORT_DIR = "chat_exports"
    const val DEFAULT_MODEL_ID = "TinyLlama/TinyLlama-1.1B-Chat-v1.0"

    const val PREF_KEY_API_TOKEN = "api_token"
    const val PREF_KEY_DEFAULT_MODEL = "default_model"
    const val PREF_KEY_THEME_MODE = "theme_mode"
    const val PREF_KEY_FONT_SIZE = "font_size"
    const val PREF_KEY_SHOW_SYSTEM_MESSAGES = "show_system_messages"
    const val PREF_KEY_AUTO_SAVE_CHATS = "auto_save_chats"
    const val PREF_KEY_STREAM_RESPONSES = "stream_responses"

    val SUPPORTED_MODEL_ARCHITECTURES = listOf(
        "llama",
        "mistral",
        "gemma",
        "phi",
        "qwen",
        "falcon",
        "mpt",
        "gpt2",
        "bloom",
        "opt"
    )

    val QUANTIZATION_TYPES = listOf(
        "fp16",
        "fp32",
        "int8",
        "int4",
        "q4_0",
        "q4_1",
        "q4_k_m",
        "q5_0",
        "q5_1",
        "q5_k_m",
        "q8_0",
        "gguf"
    )

    const val HTTP_TIMEOUT_CONNECT = 60L
    const val HTTP_TIMEOUT_READ = 120L
    const val HTTP_TIMEOUT_WRITE = 120L

    const val NOTIFICATION_CHANNEL_ID = "chat_llm_channel"
    const val NOTIFICATION_ID = 1001
}
