package com.smartexpense.data.export

import android.content.Context
import com.smartexpense.data.local.model.club.ClubTransactionExportRow
import com.smartexpense.data.repository.club.ClubRepository
import com.smartexpense.data.repository.club.ClubTransactionRepository
import com.smartexpense.util.DownloadsExportHelper
import com.smartexpense.util.ExportShareHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.ss.usermodel.VerticalAlignment
import org.apache.poi.xssf.usermodel.XSSFWorkbook

@Singleton
class ExcelExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clubTransactionRepository: ClubTransactionRepository,
    private val clubRepository: ClubRepository
) {
    /**
     * 선택한 연도 장부 내역을 Download 폴더에 .xlsx로 저장합니다.
     * @return 저장된 파일명
     */
    suspend fun exportLedgerExcel(
        year: Int = LocalDate.now().year
    ): String = withContext(Dispatchers.IO) {
        val rows = clubTransactionRepository.getForExportByYear(year)
        val clubLabel = ExportShareHelper.sanitizeFileLabel(clubRepository.getSelectedClubName())
        val fileName = "${year}_${clubLabel}_장부내역.xlsx"

        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("장부내역")
            val headerStyle = workbook.createCellStyle().apply {
                fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
                fillPattern = FillPatternType.SOLID_FOREGROUND
                alignment = HorizontalAlignment.CENTER
                verticalAlignment = VerticalAlignment.CENTER
                val font = workbook.createFont().apply { bold = true }
                setFont(font)
            }
            val wrapStyle = workbook.createCellStyle().apply {
                wrapText = true
                verticalAlignment = VerticalAlignment.TOP
            }
            val amountStyle = workbook.createCellStyle().apply {
                alignment = HorizontalAlignment.RIGHT
                verticalAlignment = VerticalAlignment.TOP
            }

            val headerRow = sheet.createRow(0)
            listOf("날짜", "분류", "금액", "비고", "대상회원").forEachIndexed { index, title ->
                headerRow.createCell(index).apply {
                    setCellValue(title)
                    cellStyle = headerStyle
                }
            }

            rows.forEachIndexed { index, row ->
                val excelRow = sheet.createRow(index + 1)
                excelRow.createCell(0).setCellValue(row.date)
                excelRow.createCell(1).apply {
                    setCellValue(row.category)
                    cellStyle = wrapStyle
                }
                excelRow.createCell(2).apply {
                    setCellValue(amountValue(row).toDouble())
                    cellStyle = amountStyle
                }
                excelRow.createCell(3).apply {
                    setCellValue(row.note.orEmpty())
                    cellStyle = wrapStyle
                }
                excelRow.createCell(4).setCellValue(row.memberName.orEmpty())
            }

            sheet.setColumnWidth(0, 12 * 256)
            sheet.setColumnWidth(1, 28 * 256)
            sheet.setColumnWidth(2, 12 * 256)
            sheet.setColumnWidth(3, 40 * 256)
            sheet.setColumnWidth(4, 14 * 256)

            DownloadsExportHelper.save(
                context = context,
                fileName = fileName,
                mimeType = XLSX_MIME_TYPE
            ) { output ->
                workbook.write(output)
                output.flush()
            }
        }
        fileName
    }

    private fun amountValue(row: ClubTransactionExportRow): Int =
        when {
            row.incomeAmount > 0 -> row.incomeAmount
            row.expenseAmount > 0 -> -row.expenseAmount
            else -> 0
        }

    companion object {
        const val XLSX_MIME_TYPE = ExportShareHelper.XLSX_MIME_TYPE
    }
}
