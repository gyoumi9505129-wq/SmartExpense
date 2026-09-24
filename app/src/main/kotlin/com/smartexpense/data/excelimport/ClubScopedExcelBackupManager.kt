package com.smartexpense.data.excelimport

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.smartexpense.data.local.entity.club.ClubEntity
import com.smartexpense.di.ClubDatabaseGateway
import com.smartexpense.util.ExportShareHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.xssf.usermodel.XSSFWorkbook

/** 현재 선택된 단일 모임의 데이터만 엑셀(.xlsx)로 내보냅니다. */
@Singleton
class ClubScopedExcelBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val databaseGateway: ClubDatabaseGateway
) {
    private val clubDao get() = databaseGateway.clubDao()
    private val memberDao get() = databaseGateway.memberDao()
    private val clubTransactionDao get() = databaseGateway.clubTransactionDao()
    private val yearlyDuesDao get() = databaseGateway.yearlyDuesDao()
    private val clubAccountDao get() = databaseGateway.clubAccountDao()

    suspend fun exportCurrentClubToDownloads(clubId: Long): File {
        val club = clubDao.getById(clubId)
            ?: throw IllegalStateException("백업할 모임을 찾을 수 없습니다.")

        val workbook = XSSFWorkbook()
        try {
            val clubSheet = workbook.createSheet(ExcelImportSheets.CLUB)
            val membersSheet = workbook.createSheet(ExcelImportSheets.MEMBERS)
            val transactionsSheet = workbook.createSheet(ExcelImportSheets.TRANSACTIONS)
            val duesSheet = workbook.createSheet(ExcelImportSheets.DUES)
            val accountsSheet = workbook.createSheet(ExcelImportSheets.ACCOUNTS)

            writeHeaderRow(workbook, clubSheet, ExcelImportHeaders.CLUB)
            writeHeaderRow(workbook, membersSheet, ExcelImportHeaders.MEMBERS)
            writeHeaderRow(workbook, transactionsSheet, ExcelImportHeaders.TRANSACTIONS)
            writeHeaderRow(workbook, duesSheet, ExcelImportHeaders.DUES)
            writeHeaderRow(workbook, accountsSheet, ExcelImportHeaders.ACCOUNTS)

            writeClubRow(clubSheet, club)

            val clubName = club.clubName
            memberDao.getAllOnce(clubId).forEach { member ->
                writeDataRow(
                    membersSheet,
                    listOf(
                        clubName,
                        member.name,
                        member.phone,
                        member.joinDate,
                        member.birthDate,
                        member.address,
                        member.detailAddress,
                        member.residenceRegion,
                        member.email,
                        ExcelCellFormatters.formatMemberStatus(member.status),
                        ExcelCellFormatters.formatMemberRole(member.role),
                        ExcelCellFormatters.formatBooleanYn(member.isLunarBirth),
                        member.suspensionDate.orEmpty()
                    )
                )
            }

            val memberNameById = memberDao.getAllOnce(clubId).associate { it.id to it.name }
            clubTransactionDao.getAllOnce(clubId).forEach { tx ->
                val memberName = tx.targetMemberId?.let { memberNameById[it] }.orEmpty()
                writeDataRow(
                    transactionsSheet,
                    listOf(
                        clubName,
                        tx.date,
                        ExcelCellFormatters.formatTransactionType(tx.type),
                        tx.category,
                        tx.incomeAmount.toString(),
                        tx.expenseAmount.toString(),
                        tx.note.orEmpty(),
                        memberName,
                        tx.balanceAfter?.toString().orEmpty(),
                        tx.linkedDuesDetailId?.toString().orEmpty()
                    )
                )
            }

            yearlyDuesDao.getAllWithDetailsOnce(clubId).forEach { record ->
                val memberName = memberNameById[record.yearlyDues.memberId].orEmpty()
                record.details.forEach { detail ->
                    writeDataRow(
                        duesSheet,
                        listOf(
                            clubName,
                            memberName,
                            record.yearlyDues.year.toString(),
                            ExcelCellFormatters.formatPaymentMethod(record.yearlyDues.paymentMethod),
                            detail.termLabel,
                            detail.amount.toString(),
                            detail.paidAmount.toString(),
                            ExcelCellFormatters.formatBooleanYn(detail.isPaid),
                            detail.payDate,
                            detail.id.toString()
                        )
                    )
                }
            }

            clubAccountDao.getAllOnce(clubId).forEach { account ->
                writeDataRow(
                    accountsSheet,
                    listOf(
                        clubName,
                        account.bankName,
                        account.accountNumber,
                        account.holderName
                    )
                )
            }

            ExcelImportHeaders.MEMBERS.indices.forEach { membersSheet.setColumnWidth(it, 18 * 256) }
            ExcelImportHeaders.TRANSACTIONS.indices.forEach { transactionsSheet.setColumnWidth(it, 20 * 256) }
            ExcelImportHeaders.DUES.indices.forEach { duesSheet.setColumnWidth(it, 16 * 256) }
            ExcelImportHeaders.ACCOUNTS.indices.forEach { accountsSheet.setColumnWidth(it, 18 * 256) }

            return saveToDownloads(workbook, buildBackupFileName(club.clubName))
        } finally {
            workbook.close()
        }
    }

    private fun writeClubRow(sheet: org.apache.poi.ss.usermodel.Sheet, club: ClubEntity) {
        writeDataRow(
            sheet,
            listOf(
                club.clubId.toString(),
                club.clubName,
                club.clubSlogan,
                club.createdAt.toString()
            )
        )
    }

    private fun buildBackupFileName(clubName: String): String {
        val label = ExportShareHelper.sanitizeFileLabel(clubName)
        val date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
        return "모임백업_${label}_$date.xlsx"
    }

    private fun saveToDownloads(workbook: XSSFWorkbook, fileName: String): File {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            resolver.delete(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                "${MediaStore.MediaColumns.DISPLAY_NAME}=?",
                arrayOf(fileName)
            )
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(
                    MediaStore.MediaColumns.MIME_TYPE,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                )
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("다운로드 폴더에 파일을 생성할 수 없습니다.")
            resolver.openOutputStream(uri)?.use { output ->
                workbook.write(output)
                output.flush()
            } ?: throw IllegalStateException("다운로드 파일을 저장할 수 없습니다.")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                fileName
            )
        }

        @Suppress("DEPRECATION")
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }
        val file = File(downloadsDir, fileName)
        file.outputStream().use { output ->
            workbook.write(output)
            output.flush()
        }
        return file
    }

    private fun writeHeaderRow(
        workbook: XSSFWorkbook,
        sheet: org.apache.poi.ss.usermodel.Sheet,
        headers: List<String>
    ) {
        val headerStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            val font = workbook.createFont().apply { bold = true }
            setFont(font)
        }
        val row = sheet.createRow(0)
        headers.forEachIndexed { index, header ->
            row.createCell(index).apply {
                setCellValue(header)
                cellStyle = headerStyle
            }
        }
    }

    private fun writeDataRow(
        sheet: org.apache.poi.ss.usermodel.Sheet,
        values: List<String>
    ) {
        val row = sheet.createRow(sheet.lastRowNum + 1)
        values.forEachIndexed { index, value ->
            row.createCell(index).setCellValue(value)
        }
    }
}
