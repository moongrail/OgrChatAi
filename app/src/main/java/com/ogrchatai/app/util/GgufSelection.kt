package com.ogrchatai.app.util

import com.ogrchatai.app.domain.model.Sibling

/**
 * Chooses the GGUF file that the app should actually download.
 *
 * HuggingFace repositories often contain several extra files that are not a
 * complete chat model: multimodal projectors (`mmproj`), speculative-decoding
 * heads (`mtp`), importance matrices, adapters, and split shards. Downloading
 * any of those as if it were the model would either waste space or make the
 * model impossible to load.
 */
object GgufSelection {

    private val QUANTIZATION_PREFERENCE = listOf(
        "q4_k_m",
        "q4_k_s",
        "iq4_xs",
        "iq4_nl",
        "q4_0",
        "q4_1",
        "q5_k_m",
        "q5_k_s",
        "q5_0",
        "q5_1",
        "q3_k_m",
        "q3_k_s",
        "iq3_xs",
        "iq3_xxs",
        "q6_k",
        "q8_0",
        "q2_k",
        "f16",
        "fp16",
        "bf16"
    )

    private val EXCLUDED_NAME_PARTS = listOf(
        "mmproj",
        "mtp",
        "imatrix",
        "adapter",
        "lora"
    )

    private val SPLIT_SHARD = Regex(
        """-\d{5}-of-\d{5}\.gguf$""",
        RegexOption.IGNORE_CASE
    )

    fun downloadable(files: List<Sibling>): List<Sibling> {
        return files.filter { file ->
            val name = file.displayName
            name.endsWith(".gguf", ignoreCase = true) &&
                EXCLUDED_NAME_PARTS.none { name.contains(it, ignoreCase = true) } &&
                !SPLIT_SHARD.containsMatchIn(name)
        }
    }

    fun chooseBest(files: List<Sibling>): Sibling? {
        return downloadable(files)
            .sortedWith(
                compareBy<Sibling> { rank(it) }
                    .thenBy { it.size ?: Long.MAX_VALUE }
            )
            .firstOrNull()
    }

    fun estimatedSize(files: List<Sibling>): Long? = chooseBest(files)?.size

    fun displayName(file: Sibling): String = file.displayName

    private fun rank(file: Sibling): Int {
        val name = file.displayName.lowercase()
        val index = QUANTIZATION_PREFERENCE.indexOfFirst { name.contains(it) }
        return if (index < 0) QUANTIZATION_PREFERENCE.size else index
    }

    private val Sibling.displayName: String
        get() = rfilename.ifBlank { filename }
}
