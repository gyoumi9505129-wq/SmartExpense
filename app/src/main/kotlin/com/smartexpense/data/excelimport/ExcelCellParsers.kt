package com.smartexpense.data.excelimport

import com.smartexpense.data.local.entity.club.ClubCategory
import com.smartexpense.data.local.entity.club.ClubTransactionType
import com.smartexpense.data.local.entity.club.DuesPaymentMethod
import com.smartexpense.data.local.entity.club.MemberRole
import com.smartexpense.data.local.entity.club.MemberStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.Row

internal object ExcelCellParsers {
    private val supportedDatePatterns = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("yyyy/MM/dd"),
        DateTimeFormatter.ofPattern("yyyy.MM.dd"),
        DateTimeFormatter.ofPattern("yyyyMMdd")
    )

    /**
     * Android에서는 POI DataFormatter가 CellFormat(AWT) 때문에 실패하므로
     * 셀 타입별 직접 변환을 사용합니다.
     */
    fun formatCell(cell: Cell?): String {
        if (cell == null) return ""
        return runCatching { readCellAsString(cell) }.getOrDefault("").trim()
    }

    private fun readCellAsString(cell: Cell): String {
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.BLANK -> ""
            CellType.ERROR -> ""
            CellType.NUMERIC -> formatNumericCell(cell)
            CellType.FORMULA -> formatFormulaCell(cell)
            else -> cell.toString()
        }
    }

    private fun formatFormulaCell(cell: Cell): String {
        return when (cell.cachedFormulaResultType) {
            CellType.STRING -> cell.stringCellValue
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.NUMERIC -> formatNumericCell(cell)
            CellType.BLANK, CellType.ERROR -> ""
            else -> ""
        }
    }

    private fun formatNumericCell(cell: Cell): String {
        if (isExcelDateCell(cell)) {
            return runCatching {
                cell.localDateTimeCellValue.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
            }.getOrElse {
                formatPlainNumber(cell.numericCellValue)
            }
        }
        return formatPlainNumber(cell.numericCellValue)
    }

    private fun isExcelDateCell(cell: Cell): Boolean =
        runCatching { DateUtil.isCellDateFormatted(cell) }.getOrDefault(false)

    private fun formatPlainNumber(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return ""
        val asLong = value.toLong()
        return if (value == asLong.toDouble()) {
            asLong.toString()
        } else {
            value.toString()
        }
    }

    fun readHeaderMap(row: Row): Map<String, Int> {
        val map = linkedMapOf<String, Int>()
        for (cellIndex in 0 until row.lastCellNum) {
            val header = formatCell(row.getCell(cellIndex))
            if (header.isNotEmpty()) {
                map[header] = cellIndex
            }
        }
        return map
    }

    fun requireHeaders(
        headerMap: Map<String, Int>,
        expected: List<String>,
        sheetName: String
    ) {
        val missing = expected.filter { it !in headerMap }
        if (missing.isNotEmpty()) {
            throw ExcelImportException(
                "$sheetName 시트 헤더가 올바르지 않습니다. 누락: ${missing.joinToString(", ")}"
            )
        }
    }

    fun readString(row: Row, headerMap: Map<String, Int>, header: String): String {
        val index = headerMap[header]
            ?: throw ExcelImportException("필수 헤더 '$header'를 찾을 수 없습니다.")
        return formatCell(row.getCell(index))
    }

    fun readClubName(
        row: Row,
        headerMap: Map<String, Int>,
        rowNumber: Int,
        hasClubColumn: Boolean
    ): String {
        if (!hasClubColumn) {
            return ExcelImportHeaders.LEGACY_DEFAULT_CLUB_NAME
        }
        val clubName = readString(row, headerMap, ExcelImportHeaders.CLUB_NAME)
        if (clubName.isEmpty()) {
            throw ExcelImportException("${rowNumber}행 모임명이 비어 있습니다.")
        }
        return clubName
    }

    fun isTemplateExampleClub(clubName: String): Boolean =
        clubName.trim() == ExcelImportHeaders.TEMPLATE_EXAMPLE_CLUB_NAME

    fun readOptionalLong(row: Row, headerMap: Map<String, Int>, header: String, rowNumber: Int): Long? {
        val raw = readOptionalString(row, headerMap, header) ?: return null
        return parseLong(raw, rowNumber, header)
    }

    fun readOptionalString(row: Row, headerMap: Map<String, Int>, header: String): String? {
        val index = headerMap[header] ?: return null
        return formatCell(row.getCell(index)).takeIf { it.isNotEmpty() }
    }

    fun readRequiredDate(row: Row, headerMap: Map<String, Int>, header: String, rowNumber: Int): String {
        val index = headerMap[header]
            ?: throw ExcelImportException("필수 헤더 '$header'를 찾을 수 없습니다.")
        return parseDateCell(row.getCell(index), rowNumber, header)
    }

    fun readOptionalDate(row: Row, headerMap: Map<String, Int>, header: String, rowNumber: Int): String? {
        val index = headerMap[header] ?: return null
        val cell = row.getCell(index) ?: return null
        if (formatCell(cell).isEmpty() && cell.cellType != CellType.NUMERIC) return null
        if (formatCell(cell).isEmpty() && cell.cellType == CellType.NUMERIC && !isExcelDateCell(cell)) {
            return null
        }
        return parseDateCell(cell, rowNumber, header)
    }

    fun readInt(row: Row, headerMap: Map<String, Int>, header: String, rowNumber: Int): Int {
        val raw = readString(row, headerMap, header)
        if (raw.isEmpty()) return 0
        return parseInt(raw, rowNumber, header)
    }

    fun readRequiredInt(row: Row, headerMap: Map<String, Int>, header: String, rowNumber: Int): Int {
        val raw = readString(row, headerMap, header)
        if (raw.isEmpty()) {
            throw ExcelImportException("${rowNumber}행 '$header' 값이 비어 있습니다.")
        }
        return parseInt(raw, rowNumber, header)
    }

    fun readLong(row: Row, headerMap: Map<String, Int>, header: String, rowNumber: Int): Long {
        val raw = readString(row, headerMap, header)
        if (raw.isEmpty()) return 0L
        return parseLong(raw, rowNumber, header)
    }

    fun readBooleanYn(row: Row, headerMap: Map<String, Int>, header: String): Boolean {
        return when (readString(row, headerMap, header).uppercase()) {
            "Y", "YES", "TRUE", "1", "예", "완료" -> true
            "N", "NO", "FALSE", "0", "아니오", "미납", "" -> false
            else -> false
        }
    }

    fun parseMemberStatus(raw: String): MemberStatus = when (raw.trim().uppercase()) {
        "ACTIVE", "활동", "활동중" -> MemberStatus.ACTIVE
        "DORMANT", "휴면" -> MemberStatus.DORMANT
        "WITHDRAWN", "탈퇴" -> MemberStatus.WITHDRAWN
        "" -> MemberStatus.ACTIVE
        else -> throw ExcelImportException("알 수 없는 회원 상태: $raw")
    }

    fun parseMemberRole(raw: String): MemberRole = when (raw.trim().uppercase()) {
        "GENERAL", "일반", "" -> MemberRole.GENERAL
        "PRESIDENT", "회장" -> MemberRole.PRESIDENT
        "TREASURER", "총무", "공통관리자", "운영관리자" -> MemberRole.TREASURER
        "EXECUTIVE", "임원" -> MemberRole.EXECUTIVE
        else -> throw ExcelImportException("알 수 없는 회원 역할: $raw")
    }

    fun parseTransactionType(raw: String): ClubTransactionType = when (raw.trim()) {
        "수입", "INCOME" -> ClubTransactionType.INCOME
        "지출", "EXPENSE" -> ClubTransactionType.EXPENSE
        else -> throw ExcelImportException("알 수 없는 장부 구분: $raw (수입/지출)")
    }

    fun parseCategory(raw: String, type: ClubTransactionType): String {
        if (raw.isBlank()) {
            return ClubCategory.defaultCategory(type)
        }
        val normalized = ClubCategory.normalizeCategory(raw, hintType = type)
        val resolved = ClubCategory.resolveCategory(type, normalized)
        if (ClubCategory.typeFor(resolved) != type && !ClubCategory.isTransferCategory(resolved)) {
            throw ExcelImportException("분류 '$raw'는 ${type.name} 유형과 맞지 않습니다.")
        }
        return resolved
    }

    fun parsePaymentMethod(raw: String): DuesPaymentMethod = when (raw.trim().uppercase()) {
        "월별", "MONTHLY" -> DuesPaymentMethod.MONTHLY
        "분기별", "QUARTERLY" -> DuesPaymentMethod.QUARTERLY
        "반기별", "HALF_YEARLY", "HALF-YEARLY" -> DuesPaymentMethod.HALF_YEARLY
        "연도별", "YEARLY" -> DuesPaymentMethod.YEARLY
        else -> throw ExcelImportException("알 수 없는 납부방식: $raw")
    }

    fun isRowBlank(row: Row, headerMap: Map<String, Int>): Boolean =
        headerMap.values.all { index ->
            formatCell(row.getCell(index)).isEmpty()
        }

    private fun parseDateCell(cell: Cell?, rowNumber: Int, header: String): String {
        if (cell == null) {
            throw ExcelImportException("${rowNumber}행 '$header' 날짜가 비어 있습니다.")
        }
        if (cell.cellType == CellType.NUMERIC ||
            (cell.cellType == CellType.FORMULA && cell.cachedFormulaResultType == CellType.NUMERIC)
        ) {
            if (isExcelDateCell(cell)) {
                return runCatching {
                    cell.localDateTimeCellValue.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
                }.getOrElse {
                    throw ExcelImportException("${rowNumber}행 '$header' 날짜를 읽을 수 없습니다.")
                }
            }
            val asLong = cell.numericCellValue.toLong()
            if (cell.numericCellValue == asLong.toDouble() && asLong.toString().length == 8) {
                return parseDateString(asLong.toString(), rowNumber, header)
            }
        }
        val raw = formatCell(cell)
        if (raw.isEmpty()) {
            throw ExcelImportException("${rowNumber}행 '$header' 날짜가 비어 있습니다.")
        }
        return parseDateString(raw, rowNumber, header)
    }

    private fun parseDateString(raw: String, rowNumber: Int, header: String): String {
        val normalized = raw.replace('.', '-').replace('/', '-')
        supportedDatePatterns.forEach { pattern ->
            try {
                return LocalDate.parse(normalized, pattern).format(DateTimeFormatter.ISO_LOCAL_DATE)
            } catch (_: DateTimeParseException) {
            }
        }
        throw ExcelImportException("${rowNumber}행 '$header' 날짜 형식이 올바르지 않습니다: $raw")
    }

    private fun parseInt(raw: String, rowNumber: Int, header: String): Int {
        val cleaned = raw.replace(",", "").replace("원", "").trim()
        return cleaned.toIntOrNull()
            ?: cleaned.toDoubleOrNull()?.toInt()
            ?: throw ExcelImportException("${rowNumber}행 '$header' 금액 형식이 올바르지 않습니다: $raw")
    }

    private fun parseLong(raw: String, rowNumber: Int, header: String): Long {
        val cleaned = raw.replace(",", "").replace("원", "").trim()
        return cleaned.toLongOrNull()
            ?: cleaned.toDoubleOrNull()?.toLong()
            ?: throw ExcelImportException("${rowNumber}행 '$header' 금액 형식이 올바르지 않습니다: $raw")
    }
}
