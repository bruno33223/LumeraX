package com.lumera.app.data.torrent

import android.content.Context
import android.util.Log
import com.lumera.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

object TrackerFetcher {

    private const val TRACKERS_URL = "https://raw.githubusercontent.com/ngosang/trackerslist/master/trackers_best.txt"
    private const val PREFS_NAME = "lumera_trackers_cache"
    private const val KEY_TRACKERS = "best_trackers"
    private const val KEY_LAST_UPDATE = "last_update_ms"
    private const val UPDATE_INTERVAL_MS = 24L * 60L * 60L * 1000L // 24 hours

    suspend fun updateTrackersIfNeeded(context: Context) {
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastUpdate = prefs.getLong(KEY_LAST_UPDATE, 0L)
            
            if (System.currentTimeMillis() - lastUpdate > UPDATE_INTERVAL_MS) {
                try {
                    val rawText = URL(TRACKERS_URL).readText(charset = Charsets.UTF_8)
                    val trackers = rawText.lines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && (it.startsWith("udp://") || it.startsWith("http://") || it.startsWith("https://")) }
                    
                    if (trackers.isNotEmpty()) {
                        prefs.edit()
                            .putString(KEY_TRACKERS, trackers.joinToString(separator = ","))
                            .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
                            .apply()
                        if (BuildConfig.DEBUG) Log.d("LumeraTorrent", "Updated dynamic trackers list (${trackers.size} trackers)")
                    }
                } catch (e: Exception) {
                    if (BuildConfig.DEBUG) Log.w("LumeraTorrent", "Failed to fetch trackers: ${e.message}")
                }
            }
        }
    }

    fun getCachedTrackers(context: Context): List<String>? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_TRACKERS, null)
        if (stored.isNullOrBlank()) return null
        return stored.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
}
