package com.smartexpense.data.mapper.dues

import com.smartexpense.data.local.entity.club.DuesPaymentMethod
import com.smartexpense.data.local.model.club.YearlyDuesWithDetails
import com.smartexpense.domain.dues.DuesTermSchedule
import com.smartexpense.domain.dues.isEffectivelyPaid
import java.time.LocalDate

enum class UnpaidLabelStyle {
    /** 납부 탭: "3", "하" */
    COMPACT,
    /** 현황 탭: "3월", "하" */
    DESCRIPTIVE
}

data class MemberDuesSummary(
    val memberId: Long,
    val memberName: String,
    val totalUnpaidAmount: Int,
    val unpaidDetails: String,
    val isFullyPaid: Boolean,
    val defaultYearlyDuesId: Long,
    val defaultYearlyDuesYear: Int
)

fun List<YearlyDuesWithDetails>.toMemberDuesSummaries(
    memberNameById: Map<Long, String>,
    labelStyle: UnpaidLabelStyle = UnpaidLabelStyle.COMPACT,
    meetingCreatedAt: LocalDate? = null
): List<MemberDuesSummary> =
    groupBy { it.yearlyDues.memberId }
        .map { (memberId, records) ->
            val sortedByYearDesc = records.sortedByDescending { it.yearlyDues.year }
            val totalUnpaid = records.sumOf { yearlyUnpaidAmount(it, meetingCreatedAt) }
            val unpaidDetails = buildUnpaidDetailsText(records, labelStyle, meetingCreatedAt)
            val defaultRecord = records
                .filter { yearlyUnpaidAmount(it, meetingCreatedAt) > 0 }
                .minByOrNull { it.yearlyDues.year }
                ?: sortedByYearDesc.first()
            MemberDuesSummary(
                memberId = memberId,
                memberName = memberNameById[memberId] ?: "-",
                totalUnpaidAmount = totalUnpaid,
                unpaidDetails = unpaidDetails,
                isFullyPaid = totalUnpaid == 0,
                defaultYearlyDuesId = defaultRecord.yearlyDues.id,
                defaultYearlyDuesYear = defaultRecord.yearlyDues.year
            )
        }
        .sortedWith(
            compareByDescending<MemberDuesSummary> { it.totalUnpaidAmount }
                .thenBy { it.memberName }
        )

fun yearlyUnpaidAmount(
    record: YearlyDuesWithDetails,
    meetingCreatedAt: LocalDate? = null
): Int {
    val unpaidLabels = unpaidTermLabels(record, meetingCreatedAt).toSet()
    if (unpaidLabels.isEmpty()) return 0

    val fromDetails = record.details
        .filter { it.termLabel in unpaidLabels }
        .sumOf { detail ->
            (detail.amount - detail.paidAmount.toInt()).coerceAtLeast(0)
        }

    // 회비 문서에 회차 행이 아직 없는 미납 기수(기대 회차만 있는 경우)
    val expected = expectedTermLabels(
        record.yearlyDues.paymentMethod,
        record.details.map { it.termLabel }
    )
    val share = if (expected.isEmpty()) {
        0
    } else {
        record.yearlyDues.totalTargetAmount / expected.size
    }
    val missingUnpaid = unpaidLabels.count { label ->
        record.details.none { it.termLabel == label }
    }

    return (fromDetails + share * missingUnpaid).coerceAtLeast(0)
}

private fun buildUnpaidDetailsText(
    records: List<YearlyDuesWithDetails>,
    labelStyle: UnpaidLabelStyle,
    meetingCreatedAt: LocalDate?
): String {
    val termSeparator = when (labelStyle) {
        UnpaidLabelStyle.COMPACT -> ", "
        UnpaidLabelStyle.DESCRIPTIVE -> ","
    }
    return records
        .sortedBy { it.yearlyDues.year }
        .mapNotNull { record ->
            val unpaidTerms = unpaidTermLabels(record, meetingCreatedAt)
            if (unpaidTerms.isEmpty()) {
                null
            } else {
                val method = record.yearlyDues.paymentMethod
                val labels = unpaidTerms.map { formatUnpaidTermLabel(it, method, labelStyle) }
                "${record.yearlyDues.year}(${labels.joinToString(termSeparator)})"
            }
        }
        .joinToString(", ")
}

private fun unpaidTermLabels(
    record: YearlyDuesWithDetails,
    meetingCreatedAt: LocalDate?
): List<String> {
    val expected = expectedTermLabels(record.yearlyDues.paymentMethod, record.details.map { it.termLabel })
    val paidLabels = record.details.filter { it.isEffectivelyPaid() }.map { it.termLabel }.toSet()
    val excludedLabels = excludedTermLabels(record, meetingCreatedAt).toSet()
    return expected.filter { it !in paidLabels && it !in excludedLabels }
}

private fun excludedTermLabels(
    record: YearlyDuesWithDetails,
    meetingCreatedAt: LocalDate?
): List<String> {
    val method = record.yearlyDues.paymentMethod
    val year = record.yearlyDues.year
    val expected = expectedTermLabels(method, record.details.map { it.termLabel })
    val savedExcluded = record.details.filter { it.isExcluded }.map { it.termLabel }
    val autoExcluded = expected.filter { label ->
        DuesTermSchedule.isTermBeforeMeetingStart(year, method, label, meetingCreatedAt)
    }
    return (savedExcluded + autoExcluded).distinct()
}

private fun expectedTermLabels(
    method: DuesPaymentMethod,
    savedLabels: List<String>
): List<String> {
    val standard = DuesTermSchedule.termLabelsFor(method)
    if (savedLabels.any { it == "연도 전체" } && "연간" in standard) {
        return standard.map { if (it == "연간") "연도 전체" else it }
    }
    return standard
}

private fun formatUnpaidTermLabel(
    termLabel: String,
    method: DuesPaymentMethod,
    style: UnpaidLabelStyle
): String = when (style) {
    UnpaidLabelStyle.DESCRIPTIVE -> when {
        termLabel == "상반기" -> "상"
        termLabel == "하반기" -> "하"
        termLabel == "연도 전체" || termLabel == "연간" -> "전체"
        else -> termLabel
    }
    UnpaidLabelStyle.COMPACT -> when {
        termLabel == "상반기" -> "상"
        termLabel == "하반기" -> "하"
        termLabel == "연도 전체" || termLabel == "연간" -> "전체"
        method == DuesPaymentMethod.MONTHLY && termLabel.endsWith("월") ->
            termLabel.removeSuffix("월")
        method == DuesPaymentMethod.QUARTERLY && termLabel.endsWith("분기") ->
            termLabel.removeSuffix("분기")
        else -> termLabel
    }
}
