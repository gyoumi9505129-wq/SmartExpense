package com.smartexpense.ui.club.hub

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
// AppLockUnlockMethod는 HubSettingsState를 통해 전달됨
import com.smartexpense.ui.club.components.ClubTextField
import com.smartexpense.ui.theme.BorderLine
import com.smartexpense.ui.theme.ExpenseRed
import com.smartexpense.ui.theme.IncomeBlue
import com.smartexpense.ui.theme.SurfaceDeepGray
import com.smartexpense.ui.theme.TextPrimary
import com.smartexpense.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingHubSettingsSheet(
    settings: HubSettingsState,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onCurrentPasswordChange: (String) -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onSaveProfile: () -> Unit,
    onChangePassword: () -> Unit,
    onRequestLogout: () -> Unit,
    onDismissLogoutConfirm: () -> Unit,
    onConfirmLogout: () -> Unit,
    onOpenUnlockMethodDialog: () -> Unit,
    onChangePin: () -> Unit,
    onChangePattern: () -> Unit,
    onBiometricChanged: (Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(settings.feedbackMessage) {
        val message = settings.feedbackMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceDeepGray
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            HubSettingsContent(
                settings = settings,
                isSubmitting = isSubmitting,
                onDisplayNameChange = onDisplayNameChange,
                onPhoneChange = onPhoneChange,
                onCurrentPasswordChange = onCurrentPasswordChange,
                onNewPasswordChange = onNewPasswordChange,
                onConfirmPasswordChange = onConfirmPasswordChange,
                onSaveProfile = onSaveProfile,
                onChangePassword = onChangePassword,
                onRequestLogout = onRequestLogout,
                onOpenUnlockMethodDialog = onOpenUnlockMethodDialog,
                onChangePin = onChangePin,
                onChangePattern = onChangePattern,
                onBiometricChanged = onBiometricChanged
            )
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp)
            )
        }
    }

    if (settings.showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = onDismissLogoutConfirm,
            title = { Text("로그아웃", color = TextPrimary) },
            text = {
                Text(
                    "로그아웃하면 앱이 종료됩니다.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmLogout) {
                    Text("로그아웃", color = ExpenseRed)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissLogoutConfirm) {
                    Text("취소", color = TextSecondary)
                }
            },
            containerColor = SurfaceDeepGray
        )
    }
}

@Composable
private fun HubSettingsContent(
    settings: HubSettingsState,
    isSubmitting: Boolean,
    onDisplayNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onCurrentPasswordChange: (String) -> Unit,
    onNewPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onSaveProfile: () -> Unit,
    onChangePassword: () -> Unit,
    onRequestLogout: () -> Unit,
    onOpenUnlockMethodDialog: () -> Unit,
    onChangePin: () -> Unit,
    onChangePattern: () -> Unit,
    onBiometricChanged: (Boolean) -> Unit
) {
    val busy = isSubmitting || settings.isLoggingOut
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "설정",
            color = TextPrimary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "회원 정보",
            color = TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp)
        )
        OutlinedTextField(
            value = settings.email,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            label = { Text("이메일") },
            enabled = false,
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = TextPrimary,
                disabledBorderColor = BorderLine,
                disabledLabelColor = TextSecondary
            )
        )
        ClubTextField(
            value = settings.displayName,
            onValueChange = onDisplayNameChange,
            label = "이름"
        )
        ClubTextField(
            value = settings.phone,
            onValueChange = onPhoneChange,
            label = "연락처",
            keyboardType = KeyboardType.Number
        )
        settings.feedbackMessage?.let {
            Text(
                it,
                color = IncomeBlue,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        settings.errorMessage?.let {
            Text(it, color = ExpenseRed, style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = onSaveProfile,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = IncomeBlue)
        ) {
            Text(
                if (isSubmitting) "저장 중…" else "회원 정보 저장",
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }

        Text(
            "비밀번호",
            color = TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 12.dp)
        )
        if (settings.canChangePassword) {
            SettingsPasswordField(
                value = settings.currentPassword,
                onValueChange = onCurrentPasswordChange,
                label = "현재 비밀번호",
                enabled = !busy
            )
            SettingsPasswordField(
                value = settings.newPassword,
                onValueChange = onNewPasswordChange,
                label = "새 비밀번호",
                enabled = !busy
            )
            SettingsPasswordField(
                value = settings.confirmPassword,
                onValueChange = onConfirmPasswordChange,
                label = "새 비밀번호 확인",
                enabled = !busy
            )
            OutlinedButton(
                onClick = onChangePassword,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = IncomeBlue),
                border = BorderStroke(1.dp, IncomeBlue)
            ) {
                Text("비밀번호 변경", color = IncomeBlue, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Text(
                "시스템관리자 계정은 고정 비밀번호를 사용하며 앱에서 변경할 수 없습니다.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }

        // 다음 로그인에 쓸 방식 (앱 잠금 화면 없음)
        HorizontalDivider(color = BorderLine, modifier = Modifier.padding(vertical = 4.dp))
        Text(
            "로그인 설정",
            color = TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            "지금 로그인한 계정 전용입니다. PIN·패턴은 계정마다 따로 저장됩니다.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("로그인 방식", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                Text(
                    settings.unlockMethod.label,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(onClick = onOpenUnlockMethodDialog, enabled = !busy) {
                Text("변경", color = IncomeBlue)
            }
        }
        // PIN / 패턴 재설정
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onChangePin,
                enabled = !busy,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = IncomeBlue),
                border = BorderStroke(1.dp, IncomeBlue)
            ) {
                Text(
                    if (settings.hasPinRegistered) "PIN 변경" else "PIN 등록",
                    color = IncomeBlue,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            OutlinedButton(
                onClick = onChangePattern,
                enabled = !busy,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = IncomeBlue),
                border = BorderStroke(1.dp, IncomeBlue)
            ) {
                Text(
                    if (settings.hasPatternRegistered) "패턴 변경" else "패턴 등록",
                    color = IncomeBlue,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("생체 인증", color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                Text(
                    when {
                        !settings.canUseBiometric -> "이 기기에서 사용할 수 없습니다"
                        settings.isBiometricEnabled -> "기기 공통 · 마지막 로그인 계정으로 입장"
                        else -> "계정 구분 없이 마지막 저장 계정으로 로그인"
                    },
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = settings.isBiometricEnabled && settings.canUseBiometric,
                onCheckedChange = onBiometricChanged,
                enabled = settings.canUseBiometric && !busy,
                colors = SwitchDefaults.colors(checkedThumbColor = IncomeBlue, checkedTrackColor = IncomeBlue.copy(alpha = 0.4f))
            )
        }

        TextButton(
            onClick = onRequestLogout,
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text(
                if (settings.isLoggingOut) "로그아웃 중…" else "로그아웃",
                color = ExpenseRed,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SettingsPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedBorderColor = IncomeBlue,
            unfocusedBorderColor = BorderLine,
            focusedLabelColor = TextSecondary,
            unfocusedLabelColor = TextSecondary,
            cursorColor = TextPrimary
        )
    )
}
