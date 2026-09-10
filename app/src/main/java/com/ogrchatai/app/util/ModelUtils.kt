package com.ogrchatai.app.util

import java.text.DecimalFormat

object ModelUtils {

    fun parseModelId(fullModelId: String): ModelInfo {
        val parts = fullModelId.split("/")
        return if (parts.size >= 2) {
            ModelInfo(
                organization = parts[0],
                modelName = parts[1],
                fullId = fullModelId,
                shortName = parts[1]
            )
        } else {
            ModelInfo(
                organization = "",
                modelName = fullModelId,
                fullId = fullModelId,
                shortName = fullModelId
            )
        }
    }

    fun getQuantizationFromName(modelName: String): String? {
        val lowerName = modelName.lowercase()
        for (quant in Constants.QUANTIZATION_TYPES) {
            if (lowerName.contains(quant)) {
                return quant
            }
        }
        if (lowerName.contains("gguf")) return "gguf"
        if (lowerName.contains("awq")) return "awq"
        if (lowerName.contains("gptq")) return "gptq"
        if (lowerName.contains("exl2")) return "exl2"
        return null
    }

    fun getArchitectureFromName(modelName: String): String? {
        val lowerName = modelName.lowercase()
        for (arch in Constants.SUPPORTED_MODEL_ARCHITECTURES) {
            if (lowerName.contains(arch)) {
                return arch
            }
        }
        return null
    }

    fun formatFileSize(sizeInBytes: Long): String {
        if (sizeInBytes < 0) return "0 B"

        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = sizeInBytes.toDouble()
        var unitIndex = 0

        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }

        val format = DecimalFormat("#.##")
        return "${format.format(size)} ${units[unitIndex]}"
    }

    fun formatModelSize(sizeInGB: Double): String {
        return when {
            sizeInGB < 1.0 -> "${(sizeInGB * 1024).toInt()} MB"
            sizeInGB < 1024.0 -> "${DecimalFormat("#.##").format(sizeInGB)} GB"
            else -> "${DecimalFormat("#.##").format(sizeInGB / 1024)} TB"
        }
    }

    fun estimateModelSizeQuantization(baseModelSize: Double, quantization: String): Double {
        return when (quantization.lowercase()) {
            "fp32" -> baseModelSize
            "fp16" -> baseModelSize * 0.5
            "int8" -> baseModelSize * 0.25
            "int4", "q4_0", "q4_1", "q4_k_m" -> baseModelSize * 0.125
            "q5_0", "q5_1", "q5_k_m" -> baseModelSize * 0.15625
            "q8_0" -> baseModelSize * 0.25
            else -> baseModelSize * 0.25
        }
    }

    fun isModelCompatibleWithDevice(
        modelSizeBytes: Long,
        availableRamBytes: Long,
        quantization: String?
    ): ModelCompatibility {
        val estimatedRamNeeded: Double = when (quantization?.lowercase()) {
            "fp32" -> modelSizeBytes * 4.0
            "fp16" -> modelSizeBytes * 2.0
            "int8" -> modelSizeBytes * 1.5
            "int4", "q4_0", "q4_1", "q4_k_m" -> modelSizeBytes * 1.2
            else -> modelSizeBytes * 2.0
        }

        return when {
            availableRamBytes > (estimatedRamNeeded * 2).toLong() -> ModelCompatibility.FULL
            availableRamBytes > estimatedRamNeeded.toLong() -> ModelCompatibility.LIMITED
            availableRamBytes > (estimatedRamNeeded * 0.7).toLong() -> ModelCompatibility.MARGINAL
            else -> ModelCompatibility.INCOMPATIBLE
        }
    }

    fun getModelDisplayName(modelId: String): String {
        val info = parseModelId(modelId)
        val quantization = getQuantizationFromName(modelId)
        val arch = getArchitectureFromName(modelId)

        val displayName = StringBuilder()
        if (info.organization.isNotEmpty()) {
            displayName.append("${info.organization}/")
        }
        displayName.append(info.modelName)

        if (quantization != null) {
            displayName.append(" ($quantization)")
        }

        return displayName.toString()
    }

    fun extractModelParameters(modelName: String): String? {
        val paramPatterns = listOf(
            Regex("(\\d+\\.?\\d*)[Bb]"),
            Regex("(\\d+\\.?\\d*)[Mm]"),
            Regex("(\\d+)(?: billion| million)", RegexOption.IGNORE_CASE)
        )

        for (pattern in paramPatterns) {
            val match = pattern.find(modelName)
            if (match != null) {
                return match.value
            }
        }
        return null
    }

    fun isLegacyModel(modelName: String): Boolean {
        val lowerName = modelName.lowercase()
        return lowerName.contains("legacy") ||
                lowerName.contains("v1") ||
                lowerName.contains("old")
    }

    fun getModelVersion(modelName: String): String? {
        val versionPattern = Regex("[vV](\\d+\\.?\\d*)")
        val match = versionPattern.find(modelName)
        return match?.groupValues?.get(1)
    }

    fun sortByRelevance(models: List<ModelInfo>, query: String): List<ModelInfo> {
        val lowerQuery = query.lowercase()
        return models.sortedByDescending { model ->
            var score = 0
            if (model.modelName.lowercase().contains(lowerQuery)) score += 10
            if (model.organization.lowercase().contains(lowerQuery)) score += 5
            if (model.fullId.lowercase().contains(lowerQuery)) score += 3
            score
        }
    }

    data class ModelInfo(
        val organization: String,
        val modelName: String,
        val fullId: String,
        val shortName: String
    )

    enum class ModelCompatibility {
        FULL,
        LIMITED,
        MARGINAL,
        INCOMPATIBLE
    }
}
