package com.smartexpense.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

enum class ClubRestoreFileKind {
    EXCEL,
    HNDB,
    UNKNOWN
}

object ClubRestoreFileDetector {
    fun detect(context: Context, uri: Uri): ClubRestoreFileKind {
        val displayName = queryDisplayName(context, uri)?.lowercase().orEmpty()
        val mimeType = context.contentResolver.getType(uri)?.lowercase().orEmpty()

        return when {
            displayName.endsWith(".hndb") -> ClubRestoreFileKind.HNDB
            displayName.endsWith(".xlsx") || displayName.endsWith(".xls") -> ClubRestoreFileKind.EXCEL
            mimeType.contains("spreadsheet") || mimeType.contains("excel") -> ClubRestoreFileKind.EXCEL
            mimeType == "application/octet-stream" && displayName.endsWith(".hndb") ->
                ClubRestoreFileKind.HNDB
            else -> ClubRestoreFileKind.UNKNOWN
        }
    }

    fun queryDisplayName(context: Context, uri: Uri): String? {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        return runCatching {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index < 0) null else cursor.getString(index)
            }
        }.getOrNull()
            ?: uri.lastPathSegment?.substringAfterLast('/')
    }
}
