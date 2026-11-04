package com.ogrchatai.app.util

/**
 * Rough, device-side compatibility check for GGUF text models.
 *
 * The HuggingFace search endpoint returns only model names/tags, not file sizes, so we use the
 * parameter count from the repository name. A Q4_K_M GGUF needs roughly 0.65 GB per billion
 * parameters. Models whose names/tags do not expose a size are hidden because we cannot promise
 * that they fit in the device's memory.
 */
object ModelCompatibility {

    private const val BYTES_PER_GB = 1024.0 * 1024.0 * 1024.0
    private const val Q4_GB_PER_BILLION_PARAMS = 0.65
    private const val RAM_BUDGET_FRACTION = 0.60

    private val PARAMS_PATTERN = Regex(
        "(?i)(\\d+(?:\\.\\d+)?)\\s*b(?=[\\s._-]|$)"
    )

    fun estimateParametersBillions(name: String): Double? {
        return PARAMS_PATTERN.find(name)
            ?.groupValues
            ?.getOrNull(1)
            ?.toDoubleOrNull()
            ?.takeIf { it > 0.0 }
    }

    fun maxModelBytes(deviceRamMb: Long): Long {
        if (deviceRamMb <= 0L) return Long.MAX_VALUE
        return (deviceRamMb * RAM_BUDGET_FRACTION * 1024.0 * 1024.0).toLong()
    }

    fun estimatedQ4Bytes(parametersBillions: Double): Long {
        return (parametersBillions * Q4_GB_PER_BILLION_PARAMS * BYTES_PER_GB).toLong()
    }

    fun isLikelyCompatible(modelName: String, deviceRamMb: Long): Boolean {
        // If the name/tags do not expose a parameter count, we cannot honestly promise that the
        // model fits. Hide it rather than offering a multi-gigabyte download that will fail.
        val parameters = estimateParametersBillions(modelName) ?: return false
        return estimatedQ4Bytes(parameters) <= maxModelBytes(deviceRamMb)
    }
}
