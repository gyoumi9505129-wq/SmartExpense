@file:OptIn(ExperimentalComposeUiApi::class)

package com.smartexpense.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.smartexpense.R
import com.smartexpense.data.auth.SavedLoginAccount
import com.smartexpense.ui.security.AppLockViewModel
import com.smartexpense.ui.security.PatternLockPad
import com.smartexpense.ui.theme.BackgroundBlack
import com.smartexpense.ui.theme.BorderLine
import com.smartexpense.ui.theme.ExpenseRed
import com.smartexpense.ui.theme.IncomeBlue
import com.smartexpense.ui.theme.SurfaceDeepGray
import com.smartexpense.ui.theme.SurfaceElevated
import com.smartexpense.ui.theme.TextPrimary
import com.smartexpense.ui.theme.TextSecondary

private val CardShape = RoundedCornerShape(20.dp)
private val FieldShape = RoundedCornerShape(16.dp)
private val ControlShape = RoundedCornerShape(14.dp)

data class LoginQuickUnlockOptions(
    val hasPin: Boolean = false,
    val hasPattern: Boolean = false,
    val biometricEnabled: Boolean = false,
    val canUseBiometric: Boolean = false,
    val hasSavedAccount: Boolean = false
) {
    val anyQuickUnlock: Boolean
        get() = hasSavedAccount && (hasPin || hasPattern || (biometricEnabled && canUseBiometric))

    fun availableTabs(): List<LoginEntryMethod> = buildList {
        if (hasSavedAccount && biometricEnabled && canUseBiometric) add(LoginEntryMethod.BIOMETRIC)
        if (hasSavedAccount && hasPin) add(LoginEntryMethod.PIN)
        if (hasSavedAccount && hasPattern) add(LoginEntryMethod.PATTERN)
        add(LoginEntryMethod.EMAIL_PASSWORD)
    }
}

data class LoginLockPanelState(
    val pinInput: String = "",
    val patternInput: List<Int> = emptyList(),
    val errorMessage: String? = null
)

@Composable
fun EmailAuthScreen(
    uiState: EmailAuthUiState,
    profileCompletionOnly: Boolean,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordConfirmChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onRememberLoginChange: (Boolean) -> Unit = {},
    onSelectSavedAccount: (String) -> Unit = {},
    onRemoveSavedAccount: (String) -> Unit = {},
    quickUnlockOptions: LoginQuickUnlockOptions = LoginQuickUnlockOptions(),
    lockPanel: LoginLockPanelState = LoginLockPanelState(),
    onSelectEntryMethod: (LoginEntryMethod) -> Unit = {},
    onPinInputChange: (String) -> Unit = {},
    onSubmitPinUnlock: () -> Unit = {},
    onPatternChange: (List<Int>) -> Unit = {},
    onPatternComplete: (List<Int>) -> Unit = {},
    onRequestBiometric: () -> Unit = {},
    onSubmit: () -> Unit,
    onGoogleSignIn: () -> Unit = {},
    onSwitchToLogin: () -> Unit,
    onSwitchToSignUp: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    val focusEmail = remember { FocusRequester() }
    val focusPassword = remember { FocusRequester() }
    val focusPasswordConfirm = remember { FocusRequester() }
    val focusDisplayName = remember { FocusRequester() }
    val focusPhone = remember { FocusRequester() }

    val isSignUp = !profileCompletionOnly && uiState.mode == EmailAuthMode.SIGN_UP
    val availableTabs = quickUnlockOptions.availableTabs()
    val showSegmented = !profileCompletionOnly && !isSignUp && availableTabs.size > 1
    val entryMethod = when {
        !showSegmented -> LoginEntryMethod.EMAIL_PASSWORD
        uiState.entryMethod in availableTabs -> uiState.entryMethod
        else -> LoginEntryMethod.EMAIL_PASSWORD
    }

    val title = when {
        profileCompletionOnly -> "회원 정보"
        isSignUp -> "회원가입"
        else -> "로그인"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0E1116),
                        BackgroundBlack,
                        Color(0xFF12161C)
                    )
                )
            )
            .imePadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            AuthBrandHeader()
            Spacer(modifier = Modifier.height(32.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = CardShape,
                color = SurfaceElevated,
                border = BorderStroke(1.dp, BorderLine.copy(alpha = 0.7f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    if (profileCompletionOnly || isSignUp) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (showSegmented) {
                        LoginSegmentedControl(
                            tabs = availableTabs,
                            selected = entryMethod,
                            enabled = !uiState.isSubmitting,
                            onSelect = onSelectEntryMethod
                        )
                    }

                    when {
                        profileCompletionOnly -> {
                            ProfileOrSignUpForm(
                                uiState = uiState,
                                isSignUp = false,
                                profileCompletionOnly = true,
                                focusEmail = focusEmail,
                                focusPassword = focusPassword,
                                focusPasswordConfirm = focusPasswordConfirm,
                                focusDisplayName = focusDisplayName,
                                focusPhone = focusPhone,
                                focusManager = focusManager,
                                onEmailChange = onEmailChange,
                                onPasswordChange = onPasswordChange,
                                onPasswordConfirmChange = onPasswordConfirmChange,
                                onDisplayNameChange = onDisplayNameChange,
                                onPhoneChange = onPhoneChange,
                                onSubmit = onSubmit
                            )
                            PrimaryAuthButton(
                                label = "시작하기",
                                loading = uiState.isSubmitting,
                                onClick = {
                                    focusManager.clearFocus()
                                    onSubmit()
                                }
                            )
                        }

                        isSignUp -> {
                            GoogleSignUpPanel(
                                loading = uiState.isSubmitting,
                                onGoogleSignIn = onGoogleSignIn
                            )
                        }

                        entryMethod == LoginEntryMethod.BIOMETRIC -> {
                            BiometricLoginPanel(
                                loading = uiState.isSubmitting,
                                errorMessage = lockPanel.errorMessage,
                                onRequestBiometric = onRequestBiometric
                            )
                        }

                        entryMethod == LoginEntryMethod.PIN -> {
                            PinLoginPanel(
                                pinInput = lockPanel.pinInput,
                                pinLength = AppLockViewModel.PIN_LENGTH,
                                enabled = !uiState.isSubmitting,
                                errorMessage = lockPanel.errorMessage,
                                onPinInputChange = onPinInputChange
                            )
                        }

                        entryMethod == LoginEntryMethod.PATTERN -> {
                            PatternLoginPanel(
                                patternInput = lockPanel.patternInput,
                                enabled = !uiState.isSubmitting,
                                errorMessage = lockPanel.errorMessage,
                                onPatternChange = onPatternChange,
                                onPatternComplete = onPatternComplete
                            )
                        }

                        else -> {
                            EmailLoginForm(
                                uiState = uiState,
                                focusEmail = focusEmail,
                                focusPassword = focusPassword,
                                focusManager = focusManager,
                                onEmailChange = onEmailChange,
                                onPasswordChange = onPasswordChange,
                                onRememberLoginChange = onRememberLoginChange,
                                onSelectSavedAccount = onSelectSavedAccount,
                                onRemoveSavedAccount = onRemoveSavedAccount,
                                onSubmit = onSubmit
                            )
                            PrimaryAuthButton(
                                label = "로그인",
                                loading = uiState.isSubmitting,
                                onClick = {
                                    focusManager.clearFocus()
                                    onSubmit()
                                }
                            )
                            AuthOrDivider()
                            GoogleAuthButton(
                                label = "Google 계정으로 로그인",
                                loading = uiState.isSubmitting,
                                onClick = onGoogleSignIn
                            )
                        }
                    }
                }
            }

            if (!profileCompletionOnly) {
                Spacer(modifier = Modifier.height(20.dp))
                TextButton(
                    onClick = if (isSignUp) onSwitchToLogin else onSwitchToSignUp,
                    enabled = !uiState.isSubmitting
                ) {
                    Text(
                        text = if (isSignUp) {
                            "이미 계정이 있으신가요? 로그인"
                        } else {
                            "계정이 없으신가요? 회원가입"
                        },
                        color = TextSecondary.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }

        uiState.errorMessage?.takeIf {
            entryMethod == LoginEntryMethod.EMAIL_PASSWORD || profileCompletionOnly || isSignUp
        }?.let { message ->
            ErrorBanner(
                message = message,
                onDismiss = onDismissError,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(20.dp)
            )
        }
    }
}

@Composable
private fun LoginSegmentedControl(
    tabs: List<LoginEntryMethod>,
    selected: LoginEntryMethod,
    enabled: Boolean,
    onSelect: (LoginEntryMethod) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = ControlShape,
        color = Color(0xFF1A1F27)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEach { method ->
                val isSelected = method == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (isSelected) IncomeBlue else Color.Transparent)
                        .clickable(enabled = enabled) { onSelect(method) }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = method.tabLabel(),
                        color = if (isSelected) Color.White else TextSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private fun LoginEntryMethod.tabLabel(): String = when (this) {
    LoginEntryMethod.BIOMETRIC -> "생체"
    LoginEntryMethod.PIN -> "PIN"
    LoginEntryMethod.PATTERN -> "패턴"
    LoginEntryMethod.EMAIL_PASSWORD -> "이메일"
}

@Composable
private fun BiometricLoginPanel(
    loading: Boolean,
    errorMessage: String?,
    onRequestBiometric: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(IncomeBlue.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Fingerprint,
                contentDescription = null,
                tint = IncomeBlue,
                modifier = Modifier.size(52.dp)
            )
        }
        Text(
            text = "등록된 생체 정보로\n빠르게 로그인하세요",
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
        PrimaryAuthButton(
            label = "지문으로 로그인",
            loading = loading,
            onClick = onRequestBiometric
        )
        errorMessage?.let {
            Text(it, color = ExpenseRed, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PinLoginPanel(
    pinInput: String,
    pinLength: Int,
    enabled: Boolean,
    errorMessage: String?,
    onPinInputChange: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(pinLength) { index ->
                val filled = index < pinInput.length
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(if (filled) IncomeBlue else BorderLine)
                )
            }
        }

        errorMessage?.let {
            Text(it, color = ExpenseRed, style = MaterialTheme.typography.bodySmall)
        }

        PinNumberPad(
            enabled = enabled,
            onDigit = { digit ->
                if (pinInput.length < pinLength) {
                    onPinInputChange(pinInput + digit)
                }
            },
            onBackspace = {
                if (pinInput.isNotEmpty()) {
                    onPinInputChange(pinInput.dropLast(1))
                }
            }
        )
    }
}

@Composable
private fun PinNumberPad(
    enabled: Boolean,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit
) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫")
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        keys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1.55f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                when {
                                    key.isEmpty() -> Color.Transparent
                                    key == "⌫" -> SurfaceDeepGray
                                    else -> Color(0xFF1A1F27)
                                }
                            )
                            .then(
                                if (key.isNotEmpty() && enabled) {
                                    Modifier.clickable {
                                        if (key == "⌫") onBackspace() else onDigit(key)
                                    }
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        when (key) {
                            "" -> Unit
                            "⌫" -> Icon(
                                imageVector = Icons.AutoMirrored.Filled.Backspace,
                                contentDescription = "지우기",
                                tint = TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                            else -> Text(
                                text = key,
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PatternLoginPanel(
    patternInput: List<Int>,
    enabled: Boolean,
    errorMessage: String?,
    onPatternChange: (List<Int>) -> Unit,
    onPatternComplete: (List<Int>) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "패턴을 그려 주세요",
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
        errorMessage?.let {
            Text(it, color = ExpenseRed, style = MaterialTheme.typography.bodySmall)
        }
        PatternLockPad(
            selected = patternInput,
            onPatternChange = onPatternChange,
            onPatternComplete = onPatternComplete,
            enabled = enabled,
            lineColor = IncomeBlue,
            dotColor = TextSecondary,
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .padding(vertical = 4.dp)
        )
    }
}

@Composable
private fun EmailLoginForm(
    uiState: EmailAuthUiState,
    focusEmail: FocusRequester,
    focusPassword: FocusRequester,
    focusManager: androidx.compose.ui.focus.FocusManager,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onRememberLoginChange: (Boolean) -> Unit,
    onSelectSavedAccount: (String) -> Unit,
    onRemoveSavedAccount: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (uiState.savedAccounts.isNotEmpty()) {
            AccountDropdownEmailField(
                value = uiState.email,
                onValueChange = onEmailChange,
                accounts = uiState.savedAccounts,
                enabled = !uiState.isSubmitting,
                focusRequester = focusEmail,
                onSelectAccount = { email ->
                    onSelectSavedAccount(email)
                    focusPassword.requestFocus()
                },
                onRemoveAccount = onRemoveSavedAccount,
                onImeAction = { focusPassword.requestFocus() }
            )
        } else {
            AuthTextField(
                value = uiState.email,
                onValueChange = onEmailChange,
                label = "이메일",
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
                enabled = !uiState.isSubmitting,
                focusRequester = focusEmail,
                autofillTypes = listOf(AutofillType.EmailAddress, AutofillType.Username),
                onImeAction = { focusPassword.requestFocus() }
            )
        }
        AuthTextField(
            value = uiState.password,
            onValueChange = onPasswordChange,
            label = "비밀번호",
            isPassword = true,
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done,
            enabled = !uiState.isSubmitting,
            focusRequester = focusPassword,
            autofillTypes = listOf(AutofillType.Password),
            onImeAction = {
                focusManager.clearFocus()
                onSubmit()
            }
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = uiState.rememberLogin,
                    enabled = !uiState.isSubmitting,
                    onValueChange = onRememberLoginChange
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = uiState.rememberLogin,
                onCheckedChange = onRememberLoginChange,
                enabled = !uiState.isSubmitting,
                colors = CheckboxDefaults.colors(
                    checkedColor = IncomeBlue,
                    uncheckedColor = TextSecondary
                )
            )
            Text(
                text = "로그인 정보 저장",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ProfileOrSignUpForm(
    uiState: EmailAuthUiState,
    isSignUp: Boolean,
    profileCompletionOnly: Boolean,
    focusEmail: FocusRequester,
    focusPassword: FocusRequester,
    focusPasswordConfirm: FocusRequester,
    focusDisplayName: FocusRequester,
    focusPhone: FocusRequester,
    focusManager: androidx.compose.ui.focus.FocusManager,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordConfirmChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (!profileCompletionOnly) {
            AuthTextField(
                value = uiState.email,
                onValueChange = onEmailChange,
                label = "이메일",
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
                enabled = !uiState.isSubmitting,
                focusRequester = focusEmail,
                autofillTypes = listOf(AutofillType.EmailAddress, AutofillType.Username),
                onImeAction = { focusPassword.requestFocus() }
            )
            AuthTextField(
                value = uiState.password,
                onValueChange = onPasswordChange,
                label = "비밀번호",
                isPassword = true,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Next,
                enabled = !uiState.isSubmitting,
                focusRequester = focusPassword,
                autofillTypes = listOf(AutofillType.NewPassword),
                onImeAction = { focusPasswordConfirm.requestFocus() }
            )
            if (isSignUp) {
                AuthTextField(
                    value = uiState.passwordConfirm,
                    onValueChange = onPasswordConfirmChange,
                    label = "비밀번호 확인",
                    isPassword = true,
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next,
                    enabled = !uiState.isSubmitting,
                    focusRequester = focusPasswordConfirm,
                    autofillTypes = listOf(AutofillType.NewPassword),
                    onImeAction = { focusDisplayName.requestFocus() }
                )
            }
        }
        AuthTextField(
            value = uiState.displayName,
            onValueChange = onDisplayNameChange,
            label = "이름",
            imeAction = ImeAction.Next,
            enabled = !uiState.isSubmitting,
            focusRequester = focusDisplayName,
            onImeAction = { focusPhone.requestFocus() }
        )
        AuthTextField(
            value = uiState.phone,
            onValueChange = onPhoneChange,
            label = "연락처",
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
            enabled = !uiState.isSubmitting,
            focusRequester = focusPhone,
            onImeAction = {
                focusManager.clearFocus()
                onSubmit()
            }
        )
    }
}

@Composable
private fun GoogleSignUpPanel(
    loading: Boolean,
    onGoogleSignIn: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Gmail 계정으로 본인 확인해야 모임 가입·클라우드 공유를 사용할 수 있습니다.\n이메일 주소만 입력하는 가입은 할 수 없습니다.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = 22.sp
        )
        GoogleAuthButton(
            label = "Google 계정으로 가입",
            loading = loading,
            onClick = onGoogleSignIn
        )
    }
}

@Composable
private fun AuthOrDivider() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = BorderLine)
        Text(
            text = "또는",
            color = TextSecondary,
            style = MaterialTheme.typography.labelMedium
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = BorderLine)
    }
}

@Composable
private fun GoogleAuthButton(
    label: String,
    loading: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = !loading,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color(0xFF1F1F1F),
            disabledContainerColor = Color.White.copy(alpha = 0.7f)
        )
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = IncomeBlue,
                strokeWidth = 2.dp
            )
        } else {
            Text(text = label, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun PrimaryAuthButton(
    label: String,
    loading: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = !loading,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = IncomeBlue,
            contentColor = Color.White
        )
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
        } else {
            Text(text = label, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceDeepGray,
        border = BorderStroke(1.dp, ExpenseRed.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = ExpenseRed,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) {
                Text("확인", color = TextPrimary)
            }
        }
    }
}

@Composable
private fun AccountDropdownEmailField(
    value: String,
    onValueChange: (String) -> Unit,
    accounts: List<SavedLoginAccount>,
    enabled: Boolean,
    focusRequester: FocusRequester?,
    onSelectAccount: (String) -> Unit,
    onRemoveAccount: (String) -> Unit,
    onImeAction: () -> Unit
) {
    var showDropdown by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) showDropdown = true
                }
                .authAutofill(AutofillType.EmailAddress, AutofillType.Username) { filled ->
                    onValueChange(filled)
                },
            label = { Text("이메일") },
            singleLine = true,
            enabled = enabled,
            shape = FieldShape,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = { onImeAction() }),
            colors = authFieldColors()
        )

        if (showDropdown && accounts.isNotEmpty()) {
            Popup(
                onDismissRequest = { showDropdown = false },
                properties = PopupProperties(focusable = false)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceDeepGray,
                    border = BorderStroke(1.dp, BorderLine),
                    shadowElevation = 8.dp
                ) {
                    LazyColumn {
                        items(accounts, key = { it.email }) { account ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = enabled) {
                                        onSelectAccount(account.email)
                                        showDropdown = false
                                    }
                                    .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    if (account.label.isNotBlank()) {
                                        Text(
                                            text = account.label,
                                            color = TextPrimary,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Text(
                                        text = account.email,
                                        color = TextSecondary,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = { onRemoveAccount(account.email) },
                                    enabled = enabled,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "계정 삭제",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            HorizontalDivider(color = BorderLine, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    autofillTypes: List<AutofillType> = emptyList(),
    onImeAction: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        shape = FieldShape,
        visualTransformation = if (isPassword) {
            PasswordVisualTransformation()
        } else {
            VisualTransformation.None
        },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = keyboardType,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(
            onNext = { onImeAction?.invoke() },
            onDone = { onImeAction?.invoke() },
            onGo = { onImeAction?.invoke() }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
            )
            .then(
                if (autofillTypes.isNotEmpty()) {
                    Modifier.authAutofill(*autofillTypes.toTypedArray(), onFill = onValueChange)
                } else {
                    Modifier
                }
            ),
        colors = authFieldColors()
    )
}

@Composable
private fun authFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedLabelColor = TextSecondary,
    unfocusedLabelColor = TextSecondary,
    focusedBorderColor = IncomeBlue,
    unfocusedBorderColor = BorderLine,
    cursorColor = IncomeBlue
)

@Composable
fun AuthBrandHeader() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(SurfaceElevated)
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_app_logo),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Fit
            )
        }
        Text(
            text = stringResource(R.string.app_name),
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 28.sp,
            textAlign = TextAlign.Center
        )
        Text(
            text = "투명하고 편리한 모임 장부 관리",
            color = TextSecondary,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center
        )
    }
}
