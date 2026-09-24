package com.smartexpense.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

object ClubDatabaseResetHelper {
    private val relatedFileNames = listOf(
        ClubDatabase.DATABASE_NAME,
        "${ClubDatabase.DATABASE_NAME}-wal",
        "${ClubDatabase.DATABASE_NAME}-shm"
    )

    fun resetIfIncompatible(context: Context) {
        val dbFile = context.getDatabasePath(ClubDatabase.DATABASE_NAME)
        if (!dbFile.exists()) return

        val existingVersion = readSchemaVersion(dbFile)
        when {
            existingVersion == null -> deleteDatabaseFiles(context)
            existingVersion != ClubDatabase.SCHEMA_VERSION -> deleteDatabaseFiles(context)
            !hasRoomMasterTable(dbFile) -> deleteDatabaseFiles(context)
        }
    }

    fun readSchemaVersion(dbFile: File): Int? = runCatching {
        SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        ).use { database -> database.version }
    }.getOrNull()

    /** Room 2.6+의 변경 감지 테이블은 TEMP라 sqlite_master에 없습니다. room_master_table만 검사합니다. */
    fun hasRoomMasterTable(dbFile: File): Boolean = runCatching {
        SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        ).use { database ->
            database.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='room_master_table'",
                null
            ).use { cursor -> cursor.moveToFirst() }
        }
    }.getOrDefault(false)

    fun deleteDatabaseFiles(context: Context) {
        context.deleteDatabase(ClubDatabase.DATABASE_NAME)
        val dbDir = context.getDatabasePath(ClubDatabase.DATABASE_NAME).parentFile ?: return
        val allNames = relatedFileNames + "${ClubDatabase.DATABASE_NAME}-journal"
        allNames.forEach { name ->
            File(dbDir, name).delete()
        }
    }

    fun deleteSidecarFiles(context: Context) {
        val dbDir = context.getDatabasePath(ClubDatabase.DATABASE_NAME).parentFile ?: return
        File(dbDir, "${ClubDatabase.DATABASE_NAME}-wal").delete()
        File(dbDir, "${ClubDatabase.DATABASE_NAME}-shm").delete()
    }
}
