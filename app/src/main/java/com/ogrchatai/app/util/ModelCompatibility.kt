package com.ogrchatai.app.util

/**
 * Advisory memory budget for local GGUF models.
 *
 * The number is only used to warn the user; it must not hide search results or block a
 * download/load. Actual model memory depends on the architecture, quantisation, context size
 * and backend, and the user may deliberately choose a model above the recommendation.
 */
object ModelCompatibility {

    private const val RAM_BUDGET_FRACTION = 0.55

    fun maxModelBytes(deviceRamMb: Long): Long {
        if (deviceRamMb <= 0L) return Long.MAX_VALUE
        return (deviceRamMb * RAM_BUDGET_FRACTION * 1024.0 * 1024.0).toLong()
    }
}
