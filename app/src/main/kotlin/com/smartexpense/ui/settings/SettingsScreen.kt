package com.smartexpense.ui.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartexpense.data.local.ClubConstants
import com.smartexpense.ui.club.components.ClubDropdownField
import com.smartexpense.ui.club.components.ClubTextField
import com.smartexpense.ui.club.dues.duesPaymentMethodOptions
import com.smartexpense.ui.club.dues.toDisplayLabel
import com.smartexpense.ui.components.rememberClearInputOverlayAction
import com.smartexpense.ui.common.RefreshOnScreenVisible
import com.smartexpense.ui.common.UserRoleBadge
import com.smartexpense.ui.theme.BackgroundBlack
import com.smartexpense.ui.theme.ExpenseRed
import com.smartexpense.ui.theme.TextPrimary
import com.smartexpense.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSwitchClub: () -> Unit = {},
    onOpenClubHistory: () -> Unit = {},
    onOpenBankParsing: () -> Unit = {},
    onOpenClubAccount: () -> Unit = {},
    onLoggedOut: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    RefreshOnScreenVisible {
        viewModel.refreshOnVisible()
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val isBackupRestoreBusy = uiState.backupRestoreState == BackupRestoreState.Loading
    val isCloudSyncBusy = uiState.cloudSyncState == CloudSyncState.SigningIn ||
        uiState.cloudSyncState == CloudSyncState.Uploading
    val isSeedResetBusy = uiState.isResettingSeedData
    val isClearClubDataBusy = uiState.isClearingClubData
    val isLoggingOut = uiState.isLoggingOut
    val isExcelImportBusy = uiState.isExcelImportBusy
    val isFirebaseSyncing = uiState.isFirebaseSyncing
    val isHanuriClub = ClubConstants.isHanuriSeedClub(uiState.currentClubName)

    LaunchedEffect(viewModel) {
        viewModel.clubListNavigationEventsFlow.collect {
            onSwitchClub()
        }
    }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            viewModel.onCloudSyncCancelled()
            return@rememberLauncherForActivityResult
        }
        viewModel.handleGoogleSignInResult(result.data)
    }

    val firebaseSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // 일부 기기에서 resultCode가 OK가 아니어도 Intent에 계정 결과가 들어있습니다.
        if (result.data != null) {
            viewModel.handleFirebaseGoogleSignInResult(result.data)
            return@rememberLauncherForActivityResult
        }
        if (result.resultCode != Activity.RESULT_OK) {
            viewModel.onFirebaseSignInCancelled()
            return@rememberLauncherForActivityResult
        }
        viewModel.handleFirebaseGoogleSignInResult(result.data)
    }

    val clubRestoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.onClubRestoreFileSelected(uri)
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeSnackbarMessage()
        }
    }

    LaunchedEffect(uiState.backupRestoreState) {
        if (uiState.backupRestoreState == BackupRestoreState.Success &&
            !uiState.showRestartDialog
        ) {
            viewModel.resetBackupRestoreState()
        }
    }

    LaunchedEffect(uiState.cloudSyncState) {
        if (uiState.cloudSyncState == CloudSyncState.Success) {
            viewModel.resetCloudSyncState()
        }
    }

    val dismissExportDialog = rememberClearInputOverlayAction(onAfterClear = viewModel::dismissExportDialog)

    if (uiState.isLoading) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = TextPrimary)
        }
        return
    }

    if (uiState.showLogoutConfirmDialog) {
        LogoutConfirmDialog(
            onConfirm = viewModel::confirmLogout,
            onDismiss = viewModel::dismissLogoutConfirmDialog
        )
    }

    if (uiState.showFirebaseUploadConfirmDialog) {
        SettingsActionConfirmDialog(
            title = "클라우드에 올리기",
            message = "「올리기」는 이 기기 로컬(Room)에서 마지막 동기화 이후 바뀐 데이터만 클라우드에 반영합니다.\n\n" +
                "구글 로그인 + 편집 권한으로 실시간 동기화 중이면 장부·회비 편집은 이미 클라우드에 저장되므로, " +
                "로컬 변경분이 없으면 0건으로 나옵니다.\n\n" +
                "(한 번도 올린 적이 없거나 클라우드가 비어 있으면 전체 이행됩니다.)",
            confirmLabel = "올리기",
            onConfirm = viewModel::confirmFirebaseUpload,
            onDismiss = viewModel::dismissFirebaseUploadConfirmDialog
        )
    }

    if (uiState.showFirebaseFullMigrationConfirmDialog) {
        SettingsActionConfirmDialog(
            title = "초기 데이터 이행",
            message = "클라우드의 「${uiState.currentClubName}」 데이터를 모두 지운 뒤, " +
                "이 기기의 로컬 회원·장부·회비·경조사비·슬로건·이력·계좌를 통째로 다시 올립니다. " +
                "이 기기 로컬은 그대로 유지됩니다. 계속할까요?",
            confirmLabel = "이행",
            onConfirm = viewModel::confirmFirebaseFullMigration,
            onDismiss = viewModel::dismissFirebaseFullMigrationConfirmDialog
        )
    }

    if (uiState.showFirebaseDownloadConfirmDialog) {
        SettingsActionConfirmDialog(
            title = "클라우드에서 내리기",
            message = "현재 선택된 클라우드 모임의 데이터로 이 기기의 「${uiState.currentClubName}」 " +
                "회원·장부·회비·경조사비·슬로건·이력·계좌를 덮어씁니다. " +
                "이 기기에만 있는 작업 내용은 사라집니다. 계속할까요?",
            confirmLabel = "내리기",
            onConfirm = viewModel::confirmFirebaseDownload,
            onDismiss = viewModel::dismissFirebaseDownloadConfirmDialog
        )
    }

    if (uiState.showFirebaseSignOutConfirmDialog) {
        SettingsActionConfirmDialog(
            title = "구글 로그아웃",
            message = "실시간 동기화가 해제되고, 이 기기에서는 로컬 데이터를 사용합니다. 로그아웃할까요?",
            confirmLabel = "로그아웃",
            onConfirm = viewModel::confirmFirebaseSignOut,
            onDismiss = viewModel::dismissFirebaseSignOutConfirmDialog
        )
    }

    if (uiState.showMeetingShareDialog) {
        MeetingShareDialog(
            clubName = uiState.currentClubName,
            input = uiState.meetingShareInput,
            isInviting = uiState.isInvitingMember,
            errorMessage = uiState.meetingShareError,
            onInputChange = viewModel::updateMeetingShareInput,
            onConfirm = viewModel::submitMeetingShareInvite,
            onDismiss = viewModel::dismissMeetingShareDialog
        )
    }

    if (uiState.showDesignateTreasurerDialog) {
        DesignateTreasurerDialog(
            clubName = uiState.currentClubName,
            currentTreasurerUid = uiState.designatedTreasurerUid,
            input = uiState.designateTreasurerInput,
            isBusy = uiState.isDesignatingTreasurer,
            errorMessage = uiState.designateTreasurerError,
            onInputChange = viewModel::updateDesignateTreasurerInput,
            onConfirm = viewModel::submitDesignateTreasurer,
            onClear = viewModel::clearDesignatedTreasurer,
            onDismiss = viewModel::dismissDesignateTreasurerDialog
        )
    }

    if (uiState.showDriveBackupConfirmDialog) {
        SettingsActionConfirmDialog(
            title = "드라이브 백업",
            message = "구글 드라이브에 DB 백업과 결산 PDF를 저장합니다. 진행할까요?",
            confirmLabel = "저장",
            onConfirm = {
                viewModel.confirmDriveBackup()
                googleSignInLauncher.launch(viewModel.getGoogleSignInIntent())
            },
            onDismiss = viewModel::dismissDriveBackupConfirmDialog
        )
    }

    if (uiState.showExcelBackupConfirmDialog) {
        SettingsActionConfirmDialog(
            title = "엑셀 백업",
            message = "「${uiState.currentClubName}」 데이터를 다운로드 폴더에 .xlsx로 저장합니다. 진행할까요?",
            confirmLabel = "저장",
            onConfirm = viewModel::confirmExcelBackup,
            onDismiss = viewModel::dismissExcelBackupConfirmDialog
        )
    }

    if (uiState.showExcelTemplateConfirmDialog) {
        SettingsActionConfirmDialog(
            title = "입력 양식 받기",
            message = "빈 엑셀 양식을 다운로드 폴더에 저장합니다. 진행할까요?",
            confirmLabel = "저장",
            onConfirm = viewModel::confirmExcelTemplateDownload,
            onDismiss = viewModel::dismissExcelTemplateConfirmDialog
        )
    }

    uiState.logoutError?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissLogoutError,
            title = { Text("로그아웃 오류", color = ExpenseRed) },
            text = {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissLogoutError) {
                    Text("확인", color = TextPrimary)
                }
            },
            containerColor = com.smartexpense.ui.theme.SurfaceDeepGray
        )
    }

    if (isBackupRestoreBusy || isCloudSyncBusy || isSeedResetBusy || isClearClubDataBusy || isExcelImportBusy || isLoggingOut || isFirebaseSyncing) {
        BackupRestoreProgressDialog(
            message = when {
                isFirebaseSyncing -> "클라우드에 반영하는 중입니다...\n잠시만 기다려 주세요."
                uiState.cloudSyncState == CloudSyncState.SigningIn -> "구글 계정 로그인 중..."
                uiState.cloudSyncState == CloudSyncState.Uploading -> "구글 드라이브에 업로드 중..."
                isExcelImportBusy -> "「${uiState.currentClubName}」 모임 데이터를 처리하고 있습니다.\n잠시만 기다려 주세요."
                isClearClubDataBusy -> "「${uiState.currentClubName}」 모임을 삭제하고 있습니다.\n잠시만 기다려 주세요."
                isSeedResetBusy -> "초기 데이터를 복원하고 있습니다.\n잠시만 기다려 주세요."
                isLoggingOut -> "로그아웃하고 있습니다.\n잠시만 기다려 주세요."
                else -> "데이터를 안전하게 처리하고 있습니다.\n잠시만 기다려 주세요."
            }
        )
    }

    uiState.cloudSyncError?.let { errorMessage ->
        CloudSyncErrorDialog(
            message = errorMessage,
            onDismiss = viewModel::dismissCloudSyncError
        )
    }

    if (uiState.showResetSeedConfirmDialog) {
        ResetSeedConfirmDialog(
            clubName = uiState.currentClubName,
            onConfirm = viewModel::confirmResetToDefaultSeedData,
            onDismiss = viewModel::dismissResetSeedConfirmDialog
        )
    }

    if (uiState.showClearClubDataConfirmDialog) {
        DissolveClubConfirmDialog(
            clubName = uiState.currentClubName,
            onConfirm = viewModel::confirmDissolveCurrentClub,
            onDismiss = viewModel::dismissClearClubDataConfirmDialog
        )
    }

    if (uiState.showExcelRestoreConfirmDialog) {
        ExcelRestoreConfirmDialog(
            clubName = uiState.currentClubName,
            onConfirm = viewModel::confirmExcelRestore,
            onDismiss = viewModel::dismissExcelRestoreConfirmDialog
        )
    }

    if (uiState.showRestoreConfirmDialog) {
        RestoreConfirmDialog(
            onConfirm = viewModel::confirmRestore,
            onDismiss = viewModel::dismissRestoreConfirmDialog
        )
    }

    uiState.seedResetError?.let { errorMessage ->
        BackupRestoreErrorDialog(
            message = errorMessage,
            onDismiss = viewModel::dismissSeedResetError
        )
    }

    uiState.clearClubDataError?.let { errorMessage ->
        BackupRestoreErrorDialog(
            message = errorMessage,
            onDismiss = viewModel::dismissClearClubDataError
        )
    }

    if (uiState.showExportDialog) {
        ExportFormatDialog(
            isExporting = uiState.isExporting,
            exportYear = uiState.exportYear,
            yearOptions = uiState.exportYearOptions,
            onExportYearChange = viewModel::setExportYear,
            onExportExcel = { viewModel.exportData(ExportFormat.EXCEL) },
            onExportPdf = { viewModel.exportData(ExportFormat.PDF) },
            onDismiss = dismissExportDialog
        )
    }

    if (uiState.showClubSloganDialog) {
        ClubSloganDialog(
            clubName = uiState.currentClubName,
            sloganDraft = uiState.clubSloganDraft,
            isSaving = uiState.isSavingClubSlogan,
            errorMessage = uiState.clubSloganError,
            onSloganChange = viewModel::updateClubSloganDraft,
            onConfirm = viewModel::submitClubSlogan,
            onDismiss = viewModel::dismissClubSloganDialog
        )
    }

    if (uiState.showDuesPaymentMethodDialog) {
        DuesPaymentMethodDialog(
            clubName = uiState.currentClubName,
            selected = uiState.duesPaymentMethodDraft,
            isSaving = uiState.isSavingDuesPaymentMethod,
            errorMessage = uiState.duesPaymentMethodError,
            onSelected = viewModel::updateDuesPaymentMethodDraft,
            onConfirm = viewModel::submitDuesPaymentMethod,
            onDismiss = viewModel::dismissDuesPaymentMethodDialog
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = BackgroundBlack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "${uiState.currentClubName} 설정",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundBlack,
                    titleContentColor = TextPrimary
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                SettingsSection(title = "모임") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UserRoleBadge(role = uiState.userRole)
                        Text(
                            text = when {
                                uiState.isSystemAdmin -> "모든 모임을 관리할 수 있습니다"
                                uiState.isMeetingOwner -> "운영관리자 지정·편집·올리기/내리기·삭제가 가능합니다"
                                uiState.canEdit -> "편집·올리기/내리기가 가능합니다"
                                else -> "조회만 가능합니다. 다른 모임은 찾아 가입하거나 직접 만들 수 있습니다"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    SettingsItemDivider()
                    SettingsNavigationItem(
                        title = "다른 모임 선택",
                        subtitle = "다른 동호회·모임으로 전환합니다",
                        onClick = { viewModel.switchClub(onSwitchClub) }
                    )
                    if (uiState.canEdit) {
                        SettingsItemDivider()
                        SettingsNavigationItem(
                            title = "슬로건 / 타이틀",
                            subtitle = if (uiState.clubSlogan.isBlank()) {
                                "미설정 · 메인 화면 상단에 표시"
                            } else {
                                uiState.clubSlogan
                            },
                            onClick = viewModel::openClubSloganDialog
                        )
                    }
                    SettingsItemDivider()
                    if (uiState.canEdit) {
                        SettingsNavigationItem(
                            title = "회비 납부 방식",
                            subtitle = "${uiState.duesPaymentMethod.toDisplayLabel()} · 신규 회비 등록 기본값",
                            onClick = viewModel::openDuesPaymentMethodDialog
                        )
                    } else {
                        SettingsInfoItem(
                            title = "회비 납부 방식",
                            subtitle = uiState.duesPaymentMethod.toDisplayLabel()
                        )
                    }
                    SettingsItemDivider()
                    SettingsNavigationItem(
                        title = "모임 이력",
                        subtitle = "창단, 회장 선출, 가입·탈퇴 등",
                        onClick = onOpenClubHistory
                    )
                    SettingsItemDivider()
                    SettingsNavigationItem(
                        title = "모임 계좌",
                        subtitle = if (uiState.registeredAccountCount > 0) {
                            "등록 ${uiState.registeredAccountCount}개 · 회비 입금 통장"
                        } else {
                            "등록된 계좌 없음"
                        },
                        onClick = onOpenClubAccount
                    )
                    if (uiState.isFirebaseSignedIn &&
                        (uiState.isMeetingOwner || uiState.isSystemAdmin || uiState.canEdit)
                    ) {
                        SettingsItemDivider()
                        SettingsNavigationItem(
                            title = "모임 공유하기",
                            subtitle = "기존 회원 초대 · 신규는 가입 요청",
                            enabled = !isFirebaseSyncing && !uiState.isInvitingMember,
                            onClick = viewModel::openMeetingShareDialog
                        )
                        SettingsItemDivider()
                        SettingsNavigationItem(
                            title = "운영관리자 지정",
                            subtitle = uiState.designatedTreasurerUid?.let { uid ->
                                "지정됨 · UID ${uid.take(8)}…"
                            } ?: "이 모임의 운영관리자로 지정할 계정",
                            enabled = !isFirebaseSyncing && !uiState.isDesignatingTreasurer,
                            onClick = viewModel::openDesignateTreasurerDialog
                        )
                    }
                }
            }

            // 보안(로그인 방식·생체 인증)은 모임 선택 화면 우상단 설정에서 관리합니다.

            if (uiState.canSync) { // 관리자·운영관리자만 클라우드 올리기/내리기 메뉴 표시
                item {
                    SettingsSection(title = "클라우드") {
                        SettingsNavigationItem(
                            title = if (uiState.isFirebaseSignedIn) {
                                "구글 로그아웃"
                            } else {
                                "구글 로그인"
                            },
                            subtitle = if (uiState.isFirebaseSignedIn) {
                                "회원·장부·회비·경조사비 동기화 · ${uiState.firebaseEmail ?: uiState.firebaseUid.orEmpty()}"
                            } else {
                                "회원·장부·회비·경조사비 동기화"
                            },
                            enabled = !isBackupRestoreBusy && !isCloudSyncBusy && !isFirebaseSyncing,
                            onClick = {
                                if (uiState.isFirebaseSignedIn) {
                                    viewModel.requestFirebaseSignOut()
                                } else {
                                    viewModel.onFirebaseSignInStarted()
                                    firebaseSignInLauncher.launch(viewModel.getFirebaseGoogleSignInIntent())
                                }
                            }
                        )
                        if (uiState.isFirebaseSignedIn) {
                            if (uiState.showHanuriCloudUploadHint) {
                                Text(
                                    text = "한우리 로컬 시드 데이터가 있습니다. Firestore 연동을 위해 최초 1회 「올리기」를 실행해 주세요.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            if (uiState.canDangerousOps) {
                                SettingsItemDivider()
                                SettingsNavigationItem(
                                    title = "초기 데이터 이행",
                                    subtitle = "클라우드를 비우고 로컬 전체를 다시 올림",
                                    enabled = !isFirebaseSyncing,
                                    onClick = viewModel::requestFirebaseFullMigration
                                )
                            }
                            SettingsItemDivider()
                            SettingsNavigationItem(
                                title = "이 기기 → 클라우드로 올리기",
                                subtitle = "로컬 변경분만 업로드 (실시간 편집은 이미 클라우드에 저장됨)",
                                enabled = !isFirebaseSyncing,
                                onClick = viewModel::requestFirebaseUpload
                            )
                            SettingsItemDivider()
                            SettingsNavigationItem(
                                title = "클라우드 → 이 기기로 내리기",
                                subtitle = "클라우드로 로컬 회원·장부·회비·경조사비·슬로건·이력·계좌를 덮어씀",
                                enabled = !isFirebaseSyncing,
                                onClick = viewModel::requestFirebaseDownload
                            )
                            if (uiState.canDangerousOps) {
                                SettingsItemDivider()
                                SettingsNavigationItem(
                                    title = "드라이브에 백업 파일 저장",
                                    subtitle = when (uiState.cloudSyncState) {
                                        CloudSyncState.SigningIn -> "구글 계정 로그인 중..."
                                        CloudSyncState.Uploading -> "드라이브 업로드 중..."
                                        else -> "DB·결산 PDF를 Drive에 보관 (복원용, 실시간 아님)"
                                    },
                                    enabled = !isBackupRestoreBusy && !isCloudSyncBusy && !isFirebaseSyncing,
                                    onClick = viewModel::requestDriveBackup
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.canEdit) { // 관리자·운영관리자만 은행 알림 설정 표시
                item {
                    SettingsSection(title = "은행 알림") {
                        SettingsNavigationItem(
                            title = "알림 접근 권한",
                            subtitle = if (uiState.isNotificationAccessGranted) {
                                "허용됨"
                            } else {
                                "미허용 · 시스템 설정에서 허용해 주세요"
                            },
                            onClick = {
                                viewModel.refreshNotificationAccess()
                                viewModel.openNotificationAccessSettings()
                            }
                        )
                        SettingsItemDivider()
                        SettingsNavigationItem(
                            title = "은행 알림 파싱",
                            subtitle = buildString {
                                append("활성 ${uiState.enabledBankParseCount}개")
                                if (uiState.customBankCount > 0) {
                                    append(" · 커스텀 ${uiState.customBankCount}개")
                                }
                            },
                            onClick = onOpenBankParsing
                        )
                    }
                }
            }

            // 백업·복원·초기화는 시스템관리자, 현재 모임 삭제는 개설자도 가능
            if (uiState.canDangerousOps || uiState.isMeetingOwner) {
                item {
                    SettingsSection(title = if (uiState.canDangerousOps) "백업·복원" else "모임 관리") {
                        if (uiState.canDangerousOps) {
                        SettingsNavigationItem(
                            title = "엑셀로 백업",
                            subtitle = "회원·장부·회비·계좌를 .xlsx로 저장",
                            enabled = !isExcelImportBusy,
                            onClick = viewModel::requestExcelBackup
                        )
                        SettingsItemDivider()
                        SettingsNavigationItem(
                            title = "파일로 복원",
                            subtitle = ".xlsx 또는 .hndb",
                            enabled = !isExcelImportBusy &&
                                !isSeedResetBusy &&
                                !isClearClubDataBusy &&
                                !isBackupRestoreBusy,
                            onClick = {
                                clubRestoreLauncher.launch(
                                    arrayOf(
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                        "application/vnd.ms-excel",
                                        "application/octet-stream",
                                        "application/zip",
                                        "*/*"
                                    )
                                )
                            }
                        )
                        SettingsItemDivider()
                        SettingsNavigationItem(
                            title = "입력 양식 받기",
                            subtitle = "빈 엑셀 양식 → 작성 후 「파일로 복원」",
                            enabled = !isExcelImportBusy,
                            onClick = viewModel::requestExcelTemplateDownload
                        )
                        SettingsItemDivider()
                        SettingsNavigationItem(
                            title = "장부·결산 내보내기",
                            subtitle = "Excel · PDF (백업/복원용 아님)",
                            onClick = viewModel::openExportDialog
                        )
                        SettingsItemDivider()
                        }
                        SettingsNavigationItem(
                            title = "현재 모임 삭제",
                            subtitle = "로컬·클라우드 영구 삭제 · 복구 불가",
                            enabled = !isSeedResetBusy && !isClearClubDataBusy && !isExcelImportBusy,
                            onClick = viewModel::requestDissolveCurrentClub
                        )
                        if (uiState.canDangerousOps && isHanuriClub) {
                            SettingsItemDivider()
                            SettingsDangerItem(
                                title = "초기 데이터로 재설정",
                                subtitle = "한우리 시드 데이터로 되돌림",
                                enabled = !isSeedResetBusy && !isClearClubDataBusy && !isExcelImportBusy,
                                onClick = viewModel::requestResetToDefaultSeedData
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                Button(
                    onClick = viewModel::requestLogout,
                    enabled = !isBackupRestoreBusy &&
                        !isSeedResetBusy &&
                        !isClearClubDataBusy &&
                        !isLoggingOut &&
                        !isExcelImportBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ExpenseRed,
                        contentColor = TextPrimary,
                        disabledContainerColor = ExpenseRed.copy(alpha = 0.4f),
                        disabledContentColor = TextPrimary.copy(alpha = 0.6f)
                    )
                ) {
                    Text(
                        text = "로그아웃",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun MeetingShareDialog(
    clubName: String,
    input: String,
    isInviting: Boolean,
    errorMessage: String?,
    onInputChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isInviting) onDismiss() },
        title = { Text("멤버 초대", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "「$clubName」을 함께 볼 상대의 이메일(또는 UID)을 입력하세요.\n" +
                        "· 이미 앱에 로그인한 계정 → 즉시 「내 모임」에 표시\n" +
                        "· 명단에만 있는 기존 회원(미로그인) → 같은 이메일로 로그인하면 자동 합류\n" +
                        "· 신규 회원은 「모임 찾기」에서 가입 요청도 가능합니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    enabled = !isInviting,
                    singleLine = true,
                    label = { Text("이메일 또는 UID") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (!isInviting) onConfirm() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = TextPrimary,
                        unfocusedBorderColor = TextSecondary,
                        focusedLabelColor = TextPrimary,
                        unfocusedLabelColor = TextSecondary,
                        cursorColor = TextPrimary,
                        disabledTextColor = TextSecondary,
                        disabledBorderColor = TextSecondary,
                        disabledLabelColor = TextSecondary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                errorMessage?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = ExpenseRed)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isInviting && input.isNotBlank()
            ) {
                Text(if (isInviting) "초대 중…" else "초대", color = TextPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isInviting) {
                Text("취소", color = TextSecondary)
            }
        },
        containerColor = BackgroundBlack
    )
}

@Composable
private fun ClubSloganDialog(
    clubName: String,
    sloganDraft: String,
    isSaving: Boolean,
    errorMessage: String?,
    onSloganChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("모임 슬로건/타이틀 설정", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "「$clubName」 모임의 메인 화면 상단에 표시할 슬로건입니다.\n비워 두면 모임 이름만 표시됩니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                ClubTextField(
                    value = sloganDraft,
                    onValueChange = onSloganChange,
                    label = "모임 슬로건",
                    singleLine = false,
                    minLines = 2,
                    showClearButton = !isSaving
                )
                errorMessage?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = ExpenseRed)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isSaving) {
                Text(if (isSaving) "저장 중…" else "저장", color = TextPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("취소", color = TextSecondary)
            }
        },
        containerColor = com.smartexpense.ui.theme.SurfaceDeepGray
    )
}

@Composable
private fun DuesPaymentMethodDialog(
    clubName: String,
    selected: com.smartexpense.data.local.entity.club.DuesPaymentMethod,
    isSaving: Boolean,
    errorMessage: String?,
    onSelected: (com.smartexpense.data.local.entity.club.DuesPaymentMethod) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("회비 납부 방식", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "「$clubName」 모임의 신규 회비 등록 기본 방식입니다.\n월별 · 분기별 · 반기별 · 연도별 중에서 고르세요. 이미 등록된 회비에는 영향을 주지 않습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                ClubDropdownField(
                    label = "납부 방식",
                    options = duesPaymentMethodOptions,
                    selected = selected,
                    onSelected = onSelected,
                    enabled = !isSaving
                )
                errorMessage?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = ExpenseRed)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isSaving) {
                Text(if (isSaving) "저장 중…" else "저장", color = TextPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("취소", color = TextSecondary)
            }
        },
        containerColor = com.smartexpense.ui.theme.SurfaceDeepGray
    )
}

@Composable
private fun DesignateTreasurerDialog(
    clubName: String,
    currentTreasurerUid: String?,
    input: String,
    isBusy: Boolean,
    errorMessage: String?,
    onInputChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = { Text("운영관리자 지정", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "「$clubName」의 운영관리자로 설정할 구글 계정 이메일(또는 Firebase UID)을 입력하세요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                if (!currentTreasurerUid.isNullOrBlank()) {
                    Text(
                        text = "현재 지정: UID ${currentTreasurerUid.take(12)}…",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = onInputChange,
                    enabled = !isBusy,
                    singleLine = true,
                    label = { Text("이메일 또는 UID") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (!isBusy) onConfirm() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = TextPrimary,
                        unfocusedBorderColor = TextSecondary,
                        focusedLabelColor = TextPrimary,
                        unfocusedLabelColor = TextSecondary
                    )
                )
                errorMessage?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = ExpenseRed)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isBusy) {
                Text(if (isBusy) "지정 중…" else "지정", color = TextPrimary)
            }
        },
        dismissButton = {
            Row {
                if (!currentTreasurerUid.isNullOrBlank()) {
                    TextButton(onClick = onClear, enabled = !isBusy) {
                        Text("해제", color = ExpenseRed)
                    }
                }
                TextButton(onClick = onDismiss, enabled = !isBusy) {
                    Text("취소", color = TextSecondary)
                }
            }
        },
        containerColor = com.smartexpense.ui.theme.SurfaceDeepGray
    )
}



@Composable
private fun BackupRestoreProgressDialog(
    message: String = "데이터를 안전하게 처리하고 있습니다.\n잠시만 기다려 주세요."
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("처리 중", color = TextPrimary) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = TextPrimary,
                    strokeWidth = 2.dp
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        },
        confirmButton = {},
        containerColor = com.smartexpense.ui.theme.SurfaceDeepGray
    )
}

@Composable
private fun CloudSyncErrorDialog(
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("동기화 오류", color = ExpenseRed) },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("확인", color = TextPrimary)
            }
        },
        containerColor = com.smartexpense.ui.theme.SurfaceDeepGray
    )
}

@Composable
private fun ResetSeedConfirmDialog(
    clubName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("초기 데이터로 재설정", color = TextPrimary) },
        text = {
            Text(
                text = "주의: 이후에 직접 추가하거나 수정한 내역은 모두 사라지고, '$clubName' 모임의 초기 데이터 상태로 복원됩니다. 진행하시겠습니까?",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("재설정", color = ExpenseRed)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", color = TextSecondary)
            }
        },
        containerColor = com.smartexpense.ui.theme.SurfaceDeepGray
    )
}

@Composable
private fun SettingsActionConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = TextPrimary) },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = ExpenseRed)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", color = TextSecondary)
            }
        },
        containerColor = com.smartexpense.ui.theme.SurfaceDeepGray
    )
}

@Composable
private fun LogoutConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("로그아웃", color = TextPrimary) },
        text = {
            Text(
                text = "로그아웃하면 앱이 종료됩니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("로그아웃", color = ExpenseRed)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", color = TextSecondary)
            }
        },
        containerColor = com.smartexpense.ui.theme.SurfaceDeepGray
    )
}

@Composable
private fun DissolveClubConfirmDialog(
    clubName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("현재 모임 삭제", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "「$clubName」 모임의 모든 데이터가 영구 삭제되며 복구할 수 없습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Text(
                    text = "구글 로그인(실시간 동기화) 상태라면 Firestore 클라우드 데이터도 함께 삭제됩니다. Drive·엑셀 백업이 없으면 되돌릴 수 없습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ExpenseRed
                )
                Text(
                    text = "회원, 장부, 회비, 계좌, 설정과 모임 자체가 삭제됩니다. 다른 모임은 유지됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Text(
                    text = "정말 진행하시겠습니까?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ExpenseRed
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("삭제", color = ExpenseRed)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", color = TextSecondary)
            }
        },
        containerColor = com.smartexpense.ui.theme.SurfaceDeepGray
    )
}

@Composable
private fun ExcelRestoreConfirmDialog(
    clubName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("현재 모임 복원", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "「$clubName」 모임의 기존 데이터(회원·장부·회비·계좌)가 백업 파일 내용으로 덮어씌워집니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Text(
                    text = "백업 파일의 모임 ID·모임명과 현재 모임이 달라도, 데이터는 현재 선택 모임으로 이전됩니다. 다른 모임 데이터는 변경되지 않습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("복원", color = ExpenseRed)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", color = TextSecondary)
            }
        },
        containerColor = com.smartexpense.ui.theme.SurfaceDeepGray
    )
}

@Composable
private fun RestoreConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(".hndb 데이터 복원", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "시스템 DB 백업(.hndb)으로 복원합니다. 앱의 모든 모임·장부·회비 데이터가 백업 시점으로 교체됩니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Text(
                    text = "복원 후 앱이 다시 시작됩니다. 진행하시겠습니까?",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("복원", color = ExpenseRed)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소", color = TextSecondary)
            }
        },
        containerColor = com.smartexpense.ui.theme.SurfaceDeepGray
    )
}

@Composable
private fun RestoreRestartDialog(onRestart: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("복원 완료", color = TextPrimary) },
        text = {
            Text(
                text = "데이터 복원이 완료되었습니다.\n새 데이터를 불러오려면 앱을 다시 시작해야 합니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        },
        confirmButton = {
            TextButton(onClick = onRestart) {
                Text("앱 재시작", color = TextPrimary)
            }
        },
        containerColor = com.smartexpense.ui.theme.SurfaceDeepGray
    )
}

@Composable
private fun BackupRestoreErrorDialog(
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("오류", color = ExpenseRed) },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("확인", color = TextPrimary)
            }
        },
        containerColor = com.smartexpense.ui.theme.SurfaceDeepGray
    )
}
