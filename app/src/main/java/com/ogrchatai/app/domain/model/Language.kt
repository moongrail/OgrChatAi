package com.ogrchatai.app.domain.model

enum class Language(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    RUSSIAN("ru", "\u0420\u0443\u0441\u0441\u043a\u0438\u0439");

    companion object {
        fun fromCode(code: String): Language =
            entries.find { it.code == code } ?: ENGLISH
    }
}
