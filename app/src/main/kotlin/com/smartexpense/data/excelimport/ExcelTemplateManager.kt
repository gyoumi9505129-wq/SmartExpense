package com.smartexpense.data.excelimport

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.xssf.usermodel.XSSFWorkbook

@Singleton
class ExcelTemplateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun createTemplateInDownloads(): File = writeWorkbook { workbook ->
        createGuideSheet(workbook)
        createClubSheet(workbook)
        createMembersSheet(workbook)
        createTransactionsSheet(workbook)
        createDuesSheet(workbook)
        createAccountsSheet(workbook)
    }

    private fun writeWorkbook(buildSheets: (XSSFWorkbook) -> Unit): File {
        val workbook = XSSFWorkbook()
        try {
            buildSheets(workbook)
            return saveToDownloads(workbook)
        } finally {
            workbook.close()
        }
    }

    private fun saveToDownloads(workbook: XSSFWorkbook): File {
        val fileName = TEMPLATE_FILE_NAME
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
            return File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
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

    /** 복원 파서가 읽지 않는 안내 전용 시트 */
    private fun createGuideSheet(workbook: XSSFWorkbook) {
        val sheet = workbook.createSheet("작성안내")
        val lines = listOf(
            "【입력 양식 사용 방법】",
            "1. 모임정보·회원목록·장부내역·회비내역·계좌목록 시트에 실제 데이터를 입력하세요.",
            "2. 모든 시트의 '모임명'은 동일해야 합니다. (예: 한우리)",
            "3. 헤더(1행) 열 이름·순서는 변경하지 마세요.",
            "4. 작성 후 앱 설정 → 현재 모임 복원에서 이 파일을 선택하세요.",
            "5. 복원 시 현재 모임의 기존 데이터는 파일 내용으로 덮어씌워집니다.",
            "",
            "【날짜】 yyyy-MM-dd (예: 2024-03-01)",
            "【장부 구분】 수입 / 지출",
            "【회원 상태】 활동 / 휴면 / 탈퇴",
            "【회원 역할】 일반 / 회장 / 운영관리자 / 임원",
            "【납부방식】 월별 / 분기별 / 반기별 / 연도별",
            "【납부완료】 Y / N",
            "【회비상세ID·연동회비ID】 비워 두어도 됩니다. (백업 파일 연동용)"
        )
        lines.forEachIndexed { index, line ->
            sheet.createRow(index).createCell(0).setCellValue(line)
        }
        sheet.setColumnWidth(0, 90 * 256)
    }

    private fun createClubSheet(workbook: XSSFWorkbook) {
        val sheet = workbook.createSheet(ExcelImportSheets.CLUB)
        writeHeaderRow(workbook, sheet, ExcelImportHeaders.CLUB)
        ExcelImportHeaders.CLUB.indices.forEach { index ->
            sheet.setColumnWidth(index, 18 * 256)
        }
    }

    private fun createMembersSheet(workbook: XSSFWorkbook) {
        val sheet = workbook.createSheet(ExcelImportSheets.MEMBERS)
        writeHeaderRow(workbook, sheet, ExcelImportHeaders.MEMBERS)
        ExcelImportHeaders.MEMBERS.indices.forEach { index ->
            sheet.setColumnWidth(index, 18 * 256)
        }
    }

    private fun createTransactionsSheet(workbook: XSSFWorkbook) {
        val sheet = workbook.createSheet(ExcelImportSheets.TRANSACTIONS)
        writeHeaderRow(workbook, sheet, ExcelImportHeaders.TRANSACTIONS)
        ExcelImportHeaders.TRANSACTIONS.indices.forEach { index ->
            sheet.setColumnWidth(index, 20 * 256)
        }
    }

    private fun createDuesSheet(workbook: XSSFWorkbook) {
        val sheet = workbook.createSheet(ExcelImportSheets.DUES)
        writeHeaderRow(workbook, sheet, ExcelImportHeaders.DUES)
        ExcelImportHeaders.DUES.indices.forEach { index ->
            sheet.setColumnWidth(index, 16 * 256)
        }
    }

    private fun createAccountsSheet(workbook: XSSFWorkbook) {
        val sheet = workbook.createSheet(ExcelImportSheets.ACCOUNTS)
        writeHeaderRow(workbook, sheet, ExcelImportHeaders.ACCOUNTS)
        ExcelImportHeaders.ACCOUNTS.indices.forEach { index ->
            sheet.setColumnWidth(index, 18 * 256)
        }
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

    companion object {
        const val TEMPLATE_FILE_NAME = "모임_데이터입력양식.xlsx"
    }
}
