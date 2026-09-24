package com.smartexpense.ui.club.dues

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.smartexpense.ui.club.components.dateStorageToDigits
import com.smartexpense.ui.club.components.formatDateDigits
import com.smartexpense.ui.theme.BackgroundBlack
import com.smartexpense.ui.theme.DividerSubtle
import com.smartexpense.ui.theme.ExpenseRed
import com.smartexpense.ui.theme.IncomeBlue
import com.smartexpense.ui.theme.SurfaceDeepGray
import com.smartexpense.ui.theme.TextPrimary
import com.smartexpense.ui.theme.TextSecondary
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuesDetailBottomSheet(
    memberName: String,
    items: List<MemberDuesDetailItemUi>,
    isSaving: Boolean,
    isEditable: Boolean = true,
    onItemClick: (MemberDuesDetailItemUi) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = {
            if (!isSaving) onDismiss()
        },
        sheetState = sheetState,
        containerColor = BackgroundBlack,
        modifier = modifier
    ) {
        BackHandler(enabled = !isSaving, onBack = onDismiss)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "$memberName 납부 상세",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Text(
                text = if (isEditable) {
                    "항목을 눌러 분할 납부 이력을 추가·수정한 뒤 저장해 주세요."
                } else {
                    "완납·부분 납부를 눌러 납부 이력을 조회할 수 있습니다. (수정은 모임관리자·시스템관리자만 가능)"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 18.dp)
            )

            DuesDetailTableHeader()

            if (items.isEmpty()) {
                Text(
                    text = "표시할 납부 상세 내역이 없습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(items, key = { it.detailId }) { item ->
                        val canOpen = isEditable || item.paidAmount > 0 || item.isPaid ||
                            item.paymentHistory.isNotEmpty()
                        DuesDetailItemRow(
                            item = item,
                            enabled = canOpen && !isSaving && !item.isExcluded,
                            onClick = { onItemClick(item) }
                        )
                        HorizontalDivider(color = DividerSubtle, thickness = 0.5.dp)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextSecondary,
                        disabledContentColor = TextSecondary.copy(alpha = 0.4f)
                    )
                ) {
                    Text(if (isEditable) "닫기" else "확인")
                }
                if (isEditable) {
                    Button(
                        onClick = onSave,
                        enabled = !isSaving && items.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TextPrimary,
                            contentColor = BackgroundBlack,
                            disabledContainerColor = SurfaceDeepGray,
                            disabledContentColor = TextSecondary
                        )
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 8.dp),
                                strokeWidth = 2.dp,
                                color = BackgroundBlack
                            )
                        }
                        Text(if (isSaving) "저장 중…" else "저장")
                    }
                }
            }
        }
    }
}

@Composable
private fun DuesDetailTableHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "연도·항목",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            modifier = Modifier.weight(1.2f)
        )
        Text(
            text = "납부일",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            modifier = Modifier.weight(0.9f),
            textAlign = TextAlign.Center
        )
        Text(
            text = "금액",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End
        )
    }
    HorizontalDivider(color = DividerSubtle, thickness = 0.5.dp)
}

@Composable
private fun DuesDetailItemRow(
    item: MemberDuesDetailItemUi,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = when {
        item.isExcluded -> TextSecondary
        item.isPaid -> IncomeBlue
        item.paidAmount > 0 -> TextPrimary
        else -> ExpenseRed
    }
    val statusLabel = when {
        item.isExcluded -> "제외"
        item.isPaid && item.paymentHistory.size > 1 -> "완납 · ${item.paymentHistory.size}회 분할"
        item.isPaid -> "완납"
        item.paidAmount > 0 && item.paymentHistory.size > 1 -> "부분 납부 · ${item.paymentHistory.size}회"
        item.paidAmount > 0 -> "부분 납부"
        else -> "미납"
    }
    val payDateLabel = item.payDate
        .takeIf { it.isNotBlank() }
        ?.let { date ->
            val digits = dateStorageToDigits(date)
            if (digits.length == 8) formatDateDigits(digits) else date
        }
        ?: "-"
    val amountLabel = when {
        item.isExcluded -> "제외"
        item.paidAmount > 0 && !item.isPaid ->
            "${"%,d".format(Locale.KOREA, item.paidAmount)}원 / ${"%,d".format(Locale.KOREA, item.amount)}원"
        item.isPaid -> "${"%,d".format(Locale.KOREA, item.amount)}원"
        else -> "- / ${"%,d".format(Locale.KOREA, item.amount)}원"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Column(modifier = Modifier.weight(1.2f)) {
            Text(
                text = "${item.year}년 ${item.termLabel}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = statusColor
            )
        }
        Text(
            text = payDateLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = if (item.paidAmount > 0) TextPrimary else TextSecondary,
            modifier = Modifier.weight(0.9f),
            textAlign = TextAlign.Center
        )
        Text(
            text = amountLabel,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = statusColor,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End
        )
    }
}
