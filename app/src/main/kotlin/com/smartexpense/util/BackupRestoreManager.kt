package com.smartexpense.util

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import com.smartexpense.MainActivity
import com.smartexpense.data.local.ClubDatabase
import com.smartexpense.data.local.ClubDatabaseProvider
import com.smartexpense.data.local.ClubDatabaseResetHelper
import com.smartexpense.data.local.prefs.AppRestartFlags
import com.smartexpense.data.settings.SettingsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class BackupRestoreManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val databaseProvider: ClubDatabaseProvider,
    private val settingsManager: SettingsManager
) {

    suspend fun backupDatabase(destinationUri: Uri) = withContext(Dispatchers.IO) {
        val database = databaseProvider.getOrBuildBlocking()
        checkpointDatabase(database)
        val dbFiles = collectDatabaseFiles()
        if (!dbFiles.any { it.name == ClubDatabase.DATABASE_NAME }) {
            throw IllegalStateException("백업할 데이터베이스 파일을 찾을 수 없습니다.")
        }

        context.contentResolver.openOutputStream(destinationUri)?.use { output ->
            writeDatabaseZip(output, dbFiles)
        } ?: throw IllegalStateException("선택한 위치에 백업 파일을 저장할 수 없습니다. 저장 권한을 확인해 주세요.")
    }

    suspend fun createLocalBackupFile(): File = withContext(Dispatchers.IO) {
        val database = databaseProvider.getOrBuildBlocking()
        checkpointDatabase(database)
        val dbFiles = collectDatabaseFiles()
        if (!dbFiles.any { it.name == ClubDatabase.DATABASE_NAME }) {
            throw IllegalStateException("백업할 데이터베이스 파일을 찾을 수 없습니다.")
        }
        val file = File(context.cacheDir, suggestedBackupFileName())
        file.outputStream().use { output -> writeDatabaseZip(output, dbFiles) }
        file
    }

    suspend fun restoreDatabase(sourceUri: Uri) = withContext(Dispatchers.IO) {
        settingsManager.backupBeforeRestore()

        val backupCacheFile = File(context.cacheDir, "restore_${System.currentTimeMillis()}.hndb")
        val extractDir = File(context.cacheDir, "restore_extract_${System.currentTimeMillis()}")

        try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                backupCacheFile.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("백업 파일을 읽을 수 없습니다. 파일 접근 권한을 확인해 주세요.")

            extractDir.mkdirs()
            unzip(backupCacheFile, extractDir)

            val mainDb = File(extractDir, ClubDatabase.DATABASE_NAME)
            if (!mainDb.exists()) {
                throw IllegalArgumentException(
                    "올바른 백업 파일이 아닙니다. ${ClubDatabase.DATABASE_NAME}가 포함되어야 합니다."
                )
            }

            val backupVersion = ClubDatabaseResetHelper.readSchemaVersion(mainDb)
                ?: throw IllegalArgumentException(
                    "백업 DB 버전을 확인할 수 없습니다. 손상되었거나 올바른 .hndb 파일이 아닙니다."
                )
            if (backupVersion != ClubDatabase.SCHEMA_VERSION) {
                throw IllegalArgumentException(
                    "백업 DB 버전(v$backupVersion)이 현재 앱(v${ClubDatabase.SCHEMA_VERSION})과 호환되지 않습니다. " +
                        "현재 앱에서 새로 백업한 파일을 사용해 주세요."
                )
            }
            if (!ClubDatabaseResetHelper.hasRoomMasterTable(mainDb)) {
                throw IllegalArgumentException(
                    "백업 DB가 Room 형식이 아닙니다. 현재 앱에서 새로 백업한 파일을 사용해 주세요."
                )
            }

            databaseProvider.closeAndInvalidate()

            val targetDir = context.getDatabasePath(ClubDatabase.DATABASE_NAME).parentFile
                ?: throw IllegalStateException("앱 데이터베이스 저장 경로를 찾을 수 없습니다.")

            DATABASE_FILE_NAMES.forEach { name ->
                val target = File(targetDir, name)
                val source = File(extractDir, name)
                if (target.exists()) target.delete()
                if (source.exists()) {
                    source.copyTo(target, overwrite = true)
                }
            }
            ClubDatabaseResetHelper.deleteSidecarFiles(context)
        } finally {
            extractDir.deleteRecursively()
            backupCacheFile.delete()
        }
    }

    fun restartApp() {
        AppRestartFlags.markPendingRestart(context)
        databaseProvider.closeAndInvalidate()
        val componentName = ComponentName(context, MainActivity::class.java)
        val restartIntent = Intent.makeRestartActivityTask(componentName)

        finishCurrentAppTasks()

        val pendingIntent = PendingIntent.getActivity(
            context,
            RESTART_PENDING_INTENT_REQUEST_CODE,
            restartIntent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.set(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + RESTART_DELAY_MS,
            pendingIntent
        )

        System.exit(0)
    }

    private fun finishCurrentAppTasks() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        activityManager.appTasks.forEach { appTask ->
            runCatching { appTask.finishAndRemoveTask() }
        }
    }

    private fun checkpointDatabase(database: ClubDatabase) {
        val sqliteDb = database.openHelper.writableDatabase
        sqliteDb.query("PRAGMA wal_checkpoint(FULL)").use { cursor ->
            cursor.moveToFirst()
        }
    }

    private fun writeDatabaseZip(output: java.io.OutputStream, dbFiles: List<File>) {
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            dbFiles.forEach { file ->
                zip.putNextEntry(ZipEntry(file.name))
                FileInputStream(file).use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    private fun collectDatabaseFiles(): List<File> {
        val dbDir = context.getDatabasePath(ClubDatabase.DATABASE_NAME).parentFile
            ?: return emptyList()
        return DATABASE_FILE_NAMES.map { name -> File(dbDir, name) }.filter { it.exists() }
    }

    private fun unzip(zipFile: File, destinationDir: File) {
        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zipInput ->
            var entry = zipInput.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val outFile = File(destinationDir, entry.name.substringAfterLast('/'))
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { output -> zipInput.copyTo(output) }
                }
                zipInput.closeEntry()
                entry = zipInput.nextEntry
            }
        }
    }

    companion object {
        private val DATABASE_FILE_NAMES = listOf(
            ClubDatabase.DATABASE_NAME,
            "${ClubDatabase.DATABASE_NAME}-wal",
            "${ClubDatabase.DATABASE_NAME}-shm"
        )

        fun suggestedBackupFileName(): String {
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            return "SmartExpense_백업_$timestamp.hndb"
        }

        private const val RESTART_PENDING_INTENT_REQUEST_CODE = 10_001
        private const val RESTART_DELAY_MS = 600L
    }
}
