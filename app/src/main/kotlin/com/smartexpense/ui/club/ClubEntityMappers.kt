package com.smartexpense.ui.club

import com.smartexpense.data.local.entity.club.ClubCategory
import com.smartexpense.data.local.entity.club.ClubTransactionEntity
import com.smartexpense.data.local.entity.club.ClubTransactionType
import com.smartexpense.data.local.entity.club.MemberEntity
import com.smartexpense.data.local.entity.club.MemberStatusHistoryEntity
import com.smartexpense.data.local.entity.club.DuesDetailEntity
import com.smartexpense.data.local.model.club.YearlyDuesWithDetails
import com.smartexpense.ui.club.dues.DuesMemberOption
import com.smartexpense.ui.club.dues.DuesDetailItemState
import com.smartexpense.ui.club.dues.DuesPaymentItemUi
import com.smartexpense.ui.club.dues.toDisplayLabel
import com.smartexpense.ui.club.member.MemberListItemUi
import com.smartexpense.ui.club.member.MemberHistoryItemUi
import com.smartexpense.ui.club.member.statusNameToDisplayLabel
import com.smartexpense.ui.club.member.toDisplayLabel
import com.smartexpense.ui.club.transaction.ClubTransactionItemUi
import com.smartexpense.ui.club.transaction.toDisplayLabel

import com.smartexpense.ui.club.components.dateDigitsToStorage
import com.smartexpense.ui.club.components.dateStorageToDigits
import com.smartexpense.ui.club.components.formatDateDigits
import com.smartexpense.ui.club.dues.DuesPaymentFormState
import com.smartexpense.ui.club.member.MemberFormState
import com.smartexpense.ui.club.transaction.TransactionEntryMode
import com.smartexpense.ui.club.transaction.TransactionFormState

fun MemberEntity.toListItemUi() = MemberListItemUi(
    id = id,
    name = name,
    phone = phone,
    joinDate = joinDate,
    residenceRegion = residenceRegion,
    roleLabel = role.toDisplayLabel(),
    statusLabel = status.toDisplayLabel(),
    status = status
)

fun MemberStatusHistoryEntity.toHistoryItemUi() = MemberHistoryItemUi(
    changeDate = changeDate,
    changeLabel = "${statusNameToDisplayLabel(oldStatus)} → ${statusNameToDisplayLabel(newStatus)}",
    suspensionDate = suspensionDate?.takeIf { it.isNotBlank() },
    reason = reason?.takeIf { it.isNotBlank() }
)

fun MemberEntity.toDuesMemberOption() = DuesMemberOption(id = id, name = name)

fun MemberFormState.toEntity(): MemberEntity = MemberEntity(
    id = editingId ?: 0,
    clubId = 0,
    joinDate = dateDigitsToStorage(joinDate),
    name = name.trim(),
    birthDate = dateDigitsToStorage(birthDate),
    address = address.trim(),
    detailAddress = detailAddress.trim(),
    residenceRegion = residenceRegion.trim(),
    phone = phone.trim(),
    email = email.trim(),
    status = status,
    role = role,
    isLunarBirth = isLunarBirth,
    suspensionDate = if (showSuspensionDate && suspensionDate.isNotBlank()) {
        dateDigitsToStorage(suspensionDate)
    } else {
        null
    }
)

fun MemberEntity.toFormState() = MemberFormState(
    editingId = id,
    name = name,
    phone = phone,
    email = email,
    joinDate = dateStorageToDigits(joinDate),
    birthDate = dateStorageToDigits(birthDate),
    isLunarBirth = isLunarBirth,
    residenceRegion = residenceRegion,
    address = address,
    detailAddress = detailAddress,
    role = role,
    status = status,
    originalStatus = status,
    suspensionDate = suspensionDate?.let { dateStorageToDigits(it) }.orEmpty()
).withSavedBaseline()

fun YearlyDuesWithDetails.toPaymentItemUi(memberName: String): DuesPaymentItemUi {
    val paidAmount = details.filter { !it.isExcluded }.sumOf { it.paidAmount.toInt() }
    val excludedAmount = details.filter { it.isExcluded }.sumOf { it.amount }
    val unpaidAmount = (yearlyDues.totalTargetAmount - excludedAmount - paidAmount).coerceAtLeast(0)
    val billable = yearlyDues.totalTargetAmount - excludedAmount
    return DuesPaymentItemUi(
        id = yearlyDues.id,
        memberName = memberName,
        year = yearlyDues.year,
        paymentMethodLabel = yearlyDues.paymentMethod.toDisplayLabel(),
        periodSummary = formatDuesPeriodSummary(yearlyDues.year, details),
        totalTargetAmount = yearlyDues.totalTargetAmount,
        paidAmount = paidAmount,
        unpaidAmount = unpaidAmount,
        isFullyPaid = billable > 0 && unpaidAmount == 0,
        latestPayDate = details
            .filter { it.isPaid && !it.isExcluded && it.payDate.isNotBlank() }
            .maxByOrNull { it.payDate }
            ?.payDate
            ?.let { dateStorageToDigits(it).let { d -> formatDateDigits(d) } }
            ?: "-"
    )
}

private fun formatDuesPeriodSummary(year: Int, details: List<DuesDetailEntity>): String {
    val labels = details.map { it.termLabel }
    return if (labels.isEmpty()) {
        "${year}년"
    } else {
        "${year}년 (${labels.joinToString(", ")})"
    }
}

fun YearlyDuesWithDetails.toFormState(): DuesPaymentFormState = DuesPaymentFormState(
    currentDuesId = yearlyDues.id,
    memberId = yearlyDues.memberId,
    year = yearlyDues.year,
    totalTargetAmount = yearlyDues.totalTargetAmount.toString(),
    paymentMethod = yearlyDues.paymentMethod,
    details = details.map { it.toDetailItemState() }
)

fun DuesDetailEntity.toDetailItemState() = DuesDetailItemState(
    id = id,
    termLabel = termLabel,
    amount = amount.toString(),
    paidAmount = paidAmount.coerceAtLeast(0L),
    initialPaidAmount = paidAmount.coerceAtLeast(0L),
    isPaid = (isPaid || (paidAmount >= amount && amount > 0)) && !isExcluded,
    isExcluded = isExcluded,
    payDate = if (payDate.isNotBlank()) {
        val digits = dateStorageToDigits(payDate)
        if (digits.length == 8) formatDateDigits(digits) else payDate
    } else {
        ""
    },
    isCustomAmount = true
)

fun DuesDetailItemState.toEntityForSave(yearlyDuesId: Long): DuesDetailEntity {
    val target = amount.toIntOrNull() ?: 0
    val paid = effectivePaidAmountForSave()
    val payDateStorage = if (payDate.isNotBlank() && paid > 0) {
        dateDigitsToStorage(payDate)
    } else {
        ""
    }
    return DuesDetailEntity(
        id = id,
        clubId = 0,
        yearlyDuesId = yearlyDuesId,
        termLabel = termLabel,
        amount = target,
        paidAmount = paid,
        isPaid = !isExcluded && target > 0 && paid >= target,
        isExcluded = isExcluded && !isPaid,
        payDate = payDateStorage
    )
}

fun ClubTransactionEntity.toListItemUi() = ClubTransactionItemUi(
    id = id,
    date = date,
    typeLabel = when {
        ClubCategory.isTransferCategory(category) -> "이체"
        else -> type.toDisplayLabel()
    },
    category = ClubCategory.shortLabel(category),
    amount = when {
        ClubCategory.isTransferCategory(category) -> expenseAmount
        type == ClubTransactionType.INCOME -> incomeAmount
        else -> expenseAmount
    },
    note = note,
    balanceAfter = balanceAfter,
    hasReceipt = !receiptPath.isNullOrBlank(),
    isTransfer = ClubCategory.isTransferCategory(category)
)

fun ClubTransactionEntity.toFormState(): TransactionFormState {
    val isTransfer = ClubCategory.isTransferCategory(category)
    val resolvedCategory = if (isTransfer) category else ClubCategory.resolveCategory(type, category)
    val resolvedType = if (isTransfer) type else ClubCategory.resolveType(resolvedCategory, type)
    val entryMode = if (isTransfer) {
        TransactionEntryMode.TRANSFER
    } else if (resolvedType == ClubTransactionType.INCOME) {
        TransactionEntryMode.INCOME
    } else {
        TransactionEntryMode.EXPENSE
    }
    return TransactionFormState(
        editingId = id,
        entryMode = entryMode,
        type = resolvedType,
        category = resolvedCategory,
        date = date,
        amount = (if (isTransfer) expenseAmount else if (resolvedType == ClubTransactionType.INCOME) incomeAmount else expenseAmount).toString(),
        note = note.orEmpty(),
        receiptPath = receiptPath,
        originalReceiptPath = receiptPath,
        targetMemberId = targetMemberId,
        eventSubCategory = eventSubCategory,
        originalTargetMemberId = targetMemberId,
        originalEventSubCategory = eventSubCategory,
        accountId = accountId,
        transferToAccountId = transferToAccountId,
        originalAccountId = accountId,
        originalTransferToAccountId = transferToAccountId,
        duesLinkDetailId = linkedDuesDetailId,
        originalDuesLinkDetailId = linkedDuesDetailId
    )
}
