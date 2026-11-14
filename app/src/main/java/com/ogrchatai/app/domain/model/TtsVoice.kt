package com.ogrchatai.app.domain.model

/**
 * Voices exposed by the on-device Supertonic 3 model.
 *
 * Speaker ids map to the ten voice styles bundled with the int8 model. The
 * first five are female and the last five are male.
 */
enum class TtsVoice(
    val storageValue: String,
    val speakerId: Int,
    val gender: VoiceGender,
    val displayName: String,
    val description: String
) {
    F1("F1", 0, VoiceGender.FEMALE, "F1 · Warm", "Soft, friendly and natural"),
    F2("F2", 1, VoiceGender.FEMALE, "F2 · Bright", "Clear and energetic"),
    F3("F3", 2, VoiceGender.FEMALE, "F3 · Calm", "Gentle and measured"),
    F4("F4", 3, VoiceGender.FEMALE, "F4 · Confident", "Assured and expressive"),
    F5("F5", 4, VoiceGender.FEMALE, "F5 · Playful", "Lively and light"),
    M1("M1", 5, VoiceGender.MALE, "M1 · Calm", "Relaxed and steady"),
    M2("M2", 6, VoiceGender.MALE, "M2 · Deep", "Warm and low"),
    M3("M3", 7, VoiceGender.MALE, "M3 · Friendly", "Natural and approachable"),
    M4("M4", 8, VoiceGender.MALE, "M4 · Energetic", "Dynamic and clear"),
    M5("M5", 9, VoiceGender.MALE, "M5 · Mature", "Composed and rich");

    companion object {
        fun fromStorage(value: String?): TtsVoice =
            entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) } ?: F1

        fun forGender(gender: VoiceGender): TtsVoice =
            entries.firstOrNull { it.gender == gender } ?: F1
    }
}

enum class TtsEngine(val storageValue: String) {
    /** Supertonic 3 when its model is installed, system TTS as a fallback. */
    AUTO("auto"),

    /** Always use the Android system speech engine. */
    SYSTEM("system");

    companion object {
        fun fromStorage(value: String?): TtsEngine =
            entries.firstOrNull { it.storageValue == value } ?: AUTO
    }
}

/**
 * The only speech languages this app targets. The Supertonic model itself can
 * detect more languages, but the product intentionally supports this smaller,
 * best-tested set.
 */
object TtsLanguages {
    data class Entry(
        val code: String,
        val displayName: String
    )

    val supported: List<Entry> = listOf(
        Entry("en", "English"),
        Entry("ru", "Русский"),
        Entry("es", "Español"),
        Entry("fr", "Français"),
        Entry("de", "Deutsch")
    )

    val codes: Set<String> = supported.mapTo(linkedSetOf()) { it.code }

    val displayList: String = supported.joinToString(", ") { it.displayName }
}
