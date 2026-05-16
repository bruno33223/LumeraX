package com.lumera.app.data.backup

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.gson.Gson
import com.lumera.app.data.local.AddonDao
import com.lumera.app.data.model.AddonEntity
import com.lumera.app.data.model.CatalogConfigEntity
import com.lumera.app.data.model.ProfileEntity
import com.lumera.app.data.model.ThemeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections

/**
 * Payload structure for Lumera Backup.
 * Transferred as JSON to avoid SQLite binary corruption/WAL issues.
 */
data class LumeraBackupPayload(
    val profiles: List<ProfileEntity>,
    val themes: List<ThemeEntity>,
    val addons: List<AddonEntity>,
    val catalogConfigs: List<CatalogConfigEntity>,
    val timestamp: Long = System.currentTimeMillis()
)

class DriveBackupManager private constructor() {

    companion object {
        private const val TAG = "LumeraDrive"
        private const val BACKUP_FILENAME = "lumera_backup.json"
        private const val PREFS_NAME = "lumera_drive_prefs"
        private const val KEY_LAST_BACKUP_MS = "last_backup_ms"
        private const val AUTO_BACKUP_THROTTLE_MS = 3_600_000L // 1 hour

        @Volatile
        private var instance: DriveBackupManager? = null

        fun getInstance(): DriveBackupManager {
            return instance ?: synchronized(this) {
                instance ?: DriveBackupManager().also { instance = it }
            }
        }

        fun getGoogleSignInOptions(): GoogleSignInOptions {
            return GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
                .build()
        }

        fun isSignedIn(context: Context): Boolean {
            return GoogleSignIn.getLastSignedInAccount(context) != null
        }

        fun getSignedInEmail(context: Context): String? {
            return GoogleSignIn.getLastSignedInAccount(context)?.email
        }
    }

    private fun getDriveService(context: Context, account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            context, Collections.singleton(DriveScopes.DRIVE_APPDATA)
        )
        credential.selectedAccount = account.account

        return Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("Lumera").build()
    }

    private fun requireAccount(context: Context): GoogleSignInAccount {
        return GoogleSignIn.getLastSignedInAccount(context)
            ?: throw IllegalStateException("Nenhuma conta Google conectada")
    }

    /**
     * Checks if a backup file exists on Drive.
     * Returns the backup timestamp if found, null otherwise.
     */
    suspend fun hasBackup(context: Context): Long? = withContext(Dispatchers.IO) {
        try {
            val account = GoogleSignIn.getLastSignedInAccount(context) ?: return@withContext null
            val driveService = getDriveService(context, account)

            val result = driveService.files().list()
                .setSpaces("appDataFolder")
                .setFields("files(id, name, modifiedTime)")
                .execute()

            val file = result.files?.find { it.name == BACKUP_FILENAME }
            file?.modifiedTime?.value
        } catch (e: Exception) {
            Log.e(TAG, "hasBackup check failed", e)
            null
        }
    }

    /**
     * Exports current data to Google Drive.
     * Returns a Result with a user-facing success/error message.
     */
    suspend fun exportToDrive(context: Context, dao: AddonDao): Result<String> = withContext(Dispatchers.IO) {
        try {
            val account = requireAccount(context)
            val driveService = getDriveService(context, account)

            val payload = LumeraBackupPayload(
                profiles = dao.getAllProfilesSync(),
                themes = dao.getAllThemesSync(),
                addons = dao.getAllAddonsSync(),
                catalogConfigs = dao.getAllCatalogConfigsSync()
            )

            val json = Gson().toJson(payload)
            val tempFile = java.io.File(context.cacheDir, BACKUP_FILENAME)
            tempFile.writeText(json)

            val mediaContent = FileContent("application/json", tempFile)

            val existingFiles = driveService.files().list()
                .setSpaces("appDataFolder")
                .setFields("files(id, name)")
                .execute()
                .files

            val existingFile = existingFiles?.find { it.name == BACKUP_FILENAME }

            if (existingFile != null) {
                driveService.files().update(existingFile.id, null, mediaContent).execute()
            } else {
                val metadata = File().apply {
                    name = BACKUP_FILENAME
                    parents = Collections.singletonList("appDataFolder")
                }
                driveService.files().create(metadata, mediaContent).execute()
            }

            tempFile.delete()

            // Save timestamp for throttling
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_BACKUP_MS, System.currentTimeMillis())
                .apply()

            val profileCount = payload.profiles.size
            val addonCount = payload.addons.size
            Log.i(TAG, "Backup exported: $profileCount profiles, $addonCount addons")
            Result.success("Backup salvo! ($profileCount perfis, $addonCount addons)")
        } catch (e: Exception) {
            Log.e(TAG, "Export failed", e)
            Result.failure(e)
        }
    }

    /**
     * Restores data from Google Drive backup.
     * Returns a Result with a user-facing success/error message.
     */
    suspend fun restoreFromDrive(context: Context, dao: AddonDao): Result<String> = withContext(Dispatchers.IO) {
        try {
            val account = requireAccount(context)
            val driveService = getDriveService(context, account)

            val files = driveService.files().list()
                .setSpaces("appDataFolder")
                .setFields("files(id, name)")
                .execute()
                .files

            val backupFile = files?.find { it.name == BACKUP_FILENAME }
                ?: return@withContext Result.success("Nenhum backup encontrado na nuvem.")

            val outputStream = java.io.ByteArrayOutputStream()
            driveService.files().get(backupFile.id).executeMediaAndDownloadTo(outputStream)

            val json = outputStream.toString("UTF-8")
            val payload = Gson().fromJson(json, LumeraBackupPayload::class.java)

            dao.restoreFullBackup(
                profiles = payload.profiles,
                themes = payload.themes,
                addons = payload.addons,
                catalogConfigs = payload.catalogConfigs
            )

            val profileCount = payload.profiles.size
            val addonCount = payload.addons.size
            Log.i(TAG, "Backup restored: $profileCount profiles, $addonCount addons")
            Result.success("Backup restaurado! ($profileCount perfis, $addonCount addons)")
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed", e)
            Result.failure(e)
        }
    }

    /**
     * Silent auto-backup with throttle. Only runs if >1h since last backup.
     */
    suspend fun autoBackupIfNeeded(context: Context, dao: AddonDao) {
        if (!isSignedIn(context)) return

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastBackup = prefs.getLong(KEY_LAST_BACKUP_MS, 0L)
        val elapsed = System.currentTimeMillis() - lastBackup

        if (elapsed < AUTO_BACKUP_THROTTLE_MS) {
            Log.d(TAG, "Auto-backup skipped: ${elapsed / 1000}s since last backup")
            return
        }

        Log.i(TAG, "Auto-backup triggered")
        val result = exportToDrive(context, dao)
        result.onFailure { Log.e(TAG, "Auto-backup failed", it) }
        result.onSuccess { Log.i(TAG, "Auto-backup: $it") }
    }

    suspend fun checkBackupStatus(context: Context): String = withContext(Dispatchers.IO) {
        try {
            val account = GoogleSignIn.getLastSignedInAccount(context)
                ?: return@withContext "Nenhuma conta Google conectada."
            val driveService = getDriveService(context, account)

            val result = driveService.files().list()
                .setSpaces("appDataFolder")
                .setFields("files(id, name, size, modifiedTime)")
                .execute()

            val file = result.files?.firstOrNull()
            if (file != null) {
                "Backup encontrado! Tamanho: ${file.getSize()} bytes. Modificado em: ${file.modifiedTime}"
            } else {
                "Nenhum backup encontrado na nuvem."
            }
        } catch (e: Exception) {
            "Erro ao consultar Drive: ${e.localizedMessage}"
        }
    }
}
