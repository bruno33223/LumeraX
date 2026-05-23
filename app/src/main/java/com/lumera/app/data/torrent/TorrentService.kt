package com.lumera.app.data.torrent

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.lumera.app.BuildConfig

import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class TorrentService : Service() {

    @Inject lateinit var engine: TorrServerEngine

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val api = TorrServerApi()
    private var downloadJob: Job? = null
    private var currentMagnet: String? = null

    companion object {
        private const val TAG = "LumeraTorrent"
        // ~5MB: approximate buffer needed before ExoPlayer renders first frame
        private const val PRELOAD_TARGET_BYTES = 5_242_880f
        var onStreamReady: ((String) -> Unit)? = null
        var onStreamError: ((String) -> Unit)? = null
        var onStreamProgress: ((TorrentProgress) -> Unit)? = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val magnetLink = intent?.getStringExtra("MAGNET_LINK") ?: return START_NOT_STICKY
        val fileIdx = intent.getIntExtra("FILE_IDX", -1)
        val fileName = intent.getStringExtra("FILE_NAME") ?: ""

        try {
            startForegroundService()
            startDownload(magnetLink, fileIdx, fileName)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "Critical error starting service: ${e.message}")
            scope.launch(Dispatchers.Main) {
                onStreamError?.invoke(e.message ?: "Failed to start torrent engine")
            }
            stopSelf()
        }

        return START_STICKY
    }

    private fun startForegroundService() {
        val channelId = "torrent_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Torrent Download", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Lumera Streaming")
            .setContentText("Starting torrent engine...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, notification)
        }
    }

    private fun startDownload(magnet: String, fileIdx: Int, fileName: String = "") {
        val sanitizedMagnet = appendDefaultTrackers(magnet)
        downloadJob?.cancel()

        // Drop previous torrent to free TorrServer's RAM cache
        val previousMagnet = currentMagnet
        currentMagnet = sanitizedMagnet

        downloadJob = scope.launch {
            if (previousMagnet != null && previousMagnet != sanitizedMagnet) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Dropping previous torrent")
                api.dropTorrent(previousMagnet)
            }
            try {
                // Phase 1: Start TorrServer process
                if (BuildConfig.DEBUG) Log.d(TAG, "Starting TorrServer engine...")
                withContext(Dispatchers.Main) {
                    onStreamProgress?.invoke(TorrentProgress(status = "Starting engine..."))
                }
                engine.start()
                val prefs = getSharedPreferences("lumera_prefs", android.content.Context.MODE_PRIVATE)
                val cacheMb = prefs.getInt("torrserver_cache_mb", 200)
                api.applyOptimalSettings(cacheMb)

                // Phase 2: Add torrent
                if (BuildConfig.DEBUG) Log.d(TAG, "Adding magnet: ${sanitizedMagnet.take(120)}...")
                withContext(Dispatchers.Main) {
                    onStreamProgress?.invoke(TorrentProgress(status = "Fetching metadata..."))
                }
                api.addTorrent(sanitizedMagnet)

                // Phase 3: Resolve correct video file, then start streaming
                val targetFileIndex = resolveFileIndex(sanitizedMagnet, fileIdx, fileName)
                if (BuildConfig.DEBUG) Log.d(TAG, "Streaming file index: $targetFileIndex")

                val streamUrl = api.getStreamUrl(sanitizedMagnet, targetFileIndex)
                updateNotification("Streaming...")
                withContext(Dispatchers.Main) {
                    onStreamProgress?.invoke(TorrentProgress(status = "Starting playback..."))
                    onStreamReady?.invoke(streamUrl)
                }

                // Phase 4: Poll progress until cancelled
                while (isActive) {
                    delay(1000)
                    try {
                        val stats = api.getTorrentStats(sanitizedMagnet)
                        // Show determinate progress only while preloading (< target)
                        val progress = if (stats.preloadedBytes in 1 until PRELOAD_TARGET_BYTES.toLong()) {
                            stats.preloadedBytes.toFloat() / PRELOAD_TARGET_BYTES
                        } else null
                        withContext(Dispatchers.Main) {
                            onStreamProgress?.invoke(
                                TorrentProgress(
                                    status = stats.statusText(),
                                    downloadSpeed = stats.downloadSpeed,
                                    peers = stats.activePeers,
                                    seeds = stats.connectedSeeders,
                                    progress = progress
                                )
                            )
                        }
                    } catch (_: Exception) {}
                }
            } catch (e: CancellationException) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Download coroutine cancelled")
                throw e
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) Log.e(TAG, "Error in download: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    onStreamError?.invoke("Torrent error: ${e.message}")
                }
                stopSelf()
            }
        }
    }

    private val videoExtensions = setOf("mkv", "mp4", "avi", "webm", "ts", "m4v", "mov", "wmv", "flv")

    private suspend fun resolveFileIndex(magnet: String, hintIdx: Int, hintName: String = ""): Int {
        val deadline = System.currentTimeMillis() + 45_000L
        while (System.currentTimeMillis() < deadline) {
            val files = api.getFileList(magnet)
            if (files.isNotEmpty()) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "File list (${files.size} files), hintIdx=$hintIdx, hintName=$hintName:")
                    files.forEach { f -> Log.d(TAG, "  id=${f.id} path=${f.path} size=${f.length / 1024 / 1024}MB") }
                }
                val videoFiles = files.filter { f ->
                    val ext = f.path.substringAfterLast('.', "").lowercase()
                    ext in videoExtensions
                }
                // Strategy 0: match by filename from behaviorHints (most reliable —
                // immune to TorrServer reordering files alphabetically)
                if (hintName.isNotEmpty()) {
                    val byName = videoFiles.firstOrNull {
                        it.path.endsWith(hintName, ignoreCase = true) ||
                        it.path.substringAfterLast('/').equals(hintName, ignoreCase = true)
                    }
                    if (byName != null) {
                        if (BuildConfig.DEBUG) Log.d(TAG, "Using filename hint: ${byName.path} (id=${byName.id})")
                        return byName.id
                    }
                    if (BuildConfig.DEBUG) Log.d(TAG, "Filename hint '$hintName' not found in file list")
                }
                // If addon provided a specific file index (0-based torrent index), use it
                // TorrServer IDs are 1-based, so fileIdx N = TorrServer id N+1
                if (hintIdx >= 0) {
                    // Strategy 1: match by ID offset
                    val byId = videoFiles.firstOrNull { it.id == hintIdx + 1 }
                    if (byId != null) {
                        if (BuildConfig.DEBUG) Log.d(TAG, "Using addon hint (by id): ${byId.path} (id=${byId.id})")
                        return byId.id
                    }
                    // Strategy 2: positional index into full file list
                    if (hintIdx < files.size) {
                        val byPos = files[hintIdx]
                        val ext = byPos.path.substringAfterLast('.', "").lowercase()
                        if (ext in videoExtensions) {
                            if (BuildConfig.DEBUG) Log.d(TAG, "Using addon hint (by pos): ${byPos.path} (id=${byPos.id})")
                            return byPos.id
                        }
                    }
                    if (BuildConfig.DEBUG) Log.w(TAG, "Hint idx=$hintIdx not resolved, falling back to largest")
                }
                // Fallback: pick largest video file
                val target = videoFiles.maxByOrNull { it.length }
                    ?: files.maxByOrNull { it.length }!!
                if (BuildConfig.DEBUG) Log.d(TAG, "Resolved file: ${target.path} (${target.length / 1024 / 1024} MB, id=${target.id})")
                return target.id
            }
            delay(500)
        }
        val fallback = hintIdx.coerceAtLeast(0)
        if (BuildConfig.DEBUG) Log.w(TAG, "Timeout resolving file list, using index $fallback")
        return fallback
    }

    private fun updateNotification(text: String) {
        val channelId = "torrent_channel"
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Lumera Streaming")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .build()
        getSystemService(NotificationManager::class.java)?.notify(1, notification)
    }

    override fun onDestroy() {
        downloadJob?.cancel()
        // Run cleanup synchronously on IO thread — must complete before job is cancelled
        runBlocking(Dispatchers.IO) {
            currentMagnet?.let { api.dropTorrent(it) }
            engine.stop()
        }
        currentMagnet = null
        job.cancel()
        onStreamReady = null
        onStreamError = null
        onStreamProgress = null
        super.onDestroy()
    }

    private val defaultTrackers = listOf(
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://tracker.internetwarriors.net:1337/announce",
        "udp://exodus.desync.com:6969/announce",
        "udp://tracker.cyberia.is:6969/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "udp://tracker.moeking.me:6969/announce",
        "udp://retracker.lanta-net.ru:2710/announce",
        "udp://open.demonii.com:1337/announce",
        "udp://tracker.coppersurfer.tk:6969/announce",
        "http://tracker.openbittorrent.com:80/announce"
    )

    private fun appendDefaultTrackers(magnet: String): String {
        if (!magnet.startsWith("magnet:", ignoreCase = true)) return magnet
        val builder = StringBuilder(magnet)
        for (tracker in defaultTrackers) {
            try {
                val encodedTracker = java.net.URLEncoder.encode(tracker, "UTF-8")
                val trackerParam = "&tr=$encodedTracker"
                if (!magnet.contains(trackerParam, ignoreCase = true) && !magnet.contains(tracker, ignoreCase = true)) {
                    builder.append(trackerParam)
                }
            } catch (_: Exception) {}
        }
        return builder.toString()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
