package com.smartexpense.util

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class GoogleDriveSyncManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val jsonFactory = GsonFactory.getDefaultInstance()
    private val httpTransport = NetHttpTransport()

    fun createSignInClient(): GoogleSignInClient {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
        return GoogleSignIn.getClient(context, options)
    }

    fun createSignInIntent(): Intent = createSignInClient().signInIntent

    fun getLastSignedInAccount(): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)

    suspend fun uploadBackupFileToDrive(
        file: File,
        account: GoogleSignInAccount
    ): String = withContext(Dispatchers.IO) {
        require(file.exists()) { "업로드할 파일이 존재하지 않습니다: ${file.name}" }

        val driveService = buildDriveService(account)
        val folderId = getOrCreateBackupFolder(driveService)
        uploadOrUpdateFile(driveService, folderId, file)
    }

    private fun buildDriveService(account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            context,
            listOf(DriveScopes.DRIVE_FILE)
        )
        credential.selectedAccount = account.account

        return Drive.Builder(httpTransport, jsonFactory, credential)
            .setApplicationName(AppConstants.APP_BRAND_NAME)
            .build()
    }

    private fun getOrCreateBackupFolder(driveService: Drive): String {
        val query = buildString {
            append("name = '")
            append(BACKUP_FOLDER_NAME.replace("'", "\\'"))
            append("' and mimeType = 'application/vnd.google-apps.folder' ")
            append("and 'root' in parents and trashed = false")
        }
        val existing = driveService.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id)")
            .execute()

        existing.files.firstOrNull()?.id?.let { return it }

        val folderMetadata = DriveFile().apply {
            name = BACKUP_FOLDER_NAME
            mimeType = "application/vnd.google-apps.folder"
            parents = listOf("root")
        }
        return driveService.files().create(folderMetadata)
            .setFields("id")
            .execute()
            .id
    }

    private fun uploadOrUpdateFile(
        driveService: Drive,
        folderId: String,
        localFile: File
    ): String {
        val escapedName = localFile.name.replace("'", "\\'")
        val query = buildString {
            append("name = '$escapedName' and '$folderId' in parents and trashed = false")
        }
        val existing = driveService.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id)")
            .execute()

        val mimeType = resolveMimeType(localFile)
        val mediaContent = FileContent(mimeType, localFile)

        return if (existing.files.isNotEmpty()) {
            driveService.files()
                .update(existing.files.first().id, null, mediaContent)
                .execute()
                .id
        } else {
            val metadata = DriveFile().apply {
                name = localFile.name
                parents = listOf(folderId)
            }
            driveService.files()
                .create(metadata, mediaContent)
                .setFields("id")
                .execute()
                .id
        }
    }

        private fun resolveMimeType(file: File): String = when (file.extension.lowercase()) {
        "pdf" -> "application/pdf"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "hndb" -> "application/octet-stream"
        "csv" -> "text/csv"
        else -> "application/octet-stream"
    }

    companion object {
        const val BACKUP_FOLDER_NAME = "SmartExpense_백업"
    }
}
