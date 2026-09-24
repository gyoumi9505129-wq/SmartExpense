package com.smartexpense.ui.club.dues

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.smartexpense.ui.club.components.ClubDateField
import com.smartexpense.ui.club.components.ClubNumberField
import com.smartexpense.ui.club.components.dateStorageToDigits
import com.smartexpense.ui.club.components.formatDateDigits
import com.smartexpense.ui.theme.DividerSubtle
import com.smartexpense.ui.theme.ExpenseRed
import com.smartexpense.ui.theme.SurfaceDeepGray
import com.smartexpense.ui.theme.TextPrimary
import com.smartexpense.ui.theme.TextSecondary
import java.time.LocalDate
import java.util.Locale

@Composable
fun DuesEditDialog(
    item: MemberDuesDetailItemUi,
    onConfirm: (List<DuesPaymentEntryUi>) -> Unit,
    onDismiss: () -> Unit,
    isReadOnly: Boolean = false
) {
    fun resolveHistory(source: MemberDuesDetailItemUi): List<DuesPaymentEntryUi> =
        source.paymentHistory.ifEmpty {
            if (source.paidAmount > 0 && source.payDate.isNotBlank()) {
                listOf(
                    DuesPaymentEntryUi(
                        payDate = source.payDate,
                        amount = source.paidAmount
                    )
                )
            } else {
                emptyList()
            }
        }

    var history by remember(item.detailId) { mutableStateOf(resolveHistory(item)) }
    // 부모 StateFlow가 비동기로 이력을 보강해도, 편집 중인 삭제/추가를 덮어쓰지 않는다.
    var payDate by remember(item.detailId) {
        mutableStateOf(LocalDate.now().toString())
    }
    var paidAmount by remember(item.detailId) {
        mutableStateOf(
            item.remainingAmount.takeIf { it > 0 }?.toString().orEmpty()
        )
    }

    val totalPaid = history.toAggregatePaidAmount()
    val remaining = (item.amount - totalPaid).coerceAtLeast(0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isReadOnly) "회비 납부 내역" else "회비 납부 내역 수정",
                color = TextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "${item.year}년 ${item.termLabel} · 목표 ${"%,d".format(Locale.KOREA, item.amount)}원",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Text(
                    text = "누적 ${"%,d".format(Locale.KOREA, totalPaid)}원 · 잔여 ${"%,d".format(Locale.KOREA, remaining)}원",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )

                if (history.isEmpty()) {
                    Text(
                        text = "아직 분할 납부 이력이 없습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(history, key = { index, entry -> "${entry.id}-$index-${entry.payDate}" }) { index, entry ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = formatHistoryDate(entry.payDate),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "${"%,d".format(Locale.KOREA, entry.amount)}원",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                                if (!isReadOnly) {
                                    IconButton(
                                        onClick = {
                                            history = history.toMutableList().also { it.removeAt(index) }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "이력 삭제",
                                            tint = ExpenseRed
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(color = DividerSubtle)
                        }
                    }
                }

                if (!isReadOnly) {
                    ClubDateField(
                        value = payDate,
                        onValueChange = { payDate = it },
                        label = "납부일 추가"
                    )
                    ClubNumberField(
                        value = paidAmount,
                        onValueChange = { paidAmount = it },
                        label = "납부 금액 추가",
                        suffix = "원",
                        formatWithComma = true,
                        imeAction = ImeAction.Done
                    )
                    OutlinedButton(
                        onClick = {
                            val amount = paidAmount.filter { it.isDigit() }.toLongOrNull() ?: 0L
                            if (amount <= 0) return@OutlinedButton
                            val normalizedDate = normalizeDialogDate(payDate)
                            if (normalizedDate.isBlank()) return@OutlinedButton
                            history = history + DuesPaymentEntryUi(
                                payDate = normalizedDate,
                                amount = amount
                            )
                            paidAmount = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("분할 납부 추가", color = TextPrimary)
                    }
                    Text(
                        text = "여러 번 나눠 낸 금액을 이력으로 남길 수 있습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        },
        confirmButton = {
            if (isReadOnly) {
                TextButton(onClick = onDismiss) {
                    Text("확인", color = TextPrimary)
                }
            } else {
                TextButton(onClick = { onConfirm(history) }) {
                    Text("확인", color = TextPrimary)
                }
            }
        },
        dismissButton = {
            if (!isReadOnly) {
                TextButton(onClick = onDismiss) {
                    Text("취소", color = TextSecondary)
                }
            }
        },
        containerColor = SurfaceDeepGray
    )
}

private fun formatHistoryDate(stored: String): String {
    val digits = dateStorageToDigits(stored)
    return if (digits.length == 8) formatDateDigits(digits) else stored
}

private fun normalizeDialogDate(value: String): String {
    val digits = dateStorageToDigits(value)
    return if (digits.length == 8) {
        "${digits.take(4)}-${digits.drop(4).take(2)}-${digits.drop(6)}"
    } else {
        value.trim()
    }
}
