package com.lumera.app.data.torrent

import android.util.Log
import com.lumera.app.BuildConfig
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class TorrServerApi(private val baseUrl: String = "http://127.0.0.1:8090") {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun addTorrent(magnetLink: String, title: String = ""): JsonObject =
        withContext(Dispatchers.IO) {
            val body = JsonObject().apply {
                addProperty("action", "add")
                addProperty("link", magnetLink)
                addProperty("title", title)
                addProperty("save_to_db", false)
            }
            val request = Request.Builder()
                .url("$baseUrl/torrents")
                .post(body.toString().toRequestBody(jsonType))
                .build()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: "{}"
            response.close()
            if (!response.isSuccessful) {
                throw Exception("Failed to add torrent: HTTP ${response.code}")
            }
            JsonParser.parseString(responseBody).asJsonObject
        }

    suspend fun applyOptimalSettings(cacheSizeMb: Int = 200, connectionsLimit: Int = 500): Unit = withContext(Dispatchers.IO) {
        val cacheBytes = cacheSizeMb.toLong() * 1024 * 1024
        val body = JsonObject().apply {
            addProperty("action", "set")
            add("sets", JsonObject().apply {
                addProperty("CacheSize", cacheBytes) // Dynamic RAM Cache
                addProperty("ReaderReadAHead", 90) // Increased for more aggressive read ahead
                addProperty("PreloadCache", 30) // Preload 30% of cache to ensure buffer stability
                addProperty("ForceAllPeers", true) // Ensure it queries all possible peers for rare torrents
                addProperty("ConnectionsLimit", connectionsLimit) // Increased for max peer discovery
                addProperty("DhtConnectionLimit", connectionsLimit) // Increased for better DHT discovery
                addProperty("PeersListenPort", 0)
                addProperty("EnableIPv6", false) // IPv6 sometimes causes slow peer discovery timeout
                addProperty("DisableUPNP", false) // Crucial for getting inbound connections from peers
                addProperty("DisableUTP", false) // Crucial for connecting to uTP-only peers
                addProperty("LimitSpeed", 0) // Explicitly remove any internal speed limit
                addProperty("TorrentDisconnectTimeout", 3600) // Keep torrent alive in RAM (up to 1 hour) when paused!
                addProperty("RetrackersMode", 1) // Allow local retrackers if available
            })
        }
        val request = Request.Builder()
            .url("$baseUrl/settings")
            .post(body.toString().toRequestBody(jsonType))
            .build()
        try {
            client.newCall(request).execute().close()
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.w("TorrServerApi", "Failed to apply settings", e)
        }
    }

    suspend fun getTorrentStats(magnetLink: String): TorrentStats =
        withContext(Dispatchers.IO) {
            val hash = extractHash(magnetLink)
            val body = JsonObject().apply {
                addProperty("action", "get")
                addProperty("hash", hash)
            }
            val request = Request.Builder()
                .url("$baseUrl/torrents")
                .post(body.toString().toRequestBody(jsonType))
                .build()
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: "{}"
            response.close()
            if (!response.isSuccessful) {
                return@withContext TorrentStats()
            }
            parseTorrentStats(JsonParser.parseString(responseBody).asJsonObject)
        }

    suspend fun dropTorrent(magnetLink: String) = withContext(Dispatchers.IO) {
        val hash = extractHash(magnetLink)
        val body = JsonObject().apply {
            addProperty("action", "rem") // 'rem' explicitly removes from memory and stops all peer connections
            addProperty("hash", hash)
        }
        val request = Request.Builder()
            .url("$baseUrl/torrents")
            .post(body.toString().toRequestBody(jsonType))
            .build()
        try {
            client.newCall(request).execute().close()
        } catch (_: Exception) {}
    }

    fun getStreamUrl(magnetLink: String, fileIndex: Int): String {
        val encoded = URLEncoder.encode(magnetLink, "UTF-8")
        return "$baseUrl/stream?link=$encoded&index=$fileIndex&play"
    }

    suspend fun getFileList(magnetLink: String): List<TorrServerFile> =
        withContext(Dispatchers.IO) {
            // Use info hash for lookup — TorrServer matches by hash, not full magnet
            val hash = extractHash(magnetLink)
            val body = JsonObject().apply {
                addProperty("action", "get")
                addProperty("hash", hash)
            }
            val request = Request.Builder()
                .url("$baseUrl/torrents")
                .post(body.toString().toRequestBody(jsonType))
                .build()
            try {
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: "{}"
                response.close()
                if (!response.isSuccessful) {
                    if (BuildConfig.DEBUG) Log.w("LumeraTorrent", "getFileList HTTP ${response.code}")
                    return@withContext emptyList()
                }

                val json = JsonParser.parseString(responseBody).asJsonObject
                if (BuildConfig.DEBUG) Log.v("LumeraTorrent", "getFileList stat=${json.get("stat")}, file_stats=${json.has("file_stats")}")
                val files = json.getAsJsonArray("file_stats") ?: return@withContext emptyList()
                if (BuildConfig.DEBUG) Log.v("LumeraTorrent", "file_stats count: ${files.size()}")
                files.mapIndexed { index, element ->
                    val file = element.asJsonObject
                    TorrServerFile(
                        id = file.get("id")?.asInt ?: index,
                        path = file.get("path")?.asString ?: "",
                        length = file.get("length")?.asLong ?: 0L
                    )
                }
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.w("LumeraTorrent", "getFileList error: ${e.message}")
                emptyList()
            }
        }

    internal fun extractHash(magnetLink: String): String {
        val cleanedLink = magnetLink.trim()
        val btihPrefix = "urn:btih:"
        val xtIndex = cleanedLink.indexOf(btihPrefix, ignoreCase = true)
        
        val rawHash = if (xtIndex != -1) {
            val start = xtIndex + btihPrefix.length
            val end = cleanedLink.indexOf('&', start).let { if (it == -1) cleanedLink.length else it }
            cleanedLink.substring(start, end).trim()
        } else {
            cleanedLink
        }

        if (rawHash.length == 40 && rawHash.all { it.isDigit() || (it in 'a'..'f') || (it in 'A'..'F') }) {
            return rawHash.lowercase()
        }

        if (rawHash.length == 32 && rawHash.all { it.isDigit() || (it in 'a'..'z') || (it in 'A'..'Z') }) {
            try {
                return base32ToHex(rawHash)
            } catch (_: Exception) {}
        }

        val regex = Regex("btih:([a-fA-F0-9]{40})", RegexOption.IGNORE_CASE)
        return regex.find(cleanedLink)?.groupValues?.get(1)?.lowercase() ?: cleanedLink
    }

    internal fun base32ToHex(base32: String): String {
        val base32Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val clean = base32.uppercase()
        var bits = 0
        var value = 0
        val hex = StringBuilder()
        
        for (char in clean) {
            val idx = base32Chars.indexOf(char)
            if (idx == -1) continue
            value = (value shl 5) or idx
            bits += 5
            if (bits >= 8) {
                bits -= 8
                val byteVal = (value shr bits) and 0xFF
                hex.append(String.format("%02x", byteVal))
            }
        }
        return hex.toString()
    }

    private fun parseTorrentStats(json: JsonObject): TorrentStats {
        return TorrentStats(
            stat = json.get("stat")?.asInt ?: 0,
            activePeers = json.get("active_peers")?.asInt ?: 0,
            totalPeers = json.get("total_peers")?.asInt ?: 0,
            connectedSeeders = json.get("connected_seeders")?.asInt ?: 0,
            downloadSpeed = json.get("download_speed")?.asLong ?: 0L,
            uploadSpeed = json.get("upload_speed")?.asLong ?: 0L,
            bytesRead = json.get("bytes_read")?.asLong ?: 0L,
            torrentSize = json.get("torrent_size")?.asLong ?: 0L,
            preloadedBytes = json.get("preloaded_bytes")?.asLong ?: 0L
        )
    }
}

data class TorrentStats(
    val stat: Int = 0,          // 0=Added, 1=GettingInfo, 2=Preload, 3=Working, 4=Closed
    val activePeers: Int = 0,
    val totalPeers: Int = 0,
    val connectedSeeders: Int = 0,
    val downloadSpeed: Long = 0L,
    val uploadSpeed: Long = 0L,
    val bytesRead: Long = 0L,
    val torrentSize: Long = 0L,
    val preloadedBytes: Long = 0L
) {
    fun statusText(): String = when (stat) {
        0 -> "Connecting to peers..."
        1 -> "Fetching metadata..."
        2 -> "Buffering..."
        3 -> "Streaming"
        4 -> "Stopped"
        else -> "Connecting..."
    }
}

data class TorrServerFile(
    val id: Int,
    val path: String,
    val length: Long
)
