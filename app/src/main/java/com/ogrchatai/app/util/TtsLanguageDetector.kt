package com.ogrchatai.app.util

import com.ogrchatai.app.domain.model.TtsLanguages
import java.util.Locale

/**
 * Detects the spoken language of a reply so neural and system speech can switch
 * voices automatically. Only the five languages shipped by the app are returned.
 *
 * This is deliberately dependency-free: it combines Unicode script checks,
 * language-specific letters and a small set of high-frequency words. It is used
 * per sentence, never for the whole conversation, so mixed-language answers can
 * still be pronounced naturally.
 */
object TtsLanguageDetector {

    fun detect(text: String, fallback: String = "en"): String {
        val clean = text
            .replace(Regex("[`*_#>|]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (clean.isEmpty()) return normalizedFallback(fallback)

        val letters = clean.count { it.isLetter() }
        if (letters == 0) return normalizedFallback(fallback)

        val cyrillic = clean.count { it in '\u0400'..'\u04FF' }
        if (cyrillic.toDouble() / letters >= CYRILLIC_RATIO) return "ru"

        val lower = clean.lowercase(Locale.ROOT)
        val tokens = TOKEN_REGEX.findAll(lower).map { it.value }.toList()
        val tokenSet = tokens.toSet()

        val scores = mutableMapOf(
            "en" to 0.0,
            "es" to 0.0,
            "fr" to 0.0,
            "de" to 0.0
        )

        // Letters that strongly identify a language.
        if ('ñ' in lower || '¿' in lower || '¡' in lower) scores["es"] = scores.getValue("es") + 3.0
        if ('ç' in lower || 'œ' in lower || 'æ' in lower) scores["fr"] = scores.getValue("fr") + 3.0
        if ('ß' in lower) scores["de"] = scores.getValue("de") + 3.5
        if ('ä' in lower || 'ö' in lower) scores["de"] = scores.getValue("de") + 2.5
        if ('ü' in lower) {
            scores["de"] = scores.getValue("de") + 1.2
            scores["es"] = scores.getValue("es") + 0.4
        }
        if (lower.any { it in "àèùêâîôûëïÿ" }) scores["fr"] = scores.getValue("fr") + 2.0
        if (lower.any { it in "áíóú" }) scores["es"] = scores.getValue("es") + 2.0
        if (lower.contains("l'") || lower.contains("d'") || lower.contains("qu'")) {
            scores["fr"] = scores.getValue("fr") + 1.2
        }

        // High-frequency function words. Count each language separately so a
        // sentence containing "el" is not mistaken for English just because it
        // also contains "is".
        LANGUAGE_WORDS.forEach { (language, words) ->
            var hits = 0
            words.forEach { word ->
                if (word in tokenSet) hits++
            }
            if (hits > 0) {
                scores[language] = scores.getValue(language) + hits * 1.1
            }
        }

        // English is the safe fallback, but only give it a small bonus when it
        // has no competition. This avoids returning English for a short Spanish
        // phrase such as "hola, ¿cómo estás?".
        if (scores.getValue("en") == 0.0 && tokens.size >= 3) {
            scores["en"] = scores.getValue("en") + 0.6
        }

        val best = scores.maxByOrNull { it.value } ?: return normalizedFallback(fallback)
        val second = scores
            .filterKeys { it != best.key }
            .maxByOrNull { it.value }
            ?.value ?: 0.0
        if (best.value < MIN_SCORE || best.value - second < MIN_MARGIN) {
            return normalizedFallback(fallback)
        }
        return best.key
    }

    private fun normalizedFallback(fallback: String): String {
        val code = Locale.forLanguageTag(fallback).language.lowercase(Locale.ROOT)
        return if (code in TtsLanguages.codes) code else "en"
    }

    private const val CYRILLIC_RATIO = 0.18
    private const val MIN_SCORE = 1.0
    private const val MIN_MARGIN = 0.2
    private val TOKEN_REGEX = Regex("[\\p{L}']+")

    private val LANGUAGE_WORDS = mapOf(
        "en" to setOf(
            "the", "and", "is", "are", "you", "your", "this", "that", "with", "have",
            "for", "not", "but", "what", "how", "why", "can", "will", "would", "there"
        ),
        "es" to setOf(
            "el", "la", "los", "las", "de", "que", "y", "en", "un", "una", "por", "para",
            "con", "no", "se", "es", "son", "como", "pero", "muy", "hola", "gracias",
            "está", "esta", "estás", "estas", "también", "tambien", "porque", "cuando"
        ),
        "fr" to setOf(
            "le", "la", "les", "de", "des", "et", "en", "un", "une", "du", "que", "qui",
            "pour", "dans", "avec", "pas", "est", "sont", "ce", "cette", "mais", "très",
            "tres", "bonjour", "merci", "vous", "nous", "je", "tu", "il", "elle", "au"
        ),
        "de" to setOf(
            "der", "die", "das", "und", "ist", "nicht", "ein", "eine", "mit", "sich",
            "auf", "für", "fur", "von", "den", "dem", "des", "auch", "als", "aus", "bei",
            "ich", "du", "er", "sie", "es", "wir", "ihr", "hallo", "danke", "bitte", "sehr"
        )
    )
}
