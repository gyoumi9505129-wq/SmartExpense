package com.smartexpense.ui.club.transaction

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.smartexpense.data.local.entity.club.ClubTransactionType
import com.smartexpense.data.local.entity.club.EventSubCategory
import com.smartexpense.ui.club.components.ClubDateField
import com.smartexpense.ui.club.components.ClubDropdownField
import com.smartexpense.ui.club.components.ClubFormBottomSheet
import com.smartexpense.ui.club.components.ClubNumberField
import com.smartexpense.ui.club.components.ClubReceiptField
import com.smartexpense.ui.club.components.ClubTextField
import com.smartexpense.ui.club.components.rememberClubFormFinishInput
import com.smartexpense.ui.theme.ExpenseRed
import com.smartexpense.ui.theme.IncomeBlue
import com.smartexpense.ui.theme.SurfaceDeepGray
import com.smartexpense.ui.theme.TextPrimary
import com.smartexpense.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionRegistrationBottomSheet(
    form: TransactionFormState,
    memberOptions: List<Pair<Long, String>>,
    accountOptions: List<Pair<Int, String>>,
    isSaving: Boolean,
    isReadOnly: Boolean = false,
    onEntryModeChange: (TransactionEntryMode) -> Unit,
    onCategoryChange: (String) -> Unit,
    onTargetMemberChange: (Long) -> Unit,
    onEventSubCategoryChange: (String) -> Unit,
    onAccountChange: (Int) -> Unit,
    onTransferToAccountChange: (Int) -> Unit,
    onDateChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onGalleryPicked: (Uri) -> Unit,
    onCameraCaptured: (String) -> Unit,
    onCreateCameraUri: () -> Pair<Uri, String>,
    onReceiptClear: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onConfirmDuesAutoMatch: () -> Unit = {},
    onDismissDuesAutoMatchBanner: () -> Unit = {},
    onClearMemberDropdownFocus: () -> Unit = {},
    unpaidDuesOptions: List<Pair<Long, String>> = emptyList(),
    duesMemoPreview: String? = null,
    onDuesLinkDetailChange: (Long) -> Unit = {},
    onDuesLedgerPaymentModeChange: (DuesLedgerPaymentMode) -> Unit = {}
) {
    val categoryOptions = categoriesForEntryMode(form.entryMode).map { it to it }
    val finishInput = rememberClubFormFinishInput()
    val focusDate = remember { FocusRequester() }
    val focusAmount = remember { FocusRequester() }
    val focusNote = remember { FocusRequester() }
    val focusMember = remember { FocusRequester() }

    ClubFormBottomSheet(
        title = when {
            isReadOnly -> "입출금 상세"
            form.isEditing -> "입출금 수정"
            else -> "입출금 등록"
        },
        onDismiss = onDismiss,
        onSave = {
            finishInput()
            onSave()
        },
        saveLabel = if (form.isEditing) "수정" else "저장",
        isSaving = isSaving,
        hasUnsavedChanges = form.hasDraftInput && !isReadOnly,
        showSaveButton = !isReadOnly
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!isReadOnly && form.showDuesAutoMatchBanner) {
                DuesAutoMatchBanner(
                    autoMatch = form.duesAutoMatch!!,
                    isSaving = isSaving,
                    onConfirm = onConfirmDuesAutoMatch,
                    onDismiss = onDismissDuesAutoMatchBanner
                )
            }

            TransactionEntryModeTabs(
                selected = form.entryMode,
                onSelected = onEntryModeChange,
                enabled = !isReadOnly && !form.isEditing
            )

            if (form.isTransferMode) {
                if (accountOptions.isEmpty()) {
                    Text(
                        text = "등록된 계좌가 없습니다. 설정 > 모임 계좌 관리에서 계좌를 먼저 등록해 주세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ExpenseRed
                    )
                } else {
                    ClubDropdownField(
                        enabled = !isReadOnly,
                        label = "출금 계좌",
                        options = accountOptions,
                        selected = form.accountId,
                        onSelected = onAccountChange,
                        placeholder = "돈이 빠져나갈 계좌",
                        menuMaxHeight = 280.dp
                    )
                    ClubDropdownField(
                        enabled = !isReadOnly,
                        label = "입금 계좌",
                        options = accountOptions,
                        selected = form.transferToAccountId,
                        onSelected = onTransferToAccountChange,
                        placeholder = "돈이 들어올 계좌",
                        menuMaxHeight = 280.dp
                    )
                }
            } else {
                ClubDropdownField(
                    enabled = !isReadOnly,
                    label = "카테고리 (분류)",
                    options = categoryOptions,
                    selected = form.category,
                    onSelected = onCategoryChange,
                    menuMaxHeight = 320.dp
                )
                TransactionTypeBadge(type = form.type)
                if (form.isCondolenceCategory) {
                    ClubDropdownField(
                        enabled = !isReadOnly,
                        label = "대상 회원",
                        options = memberOptions,
                        selected = form.targetMemberId,
                        onSelected = onTargetMemberChange,
                        placeholder = "경조사 대상 회원 선택",
                        menuMaxHeight = 280.dp
                    )
                    ClubDropdownField(
                        enabled = !isReadOnly,
                        label = "세부 항목",
                        options = EventSubCategory.all.map { it to it },
                        selected = form.eventSubCategory,
                        onSelected = onEventSubCategoryChange,
                        placeholder = "경조사 종류 선택",
                        menuMaxHeight = 320.dp
                    )
                } else if (form.showManualDuesLinkUi) {
                    ClubDropdownField(
                        enabled = !isReadOnly,
                        label = "회원",
                        options = memberOptions,
                        selected = form.targetMemberId,
                        onSelected = onTargetMemberChange,
                        placeholder = "회비 납부 회원 선택",
                        menuMaxHeight = 280.dp,
                        focusRequester = focusMember,
                        requestExpand = form.focusMemberDropdown,
                        onExpandHandled = onClearMemberDropdownFocus
                    )
                    ClubDropdownField(
                        enabled = !isReadOnly,
                        label = "납부 대상 월/기수",
                        options = unpaidDuesOptions,
                        selected = form.duesLinkDetailId,
                        onSelected = onDuesLinkDetailChange,
                        placeholder = if (form.targetMemberId == null) {
                            "먼저 회원을 선택해 주세요"
                        } else if (unpaidDuesOptions.isEmpty()) {
                            "미납·부분납 회비가 없습니다"
                        } else {
                            "납부할 월/기수 선택"
                        },
                        menuMaxHeight = 320.dp
                    )
                    DuesLedgerPaymentModeTabs(
                        selected = form.duesLedgerPaymentMode,
                        onSelected = onDuesLedgerPaymentModeChange,
                        enabled = !isReadOnly,
                        remainingAmount = form.duesLinkRemainingAmount
                    )
                } else if (form.showDuesMemberSelector) {
                    ClubDropdownField(
                        enabled = !isReadOnly,
                        label = "회원 (회비 납부 대상)",
                        options = memberOptions,
                        selected = form.targetMemberId,
                        onSelected = onTargetMemberChange,
                        placeholder = "입금 회원 선택",
                        menuMaxHeight = 280.dp,
                        focusRequester = focusMember,
                        requestExpand = form.focusMemberDropdown,
                        onExpandHandled = onClearMemberDropdownFocus
                    )
                }
            }

            ClubDateField(
                value = form.date,
                onValueChange = onDateChange,
                label = "연월일",
                enabled = !isReadOnly,
                focusRequester = focusDate,
                onDatePicked = { focusAmount.requestFocus() }
            )
            ClubNumberField(
                value = form.amount,
                onValueChange = onAmountChange,
                label = when {
                    form.showManualDuesLinkUi &&
                        form.duesLedgerPaymentMode == DuesLedgerPaymentMode.PARTIAL ->
                        form.duesLinkRemainingAmount
                            ?.takeIf { it > 0 }
                            ?.let { "부분 납부 금액 (잔여 %,d원 미만)".format(it) }
                            ?: "부분 납부 금액"
                    form.showManualDuesLinkUi &&
                        form.duesLedgerPaymentMode == DuesLedgerPaymentMode.FULL &&
                        form.duesLinkDetailId != null ->
                        "금액 (잔여 전액)"
                    else -> "금액"
                },
                suffix = "원",
                formatWithComma = true,
                readOnly = isReadOnly ||
                    (form.showManualDuesLinkUi &&
                        form.duesLedgerPaymentMode == DuesLedgerPaymentMode.FULL &&
                        form.duesLinkDetailId != null),
                focusRequester = focusAmount,
                imeAction = ImeAction.Done,
                onImeAction = { finishInput() }
            )
            if (form.showManualDuesLinkUi) {
                DuesMemoPreviewField(
                    preview = duesMemoPreview,
                    emptyHint = if (form.duesLedgerPaymentMode == DuesLedgerPaymentMode.PARTIAL) {
                        "회원·기수 선택 후 부분 납부 금액을 입력하면 분납으로 반영됩니다."
                    } else {
                        "회원과 납부 대상 월/기수를 선택하면 잔여 전액이 자동 입력됩니다. 일부만 내려면 부분 납부를 선택하세요."
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                ClubTextField(
                    value = form.note,
                    onValueChange = onNoteChange,
                    label = if (form.isRegularDuesCategory && form.isEditing) "내역(적요) · 회비 연동" else "내역(적요)",
                    minLines = 3,
                    singleLine = false,
                    readOnly = isReadOnly || (form.isRegularDuesCategory && form.isEditing),
                    focusRequester = focusNote,
                    imeAction = ImeAction.Done,
                    onImeAction = { finishInput() }
                )
            }
            if (!form.isTransferMode) {
                ClubReceiptField(
                    receiptPath = form.receiptPath,
                    onGalleryPicked = onGalleryPicked,
                    onCameraCaptured = onCameraCaptured,
                    onCreateCameraUri = onCreateCameraUri,
                    onClear = onReceiptClear,
                    enabled = !isReadOnly
                )
            }
        }
    }
}

@Composable
private fun DuesMemoPreviewField(
    preview: String?,
    emptyHint: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(SurfaceDeepGray, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "장부 내역(자동 생성)",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Text(
            text = preview ?: emptyHint,
            style = MaterialTheme.typography.bodyLarge,
            color = if (preview != null) TextPrimary else TextSecondary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DuesLedgerPaymentModeTabs(
    selected: DuesLedgerPaymentMode,
    onSelected: (DuesLedgerPaymentMode) -> Unit,
    enabled: Boolean,
    remainingAmount: Int?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "납부 방식",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            DuesLedgerPaymentMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = selected == mode,
                    onClick = { if (enabled) onSelected(mode) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = DuesLedgerPaymentMode.entries.size
                    ),
                    label = {
                        Text(
                            text = when (mode) {
                                DuesLedgerPaymentMode.FULL -> "완납"
                                DuesLedgerPaymentMode.PARTIAL -> "부분 납부"
                            }
                        )
                    }
                )
            }
        }
        if (selected == DuesLedgerPaymentMode.PARTIAL) {
            Text(
                text = remainingAmount
                    ?.takeIf { it > 0 }
                    ?.let { "잔여 %,d원보다 적은 금액을 입력하세요.".format(it) }
                    ?: "납부 대상 월/기수를 먼저 선택해 주세요!!",
                style = MaterialTheme.typography.bodySmall,
                color = if (remainingAmount == null || remainingAmount <= 0) {
                    ExpenseRed
                } else {
                    TextSecondary
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionEntryModeTabs(
    selected: TransactionEntryMode,
    onSelected: (TransactionEntryMode) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val modes = TransactionEntryMode.entries
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = selected == mode,
                onClick = { if (enabled) onSelected(mode) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                label = {
                    Text(
                        text = when (mode) {
                            TransactionEntryMode.INCOME -> "수입"
                            TransactionEntryMode.EXPENSE -> "지출"
                            TransactionEntryMode.TRANSFER -> "자금 이동"
                        }
                    )
                }
            )
        }
    }
}

@Composable
private fun DuesAutoMatchBanner(
    autoMatch: DuesAutoMatchUi,
    isSaving: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val message = autoMatch.bannerMessage ?: return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceDeepGray, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (autoMatch.canAutoMatch) {
                Button(
                    onClick = onConfirm,
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isSaving) "처리 중…" else "확인 (자동 매칭)")
                }
            }
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isSaving,
                modifier = if (autoMatch.canAutoMatch) Modifier.weight(1f) else Modifier.fillMaxWidth()
            ) {
                Text(if (autoMatch.canAutoMatch) "나중에" else "닫기")
            }
        }
    }
}

@Composable
private fun TransactionTypeBadge(
    type: ClubTransactionType,
    modifier: Modifier = Modifier
) {
    val (label, color) = when (type) {
        ClubTransactionType.INCOME -> "수입" to IncomeBlue
        ClubTransactionType.EXPENSE -> "지출" to ExpenseRed
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "구분",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = TextPrimary,
            modifier = Modifier
                .background(color.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
        Text(
            text = "카테고리 선택에 따라 자동 설정됩니다.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            modifier = Modifier
                .weight(1f)
                .padding(top = 4.dp)
        )
    }
}
