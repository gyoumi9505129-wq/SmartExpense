package com.smartexpense.domain.dues

import com.smartexpense.data.local.entity.club.DuesDetailEntity
import com.smartexpense.data.local.entity.club.DuesPaymentMethod
import com.smartexpense.data.local.entity.club.MemberEntity
import com.smartexpense.data.local.model.club.YearlyDuesWithDetails

data class MatchedDuesDetail(
    val detailId: Long,
    val year: Int,
    val termLabel: String,
    val amount: Int,
    val paymentMethod: DuesPaymentMethod
)

enum class DuesMemberMatchStatus {
    MATCHED,
    AMBIGUOUS,
    UNIDENTIFIED
}

data class DuesMatchResult(
    val memberMatchStatus: DuesMemberMatchStatus,
    val memberId: Long? = null,
    val memberName: String? = null,
    val matchedDetails: List<MatchedDuesDetail> = emptyList(),
    val remainderAmount: Int = 0,
    val ambiguousMemberIds: List<Long> = emptyList()
) {
    val canAutoMatch: Boolean
        get() = memberMatchStatus == DuesMemberMatchStatus.MATCHED && matchedDetails.isNotEmpty()

    val needsMemberSelection: Boolean
        get() = memberMatchStatus == DuesMemberMatchStatus.AMBIGUOUS ||
            memberMatchStatus == DuesMemberMatchStatus.UNIDENTIFIED

    fun termSummaryText(): String {
        if (matchedDetails.isEmpty()) return ""
        val first = matchedDetails.first()
        val firstLabel = formatTermForSummary(first.year, first.termLabel)
        return if (matchedDetails.size == 1) {
            firstLabel
        } else {
            "$firstLabel 외 ${matchedDetails.size - 1}건"
        }
    }

    fun bannerMessage(): String? = when {
        canAutoMatch -> {
            val name = memberName.orEmpty()
            "📢 [$name] 회원의 입금 알림이 있습니다. 확인을 누르면 장부 등록과 함께 " +
                "미납 회비(${termSummaryText()})가 자동으로 납부 처리됩니다."
        }
        memberMatchStatus == DuesMemberMatchStatus.AMBIGUOUS -> {
            "입금 메모에서 여러 회원이 식별되었습니다. 아래에서 회원을 직접 선택해 주세요."
        }
        memberMatchStatus == DuesMemberMatchStatus.UNIDENTIFIED -> {
            "입금자명으로 회원을 식별할 수 없습니다. 아래에서 회원을 직접 선택해 주세요."
        }
        memberMatchStatus == DuesMemberMatchStatus.MATCHED && matchedDetails.isEmpty() -> {
            val name = memberName.orEmpty()
            "[$name] 회원은 식별되었으나, 입금 금액으로 납부 가능한 미납 회비가 없습니다."
        }
        else -> null
    }
}

object DuesMatcher {

    fun match(
        memo: String,
        amount: Int,
        members: List<MemberEntity>,
        yearlyDuesRecords: List<YearlyDuesWithDetails>
    ): DuesMatchResult {
        if (amount <= 0) {
            return DuesMatchResult(memberMatchStatus = DuesMemberMatchStatus.UNIDENTIFIED)
        }
        val matchedMembers = findMembersInMemo(memo, members)
        return when {
            matchedMembers.isEmpty() -> DuesMatchResult(
                memberMatchStatus = DuesMemberMatchStatus.UNIDENTIFIED
            )
            matchedMembers.size > 1 -> DuesMatchResult(
                memberMatchStatus = DuesMemberMatchStatus.AMBIGUOUS,
                ambiguousMemberIds = matchedMembers.map { it.id }
            )
            else -> matchForMember(
                member = matchedMembers.first(),
                amount = amount,
                yearlyDuesRecords = yearlyDuesRecords
            )
        }
    }

    fun matchForMember(
        memberId: Long,
        amount: Int,
        members: List<MemberEntity>,
        yearlyDuesRecords: List<YearlyDuesWithDetails>
    ): DuesMatchResult {
        val member = members.firstOrNull { it.id == memberId }
            ?: return DuesMatchResult(memberMatchStatus = DuesMemberMatchStatus.UNIDENTIFIED)
        return matchForMember(member, amount, yearlyDuesRecords)
    }

    fun matchForMember(
        member: MemberEntity,
        amount: Int,
        yearlyDuesRecords: List<YearlyDuesWithDetails>
    ): DuesMatchResult {
        if (amount <= 0) {
            return DuesMatchResult(
                memberMatchStatus = DuesMemberMatchStatus.MATCHED,
                memberId = member.id,
                memberName = member.name
            )
        }
        val unpaidOrdered = collectUnpaidDetailsOrdered(member.id, yearlyDuesRecords)
        val (matched, remainder) = allocatePayment(amount, unpaidOrdered, yearlyDuesRecords)
        return DuesMatchResult(
            memberMatchStatus = DuesMemberMatchStatus.MATCHED,
            memberId = member.id,
            memberName = member.name,
            matchedDetails = matched,
            remainderAmount = remainder
        )
    }

    internal fun findMembersInMemo(memo: String, members: List<MemberEntity>): List<MemberEntity> {
        val normalizedMemo = memo.trim()
        if (normalizedMemo.isEmpty()) return emptyList()
        return members
            .filter { it.name.isNotBlank() && normalizedMemo.contains(it.name) }
            .sortedByDescending { it.name.length }
    }

    internal fun collectUnpaidDetailsOrdered(
        memberId: Long,
        yearlyDuesRecords: List<YearlyDuesWithDetails>
    ): List<DuesDetailEntity> =
        yearlyDuesRecords
            .filter { it.yearlyDues.memberId == memberId }
            .sortedBy { it.yearlyDues.year }
            .flatMap { record ->
                record.details
                    .filter { it.isPayableUnpaid() }
                    .sortedBy { termSortOrder(it.termLabel, record.yearlyDues.paymentMethod) }
            }

    internal fun allocatePayment(
        depositAmount: Int,
        unpaidDetails: List<DuesDetailEntity>,
        yearlyDuesRecords: List<YearlyDuesWithDetails>
    ): Pair<List<MatchedDuesDetail>, Int> {
        var remaining = depositAmount
        val matched = mutableListOf<MatchedDuesDetail>()
        val yearlyById = yearlyDuesRecords.associateBy { it.yearlyDues.id }

        for (detail in unpaidDetails) {
            if (remaining < detail.amount) break
            val yearly = yearlyById[detail.yearlyDuesId]?.yearlyDues ?: continue
            matched += MatchedDuesDetail(
                detailId = detail.id,
                year = yearly.year,
                termLabel = detail.termLabel,
                amount = detail.amount,
                paymentMethod = yearly.paymentMethod
            )
            remaining -= detail.amount
        }
        return matched to remaining
    }

    internal fun termSortOrder(termLabel: String, method: DuesPaymentMethod): Int = when (method) {
        DuesPaymentMethod.MONTHLY -> termLabel.removeSuffix("월").toIntOrNull()?.times(10) ?: Int.MAX_VALUE
        DuesPaymentMethod.QUARTERLY -> when (termLabel) {
            "1분기" -> 10
            "2분기" -> 20
            "3분기" -> 30
            "4분기" -> 40
            else -> Int.MAX_VALUE
        }
        DuesPaymentMethod.HALF_YEARLY -> when (termLabel) {
            "상반기" -> 10
            "하반기" -> 20
            else -> Int.MAX_VALUE
        }
        DuesPaymentMethod.YEARLY -> 0
    }
}

fun formatTermForSummary(year: Int, termLabel: String): String {
    val suffix = when {
        termLabel.endsWith("월") -> "${termLabel}분"
        termLabel == "연도 전체" -> "연간 회비"
        else -> termLabel
    }
    return "${year}년 $suffix"
}
