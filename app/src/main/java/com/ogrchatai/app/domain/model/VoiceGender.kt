package com.ogrchatai.app.domain.model

enum class VoiceGender(val storageValue: String) {
    FEMALE("female"),
    MALE("male");

    companion object {
        fun fromStorage(value: String?): VoiceGender = when (value) {
            MALE.storageValue -> MALE
            else -> FEMALE
        }
    }
}
