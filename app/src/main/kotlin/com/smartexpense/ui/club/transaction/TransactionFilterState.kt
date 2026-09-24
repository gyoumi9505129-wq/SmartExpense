package com.smartexpense.ui.club.transaction

import com.smartexpense.data.local.entity.club.ClubCategory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * 장부 고급 필터 조건.
 *
 * [startDate]/[endDate]는 epoch millis(로컬 자정) 기준이며, DAO 쿼리 시 `yyyy-MM-dd` 문자열로 변환됩니다.
 */
data class TransactionFilterState(
    val startDate: Long? = null,
    val endDate: Long? = null,
    val targetMemberId: Long? = null,
    val category: String? = null,
    val minAmount: Long? = null,
    val maxAmount: Long? = null,
    val hasReceipt: Boolean? = null
) {
    val isActive: Boolean
        get() = startDate != null ||
            endDate != null ||
            targetMemberId != null ||
            category != null ||
            minAmount != null ||
            maxAmount != null ||
            hasReceipt != null
}

enum class TransactionFilterKey {
    START_DATE,
    END_DATE,
    DATE_RANGE,
    MEMBER,
    CATEGORY,
    MIN_AMOUNT,
    MAX_AMOUNT,
    AMOUNT_RANGE,
    RECEIPT
}

data class ActiveFilterChipUi(
    val key: TransactionFilterKey,
    val label: String
)

fun TransactionFilterState.toActiveChips(
    memberOptions: List<Pair<Long, String>>
): List<ActiveFilterChipUi> = buildList {
    when {
        startDate != null && endDate != null -> add(
            ActiveFilterChipUi(
                key = TransactionFilterKey.DATE_RANGE,
                label = "${startDate!!.toFilterDateLabel()} ~ ${endDate!!.toFilterDateLabel()}"
            )
        )
        startDate != null -> add(
            ActiveFilterChipUi(
                key = TransactionFilterKey.START_DATE,
                label = "${startDate!!.toFilterDateLabel()} 이후"
            )
        )
        endDate != null -> add(
            ActiveFilterChipUi(
                key = TransactionFilterKey.END_DATE,
                label = "${endDate!!.toFilterDateLabel()} 이전"
            )
        )
    }
    targetMemberId?.let { memberId ->
        val name = memberOptions.firstOrNull { it.first == memberId }?.second ?: "회원 #$memberId"
        add(ActiveFilterChipUi(key = TransactionFilterKey.MEMBER, label = name))
    }
    category?.let {
        add(ActiveFilterChipUi(key = TransactionFilterKey.CATEGORY, label = ClubCategory.shortLabel(it)))
    }
    when {
        minAmount != null && maxAmount != null -> add(
            ActiveFilterChipUi(
                key = TransactionFilterKey.AMOUNT_RANGE,
                label = "%,d ~ %,d원".format(Locale.KOREA, minAmount, maxAmount)
            )
        )
        minAmount != null -> add(
            ActiveFilterChipUi(
                key = TransactionFilterKey.MIN_AMOUNT,
                label = "%,d원 이상".format(Locale.KOREA, minAmount)
            )
        )
        maxAmount != null -> add(
            ActiveFilterChipUi(
                key = TransactionFilterKey.MAX_AMOUNT,
                label = "%,d원 이하".format(Locale.KOREA, maxAmount)
            )
        )
    }
    if (hasReceipt == true) {
        add(ActiveFilterChipUi(key = TransactionFilterKey.RECEIPT, label = "영수증 첨부됨"))
    }
}

fun epochMillisFromDateString(date: String): Long? =
    runCatching {
        LocalDate.parse(date)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }.getOrNull()

fun epochMillisToDateString(millis: Long?): String? =
    millis?.let {
        Instant.ofEpochMilli(it)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toString()
    }

fun Long.toFilterDateString(): String =
    Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .toString()

private fun Long.toFilterDateLabel(): String = toFilterDateString().toFullDateLabel()
