package com.smartexpense.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import java.io.File

object ExportShareHelper {
    const val XLSX_MIME_TYPE =
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    fun sanitizeFileLabel(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "모임" }

    /** 외부 공유용 임시 디렉터리 (externalCacheDir 우선, 없으면 cacheDir). */
    fun resolveExportDirectory(context: Context): File {
        val dir = context.externalCacheDir ?: context.cacheDir
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun createShareIntent(
        context: Context,
        file: File,
        mimeType: String,
        title: String
    ): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val resolvedMime = normalizeMimeType(mimeType, file)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = resolvedMime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            clipData = ClipData.newUri(context.contentResolver, file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        grantReadUriPermissionToTargets(context, sendIntent, uri)

        return Intent.createChooser(sendIntent, title).apply {
            // Chooser로 넘길 때도 ClipData + FLAG가 있어야 대상 앱에 읽기 권한이 전달됩니다.
            clipData = ClipData.newUri(context.contentResolver, file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun createViewIntent(
        context: Context,
        file: File,
        mimeType: String
    ): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val resolvedMime = normalizeMimeType(mimeType, file)
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, resolvedMime)
            clipData = ClipData.newUri(context.contentResolver, file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        grantReadUriPermissionToTargets(context, viewIntent, uri)
        return viewIntent
    }

    private fun normalizeMimeType(mimeType: String, file: File): String {
        if (file.extension.equals("xlsx", ignoreCase = true)) {
            return XLSX_MIME_TYPE
        }
        val trimmed = mimeType.trim()
        if (trimmed.isBlank() || trimmed == "*/*" || trimmed == "application/octet-stream") {
            return when {
                file.extension.equals("pdf", ignoreCase = true) -> "application/pdf"
                else -> trimmed.ifBlank { "*/*" }
            }
        }
        return trimmed
    }

    private fun grantReadUriPermissionToTargets(
        context: Context,
        intent: Intent,
        uri: android.net.Uri
    ) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val targets = context.packageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        for (resolveInfo in targets) {
            val packageName = resolveInfo.activityInfo?.packageName ?: continue
            runCatching {
                context.grantUriPermission(packageName, uri, flags)
            }
        }
    }
}
