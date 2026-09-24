package com.smartexpense.ui.club.member

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartexpense.domain.admission.AdmissionFeeBreakdown
import com.smartexpense.ui.components.rememberModalBottomSheetDismissAction
import com.smartexpense.ui.theme.IncomeBlue
import com.smartexpense.ui.theme.SurfaceDeepGray
import com.smartexpense.ui.theme.SurfaceElevated
import com.smartexpense.ui.theme.TextPrimary
import com.smartexpense.ui.theme.TextSecondary
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdmissionFeeBottomSheet(
    breakdown: AdmissionFeeBreakdown?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dismissSheet = rememberModalBottomSheetDismissAction(
        sheetState = sheetState,
        onDismiss = onDismiss
    )
    val currencyFormat = remember { NumberFormat.getNumberInstance(Locale.KOREA) }
    fun formatAmount(amount: Long): String = currencyFormat.format(amount)

    ModalBottomSheet(
        onDismissRequest = dismissSheet,
        sheetState = sheetState,
        containerColor = SurfaceDeepGray,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "※ 신규 입회비 계산 (가입일 기준)",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )

            if (breakdown == null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceElevated, RoundedCornerShape(12.dp))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "입회비를 계산할 수 없습니다.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "활동 중인 회원이 없으면 공평한 입회비를 산출할 수 없습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            } else {
                val balanceText = formatAmount(breakdown.totalBalance)
                val unpaidText = formatAmount(breakdown.totalUnpaidDues)
                val feeText = formatAmount(breakdown.admissionFee)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceElevated, RoundedCornerShape(12.dp))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CalculationLine(
                        step = "1.",
                        text = "현재 기준 통장잔액 : ${balanceText}원"
                    )
                    CalculationLine(
                        step = "2.",
                        text = "미수금 (회비미납) : ${unpaidText}원"
                    )
                    CalculationLine(
                        step = "3.",
                        text = "회원수 = ${breakdown.activeMemberCount}명"
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "4. 입회비 계산",
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "   · (통장잔액 + 미수금) / 회원수 = (천단위 절사)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        lineHeight = 22.sp
                    )
                    Text(
                        text = "   · ($balanceText + $unpaidText) / ${breakdown.activeMemberCount} = ${feeText}원",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        lineHeight = 22.sp
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "최종 입회비",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary
                    )
                    Text(
                        text = "${feeText}원",
                        style = MaterialTheme.typography.displaySmall,
                        color = IncomeBlue,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CalculationLine(
    step: String,
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = "$step $text",
        style = MaterialTheme.typography.bodyLarge,
        color = TextPrimary,
        lineHeight = 24.sp,
        modifier = modifier
    )
}
