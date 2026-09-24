package com.smartexpense.ui.club.member

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.smartexpense.data.local.entity.club.MemberRole
import com.smartexpense.data.local.entity.club.MemberStatus
import com.smartexpense.ui.club.components.ClubAddressField
import com.smartexpense.ui.club.components.ClubBirthDateField
import com.smartexpense.ui.club.components.ClubDateField
import com.smartexpense.ui.club.components.ClubEmailField
import com.smartexpense.ui.club.components.ClubFormBottomSheet
import com.smartexpense.ui.club.components.ClubDropdownField
import com.smartexpense.ui.club.components.ClubPhoneField
import com.smartexpense.ui.club.components.ClubTextField
import com.smartexpense.ui.club.components.rememberClubFormFinishInput
import com.smartexpense.ui.theme.ExpenseRed

@Composable
fun MemberRegistrationBottomSheet(
    form: MemberFormState,
    isSaving: Boolean,
    isReadOnly: Boolean = false,
    hideRoleAndStatus: Boolean = false,
    titleOverride: String? = null,
    saveLabelOverride: String? = null,
    errorMessage: String? = null,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onJoinDateChange: (String) -> Unit,
    onBirthDateChange: (String) -> Unit,
    onIsLunarBirthChange: (Boolean) -> Unit,
    onResidenceRegionChange: (String) -> Unit,
    onAddressChange: (String) -> Unit,
    onDetailAddressChange: (String) -> Unit,
    onRoleChange: (MemberRole) -> Unit,
    onStatusChange: (MemberStatus) -> Unit,
    onSuspensionDateChange: (String) -> Unit,
    onStatusChangeReasonChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    hasUnsavedChanges: Boolean? = null,
    showForceWithdraw: Boolean = false,
    onForceWithdraw: () -> Unit = {}
) {
    val focusName = remember { FocusRequester() }
    val focusPhone = remember { FocusRequester() }
    val focusEmail = remember { FocusRequester() }
    val focusJoinDate = remember { FocusRequester() }
    val focusBirthDate = remember { FocusRequester() }
    val focusResidenceRegion = remember { FocusRequester() }
    val focusAddress = remember { FocusRequester() }
    val focusDetailAddress = remember { FocusRequester() }
    val focusSuspensionDate = remember { FocusRequester() }
    val focusStatusChangeReason = remember { FocusRequester() }
    val finishInput = rememberClubFormFinishInput()

    ClubFormBottomSheet(
        title = titleOverride ?: when {
            isReadOnly -> "회원 상세"
            form.isEditing -> "회원 수정"
            else -> "회원 등록"
        },
        onDismiss = onDismiss,
        onSave = {
            finishInput()
            onSave()
        },
        saveLabel = saveLabelOverride ?: if (form.isEditing) "수정" else "저장",
        isSaving = isSaving,
        hasUnsavedChanges = hasUnsavedChanges ?: (form.hasUnsavedChanges && !isReadOnly),
        showSaveButton = !isReadOnly,
        extraActions = if (showForceWithdraw) {
            {
                TextButton(
                    onClick = onForceWithdraw,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("강제 탈퇴", color = ExpenseRed)
                }
            }
        } else {
            null
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage,
                    color = ExpenseRed,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            ClubTextField(
                value = form.name,
                onValueChange = onNameChange,
                label = "이름",
                readOnly = isReadOnly,
                focusRequester = focusName,
                imeAction = ImeAction.Next,
                onImeAction = { focusPhone.requestFocus() }
            )
            ClubPhoneField(
                value = form.phone,
                onValueChange = onPhoneChange,
                label = "핸드폰번호",
                readOnly = isReadOnly,
                focusRequester = focusPhone,
                imeAction = ImeAction.Next,
                onImeAction = { focusEmail.requestFocus() }
            )
            ClubEmailField(
                value = form.email,
                onValueChange = onEmailChange,
                label = "이메일",
                readOnly = isReadOnly,
                focusRequester = focusEmail,
                imeAction = ImeAction.Next,
                onImeAction = { focusJoinDate.requestFocus() },
                errorMessage = form.emailError
            )
            ClubDateField(
                value = form.joinDate,
                onValueChange = onJoinDateChange,
                label = "가입년월일",
                enabled = !isReadOnly,
                focusRequester = focusJoinDate,
                imeAction = ImeAction.Next,
                onImeAction = { focusBirthDate.requestFocus() },
                onDatePicked = { focusBirthDate.requestFocus() }
            )
            ClubBirthDateField(
                value = form.birthDate,
                onValueChange = onBirthDateChange,
                isLunar = form.isLunarBirth,
                onLunarChange = onIsLunarBirthChange,
                enabled = !isReadOnly,
                focusRequester = focusBirthDate,
                imeAction = ImeAction.Next,
                onImeAction = { focusResidenceRegion.requestFocus() }
            )
            ClubTextField(
                value = form.residenceRegion,
                onValueChange = onResidenceRegionChange,
                label = "거주 지역",
                readOnly = isReadOnly,
                focusRequester = focusResidenceRegion,
                imeAction = ImeAction.Next,
                onImeAction = { focusAddress.requestFocus() }
            )
            ClubAddressField(
                value = form.address,
                onValueChange = onAddressChange,
                label = "주소",
                readOnly = isReadOnly,
                focusRequester = focusAddress,
                imeAction = ImeAction.Next,
                onImeAction = { focusDetailAddress.requestFocus() }
            )
            ClubTextField(
                value = form.detailAddress,
                onValueChange = onDetailAddressChange,
                label = "상세 주소",
                readOnly = isReadOnly,
                focusRequester = focusDetailAddress,
                imeAction = when {
                    !hideRoleAndStatus && (form.showSuspensionDate || form.showStatusChangeReason) ->
                        ImeAction.Next
                    else -> ImeAction.Done
                },
                onImeAction = {
                    when {
                        !hideRoleAndStatus && form.showSuspensionDate ->
                            focusSuspensionDate.requestFocus()
                        !hideRoleAndStatus && form.showStatusChangeReason ->
                            focusStatusChangeReason.requestFocus()
                        else -> finishInput()
                    }
                }
            )
            if (!hideRoleAndStatus) {
                ClubDropdownField(
                    label = "직책",
                    options = memberRoleOptions,
                    selected = form.role,
                    onSelected = onRoleChange,
                    enabled = !isReadOnly
                )
                ClubDropdownField(
                    label = "상태",
                    options = memberStatusOptions,
                    selected = form.status,
                    onSelected = onStatusChange,
                    enabled = !isReadOnly
                )
            }
            if (!hideRoleAndStatus && form.showSuspensionDate) {
                ClubDateField(
                    value = form.suspensionDate,
                    onValueChange = onSuspensionDateChange,
                    label = "활동 정지일",
                    enabled = !isReadOnly,
                    focusRequester = focusSuspensionDate,
                    onDatePicked = {
                        if (form.showStatusChangeReason) {
                            focusStatusChangeReason.requestFocus()
                        } else {
                            finishInput()
                        }
                    }
                )
            }
            if (!hideRoleAndStatus && form.showStatusChangeReason) {
                ClubTextField(
                    value = form.statusChangeReason,
                    onValueChange = onStatusChangeReasonChange,
                    label = "변경 사유",
                    readOnly = isReadOnly,
                    focusRequester = focusStatusChangeReason,
                    imeAction = ImeAction.Done,
                    onImeAction = finishInput
                )
            }
        }
    }
}
