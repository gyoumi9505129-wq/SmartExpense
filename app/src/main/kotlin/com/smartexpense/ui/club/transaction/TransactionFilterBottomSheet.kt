package com.smartexpense.ui.club.transaction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.smartexpense.data.local.entity.club.ClubCategory
import com.smartexpense.ui.club.components.ClubDateField
import com.smartexpense.ui.club.components.ClubDropdownField
import com.smartexpense.ui.club.components.ClubNumberField
import com.smartexpense.ui.theme.BackgroundBlack
import com.smartexpense.ui.theme.SurfaceDeepGray
import com.smartexpense.ui.theme.TextPrimary
import com.smartexpense.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionFilterBottomSheet(
    draft: TransactionFilterState,
    memberOptions: List<Pair<Long, String>>,
    categoryOptions: List<String>,
    onStartDateChange: (String) -> Unit,
    onEndDateChange: (String) -> Unit,
    onMemberChange: (Long) -> Unit,
    onMemberClear: () -> Unit,
    onCategoryChange: (String) -> Unit,
    onCategoryClear: () -> Unit,
    onMinAmountChange: (String) -> Unit,
    onMaxAmountChange: (String) -> Unit,
    onHasReceiptChange: (Boolean) -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusMinAmount = remember { FocusRequester() }
    val focusMaxAmount = remember { FocusRequester() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceDeepGray
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "고급 필터",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Text(
                text = "기간, 회원, 카테고리, 금액, 증빙 유무를 조합해 내역을 찾을 수 있습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            ClubDateField(
                value = epochMillisToDateString(draft.startDate).orEmpty(),
                onValueChange = onStartDateChange,
                label = "시작일"
            )
            ClubDateField(
                value = epochMillisToDateString(draft.endDate).orEmpty(),
                onValueChange = onEndDateChange,
                label = "종료일"
            )

            ClubDropdownField(
                label = "회원",
                options = memberOptions,
                selected = draft.targetMemberId,
                onSelected = onMemberChange,
                placeholder = "전체",
                menuMaxHeight = 280.dp
            )
            if (draft.targetMemberId != null) {
                OutlinedButton(onClick = onMemberClear, modifier = Modifier.fillMaxWidth()) {
                    Text("회원 필터 해제")
                }
            }

            ClubDropdownField(
                label = "카테고리",
                options = categoryOptions.map { it to ClubCategory.shortLabel(it) },
                selected = draft.category,
                onSelected = onCategoryChange,
                placeholder = "전체",
                menuMaxHeight = 320.dp
            )
            if (draft.category != null) {
                OutlinedButton(onClick = onCategoryClear, modifier = Modifier.fillMaxWidth()) {
                    Text("카테고리 필터 해제")
                }
            }

            ClubNumberField(
                value = draft.minAmount?.toString().orEmpty(),
                onValueChange = onMinAmountChange,
                label = "최소 금액",
                suffix = "원",
                formatWithComma = true,
                focusRequester = focusMinAmount,
                imeAction = ImeAction.Next,
                onImeAction = { focusMaxAmount.requestFocus() }
            )
            ClubNumberField(
                value = draft.maxAmount?.toString().orEmpty(),
                onValueChange = onMaxAmountChange,
                label = "최대 금액",
                suffix = "원",
                formatWithComma = true,
                focusRequester = focusMaxAmount,
                imeAction = ImeAction.Done
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = draft.hasReceipt == true,
                    onCheckedChange = onHasReceiptChange,
                    colors = CheckboxDefaults.colors(
                        checkedColor = TextPrimary,
                        uncheckedColor = TextSecondary,
                        checkmarkColor = BackgroundBlack
                    )
                )
                Text(
                    text = "영수증 첨부됨",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("초기화")
                }
                Button(
                    onClick = onApply,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("적용")
                }
            }
        }
    }
}
