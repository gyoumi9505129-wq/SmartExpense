package com.smartexpense.ui.settings

import com.smartexpense.domain.user.UserRole
import com.smartexpense.data.local.entity.club.DuesPaymentMethod
import java.time.LocalDate

enum class BackupRestoreState {
    Idle,
    Loading,
    Success,
    Error
}

enum class CloudSyncState {
    Idle,
    SigningIn,
    Uploading,
    Success,
    Error
}

/** 「가입 계정」 목록 한 줄. 웹 AdminAccountsPanel의 승인 계정 목록 대응. */
data class AccountRowUiModel(
    val uid: String,
    val displayName: String,
    val email: String,
    val isOwner: Boolean,
    val isTreasurer: Boolean,
    val isDuplicateEmail: Boolean
) {
    val label: String
        get() = displayName.ifBlank { email.ifBlank { uid } }
}

/** 「승인 대기」 한 줄. 웹 AdminAccountsPanel pending 대응. */
data class PendingJoinRequestUiModel(
    val meetingId: String,
    val requestId: String,
    val displayName: String,
    val email: String,
    val message: String = ""
) {
    val label: String
        get() = displayName.ifBlank { email.ifBlank { requestId } }
}

data class SettingsUiState(
    val currentClubName: String = "모임",
    /** 편집 가능 (시스템관리자·지정 운영관리자·모임 개설자) */
    val canEdit: Boolean = false,
    /** 클라우드 올리기/내리기 */
    val canSync: Boolean = false,
    /** 백업·복원·초기화 등 시스템관리자 전용 */
    val canDangerousOps: Boolean = false,
    /** 시스템관리자 */
    val isSystemAdmin: Boolean = false,
    /** 현재 모임을 만든 관리자 */
    val isMeetingOwner: Boolean = false,
    /** UI 배지용 역할 */
    val userRole: UserRole = UserRole.MEMBER,
    val isNotificationAccessGranted: Boolean = false,
    val showExportDialog: Boolean = false,
    val isExporting: Boolean = false,
    val exportYear: Int = LocalDate.now().year,
    val exportYearOptions: List<Int> = emptyList(),
    val snackbarMessage: String? = null,
    val backupRestoreState: BackupRestoreState = BackupRestoreState.Idle,
    val backupRestoreError: String? = null,
    val showRestoreConfirmDialog: Boolean = false,
    val pendingRestoreUri: String? = null,
    val showRestartDialog: Boolean = false,
    val showResetSeedConfirmDialog: Boolean = false,
    val isResettingSeedData: Boolean = false,
    val seedResetError: String? = null,
    /** 한우리 모임 + Firestore 미연결 + 동기화 권한 있을 때 올리기 안내 */
    val showHanuriCloudUploadHint: Boolean = false,
    val showClearClubDataConfirmDialog: Boolean = false,
    val isClearingClubData: Boolean = false,
    val clearClubDataError: String? = null,
    val cloudSyncState: CloudSyncState = CloudSyncState.Idle,
    val cloudSyncError: String? = null,
    val firebaseUid: String? = null,
    val firebaseEmail: String? = null,
    val isFirebaseSignedIn: Boolean = false,
    val isFirebaseSyncing: Boolean = false,
    val designatedTreasurerUid: String? = null,
    val showDesignateTreasurerDialog: Boolean = false,
    val designateTreasurerInput: String = "",
    val designateTreasurerError: String? = null,
    val isDesignatingTreasurer: Boolean = false,
    val showFirebaseUploadConfirmDialog: Boolean = false,
    val showFirebaseFullMigrationConfirmDialog: Boolean = false,
    val showFirebaseDownloadConfirmDialog: Boolean = false,
    val showFirebaseSignOutConfirmDialog: Boolean = false,
    val showDriveBackupConfirmDialog: Boolean = false,
    val showExcelBackupConfirmDialog: Boolean = false,
    val showExcelTemplateConfirmDialog: Boolean = false,
    val isExcelImportBusy: Boolean = false,
    val pendingExcelImportUri: String? = null,
    val showExcelRestoreConfirmDialog: Boolean = false,
    val navigateBackAfterExcelImport: Boolean = false,
    val isLoading: Boolean = true,
    val registeredAccountCount: Int = 0,
    val enabledBankParseCount: Int = 0,
    val customBankCount: Int = 0,
    val showLogoutConfirmDialog: Boolean = false,
    val isLoggingOut: Boolean = false,
    val logoutError: String? = null,
    val clubSlogan: String = "",
    val showClubSloganDialog: Boolean = false,
    val clubSloganDraft: String = "",
    val isSavingClubSlogan: Boolean = false,
    val clubSloganError: String? = null,
    val duesPaymentMethod: DuesPaymentMethod = DuesPaymentMethod.DEFAULT,
    val showDuesPaymentMethodDialog: Boolean = false,
    val duesPaymentMethodDraft: DuesPaymentMethod = DuesPaymentMethod.DEFAULT,
    val isSavingDuesPaymentMethod: Boolean = false,
    val duesPaymentMethodError: String? = null,
    val showMeetingShareDialog: Boolean = false,
    val meetingShareInput: String = "",
    val meetingShareError: String? = null,
    val isInvitingMember: Boolean = false,
    /** 클라우드 한우리 미승인 시 설정에서 승인 요청 */
    val canRequestAccess: Boolean = false,
    val accessRequestStatus: String? = null,
    val isRequestingAccess: Boolean = false,
    val accessRequestMeetingId: String? = null,
    val accessRequestMeetingName: String = "한우리",
    /** 시스템관리자용 「가입 계정」 목록 (강퇴/삭제) · 「승인 대기」 */
    val accountRows: List<AccountRowUiModel> = emptyList(),
    val pendingJoinRequests: List<PendingJoinRequestUiModel> = emptyList(),
    val isLoadingAccounts: Boolean = false,
    val isDecidingJoinRequest: Boolean = false,
    val accountsError: String? = null,
    val showPurgeAccountDialog: Boolean = false,
    val purgeTargetUid: String? = null,
    val purgeTargetLabel: String = "",
    val isPurgingAccount: Boolean = false,
    val showKeepOnlyUidDialog: Boolean = false,
    val keepOnlyUidTarget: String? = null,
    val keepOnlyUidTargetLabel: String = "",
    val isCleaningDuplicateAccounts: Boolean = false
)
