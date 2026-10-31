package com.smartexpense.ui.club.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartexpense.domain.security.AppLockUnlockMethod
import com.smartexpense.ui.club.member.MemberRegistrationBottomSheet
import com.smartexpense.ui.common.RefreshOnScreenVisible
import com.smartexpense.ui.security.AppLockViewModel
import com.smartexpense.ui.security.PatternLockPad
import com.smartexpense.ui.theme.BackgroundBlack
import com.smartexpense.ui.theme.BorderLine
import com.smartexpense.ui.theme.ExpenseRed
import com.smartexpense.ui.theme.IncomeBlue
import com.smartexpense.ui.theme.SurfaceDeepGray
import com.smartexpense.ui.theme.TextPrimary
import com.smartexpense.ui.theme.TextSecondary

/**
 * 로그인 직후 한우리(또는 단일 모임)로 바로 입장하는 게이트 화면.
 * 검색·만들기·가입 대기 탭은 쓰지 않음. 가입 승인은 모임 안 「관리」 탭에서 처리.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingHubScreen(
    onMeetingEntered: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MeetingHubViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    RefreshOnScreenVisible(onRefresh = {
        viewModel.onHubVisible()
        viewModel.refreshHub(userInitiated = false)
    })

    LaunchedEffect(uiState.snackbarMessage) {
        val message = uiState.snackbarMessage ?: return@LaunchedEffect
        viewModel.consumeSnackbar()
        snackbarHostState.showSnackbar(message)
    }

    // 로그인 후 한우리(또는 미승인 시 샘플)로 바로 입장. 한우리 관리자는 항상 실데이터.
    LaunchedEffect(uiState.isLoading, uiState.autoEnterCandidate?.meeting?.id) {
        if (uiState.isLoading) return@LaunchedEffect
        val item = uiState.autoEnterCandidate ?: return@LaunchedEffect
        viewModel.consumeAutoEnter()
        when (item.accessStatus) {
            MeetingAccessStatus.AVAILABLE,
            MeetingAccessStatus.PENDING,
            MeetingAccessStatus.REJECTED -> viewModel.enterSampleMode(onMeetingEntered)
            else -> viewModel.enterMeeting(item.meeting, onMeetingEntered)
        }
    }

    MeetingHubDialogs(
        uiState = uiState,
        viewModel = viewModel,
        onMeetingEntered = onMeetingEntered
    )

    Scaffold(
        modifier = modifier,
        containerColor = BackgroundBlack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text("모임", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                },
                actions = {
                    IconButton(
                        onClick = viewModel::refreshHub,
                        enabled = !uiState.isRefreshing && !uiState.isSubmitting
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "새로고침",
                            tint = TextPrimary
                        )
                    }
                    IconButton(onClick = viewModel::openSettingsSheet) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "설정",
                            tint = TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundBlack)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            when {
                uiState.isLoading ||
                    uiState.autoEnterCandidate != null ||
                    uiState.isAutoEntering ||
                    uiState.isSubmitting ||
                    uiState.isElevated -> {
                    // 시스템관리자는 샘플 안내 대신 입장/로딩만 표시
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = TextPrimary)
                        Text(
                            if (uiState.isElevated) "모임으로 이동 중…" else "불러오는 중…",
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }

                else -> {
                    // 자동 입장할 모임이 없을 때만 안내 (미승인 샘플 등)
                    MeetingItemList(
                        items = emptyList(),
                        isSubmitting = false,
                        emptyTitle = "한우리 샘플",
                        emptyDescription = "승인 전에는 로컬 샘플로 체험할 수 있습니다.\n설정(⚙)에서 「승인 요청」을 보내세요. 승인되면 실데이터를 볼 수 있습니다.",
                        onEnter = { },
                        onJoinRequest = { },
                        onCompleteProfile = { }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeetingHubDialogs(
    uiState: MeetingHubUiState,
    viewModel: MeetingHubViewModel,
    onMeetingEntered: () -> Unit
) {
    if (uiState.showCreateDialog) {
        CreateMeetingDialog(
            name = uiState.newMeetingName,
            description = uiState.newMeetingDescription,
            slogan = uiState.newMeetingSlogan,
            isSubmitting = uiState.isSubmitting,
            errorMessage = uiState.dialogErrorMessage,
            onNameChange = viewModel::updateNewMeetingName,
            onDescriptionChange = viewModel::updateNewMeetingDescription,
            onSloganChange = viewModel::updateNewMeetingSlogan,
            onConfirm = { viewModel.createMeeting(onMeetingEntered) },
            onDismiss = viewModel::dismissCreateDialog
        )
    }

    if (uiState.showJoinDialog) {
        val meeting = uiState.joinTarget
        if (meeting != null) {
            JoinRequestDialog(
                meetingName = meeting.name,
                message = uiState.joinMessage,
                isSubmitting = uiState.isSubmitting,
                errorMessage = uiState.dialogErrorMessage,
                onMessageChange = viewModel::updateJoinMessage,
                onConfirm = viewModel::submitJoinRequest,
                onDismiss = viewModel::dismissJoinDialog
            )
        }
    }

    val profilePrompt = uiState.memberProfilePrompt
    if (profilePrompt != null) {
        key(profilePrompt.promptKey) {
            MemberRegistrationBottomSheet(
                form = profilePrompt.form,
                isSaving = uiState.isSubmitting,
                hideRoleAndStatus = true,
                titleOverride = "「${profilePrompt.meeting.name}」 회원 정보 입력",
                saveLabelOverride = "등록",
                errorMessage = profilePrompt.errorMessage,
                hasUnsavedChanges = false,
                onNameChange = viewModel::updateProfileFormName,
                onPhoneChange = viewModel::updateProfileFormPhone,
                onEmailChange = viewModel::updateProfileFormEmail,
                onJoinDateChange = viewModel::updateProfileFormJoinDate,
                onBirthDateChange = viewModel::updateProfileFormBirthDate,
                onIsLunarBirthChange = viewModel::updateProfileFormIsLunarBirth,
                onResidenceRegionChange = viewModel::updateProfileFormResidenceRegion,
                onAddressChange = viewModel::updateProfileFormAddress,
                onDetailAddressChange = viewModel::updateProfileFormDetailAddress,
                onRoleChange = {},
                onStatusChange = {},
                onSuspensionDateChange = {},
                onStatusChangeReasonChange = {},
                onSave = { viewModel.saveMemberProfile(onMeetingEntered) },
                onDismiss = viewModel::dismissMemberProfilePrompt
            )
        }
    }

    if (uiState.settings.showUnlockMethodDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissUnlockMethodDialog,
            title = { Text("로그인 방식 선택", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "방식을 고르면 등록하거나 기존 값을 변경할 수 있습니다.",
                        color = TextSecondary,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                    )
                    AppLockUnlockMethod.entries.forEach { method ->
                        val already = when (method) {
                            AppLockUnlockMethod.PIN -> uiState.settings.hasPinRegistered
                            AppLockUnlockMethod.PATTERN -> uiState.settings.hasPatternRegistered
                        }
                        TextButton(
                            onClick = { viewModel.setUnlockMethod(method) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (already) "${method.label} 변경" else "${method.label} 등록",
                                color = TextPrimary
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = viewModel::dismissUnlockMethodDialog) {
                    Text("취소", color = TextSecondary)
                }
            },
            containerColor = SurfaceDeepGray
        )
    }

    if (uiState.settings.showPinSetupDialog) {
        val changingPin = uiState.settings.isChangingCredential
        AlertDialog(
            onDismissRequest = viewModel::dismissPinSetupDialog,
            title = {
                Text(if (changingPin) "PIN 변경" else "PIN 등록", color = TextPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (changingPin) {
                            "새 PIN ${AppLockViewModel.PIN_LENGTH}자리를 입력해 주세요."
                        } else {
                            "로그인 화면에서 쓸 PIN ${AppLockViewModel.PIN_LENGTH}자리를 입력해 주세요."
                        },
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = uiState.settings.pinInput,
                        onValueChange = viewModel::updatePinSetupInput,
                        label = { Text("PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = IncomeBlue,
                            unfocusedBorderColor = BorderLine
                        )
                    )
                    OutlinedTextField(
                        value = uiState.settings.pinConfirmInput,
                        onValueChange = viewModel::updatePinSetupConfirmInput,
                        label = { Text("PIN 확인") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = { viewModel.submitPinSetup() }),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = IncomeBlue,
                            unfocusedBorderColor = BorderLine
                        )
                    )
                    uiState.settings.pinSetupError?.let {
                        Text(it, color = ExpenseRed)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::submitPinSetup) {
                    Text("저장", color = IncomeBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissPinSetupDialog) {
                    Text("취소", color = TextSecondary)
                }
            },
            containerColor = SurfaceDeepGray
        )
    }

    if (uiState.settings.showPatternSetupDialog) {
        val changingPattern = uiState.settings.isChangingCredential
        AlertDialog(
            onDismissRequest = viewModel::dismissPatternSetupDialog,
            title = {
                Text(if (changingPattern) "패턴 변경" else "패턴 등록", color = TextPrimary)
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        when {
                            uiState.settings.patternConfirmInput.isNotEmpty() ->
                                "확인을 위해 같은 패턴을 다시 그려 주세요."
                            changingPattern -> "새 패턴을 그려 주세요."
                            else -> "로그인용 패턴을 그려 주세요."
                        },
                        color = TextSecondary
                    )
                    uiState.settings.patternSetupError?.let {
                        Text(it, color = ExpenseRed)
                    }
                    PatternLockPad(
                        selected = uiState.settings.patternInput,
                        onPatternChange = viewModel::updatePatternSetupInput,
                        onPatternComplete = viewModel::onPatternSetupComplete,
                        lineColor = IncomeBlue,
                        modifier = Modifier.fillMaxWidth(0.9f)
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = viewModel::dismissPatternSetupDialog) {
                    Text("취소", color = TextSecondary)
                }
            },
            containerColor = SurfaceDeepGray
        )
    }

    if (uiState.settings.visible) {
        MeetingHubSettingsSheet(
            settings = uiState.settings,
            isSubmitting = uiState.isSubmitting,
            onDismiss = viewModel::dismissSettingsSheet,
            onDisplayNameChange = viewModel::updateProfileDisplayName,
            onPhoneChange = viewModel::updateProfilePhone,
            onSaveProfile = viewModel::saveProfile,
            onRequestAccess = viewModel::requestAccessFromSettings,
            onRequestLogout = viewModel::requestLogout,
            onDismissLogoutConfirm = viewModel::dismissLogoutConfirm,
            onConfirmLogout = viewModel::confirmLogout,
            onOpenUnlockMethodDialog = viewModel::openUnlockMethodDialog,
            onChangePin = viewModel::openChangePin,
            onChangePattern = viewModel::openChangePattern,
            onBiometricChanged = viewModel::setBiometricEnabled
        )
    }
}
