package com.ogrchatai.app.util

import android.app.ActivityManager
import android.content.Context

/**
 * Advisory device RAM budget for local LLMs.
 *
 * A GGUF file is not the only cost: llama.cpp also allocates KV cache and
 * compute buffers. The budget is shown as a warning, but it does not block
 * installing or loading a model the user explicitly chooses.
 */
object DeviceMemory {

    fun totalRamMb(context: Context): Long {
        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return 0L
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo.totalMem / (1024L * 1024L)
    }

    fun maxModelBytes(context: Context): Long {
        val totalRamMb = totalRamMb(context)
        return if (totalRamMb > 0L) {
            ModelCompatibility.maxModelBytes(totalRamMb)
        } else {
            Long.MAX_VALUE
        }
    }
}
