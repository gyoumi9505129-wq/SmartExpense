package com.smartexpense.data.excelimport

import com.smartexpense.data.local.entity.club.ClubTransactionType
import com.smartexpense.data.local.entity.club.DuesPaymentMethod
import com.smartexpense.data.local.entity.club.MemberRole
import com.smartexpense.data.local.entity.club.MemberStatus

object ExcelImportSheets {
    const val CLUB = "모임정보"
    const val MEMBERS = "회원목록"
    const val TRANSACTIONS = "장부내역"
    const val DUES = "회비내역"
    const val ACCOUNTS = "계좌목록"
}

object ExcelImportHeaders {
    const val CLUB_NAME = "모임명"

    /** 모임명 없는 구형 양식 복원 시 사용하는 단일 모임 이름 */
    const val LEGACY_DEFAULT_CLUB_NAME = "복원모임"

    /** 입력 양식의 샘플 행 모임명 — 복원 시 무시합니다. */
    const val TEMPLATE_EXAMPLE_CLUB_NAME = "예시모임"

    const val DUES_DETAIL_ID = "회비상세ID"
    const val LINKED_DUES_DETAIL_ID = "연동회비ID"

    val MEMBERS = listOf(
        CLUB_NAME,
        "이름",
        "연락처",
        "가입일",
        "생년월일",
        "주소",
        "상세주소",
        "거주지역",
        "이메일",
        "상태",
        "역할",
        "음력생일",
        "휴면일"
    )
    val MEMBERS_LEGACY = MEMBERS.drop(1)

    private val TRANSACTIONS_REQUIRED = listOf(
        CLUB_NAME,
        "날짜",
        "구분",
        "분류",
        "수입금액",
        "지출금액",
        "적요",
        "대상회원",
        "잔액"
    )
    val TRANSACTIONS = TRANSACTIONS_REQUIRED + LINKED_DUES_DETAIL_ID
    val TRANSACTIONS_LEGACY = TRANSACTIONS_REQUIRED.drop(1)
    private val DUES_REQUIRED = listOf(
        CLUB_NAME,
        "회원이름",
        "연도",
        "납부방식",
        "회차",
        "목표금액",
        "납부금액",
        "납부완료",
        "납부일"
    )
    val DUES = DUES_REQUIRED + DUES_DETAIL_ID
    val DUES_LEGACY = DUES_REQUIRED.drop(1)
    val CLUB = listOf("모임ID", "모임명", "슬로건", "생성일")
    val ACCOUNTS = listOf(CLUB_NAME, "은행명", "계좌번호", "예금주")

    fun requiredTransactionHeaders(hasClubColumn: Boolean): List<String> =
        if (hasClubColumn) TRANSACTIONS_REQUIRED else TRANSACTIONS_LEGACY

    fun requiredDuesHeaders(hasClubColumn: Boolean): List<String> =
        if (hasClubColumn) DUES_REQUIRED else DUES_LEGACY
}

data class ExcelClubRow(
    val clubId: Long?,
    val clubName: String,
    val clubSlogan: String,
    val createdAt: String?
)

data class ExcelAccountRow(
    val clubName: String,
    val bankName: String,
    val accountNumber: String,
    val holderName: String
)

data class ExcelMemberRow(
    val clubName: String,
    val name: String,
    val phone: String,
    val joinDate: String,
    val birthDate: String,
    val address: String,
    val detailAddress: String,
    val residenceRegion: String,
    val email: String,
    val status: MemberStatus,
    val role: MemberRole,
    val isLunarBirth: Boolean,
    val suspensionDate: String?
)

data class ExcelTransactionRow(
    val clubName: String,
    val date: String,
    val type: ClubTransactionType,
    val category: String,
    val incomeAmount: Int,
    val expenseAmount: Int,
    val note: String?,
    val targetMemberName: String?,
    val balanceAfter: Int?,
    val linkedDuesDetailId: Long? = null
)

data class ExcelDuesRow(
    val clubName: String,
    val memberName: String,
    val year: Int,
    val paymentMethod: DuesPaymentMethod,
    val termLabel: String,
    val amount: Int,
    val paidAmount: Long,
    val isPaid: Boolean,
    val payDate: String,
    val sourceDetailId: Long? = null
)

data class ExcelParsedData(
    val clubInfo: ExcelClubRow?,
    val members: List<ExcelMemberRow>,
    val transactions: List<ExcelTransactionRow>,
    val dues: List<ExcelDuesRow>,
    val accounts: List<ExcelAccountRow>
) {
    val isEmpty: Boolean
        get() = clubInfo == null &&
            members.isEmpty() &&
            transactions.isEmpty() &&
            dues.isEmpty() &&
            accounts.isEmpty()
}

data class ExcelImportResult(
    val memberCount: Int,
    val transactionCount: Int,
    val duesDetailCount: Int,
    val accountCount: Int,
    val clubId: Long
) {
    val totalCount: Int = memberCount + transactionCount + duesDetailCount + accountCount
}

class ExcelImportException(message: String) : Exception(message)
