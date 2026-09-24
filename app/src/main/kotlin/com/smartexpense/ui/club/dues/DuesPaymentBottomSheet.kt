package com.smartexpense.ui.club.dues

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartexpense.data.local.entity.club.DuesPaymentMethod
import com.smartexpense.ui.club.components.ClubDateField
import com.smartexpense.ui.club.components.ClubDropdownField
import com.smartexpense.ui.club.components.ClubFormBottomSheet
import com.smartexpense.ui.club.components.ClubNumberField
import com.smartexpense.ui.club.components.rememberClubFormFinishInput
import com.smartexpense.ui.theme.ExpenseRed
import com.smartexpense.ui.theme.IncomeBlue
import com.smartexpense.ui.theme.TextPrimary
import com.smartexpense.ui.theme.TextSecondary
import java.util.Locale.KOREA

private val PaidCompleteGreen = Color(0xFF22C55E)

private val DuesTermLabelWidth = 42.dp
private val DuesMonthDropdownWidth = 88.dp
private val DuesPaidToggleWidth = 80.dp
private val DuesDetailFieldSpacing = 6.dp

@Composable
private fun DuesPaidToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .width(DuesPaidToggleWidth)
            .padding(horizontal = 4.dp, vertical = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Text(
            text = "납부",
            fontSize = 12.sp,
            color = TextSecondary
        )
    }
}

@Composable
private fun DuesExcludeToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .width(DuesPaidToggleWidth)
            .padding(horizontal = 4.dp, vertical = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Text(
            text = "제외",
            fontSize = 12.sp,
            color = TextSecondary
        )
    }
}

@Composable
fun DuesPaymentBottomSheet(
    form: DuesPaymentFormState,
    memberOptions: List<DuesMemberOption>,
    isSaving: Boolean,
    onMemberChange: (Long) -> Unit,
    onYearChange: (Int) -> Unit,
    onTotalTargetAmountChange: (String) -> Unit,
    onPaymentMethodChange: (DuesPaymentMethod) -> Unit,
    onDetailAmountChange: (localKey: String, value: String) -> Unit,
    onDetailAdditionalPayAmountChange: (localKey: String, value: String) -> Unit = { _, _ -> },
    onDetailPayDateChange: (localKey: String, value: String) -> Unit,
    onDetailPaidChange: (localKey: String, isPaid: Boolean) -> Unit,
    onDetailExcludedChange: (localKey: String, isExcluded: Boolean) -> Unit,
    onAddMonthlyDetail: () -> Unit = {},
    onRemoveMonthlyDetail: (localKey: String) -> Unit = {},
    onMonthlyMonthChange: (localKey: String, month: Int) -> Unit = { _, _ -> },
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val memberDropdownOptions = memberOptions.map { it.id to it.name }
    val focusTotal = remember { FocusRequester() }
    val detailAmountFocusRequesters = remember(form.details.map { it.localKey }) {
        List(form.details.size) { FocusRequester() }
    }
    val finishInput = rememberClubFormFinishInput()
    val compactFieldStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp)
    val hasDetails = form.details.isNotEmpty()
    val isMonthly = form.paymentMethod == DuesPaymentMethod.MONTHLY

    ClubFormBottomSheet(
        title = if (form.isEditMode) "회비 납부 수정" else "회비 납부 등록",
        onDismiss = onDismiss,
        onSave = {
            finishInput()
            onSave()
        },
        saveLabel = if (form.isEditMode) "수정" else "저장",
        isSaving = isSaving,
        hasUnsavedChanges = form.hasDraftInput
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (form.isEditMode) {
                Text(
                    text = "이 회원·연도의 등록된 회비입니다. 미납·부분납 회차를 확인한 뒤 납부 처리할 수 있습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            ClubNumberField(
                value = form.totalTargetAmount,
                onValueChange = onTotalTargetAmountChange,
                label = "연회비 (총 납부 대상 금액)",
                formatWithComma = true,
                focusRequester = focusTotal,
                imeAction = if (hasDetails) ImeAction.Next else ImeAction.Done,
                onImeAction = {
                    if (hasDetails) {
                        detailAmountFocusRequesters.firstOrNull()?.requestFocus()
                    } else {
                        finishInput()
                    }
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "납부: %,d원".format(KOREA, form.paidAccumulatedAmount),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = IncomeBlue
                )
                if (form.isFullyPaid) {
                    Text(
                        text = "완납",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = PaidCompleteGreen
                    )
                } else {
                    Text(
                        text = "미납: %,d원".format(KOREA, form.unpaidAmount),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = ExpenseRed
                    )
                }
            }

            ClubDropdownField(
                label = "납부 방법",
                options = duesPaymentMethodOptions,
                selected = form.paymentMethod,
                onSelected = onPaymentMethodChange
            )

            ClubDropdownField(
                label = "회원",
                options = memberDropdownOptions,
                selected = form.memberId,
                onSelected = onMemberChange,
                placeholder = "회원 선택",
                menuMaxHeight = 280.dp
            )

            ClubDropdownField(
                label = "납부연도",
                options = duesYearOptions,
                selected = form.year,
                onSelected = onYearChange,
                menuMaxHeight = 280.dp,
                scrollToValueOnExpand = java.time.Year.now().value
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (form.details.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isMonthly) {
                            "납부할 월을 추가해 주세요."
                        } else {
                            "납부 방법을 선택해 주세요."
                        },
                        color = TextSecondary
                    )
                    if (isMonthly) {
                        TextButton(onClick = onAddMonthlyDetail) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ 납부 월 추가")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(form.details, key = { _, item -> item.localKey }) { index, item ->
                        val isLastDetail = index == form.details.lastIndex
                        val statusText: String?
                        val statusColor: Color
                        when {
                            item.isExcluded -> {
                                statusText = "제외"
                                statusColor = TextSecondary
                            }
                            item.isPartialPaid -> {
                                statusText = "부분납 %,d · 잔여 %,d".format(
                                    KOREA,
                                    item.paidAmount,
                                    item.remainingAmount
                                )
                                statusColor = ExpenseRed
                            }
                            item.isPaid -> {
                                statusText = "완납"
                                statusColor = PaidCompleteGreen
                            }
                            item.targetAmount > 0 -> {
                                statusText = "미납 %,d".format(KOREA, item.targetAmount)
                                statusColor = ExpenseRed
                            }
                            else -> {
                                statusText = null
                                statusColor = TextSecondary
                            }
                        }
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(DuesDetailFieldSpacing)
                            ) {
                                if (isMonthly) {
                                    ClubDropdownField(
                                        label = "월",
                                        options = duesMonthOptions,
                                        selected = item.monthNumber,
                                        onSelected = { onMonthlyMonthChange(item.localKey, it) },
                                        modifier = Modifier.width(DuesMonthDropdownWidth),
                                        menuMaxHeight = 240.dp
                                    )
                                } else {
                                    Column(
                                        modifier = Modifier.width(DuesTermLabelWidth + 36.dp)
                                    ) {
                                        Text(
                                            text = item.termLabel,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (statusText != null) {
                                            Text(
                                                text = statusText,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = statusColor,
                                                maxLines = 2
                                            )
                                        }
                                    }
                                }
                                ClubNumberField(
                                    value = item.amount,
                                    onValueChange = { onDetailAmountChange(item.localKey, it) },
                                    label = when {
                                        item.isPartialPaid ->
                                            "회차 목표(누적 %,d · 잔여 %,d)".format(
                                                KOREA,
                                                item.paidAmount,
                                                item.remainingAmount
                                            )
                                        item.isPaid && item.initialPaidAmount in 1 until item.targetAmount.toLong()
                                            .coerceAtLeast(1) ->
                                            "회차금액(잔여 완납)"
                                        else -> "회차 목표 금액"
                                    },
                                    formatWithComma = true,
                                    // 목표액은 기존 회차에서 잠금(잔여액을 목표로 덮어쓰는 사고 방지)
                                    readOnly = item.initialPaidAmount > 0L || item.id > 0L,
                                    modifier = Modifier.weight(1f),
                                    focusRequester = detailAmountFocusRequesters[index],
                                    imeAction = if (isLastDetail) ImeAction.Done else ImeAction.Next,
                                    onImeAction = {
                                        if (isLastDetail) {
                                            finishInput()
                                        } else {
                                            detailAmountFocusRequesters.getOrNull(index + 1)?.requestFocus()
                                        }
                                    },
                                    textStyle = compactFieldStyle
                                )
                                if (isMonthly) {
                                    IconButton(
                                        onClick = { onRemoveMonthlyDetail(item.localKey) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "항목 삭제",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                DuesPaidToggle(
                                    checked = item.isPaid,
                                    onCheckedChange = { checked ->
                                        onDetailPaidChange(item.localKey, checked)
                                    }
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(DuesDetailFieldSpacing)
                            ) {
                                Spacer(
                                    modifier = Modifier.width(
                                        if (isMonthly) {
                                            DuesMonthDropdownWidth
                                        } else {
                                            DuesTermLabelWidth + 36.dp
                                        }
                                    )
                                )
                                ClubDateField(
                                    value = item.payDate,
                                    onValueChange = { onDetailPayDateChange(item.localKey, it) },
                                    label = "납부일자",
                                    modifier = Modifier.weight(1f),
                                    textStyle = compactFieldStyle,
                                    compact = true
                                )
                                if (isMonthly) {
                                    Spacer(modifier = Modifier.size(36.dp))
                                }
                                DuesExcludeToggle(
                                    checked = item.isExcluded,
                                    onCheckedChange = { checked ->
                                        onDetailExcludedChange(item.localKey, checked)
                                    }
                                )
                            }
                            if (!item.isExcluded && !item.isPaid && item.remainingAmount > 0) {
                                if (item.paymentHistory.isNotEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(
                                                start = if (isMonthly) {
                                                    DuesMonthDropdownWidth + DuesDetailFieldSpacing
                                                } else {
                                                    DuesTermLabelWidth + 36.dp + DuesDetailFieldSpacing
                                                }
                                            ),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = "분납 이력",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                        item.paymentHistory.forEach { entry ->
                                            Text(
                                                text = "${entry.payDate} · %,d원".format(
                                                    KOREA,
                                                    entry.amount
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextPrimary
                                            )
                                        }
                                    }
                                }
                                ClubNumberField(
                                    value = item.additionalPayAmount,
                                    onValueChange = {
                                        onDetailAdditionalPayAmountChange(item.localKey, it)
                                    },
                                    label = "추가 납부 금액 (잔여 %,d원 이내)".format(
                                        KOREA,
                                        item.remainingAmount
                                    ),
                                    formatWithComma = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            start = if (isMonthly) {
                                                DuesMonthDropdownWidth + DuesDetailFieldSpacing
                                            } else {
                                                DuesTermLabelWidth + 36.dp + DuesDetailFieldSpacing
                                            }
                                        ),
                                    textStyle = compactFieldStyle
                                )
                                Text(
                                    text = "잔여 전액 완납은 위 납부 체크를 사용하세요.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(
                                        start = if (isMonthly) {
                                            DuesMonthDropdownWidth + DuesDetailFieldSpacing
                                        } else {
                                            DuesTermLabelWidth + 36.dp + DuesDetailFieldSpacing
                                        }
                                    )
                                )
                            }
                            if (isMonthly && statusText != null) {
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = statusColor,
                                    modifier = Modifier.padding(
                                        start = DuesMonthDropdownWidth + DuesDetailFieldSpacing
                                    )
                                )
                            }
                        }
                    }
                    if (isMonthly) {
                        item {
                            TextButton(
                                onClick = onAddMonthlyDetail,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("+ 납부 월 추가")
                            }
                        }
                    }
                }
            }
        }
    }
}
