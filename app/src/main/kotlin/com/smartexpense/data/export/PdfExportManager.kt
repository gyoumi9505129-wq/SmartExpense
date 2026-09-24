package com.smartexpense.data.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.smartexpense.data.local.model.club.ClubTransactionCategorySummaryRow
import com.smartexpense.data.mapper.dues.MemberDuesSummary
import com.smartexpense.data.repository.club.ClubRepository
import com.smartexpense.data.repository.club.ClubTransactionRepository
import com.smartexpense.data.repository.club.DuesRepository
import com.smartexpense.util.DownloadsExportHelper
import com.smartexpense.util.ExportShareHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.OutputStream
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class PdfExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clubTransactionRepository: ClubTransactionRepository,
    private val duesRepository: DuesRepository,
    private val clubRepository: ClubRepository
) {
    suspend fun exportSettlementReport(
        year: Int = LocalDate.now().year
    ): File = withContext(Dispatchers.IO) {
        val payload = buildSettlementPayload(year)
        val clubLabel = ExportShareHelper.sanitizeFileLabel(payload.report.clubName)
        val file = File(
            ExportShareHelper.resolveExportDirectory(context),
            "${year}_${clubLabel}_결산보고서.pdf"
        )
        file.outputStream().use { output ->
            renderPdf(payload.report, payload.unpaidMembers, output)
        }
        file
    }

    /**
     * 선택한 연도 결산 PDF를 Download 폴더에 저장합니다.
     * @return 저장된 파일명
     */
    suspend fun saveSettlementReportToDownloads(
        year: Int = LocalDate.now().year
    ): String = withContext(Dispatchers.IO) {
        val payload = buildSettlementPayload(year)
        val clubLabel = ExportShareHelper.sanitizeFileLabel(payload.report.clubName)
        val fileName = "${year}_${clubLabel}_결산보고서.pdf"
        DownloadsExportHelper.save(
            context = context,
            fileName = fileName,
            mimeType = PDF_MIME_TYPE
        ) { output ->
            renderPdf(payload.report, payload.unpaidMembers, output)
        }
        fileName
    }

    private suspend fun buildSettlementPayload(year: Int): SettlementExportPayload {
        val snapshot = clubTransactionRepository.getSettlementSnapshot(year)
        val duesTotal = duesRepository.getYearTotalOnce(year)
        // 활동 회원만(휴면·탈퇴 제외). 결산 연도와 무관한 누적 미납 기준.
        val unpaidMembers = duesRepository.getAllMemberDuesSummariesForExport()
        val clubName = clubRepository.getSelectedClubName()
        return SettlementExportPayload(
            report = SettlementReportData(
                year = year,
                clubName = clubName,
                duesTotal = duesTotal,
                ledgerIncome = snapshot.ledgerIncome,
                ledgerExpense = snapshot.ledgerExpense,
                currentBalance = snapshot.currentBalance,
                categorySummaries = snapshot.categorySummaries
            ),
            unpaidMembers = unpaidMembers
        )
    }

    private fun renderPdf(
        report: SettlementReportData,
        unpaidMembers: List<MemberDuesSummary>,
        output: OutputStream
    ) {
        val document = PdfDocument()
        val layout = PdfPageLayout(document)

        layout.withPage { canvas ->
            var y = MARGIN_TOP.toFloat()
            y = drawSlogan(canvas, y, report.clubName)
            y = drawTitle(canvas, y, report.year)
            y = drawSummarySection(canvas, y, report)
            drawCategoryTable(canvas, layout, y, report.categorySummaries)
        }

        renderUnpaidPages(layout, unpaidMembers)

        layout.finishCurrentPage()
        document.writeTo(output)
        document.close()
    }

    private fun renderUnpaidPages(
        layout: PdfPageLayout,
        unpaidMembers: List<MemberDuesSummary>
    ) {
        layout.withPage { canvas ->
            var y = MARGIN_TOP.toFloat()
            y = drawUnpaidPageHeader(canvas, y, unpaidMembers)

            if (unpaidMembers.isEmpty()) {
                canvas.drawText(
                    "활동 회원이 없습니다.",
                    MARGIN_HORIZONTAL.toFloat(),
                    y + 16f,
                    bodyPaint(textSize = 12f, color = Color.DKGRAY)
                )
                return@withPage
            }

            y = drawUnpaidTableHeader(canvas, y)
            unpaidMembers.forEachIndexed { index, member ->
                val detailText = when {
                    member.isFullyPaid || member.totalUnpaidAmount <= 0 -> "완납"
                    else -> member.unpaidDetails.ifBlank { "완납" }
                }
                val amountText = if (member.totalUnpaidAmount <= 0) {
                    "0원"
                } else {
                    formatCurrency(member.totalUnpaidAmount)
                }
                val cells = listOf(
                    (index + 1).toString(),
                    member.memberName,
                    detailText,
                    amountText
                )
                val rowHeight = computeGridRowHeight(
                    cells = cells,
                    columnWidths = UNPAID_COLUMN_WIDTHS,
                    textPaint = bodyPaint(textSize = 10f),
                    rightAlignedColumnIndices = setOf(3),
                    boldFirstColumn = false,
                    minRowHeight = MIN_DATA_ROW_HEIGHT
                )
                y = layout.ensureRowSpace(y, rowHeight) { currentY ->
                    drawUnpaidTableHeader(canvas, currentY)
                }
                y = drawGridTableRow(
                    canvas = canvas,
                    y = y,
                    cells = cells,
                    columnWidths = UNPAID_COLUMN_WIDTHS,
                    rightAlignedColumnIndices = setOf(3),
                    rowHeight = rowHeight
                )
            }
        }
    }

    private fun drawUnpaidPageHeader(
        canvas: Canvas,
        startY: Float,
        unpaidMembers: List<MemberDuesSummary>
    ): Float {
        var y = startY + 8f
        val titlePaint = bodyPaint(textSize = 18f, typeface = Typeface.DEFAULT_BOLD)
        canvas.drawText("■ 회원별 회비 납부 현황", MARGIN_HORIZONTAL.toFloat(), y, titlePaint)
        y += 28f

        val unpaidOnly = unpaidMembers.filter { !it.isFullyPaid && it.totalUnpaidAmount > 0 }
        val totalUnpaidAmount = unpaidOnly.sumOf { it.totalUnpaidAmount }
        val summaryPaint = bodyPaint(
            textSize = 13f,
            typeface = Typeface.DEFAULT_BOLD,
            color = Color.parseColor("#E53935")
        )
        val summary =
            "활동 회원 ${unpaidMembers.size}명 / 미납자 ${unpaidOnly.size}명 / 총 미납액 합계: ${formatCurrency(totalUnpaidAmount)}"
        canvas.drawText(summary, MARGIN_HORIZONTAL.toFloat(), y, summaryPaint)
        return y + 24f
    }

    private fun drawUnpaidTableHeader(canvas: Canvas, y: Float): Float =
        drawGridTableHeader(
            canvas = canvas,
            y = y,
            headers = UNPAID_TABLE_HEADERS,
            columnWidths = UNPAID_COLUMN_WIDTHS,
            rightAlignedColumnIndices = setOf(3)
        )

    private fun drawCategoryTable(
        canvas: Canvas,
        layout: PdfPageLayout,
        startY: Float,
        categories: List<ClubTransactionCategorySummaryRow>
    ) {
        var y = startY
        val headerPaint = bodyPaint(textSize = 16f, typeface = Typeface.DEFAULT_BOLD)
        canvas.drawText("카테고리별 지출·수입 통계", MARGIN_HORIZONTAL.toFloat(), y, headerPaint)
        y += 28f

        val categoryColumnWidths = listOf(0.42f, 0.18f, 0.18f, 0.18f)
        y = drawGridTableHeader(
            canvas = canvas,
            y = y,
            headers = listOf("분류", "수입", "지출", "합계"),
            columnWidths = categoryColumnWidths,
            rightAlignedColumnIndices = setOf(1, 2, 3)
        )

        val rows = categories
            .filter { it.totalIncome > 0 || it.totalExpense > 0 }
            .sortedByDescending { it.totalExpense }

        if (rows.isEmpty()) {
            canvas.drawText(
                "해당 연도 장부 데이터가 없습니다.",
                MARGIN_HORIZONTAL.toFloat(),
                y + 16f,
                bodyPaint(textSize = 12f, color = Color.DKGRAY)
            )
            return
        }

        rows.forEach { row ->
            val net = row.totalIncome - row.totalExpense
            val cells = listOf(
                row.category,
                formatCurrency(row.totalIncome),
                formatCurrency(row.totalExpense),
                formatCurrency(net)
            )
            val rowHeight = computeGridRowHeight(
                cells = cells,
                columnWidths = categoryColumnWidths,
                textPaint = bodyPaint(textSize = 10f),
                rightAlignedColumnIndices = setOf(1, 2, 3),
                boldFirstColumn = false,
                minRowHeight = MIN_DATA_ROW_HEIGHT
            )
            y = layout.ensureRowSpace(y, rowHeight) { currentY ->
                drawGridTableHeader(
                    canvas = canvas,
                    y = currentY,
                    headers = listOf("분류", "수입", "지출", "합계"),
                    columnWidths = categoryColumnWidths,
                    rightAlignedColumnIndices = setOf(1, 2, 3)
                )
            }
            y = drawGridTableRow(
                canvas = canvas,
                y = y,
                cells = cells,
                columnWidths = categoryColumnWidths,
                rightAlignedColumnIndices = setOf(1, 2, 3),
                rowHeight = rowHeight
            )
        }
    }

    private fun drawGridTableHeader(
        canvas: Canvas,
        y: Float,
        headers: List<String>,
        columnWidths: List<Float>,
        rightAlignedColumnIndices: Set<Int>
    ): Float {
        val rowHeight = HEADER_ROW_HEIGHT
        drawGridRowBackground(canvas, y, rowHeight, Color.parseColor("#333333"))
        drawGridRowText(
            canvas = canvas,
            y = y,
            rowHeight = rowHeight,
            cells = headers,
            columnWidths = columnWidths,
            textPaint = bodyPaint(textSize = 11f, typeface = Typeface.DEFAULT_BOLD, color = Color.WHITE),
            rightAlignedColumnIndices = rightAlignedColumnIndices,
            boldFirstColumn = true
        )
        drawGridBorders(canvas, y, rowHeight, columnWidths)
        return y + rowHeight
    }

    private fun drawGridTableRow(
        canvas: Canvas,
        y: Float,
        cells: List<String>,
        columnWidths: List<Float>,
        rightAlignedColumnIndices: Set<Int>,
        rowHeight: Float = computeGridRowHeight(
            cells = cells,
            columnWidths = columnWidths,
            textPaint = bodyPaint(textSize = 10f),
            rightAlignedColumnIndices = rightAlignedColumnIndices,
            boldFirstColumn = false,
            minRowHeight = MIN_DATA_ROW_HEIGHT
        )
    ): Float {
        drawGridRowBackground(canvas, y, rowHeight, Color.parseColor("#F8F8F8"))
        drawGridRowText(
            canvas = canvas,
            y = y,
            rowHeight = rowHeight,
            cells = cells,
            columnWidths = columnWidths,
            textPaint = bodyPaint(textSize = 10f),
            rightAlignedColumnIndices = rightAlignedColumnIndices,
            boldFirstColumn = false
        )
        drawGridBorders(canvas, y, rowHeight, columnWidths)
        return y + rowHeight
    }

    private fun computeGridRowHeight(
        cells: List<String>,
        columnWidths: List<Float>,
        textPaint: Paint,
        rightAlignedColumnIndices: Set<Int>,
        boldFirstColumn: Boolean,
        minRowHeight: Float
    ): Float {
        var maxCellHeight = 0f
        cells.forEachIndexed { index, text ->
            val cellPaint = cellPaintFor(index, textPaint, boldFirstColumn)
            val layoutWidth = cellInnerWidth(columnWidths[index]).toInt().coerceAtLeast(1)
            val alignment = if (index in rightAlignedColumnIndices) {
                Layout.Alignment.ALIGN_OPPOSITE
            } else {
                Layout.Alignment.ALIGN_NORMAL
            }
            val layout = buildStaticLayout(text, cellPaint, layoutWidth, alignment)
            maxCellHeight = maxOf(maxCellHeight, layout.height.toFloat())
        }
        return maxOf(minRowHeight, maxCellHeight + CELL_PADDING_VERTICAL * 2)
    }

    private fun drawGridRowBackground(canvas: Canvas, y: Float, rowHeight: Float, color: Int) {
        canvas.drawRect(
            MARGIN_HORIZONTAL.toFloat(),
            y,
            (PAGE_WIDTH - MARGIN_HORIZONTAL).toFloat(),
            y + rowHeight,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        )
    }

    private fun drawGridBorders(
        canvas: Canvas,
        y: Float,
        rowHeight: Float,
        columnWidths: List<Float>
    ) {
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CCCCCC")
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val left = MARGIN_HORIZONTAL.toFloat()
        val right = (PAGE_WIDTH - MARGIN_HORIZONTAL).toFloat()
        val top = y
        val bottom = y + rowHeight
        canvas.drawRect(left, top, right, bottom, borderPaint)

        val tableWidth = PAGE_WIDTH - (MARGIN_HORIZONTAL * 2)
        var x = left
        columnWidths.dropLast(1).forEach { ratio ->
            x += tableWidth * ratio
            canvas.drawLine(x, top, x, bottom, borderPaint)
        }
    }

    private fun drawGridRowText(
        canvas: Canvas,
        y: Float,
        rowHeight: Float,
        cells: List<String>,
        columnWidths: List<Float>,
        textPaint: Paint,
        rightAlignedColumnIndices: Set<Int>,
        boldFirstColumn: Boolean
    ) {
        val tableWidth = PAGE_WIDTH - (MARGIN_HORIZONTAL * 2)
        var x = MARGIN_HORIZONTAL.toFloat()
        cells.forEachIndexed { index, text ->
            val cellPaint = cellPaintFor(index, textPaint, boldFirstColumn)
            val cellWidth = tableWidth * columnWidths[index]
            val innerWidth = cellInnerWidth(columnWidths[index]).toInt().coerceAtLeast(1)
            val alignment = if (index in rightAlignedColumnIndices) {
                Layout.Alignment.ALIGN_OPPOSITE
            } else {
                Layout.Alignment.ALIGN_NORMAL
            }
            val layout = buildStaticLayout(text, cellPaint, innerWidth, alignment)
            canvas.save()
            canvas.translate(x + CELL_PADDING_HORIZONTAL, y + CELL_PADDING_VERTICAL)
            layout.draw(canvas)
            canvas.restore()
            x += cellWidth
        }
    }

    private fun cellPaintFor(index: Int, textPaint: Paint, boldFirstColumn: Boolean): Paint =
        if (index == 0 && boldFirstColumn) {
            bodyPaint(
                textSize = textPaint.textSize,
                typeface = Typeface.DEFAULT_BOLD,
                color = textPaint.color
            )
        } else {
            textPaint
        }

    private fun cellInnerWidth(columnWidthRatio: Float): Float {
        val tableWidth = PAGE_WIDTH - (MARGIN_HORIZONTAL * 2)
        return tableWidth * columnWidthRatio - CELL_PADDING_HORIZONTAL * 2
    }

    private fun buildStaticLayout(
        text: String,
        paint: Paint,
        width: Int,
        alignment: Layout.Alignment
    ): StaticLayout {
        val textPaint = TextPaint(paint)
        return StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width)
            .setAlignment(alignment)
            .setLineSpacing(0f, 1f)
            .setIncludePad(false)
            .build()
    }

    private fun drawSlogan(canvas: Canvas, startY: Float, clubName: String): Float {
        var x = MARGIN_HORIZONTAL.toFloat()
        val y = startY + 28f
        val basePaint = bodyPaint(textSize = 14f)
        val bluePaint = bodyPaint(textSize = 14f, color = Color.parseColor("#4A90D9"))
        val redPaint = bodyPaint(textSize = 14f, color = Color.parseColor("#E53935"))

        // 「友情과 信義로 하나되는 {clubName}」 — 캡처와 동일 배색
        canvas.drawText("友情", x, y, bluePaint)
        x += bluePaint.measureText("友情")
        canvas.drawText("과 ", x, y, basePaint)
        x += basePaint.measureText("과 ")
        canvas.drawText("信義", x, y, bluePaint)
        x += bluePaint.measureText("信義")
        canvas.drawText("로 하나되는 ", x, y, basePaint)
        x += basePaint.measureText("로 하나되는 ")
        canvas.drawText(clubName.ifBlank { "한우리" }, x, y, redPaint)
        return y + 24f
    }

    private fun drawTitle(canvas: Canvas, startY: Float, year: Int): Float {
        val titlePaint = bodyPaint(textSize = 22f, typeface = Typeface.DEFAULT_BOLD)
        val subtitlePaint = bodyPaint(textSize = 12f, color = Color.DKGRAY)
        canvas.drawText("${year}년도 정기 결산 보고서", MARGIN_HORIZONTAL.toFloat(), startY + 36f, titlePaint)
        canvas.drawText(
            "생성일: ${LocalDate.now()}",
            MARGIN_HORIZONTAL.toFloat(),
            startY + 56f,
            subtitlePaint
        )
        return startY + 72f
    }

    private fun drawSummarySection(canvas: Canvas, startY: Float, report: SettlementReportData): Float {
        var y = startY + 16f
        val headerPaint = bodyPaint(textSize = 16f, typeface = Typeface.DEFAULT_BOLD)
        canvas.drawText("결산 요약", MARGIN_HORIZONTAL.toFloat(), y, headerPaint)
        y += 24f

        listOf(
            "회비 수납 총액" to report.duesTotal,
            "장부 수입 총액" to report.ledgerIncome,
            "장부 지출 총액" to report.ledgerExpense,
            "현재 잔액" to report.currentBalance
        ).forEach { (label, amount) ->
            y = drawKeyValueRow(canvas, y, label, formatCurrency(amount))
        }
        return y + 20f
    }

    private fun drawKeyValueRow(canvas: Canvas, y: Float, label: String, value: String): Float {
        val labelPaint = bodyPaint(textSize = 13f)
        val valuePaint = bodyPaint(textSize = 13f, typeface = Typeface.DEFAULT_BOLD)
        canvas.drawText(label, MARGIN_HORIZONTAL.toFloat(), y, labelPaint)
        canvas.drawText(
            value,
            PAGE_WIDTH - MARGIN_HORIZONTAL - valuePaint.measureText(value),
            y,
            valuePaint
        )
        return y + 22f
    }

    private fun bodyPaint(
        textSize: Float,
        typeface: Typeface = Typeface.create("sans-serif", Typeface.NORMAL),
        color: Int = Color.BLACK
    ): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.textSize = textSize
        this.typeface = typeface
        this.color = color
    }

    private fun formatCurrency(amount: Int): String =
        NumberFormat.getNumberInstance(Locale.KOREA).format(amount) + "원"

    private inner class PdfPageLayout(private val document: PdfDocument) {
        private var pageNumber = 0
        private var currentPage: PdfDocument.Page? = null
        private var canvas: Canvas? = null

        fun withPage(block: (Canvas) -> Unit) {
            startNewPage()
            block(requireNotNull(canvas))
        }

        fun ensureRowSpace(currentY: Float, rowHeight: Float, drawContinuationHeader: (Float) -> Float): Float {
            if (currentY + rowHeight <= PAGE_HEIGHT - MARGIN_BOTTOM) {
                return currentY
            }
            finishCurrentPage()
            startNewPage()
            return drawContinuationHeader(MARGIN_TOP.toFloat())
        }

        fun finishCurrentPage() {
            currentPage?.let { document.finishPage(it) }
            currentPage = null
            canvas = null
        }

        private fun startNewPage() {
            finishCurrentPage()
            pageNumber += 1
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            currentPage = document.startPage(pageInfo)
            canvas = currentPage!!.canvas
        }
    }

    companion object {
        const val PDF_MIME_TYPE = "application/pdf"
        private const val PAGE_WIDTH = 595
        private const val PAGE_HEIGHT = 842
        private const val MARGIN_HORIZONTAL = 40
        private const val MARGIN_TOP = 48
        private const val MARGIN_BOTTOM = 48
        private const val HEADER_ROW_HEIGHT = 28f
        private const val MIN_DATA_ROW_HEIGHT = 24f
        private const val CELL_PADDING_HORIZONTAL = 6f
        private const val CELL_PADDING_VERTICAL = 6f
        private val UNPAID_TABLE_HEADERS = listOf("순번", "성명", "미납 상세 내역", "미납 금액")
        private val UNPAID_COLUMN_WIDTHS = listOf(0.08f, 0.14f, 0.50f, 0.28f)
    }
}

private data class SettlementExportPayload(
    val report: SettlementReportData,
    val unpaidMembers: List<MemberDuesSummary>
)
