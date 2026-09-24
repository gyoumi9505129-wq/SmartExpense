package com.smartexpense.data.storage

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class TransactionReceiptStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val receiptDir: File
        get() = File(context.filesDir, RECEIPT_DIR_NAME).also { it.mkdirs() }

    suspend fun saveFromUri(uri: Uri, transactionId: Long? = null): String = withContext(Dispatchers.IO) {
        val prefix = transactionId?.let { "tx_$it" } ?: "draft"
        val fileName = "${prefix}_${UUID.randomUUID()}.jpg"
        val dest = File(receiptDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: error("증빙 자료를 불러올 수 없습니다.")
        dest.absolutePath
    }

    fun createCameraImageUri(): Pair<Uri, File> {
        val file = File(receiptDir, "camera_${UUID.randomUUID()}.jpg")
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return uri to file
    }

    fun deleteReceipt(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).delete() }
    }

    fun receiptFile(path: String?): File? = path?.let { File(it).takeIf { file -> file.exists() } }

    companion object {
        private const val RECEIPT_DIR_NAME = "transaction_receipts"
    }
}
