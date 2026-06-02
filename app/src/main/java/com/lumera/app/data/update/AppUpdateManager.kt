package com.lumera.app.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.lumera.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class UpdateInfo(
    val versionName: String,
    val changelog: String,
    val apkUrl: String
)

sealed class UpdateState {
    data object Idle : UpdateState()
    data object Checking : UpdateState()
    data class UpdateAvailable(val info: UpdateInfo) : UpdateState()
    data object UpToDate : UpdateState()
    data class Downloading(val progress: Float, val downloadedMb: Float = 0f, val totalMb: Float = 0f) : UpdateState()
    data class ReadyToInstall(val file: File) : UpdateState()
    data class Error(val message: String) : UpdateState()
}

// GitHub Releases API response (only fields we need)
private data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("body") val body: String?,
    @SerializedName("assets") val assets: List<GitHubAsset>
)

private data class GitHubAsset(
    @SerializedName("browser_download_url") val downloadUrl: String,
    @SerializedName("name") val name: String
)

@Singleton
class AppUpdateManager @Inject constructor(
    private val okHttpClient: OkHttpClient,
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("lumera_update_prefs", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state = _state.asStateFlow()

    val isPopupEnabled: Boolean
        get() = prefs.getBoolean(KEY_UPDATE_POPUP_ENABLED, true)

    fun setPopupEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_UPDATE_POPUP_ENABLED, enabled).apply()
    }

    private val gson = Gson()

    /** Cached release body from the last checkForUpdate, used for checksum extraction. */
    @Volatile
    private var lastReleaseBody: String? = null

    suspend fun checkForUpdate(force: Boolean = false) {
        _state.value = UpdateState.Checking
        try {
            val release = withContext(Dispatchers.IO) { fetchLatestRelease() }
            if (release == null) {
                _state.value = UpdateState.UpToDate
                return
            }

            val remoteVersion = release.tagName.removePrefix("v")
            val currentVersion = BuildConfig.VERSION_NAME

            if (force || remoteVersion != currentVersion) {
                val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") }
                if (apkAsset != null) {
                    if (!isAllowedDownloadUrl(apkAsset.downloadUrl)) {
                        _state.value = UpdateState.Error("Update URL is not from a trusted source.")
                        return
                    }
                    lastReleaseBody = release.body
                    _state.value = UpdateState.UpdateAvailable(
                        UpdateInfo(
                            versionName = remoteVersion,
                            changelog = stripHashFromChangelog(release.body),
                            apkUrl = apkAsset.downloadUrl
                        )
                    )
                } else {
                    _state.value = UpdateState.Error("Release found but no APK attached.")
                }
            } else {
                _state.value = UpdateState.UpToDate
            }
        } catch (e: Exception) {
            _state.value = UpdateState.Error(e.message ?: "Failed to check for updates.")
        }
    }

    suspend fun downloadAndInstall(apkUrl: String) {
        if (!isAllowedDownloadUrl(apkUrl)) {
            _state.value = UpdateState.Error("Download URL is not from a trusted source.")
            return
        }
        _state.value = UpdateState.Downloading(0f)
        try {
            val apkFile = withContext(Dispatchers.IO) { downloadApk(apkUrl) }
            _state.value = UpdateState.ReadyToInstall(apkFile)
            installApk(apkFile)
        } catch (e: Exception) {
            _state.value = UpdateState.Error("Download failed: ${e.message}")
        }
    }

    fun resetState() {
        _state.value = UpdateState.Idle
    }

    private fun fetchLatestRelease(): GitHubRelease? {
        val url = "https://api.github.com/repos/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases/latest"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) return null

        val body = response.body?.string() ?: return null
        return gson.fromJson(body, GitHubRelease::class.java)
    }

    private fun downloadApk(apkUrl: String): File {
        val request = Request.Builder().url(apkUrl).build()
        val response = okHttpClient.newCall(request).execute()

        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")

        val updateDir = File(context.cacheDir, "updates")
        updateDir.mkdirs()
        val apkFile = File(updateDir, "lumera-update.apk")

        val totalBytes = response.body?.contentLength() ?: -1L
        var downloadedBytes = 0L
        val digest = MessageDigest.getInstance("SHA-256")

        response.body?.byteStream()?.use { input ->
            apkFile.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    digest.update(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    if (totalBytes > 0) {
                        _state.value = UpdateState.Downloading(
                            progress = downloadedBytes.toFloat() / totalBytes,
                            downloadedMb = downloadedBytes / (1024f * 1024f),
                            totalMb = totalBytes / (1024f * 1024f)
                        )
                    }
                }
            }
        }

        // Verify checksum if one was provided in the release notes
        val expectedHash = extractSha256FromBody(lastReleaseBody)
        if (expectedHash != null) {
            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actualHash.equals(expectedHash, ignoreCase = true)) {
                apkFile.delete()
                throw SecurityException("APK checksum mismatch — file may be tampered")
            }
        }

        return apkFile
    }

    private fun installApk(apkFile: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    companion object {
        private const val KEY_UPDATE_POPUP_ENABLED = "update_popup_enabled"

        /** Trusted domains for APK downloads. */
        private val ALLOWED_DOWNLOAD_HOSTS = setOf(
            "github.com",
            "objects.githubusercontent.com"
        )

        /** Validate that the download URL is from a trusted GitHub domain. */
        private fun isAllowedDownloadUrl(url: String): Boolean {
            val host = try {
                Uri.parse(url).host?.lowercase()
            } catch (_: Exception) {
                null
            } ?: return false
            return ALLOWED_DOWNLOAD_HOSTS.any { host == it || host.endsWith(".$it") }
        }

        /** Extract SHA-256 hash from release body. Expected format: `SHA-256: abcdef1234...` */
        private fun extractSha256FromBody(body: String?): String? {
            if (body == null) return null
            val regex = Regex("""SHA-256:\s*([a-fA-F0-9]{64})""")
            return regex.find(body)?.groupValues?.get(1)
        }

        /** Strip the SHA-256 line from the changelog shown to users. */
        private fun stripHashFromChangelog(body: String?): String {
            if (body == null) return "No changelog provided."
            return body.replace(Regex("""(?m)^SHA-256:\s*[a-fA-F0-9]{64}\s*$"""), "").trim()
                .ifEmpty { "No changelog provided." }
        }
    }
}
