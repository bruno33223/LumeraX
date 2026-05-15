package com.lumera.app.data.backup

import android.content.Context
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
import java.io.FileOutputStream
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
        private const val BACKUP_FILENAME = "lumera_backup.json"
        
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

    suspend fun exportToDrive(context: Context, dao: AddonDao) = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return@withContext
        val driveService = getDriveService(context, account)

        // 1. Prepare Payload
        // Using firstOrNull() or similar if they were flows, but for backup we need them once.
        // AddonDao needs non-flow versions of these for backup.
        // For now, assuming DAO has or will have these methods.
        val payload = LumeraBackupPayload(
            profiles = dao.getAllProfilesSync(),
            themes = dao.getAllThemesSync(),
            addons = dao.getAllAddonsSync(),
            catalogConfigs = dao.getAllCatalogConfigsSync()
        )

        val json = Gson().toJson(payload)
        val tempFile = java.io.File(context.cacheDir, BACKUP_FILENAME)
        tempFile.writeText(json)

        // 2. Drive Upload
        val metadata = File().apply {
            name = BACKUP_FILENAME
            parents = Collections.singletonList("appDataFolder")
        }
        val mediaContent = FileContent("application/json", tempFile)

        // Check if file already exists to update or create
        val existingFiles = driveService.files().list()
            .setSpaces("appDataFolder")
            .setFields("files(id, name)")
            .execute()
            .files

        val existingFile = existingFiles?.find { it.name == BACKUP_FILENAME }

        if (existingFile != null) {
            driveService.files().update(existingFile.id, null, mediaContent).execute()
        } else {
            driveService.files().create(metadata, mediaContent).execute()
        }
        
        tempFile.delete()
    }

    suspend fun restoreFromDrive(context: Context, dao: AddonDao) = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return@withContext
        val driveService = getDriveService(context, account)

        val files = driveService.files().list()
            .setSpaces("appDataFolder")
            .setFields("files(id, name)")
            .execute()
            .files

        val backupFile = files?.find { it.name == BACKUP_FILENAME } ?: return@withContext

        val outputStream = java.io.ByteArrayOutputStream()
        driveService.files().get(backupFile.id).executeMediaAndDownloadTo(outputStream)
        
        val json = outputStream.toString("UTF-8")
        val payload = Gson().fromJson(json, LumeraBackupPayload::class.java)

        // 3. Restore to DAO
        dao.restoreFullBackup(
            profiles = payload.profiles,
            themes = payload.themes,
            addons = payload.addons,
            catalogConfigs = payload.catalogConfigs
        )
    }
}
