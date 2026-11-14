package com.ogrchatai.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TtsLanguageDetectorTest {

    @Test
    fun detectsEnglish() {
        assertEquals(
            "en",
            TtsLanguageDetector.detect("Hello! How can I help you today?")
        )
    }

    @Test
    fun detectsRussian() {
        assertEquals(
            "ru",
            TtsLanguageDetector.detect("Привет! Как у тебя дела? Спасибо за помощь.")
        )
    }

    @Test
    fun detectsSpanish() {
        assertEquals(
            "es",
            TtsLanguageDetector.detect("Hola, ¿cómo estás? Gracias por tu ayuda.")
        )
    }

    @Test
    fun detectsFrench() {
        assertEquals(
            "fr",
            TtsLanguageDetector.detect("Bonjour, comment allez-vous ? Je vous remercie.")
        )
    }

    @Test
    fun detectsGerman() {
        assertEquals(
            "de",
            TtsLanguageDetector.detect("Hallo, wie geht es dir? Vielen Dank für deine Hilfe.")
        )
    }

    @Test
    fun usesFallbackForTextWithoutLetters() {
        assertEquals("de", TtsLanguageDetector.detect("42 ... !!!", fallback = "de"))
        assertEquals("es", TtsLanguageDetector.detect("", fallback = "es"))
    }

    @Test
    fun unsupportedFallbackBecomesEnglish() {
        assertEquals("en", TtsLanguageDetector.detect("123", fallback = "uk"))
    }

    @Test
    fun recognizesMixedSentenceLanguage() {
        assertEquals(
            "ru",
            TtsLanguageDetector.detect("Вот результат: 42. Готово!")
        )
    }
}
