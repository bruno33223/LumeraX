package com.lumera.app.domain

import android.app.ActivityManager
import android.content.Context

enum class HardwareTier {
    LOW, MID, HIGH
}

object DeviceCapabilities {

    fun determineHardwareTier(context: Context): HardwareTier {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        
        val totalRamGb = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
        val cores = Runtime.getRuntime().availableProcessors()
        
        return when {
            totalRamGb <= 1.5 || cores <= 2 -> HardwareTier.LOW
            totalRamGb <= 2.5 && cores >= 4 -> HardwareTier.MID
            else -> HardwareTier.HIGH
        }
    }

    fun applyBenchmarkDefaultsIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences("lumera_prefs", Context.MODE_PRIVATE)
        // Only run once
        if (prefs.getBoolean("torrserver_benchmark_done", false)) {
            return
        }

        val tier = determineHardwareTier(context)
        
        val cacheMb = when (tier) {
            HardwareTier.LOW -> 100
            HardwareTier.MID -> 200
            HardwareTier.HIGH -> 400
        }
        
        val connections = when (tier) {
            HardwareTier.LOW -> 250
            HardwareTier.MID -> 500
            HardwareTier.HIGH -> 1000
        }

        prefs.edit()
            .putInt("torrserver_cache_mb", cacheMb)
            .putInt("torrserver_connections_limit", connections)
            .putBoolean("torrserver_benchmark_done", true)
            .apply()
    }
}
