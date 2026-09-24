package com.smartexpense.data.excelimport

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.apache.poi.ss.usermodel.WorkbookFactory

@Singleton
class ExcelImporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun parse(uri: Uri): ExcelParsedData {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw ExcelImportException("파일을 열 수 없습니다.")

        return inputStream.use { stream ->
            runCatching {
                WorkbookFactory.create(stream).use { workbook ->
                    val clubInfo = workbook.getSheet(ExcelImportSheets.CLUB)?.let { parseClub(it) }
                    val members = workbook.getSheet(ExcelImportSheets.MEMBERS)?.let { parseMembers(it) }.orEmpty()
                    val transactions = workbook.getSheet(ExcelImportSheets.TRANSACTIONS)
                        ?.let { parseTransactions(it) }.orEmpty()
                    val dues = workbook.getSheet(ExcelImportSheets.DUES)?.let { parseDues(it) }.orEmpty()
                    val accounts = workbook.getSheet(ExcelImportSheets.ACCOUNTS)
                        ?.let { parseAccounts(it) }.orEmpty()

                    if (members.isEmpty() && transactions.isEmpty() && dues.isEmpty() && accounts.isEmpty()) {
                        requireSheet(workbook, ExcelImportSheets.MEMBERS)
                    }

                    val parsed = ExcelParsedData(
                        clubInfo = clubInfo,
                        members = members,
                        transactions = transactions,
                        dues = dues,
                        accounts = accounts
                    )
                    if (parsed.isEmpty) {
                        throw ExcelImportException("복원할 데이터가 없습니다.")
                    }
                    parsed
                }
            }.getOrElse { error ->
                when (error) {
                    is ExcelImportException -> throw error
                    else -> throw ExcelImportException(
                        error.message?.takeIf { it.isNotBlank() }
                            ?: "양식 형식이 올바르지 않습니다."
                    )
                }
            }
        }
    }

    private fun requireSheet(
        workbook: org.apache.poi.ss.usermodel.Workbook,
        sheetName: String
    ): org.apache.poi.ss.usermodel.Sheet {
        return workbook.getSheet(sheetName)
            ?: throw ExcelImportException("'$sheetName' 시트를 찾을 수 없습니다. 백업 파일 형식이 올바르지 않습니다.")
    }

    private fun parseClub(sheet: org.apache.poi.ss.usermodel.Sheet): ExcelClubRow? {
        val headerRow = sheet.getRow(0) ?: return null
        val headerMap = ExcelCellParsers.readHeaderMap(headerRow)
        ExcelCellParsers.requireHeaders(headerMap, ExcelImportHeaders.CLUB, ExcelImportSheets.CLUB)
        val row = sheet.getRow(1) ?: return null
        if (ExcelCellParsers.isRowBlank(row, headerMap)) return null
        val clubIdRaw = ExcelCellParsers.readOptionalString(row, headerMap, "모임ID")
        val clubName = ExcelCellParsers.readString(row, headerMap, "모임명")
        if (ExcelCellParsers.isTemplateExampleClub(clubName)) return null
        return ExcelClubRow(
            clubId = clubIdRaw?.toLongOrNull(),
            clubName = clubName,
            clubSlogan = ExcelCellParsers.readString(row, headerMap, "슬로건"),
            createdAt = ExcelCellParsers.readOptionalString(row, headerMap, "생성일")
        )
    }

    private fun parseMembers(sheet: org.apache.poi.ss.usermodel.Sheet): List<ExcelMemberRow> {
        val headerRow = sheet.getRow(0)
            ?: throw ExcelImportException("'${ExcelImportSheets.MEMBERS}' 시트 헤더가 없습니다.")
        val headerMap = ExcelCellParsers.readHeaderMap(headerRow)
        val hasClubColumn = ExcelImportHeaders.CLUB_NAME in headerMap
        ExcelCellParsers.requireHeaders(
            headerMap,
            if (hasClubColumn) ExcelImportHeaders.MEMBERS else ExcelImportHeaders.MEMBERS_LEGACY,
            ExcelImportSheets.MEMBERS
        )

        val rows = mutableListOf<ExcelMemberRow>()
        for (rowIndex in 1..sheet.lastRowNum) {
            val row = sheet.getRow(rowIndex) ?: continue
            if (ExcelCellParsers.isRowBlank(row, headerMap)) continue

            val rowNumber = rowIndex + 1
            val name = ExcelCellParsers.readString(row, headerMap, "이름")
            if (name.isEmpty()) {
                throw ExcelImportException("${rowNumber}행 회원 이름이 비어 있습니다.")
            }
            val clubName = ExcelCellParsers.readClubName(row, headerMap, rowNumber, hasClubColumn)
            if (ExcelCellParsers.isTemplateExampleClub(clubName)) continue

            rows += ExcelMemberRow(
                clubName = clubName,
                name = name,
                phone = ExcelCellParsers.readString(row, headerMap, "연락처"),
                joinDate = ExcelCellParsers.readRequiredDate(row, headerMap, "가입일", rowNumber),
                birthDate = ExcelCellParsers.readOptionalDate(row, headerMap, "생년월일", rowNumber).orEmpty(),
                address = ExcelCellParsers.readString(row, headerMap, "주소"),
                detailAddress = ExcelCellParsers.readString(row, headerMap, "상세주소"),
                residenceRegion = ExcelCellParsers.readString(row, headerMap, "거주지역"),
                email = ExcelCellParsers.readString(row, headerMap, "이메일"),
                status = ExcelCellParsers.parseMemberStatus(
                    ExcelCellParsers.readString(row, headerMap, "상태")
                ),
                role = ExcelCellParsers.parseMemberRole(
                    ExcelCellParsers.readString(row, headerMap, "역할")
                ),
                isLunarBirth = ExcelCellParsers.readBooleanYn(row, headerMap, "음력생일"),
                suspensionDate = ExcelCellParsers.readOptionalDate(row, headerMap, "휴면일", rowNumber)
            )
        }
        return rows
    }

    private fun parseTransactions(sheet: org.apache.poi.ss.usermodel.Sheet): List<ExcelTransactionRow> {
        val headerRow = sheet.getRow(0)
            ?: throw ExcelImportException("'${ExcelImportSheets.TRANSACTIONS}' 시트 헤더가 없습니다.")
        val headerMap = ExcelCellParsers.readHeaderMap(headerRow)
        val hasClubColumn = ExcelImportHeaders.CLUB_NAME in headerMap
        ExcelCellParsers.requireHeaders(
            headerMap,
            ExcelImportHeaders.requiredTransactionHeaders(hasClubColumn),
            ExcelImportSheets.TRANSACTIONS
        )

        val rows = mutableListOf<ExcelTransactionRow>()
        for (rowIndex in 1..sheet.lastRowNum) {
            val row = sheet.getRow(rowIndex) ?: continue
            if (ExcelCellParsers.isRowBlank(row, headerMap)) continue

            val rowNumber = rowIndex + 1
            val clubName = ExcelCellParsers.readClubName(row, headerMap, rowNumber, hasClubColumn)
            if (ExcelCellParsers.isTemplateExampleClub(clubName)) continue

            val type = ExcelCellParsers.parseTransactionType(
                ExcelCellParsers.readString(row, headerMap, "구분")
            )
            val category = ExcelCellParsers.parseCategory(
                ExcelCellParsers.readString(row, headerMap, "분류"),
                type
            )
            val incomeAmount = ExcelCellParsers.readInt(row, headerMap, "수입금액", rowNumber)
            val expenseAmount = ExcelCellParsers.readInt(row, headerMap, "지출금액", rowNumber)
            if (incomeAmount == 0 && expenseAmount == 0) {
                throw ExcelImportException("${rowNumber}행 수입금액 또는 지출금액 중 하나는 0보다 커야 합니다.")
            }

            rows += ExcelTransactionRow(
                clubName = clubName,                date = ExcelCellParsers.readRequiredDate(row, headerMap, "날짜", rowNumber),
                type = type,
                category = category,
                incomeAmount = incomeAmount,
                expenseAmount = expenseAmount,
                note = ExcelCellParsers.readOptionalString(row, headerMap, "적요"),
                targetMemberName = ExcelCellParsers.readOptionalString(row, headerMap, "대상회원"),
                balanceAfter = ExcelCellParsers.readOptionalString(row, headerMap, "잔액")?.let {
                    ExcelCellParsers.readInt(row, headerMap, "잔액", rowNumber)
                },
                linkedDuesDetailId = ExcelCellParsers.readOptionalLong(
                    row,
                    headerMap,
                    ExcelImportHeaders.LINKED_DUES_DETAIL_ID,
                    rowNumber
                )
            )
        }
        return rows
    }

    private fun parseDues(sheet: org.apache.poi.ss.usermodel.Sheet): List<ExcelDuesRow> {
        val headerRow = sheet.getRow(0)
            ?: throw ExcelImportException("'${ExcelImportSheets.DUES}' 시트 헤더가 없습니다.")
        val headerMap = ExcelCellParsers.readHeaderMap(headerRow)
        val hasClubColumn = ExcelImportHeaders.CLUB_NAME in headerMap
        ExcelCellParsers.requireHeaders(
            headerMap,
            ExcelImportHeaders.requiredDuesHeaders(hasClubColumn),
            ExcelImportSheets.DUES
        )

        val rows = mutableListOf<ExcelDuesRow>()
        for (rowIndex in 1..sheet.lastRowNum) {
            val row = sheet.getRow(rowIndex) ?: continue
            if (ExcelCellParsers.isRowBlank(row, headerMap)) continue

            val rowNumber = rowIndex + 1
            val memberName = ExcelCellParsers.readString(row, headerMap, "회원이름")
            if (memberName.isEmpty()) {
                throw ExcelImportException("${rowNumber}행 회원이름이 비어 있습니다.")
            }
            val clubName = ExcelCellParsers.readClubName(row, headerMap, rowNumber, hasClubColumn)
            if (ExcelCellParsers.isTemplateExampleClub(clubName)) continue

            val termLabel = ExcelCellParsers.readString(row, headerMap, "회차")
            if (termLabel.isEmpty()) {
                throw ExcelImportException("${rowNumber}행 회차가 비어 있습니다.")
            }

            val isPaid = ExcelCellParsers.readBooleanYn(row, headerMap, "납부완료")
            val paidAmount = ExcelCellParsers.readLong(row, headerMap, "납부금액", rowNumber)
            val amount = ExcelCellParsers.readRequiredInt(row, headerMap, "목표금액", rowNumber)

            rows += ExcelDuesRow(
                clubName = clubName,                memberName = memberName,
                year = ExcelCellParsers.readRequiredInt(row, headerMap, "연도", rowNumber),
                paymentMethod = ExcelCellParsers.parsePaymentMethod(
                    ExcelCellParsers.readString(row, headerMap, "납부방식")
                ),
                termLabel = termLabel,
                amount = amount,
                paidAmount = if (paidAmount > 0L) paidAmount else if (isPaid) amount.toLong() else 0L,
                isPaid = isPaid,
                payDate = ExcelCellParsers.readOptionalDate(row, headerMap, "납부일", rowNumber).orEmpty(),
                sourceDetailId = ExcelCellParsers.readOptionalLong(
                    row,
                    headerMap,
                    ExcelImportHeaders.DUES_DETAIL_ID,
                    rowNumber
                )
            )
        }
        return rows
    }

    private fun parseAccounts(sheet: org.apache.poi.ss.usermodel.Sheet): List<ExcelAccountRow> {
        val headerRow = sheet.getRow(0)
            ?: throw ExcelImportException("'${ExcelImportSheets.ACCOUNTS}' 시트 헤더가 없습니다.")
        val headerMap = ExcelCellParsers.readHeaderMap(headerRow)
        ExcelCellParsers.requireHeaders(headerMap, ExcelImportHeaders.ACCOUNTS, ExcelImportSheets.ACCOUNTS)

        val rows = mutableListOf<ExcelAccountRow>()
        for (rowIndex in 1..sheet.lastRowNum) {
            val row = sheet.getRow(rowIndex) ?: continue
            if (ExcelCellParsers.isRowBlank(row, headerMap)) continue

            val rowNumber = rowIndex + 1
            val clubName = ExcelCellParsers.readClubName(row, headerMap, rowNumber, hasClubColumn = true)
            if (ExcelCellParsers.isTemplateExampleClub(clubName)) continue

            rows += ExcelAccountRow(
                clubName = clubName,                bankName = ExcelCellParsers.readString(row, headerMap, "은행명"),
                accountNumber = ExcelCellParsers.readString(row, headerMap, "계좌번호"),
                holderName = ExcelCellParsers.readString(row, headerMap, "예금주")
            )
        }
        return rows
    }
}
