package com.smartexpense.ui.settings

import android.content.Context
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.smartexpense.data.export.ExcelExportManager
import com.smartexpense.data.export.PdfExportManager
import com.smartexpense.data.firebase.CloudDataMigrationRepository
import com.smartexpense.data.firebase.CloudLedgerModeRepository
import com.smartexpense.data.firebase.CloudSyncMode
import com.smartexpense.data.firebase.MeetingRoleRepository
import com.smartexpense.data.firebase.SystemAdminConfig
import com.smartexpense.data.firebase.auth.FirebaseAuthRepository
import com.smartexpense.data.firebase.firestore.MeetingFirestoreRepository
import com.smartexpense.data.firebase.firestore.MeetingInviteResult
import com.smartexpense.data.firebase.firestore.UserProfileFirestoreRepository
import com.smartexpense.data.local.prefs.UserPreferenceKeys
import com.smartexpense.data.local.prefs.safeEdit
import com.smartexpense.data.repository.bank.BankNotificationSettingsRepository
import com.smartexpense.data.repository.club.AppNavigationGuard
import com.smartexpense.data.repository.club.ClubAccountRepository
import com.smartexpense.data.session.AuthRefreshGuard
import com.smartexpense.data.session.AuthSessionManager
import com.smartexpense.data.session.UserSessionManager
import com.smartexpense.data.excelimport.ClubScopedExcelBackupManager
import com.smartexpense.data.excelimport.ExcelImportException
import com.smartexpense.data.excelimport.ExcelTemplateManager
import com.smartexpense.data.excelimport.ExcelImporter
import com.smartexpense.data.local.ClubConstants
import com.smartexpense.data.local.bootstrap.DatabaseBootstrap
import com.smartexpense.data.local.entity.club.ClubAccountEntity
import com.smartexpense.data.local.entity.club.DuesPaymentMethod
import com.smartexpense.data.model.bank.CustomBankUiModel
import com.smartexpense.data.repository.club.ClubDataImportRepository
import com.smartexpense.data.repository.club.ClubRepository
import com.smartexpense.data.repository.club.ClubSettingsRepository
import com.smartexpense.data.repository.club.LedgerRefreshNotifier
import com.smartexpense.data.repository.club.SeedDataRepository
import com.smartexpense.data.repository.club.SelectedClubRepository
import com.smartexpense.data.repository.club.SelectedMeetingRepository
import com.smartexpense.data.repository.club.requireSelectedClubId
import com.smartexpense.domain.user.UserRole
import com.smartexpense.ui.club.dues.toDisplayLabel
import com.smartexpense.ui.common.ViewModelRefreshTrigger
import com.smartexpense.ui.common.bindScreenRefreshSignals
import com.smartexpense.util.BackupRestoreManager
import com.smartexpense.util.ClubRestoreFileDetector
import com.smartexpense.util.ClubRestoreFileKind
import com.smartexpense.util.GoogleDriveSyncManager
import com.smartexpense.util.NotificationAccessHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val excelExportManager: ExcelExportManager,
    private val pdfExportManager: PdfExportManager,
    private val backupRestoreManager: BackupRestoreManager,
    private val googleDriveSyncManager: GoogleDriveSyncManager,
    private val firebaseAuthRepository: FirebaseAuthRepository,
    private val cloudLedgerModeRepository: CloudLedgerModeRepository,
    private val meetingRoleRepository: MeetingRoleRepository,
    private val selectedMeetingRepository: SelectedMeetingRepository,
    private val meetingFirestoreRepository: MeetingFirestoreRepository,
    private val userProfileFirestoreRepository: UserProfileFirestoreRepository,
    private val cloudDataMigrationRepository: CloudDataMigrationRepository,
    private val seedDataRepository: SeedDataRepository,
    private val selectedClubRepository: SelectedClubRepository,
    private val excelTemplateManager: ExcelTemplateManager,
    private val clubScopedExcelBackupManager: ClubScopedExcelBackupManager,
    private val excelImporter: ExcelImporter,
    private val clubDataImportRepository: ClubDataImportRepository,
    private val clubRepository: ClubRepository,
    private val clubSettingsRepository: ClubSettingsRepository,
    private val clubAccountRepository: ClubAccountRepository,
    private val bankNotificationSettingsRepository: BankNotificationSettingsRepository,
    private val appNavigationGuard: AppNavigationGuard,
    private val databaseBootstrap: DatabaseBootstrap,
    private val userSessionManager: UserSessionManager,
    private val authSessionManager: AuthSessionManager,
    private val authRefreshGuard: AuthRefreshGuard,
    private val ledgerRefreshNotifier: LedgerRefreshNotifier,
    private val dataStore: DataStore<Preferences>,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val refreshTrigger = ViewModelRefreshTrigger()

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()


    private val clubListNavigationEvents = Channel<Unit>(Channel.BUFFERED)
    val clubListNavigationEventsFlow = clubListNavigationEvents.receiveAsFlow()

    private val exportYearOptions: List<Int> = run {
        val current = LocalDate.now().year
        (2011..current).toList().asReversed()
    }

    init {
        observeClubScopedSettings()
        observeFirebaseAuth()
        observeMeetingOwnerRole()
        refreshNotificationAccess()
        bindScreenRefreshSignals(ledgerRefreshNotifier, refreshTrigger, authRefreshGuard)
        _uiState.update {
            it.copy(
                exportYear = LocalDate.now().year,
                exportYearOptions = exportYearOptions
            )
        }
    }

    private fun observeFirebaseAuth() {
        viewModelScope.launch {
            firebaseAuthRepository.authState
                .catch { emit(null) }
                .collect { session ->
                    val isSystemAdmin = SystemAdminConfig.matches(session?.uid, session?.email)
                    _uiState.update {
                        it.copy(
                            isFirebaseSignedIn = session != null,
                            firebaseUid = session?.uid,
                            firebaseEmail = session?.email,
                            isSystemAdmin = isSystemAdmin,
                            canDangerousOps = isSystemAdmin,
                            canEdit = if (isSystemAdmin) true else it.canEdit,
                            canSync = if (isSystemAdmin) true else it.canSync,
                            userRole = if (isSystemAdmin) UserRole.SYSTEM_ADMIN else it.userRole
                        )
                    }
                }
        }
    }

    private fun observeMeetingOwnerRole() {
        viewModelScope.launch {
            combine(
                combine(
                    meetingRoleRepository.isSystemAdmin.catch { emit(false) },
                    meetingRoleRepository.canEdit.catch { emit(false) },
                    meetingRoleRepository.canSync.catch { emit(false) },
                    meetingRoleRepository.canDangerousOps.catch { emit(false) },
                    meetingRoleRepository.isMeetingOwner.catch { emit(false) }
                ) { isSystemAdmin, canEdit, canSync, canDangerousOps, isMeetingOwner ->
                    RoleFlags(
                        isSystemAdmin = isSystemAdmin,
                        canEdit = canEdit,
                        canSync = canSync,
                        canDangerousOps = canDangerousOps,
                        isMeetingOwner = isMeetingOwner
                    )
                },
                meetingRoleRepository.designatedTreasurerUid.catch { emit(null) },
                meetingRoleRepository.currentUserRole.catch { emit(UserRole.MEMBER) }
            ) { flags, treasurerUid, userRole ->
                RoleUiSnapshot(
                    isSystemAdmin = flags.isSystemAdmin,
                    canEdit = flags.canEdit,
                    canSync = flags.canSync,
                    canDangerousOps = flags.canDangerousOps,
                    treasurerUid = treasurerUid,
                    isMeetingOwner = flags.isMeetingOwner,
                    userRole = userRole
                )
            }.collect { snapshot ->
                val session = firebaseAuthRepository.currentSession()
                val isSystemAdmin = snapshot.isSystemAdmin ||
                    SystemAdminConfig.matches(session?.uid, session?.email)
                _uiState.update {
                    it.copy(
                        isSystemAdmin = isSystemAdmin,
                        canEdit = snapshot.canEdit || isSystemAdmin,
                        canSync = snapshot.canSync || isSystemAdmin,
                        canDangerousOps = snapshot.canDangerousOps || isSystemAdmin,
                        designatedTreasurerUid = snapshot.treasurerUid,
                        isMeetingOwner = snapshot.isMeetingOwner,
                        userRole = if (isSystemAdmin) UserRole.SYSTEM_ADMIN else snapshot.userRole
                    )
                }
            }
        }
    }

    private data class RoleFlags(
        val isSystemAdmin: Boolean,
        val canEdit: Boolean,
        val canSync: Boolean,
        val canDangerousOps: Boolean,
        val isMeetingOwner: Boolean
    )

    private data class RoleUiSnapshot(
        val isSystemAdmin: Boolean,
        val canEdit: Boolean,
        val canSync: Boolean,
        val canDangerousOps: Boolean,
        val treasurerUid: String?,
        val isMeetingOwner: Boolean,
        val userRole: UserRole
    )

    fun refreshOnVisible() {
        if (!authRefreshGuard.shouldAllowDataRefresh()) return
        refreshNotificationAccess()
        refreshTrigger.refresh()
    }

    private fun observeClubScopedSettings() {
        viewModelScope.launch {
            refreshTrigger.tick.flatMapLatest {
                _uiState.update { it.copy(isLoading = true) }
                runCatching { databaseBootstrap.awaitReady() }

                combine(
                    combine(
                        selectedClubRepository.selectedClubId.catch { emit(null) },
                        clubRepository.observeSelectedClubName().catch { emit("모임") },
                        clubRepository.observeSelectedClubSlogan().catch { emit("") }
                    ) { clubId, clubName, clubSlogan ->
                        Triple(clubId, clubName, clubSlogan)
                    },
                    combine(
                        clubAccountRepository.observeAll().catch { emit(emptyList()) },
                        bankNotificationSettingsRepository.enabledPackages().catch { emit(emptySet()) },
                        bankNotificationSettingsRepository.customBankStates().catch { emit(emptyList()) }
                    ) { accounts, enabledPackages, customBanks ->
                        Triple(accounts, enabledPackages, customBanks)
                    },
                    clubSettingsRepository.observeSettings().catch {
                        emit(com.smartexpense.domain.settings.ClubSettings.emptyDefaults(0))
                    }
                ) { clubInfo, settingsInfo, clubSettings ->
                    ClubScopedSettingsSnapshot(
                        clubId = clubInfo.first,
                        clubName = clubInfo.second,
                        clubSlogan = clubInfo.third,
                        accounts = settingsInfo.first,
                        enabledPackages = settingsInfo.second,
                        customBanks = settingsInfo.third,
                        duesPaymentMethod = clubSettings.duesPaymentMethod
                    )
                }.catch {
                    emit(
                        ClubScopedSettingsSnapshot(
                            clubId = null,
                            clubName = "모임",
                            clubSlogan = "",
                            accounts = emptyList(),
                            enabledPackages = emptySet(),
                            customBanks = emptyList(),
                            duesPaymentMethod = DuesPaymentMethod.DEFAULT
                        )
                    )
                }
            }.catch {
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        currentClubName = "모임",
                        clubSlogan = "",
                        registeredAccountCount = 0,
                        enabledBankParseCount = 0,
                        customBankCount = 0,
                        duesPaymentMethod = DuesPaymentMethod.DEFAULT
                    )
                }
            }.collect { snapshot ->
                val club = snapshot.clubId?.let { clubRepository.getClub(it) }
                val showHanuriHint = ClubConstants.isHanuriSeedClub(snapshot.clubName) &&
                    club?.firestoreMeetingId.isNullOrBlank()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentClubName = snapshot.clubName,
                        clubSlogan = snapshot.clubSlogan,
                        duesPaymentMethod = snapshot.duesPaymentMethod,
                        registeredAccountCount = snapshot.accounts.size,
                        enabledBankParseCount = snapshot.enabledPackages.size,
                        customBankCount = snapshot.customBanks.size,
                        showHanuriCloudUploadHint = showHanuriHint
                    )
                }
            }
        }
    }

    private data class ClubScopedSettingsSnapshot(
        val clubId: Long?,
        val clubName: String,
        val clubSlogan: String,
        val accounts: List<ClubAccountEntity>,
        val enabledPackages: Set<String>,
        val customBanks: List<CustomBankUiModel>,
        val duesPaymentMethod: DuesPaymentMethod = DuesPaymentMethod.DEFAULT
    )

    fun openClubSloganDialog() {
        _uiState.update {
            it.copy(
                showClubSloganDialog = true,
                clubSloganDraft = it.clubSlogan,
                clubSloganError = null
            )
        }
    }

    fun dismissClubSloganDialog() {
        if (_uiState.value.isSavingClubSlogan) return
        _uiState.update {
            it.copy(
                showClubSloganDialog = false,
                clubSloganDraft = "",
                clubSloganError = null
            )
        }
    }

    fun updateClubSloganDraft(value: String) {
        _uiState.update {
            it.copy(
                clubSloganDraft = value.take(CLUB_SLOGAN_MAX_LENGTH),
                clubSloganError = null
            )
        }
    }

    fun submitClubSlogan() {
        if (_uiState.value.isSavingClubSlogan) return

        viewModelScope.launch {
            val trimmed = _uiState.value.clubSloganDraft.trim()
            _uiState.update {
                it.copy(isSavingClubSlogan = true, clubSloganError = null)
            }
            runCatching {
                val clubId = selectedClubRepository.requireSelectedClubId()
                clubRepository.updateClubSlogan(clubId, trimmed)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isSavingClubSlogan = false,
                        showClubSloganDialog = false,
                        clubSlogan = trimmed,
                        clubSloganDraft = "",
                        snackbarMessage = if (trimmed.isEmpty()) {
                            "모임 슬로건을 삭제했습니다."
                        } else {
                            "모임 슬로건을 저장했습니다."
                        }
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSavingClubSlogan = false,
                        clubSloganError = error.toUserFriendlyMessage("모임 슬로건 저장")
                    )
                }
            }
        }
    }

    fun openDuesPaymentMethodDialog() {
        _uiState.update {
            it.copy(
                showDuesPaymentMethodDialog = true,
                duesPaymentMethodDraft = it.duesPaymentMethod,
                duesPaymentMethodError = null
            )
        }
    }

    fun dismissDuesPaymentMethodDialog() {
        if (_uiState.value.isSavingDuesPaymentMethod) return
        _uiState.update {
            it.copy(
                showDuesPaymentMethodDialog = false,
                duesPaymentMethodError = null
            )
        }
    }

    fun updateDuesPaymentMethodDraft(method: DuesPaymentMethod) {
        _uiState.update {
            it.copy(duesPaymentMethodDraft = method, duesPaymentMethodError = null)
        }
    }

    fun submitDuesPaymentMethod() {
        if (_uiState.value.isSavingDuesPaymentMethod) return
        val method = _uiState.value.duesPaymentMethodDraft
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingDuesPaymentMethod = true, duesPaymentMethodError = null) }
            runCatching {
                val clubId = selectedClubRepository.requireSelectedClubId()
                clubSettingsRepository.updateDuesPaymentMethod(clubId, method)
            }.onSuccess {
                val meetingId = selectedMeetingRepository.selectedMeetingId.first()
                val cloudFailed = !meetingId.isNullOrBlank() &&
                    runCatching {
                        meetingFirestoreRepository.updateDuesPaymentMethod(meetingId, method.name)
                    }.isFailure
                _uiState.update {
                    it.copy(
                        isSavingDuesPaymentMethod = false,
                        showDuesPaymentMethodDialog = false,
                        duesPaymentMethod = method,
                        snackbarMessage = if (cloudFailed) {
                            "로컬에 저장했습니다. 클라우드 반영은 다음에 다시 시도해 주세요."
                        } else {
                            "회비 납부 방식을 ${method.toDisplayLabel()}(으)로 저장했습니다."
                        }
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSavingDuesPaymentMethod = false,
                        duesPaymentMethodError = error.toUserFriendlyMessage("회비 납부 방식 저장")
                    )
                }
            }
        }
    }

    fun refreshNotificationAccess() {
        _uiState.update {
            it.copy(
                isNotificationAccessGranted = NotificationAccessHelper
                    .isNotificationListenerEnabled(appContext)
            )
        }
    }

    fun switchClub(onDone: () -> Unit) {
        viewModelScope.launch {
            selectedClubRepository.clearSelectedClubId()
            onDone()
        }
    }

    fun requestLogout() {
        if (_uiState.value.isLoggingOut) return
        _uiState.update { it.copy(showLogoutConfirmDialog = true, logoutError = null) }
    }

    fun dismissLogoutConfirmDialog() {
        _uiState.update { it.copy(showLogoutConfirmDialog = false) }
    }

    fun dismissLogoutError() {
        _uiState.update { it.copy(logoutError = null) }
    }

    fun confirmLogout() {
        if (_uiState.value.isLoggingOut) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    showLogoutConfirmDialog = false,
                    isLoggingOut = true,
                    logoutError = null
                )
            }
            runCatching {
                userSessionManager.logout()
            }.onSuccess {
                _uiState.update { it.copy(isLoggingOut = false) }
            }.onFailure { error ->
                authRefreshGuard.onExplicitLogoutCancelled()
                _uiState.update {
                    it.copy(
                        isLoggingOut = false,
                        logoutError = error.toUserFriendlyMessage("로그아웃")
                    )
                }
            }
        }
    }

    fun downloadCurrentClubBackup() {
        requireDangerousOps { downloadCurrentClubBackupInternal() }
    }

    fun requestExcelBackup() {
        requireDangerousOps {
            _uiState.update { it.copy(showExcelBackupConfirmDialog = true) }
        }
    }

    fun dismissExcelBackupConfirmDialog() {
        _uiState.update { it.copy(showExcelBackupConfirmDialog = false) }
    }

    fun confirmExcelBackup() {
        _uiState.update { it.copy(showExcelBackupConfirmDialog = false) }
        downloadCurrentClubBackupInternal()
    }

    private fun downloadCurrentClubBackupInternal() {
        if (_uiState.value.isExcelImportBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isExcelImportBusy = true) }
            runCatching {
                withContext(Dispatchers.IO) {
                    val clubId = selectedClubRepository.requireSelectedClubId()
                    clubScopedExcelBackupManager.exportCurrentClubToDownloads(clubId)
                }
            }.onSuccess { file ->
                _uiState.update {
                    it.copy(
                        isExcelImportBusy = false,
                        snackbarMessage = "다운로드 폴더에 ${file.name} 파일이 저장되었습니다."
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isExcelImportBusy = false,
                        snackbarMessage = error.message ?: "현재 모임 백업에 실패했습니다."
                    )
                }
            }
        }
    }

    fun downloadExcelImportTemplate() {
        requireDangerousOps { downloadExcelImportTemplateInternal() }
    }

    fun requestExcelTemplateDownload() {
        requireDangerousOps {
            _uiState.update { it.copy(showExcelTemplateConfirmDialog = true) }
        }
    }

    fun dismissExcelTemplateConfirmDialog() {
        _uiState.update { it.copy(showExcelTemplateConfirmDialog = false) }
    }

    fun confirmExcelTemplateDownload() {
        _uiState.update { it.copy(showExcelTemplateConfirmDialog = false) }
        downloadExcelImportTemplateInternal()
    }

    private fun downloadExcelImportTemplateInternal() {
        if (_uiState.value.isExcelImportBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isExcelImportBusy = true) }
            runCatching {
                withContext(Dispatchers.IO) {
                    excelTemplateManager.createTemplateInDownloads()
                }
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isExcelImportBusy = false,
                        snackbarMessage = "다운로드 폴더에 ${ExcelTemplateManager.TEMPLATE_FILE_NAME} 파일이 저장되었습니다."
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isExcelImportBusy = false,
                        snackbarMessage = error.message ?: "양식 다운로드에 실패했습니다."
                    )
                }
            }
        }
    }

    fun onExcelFileSelected(uri: Uri) {
        if (_uiState.value.isExcelImportBusy) return
        requireDangerousOps { onExcelFileSelectedInternal(uri) }
    }

    /**
     * 「현재 모임 복원」 파일 선택: .xlsx → 엑셀 모임 복원, .hndb → DB 전체 복원.
     */
    fun onClubRestoreFileSelected(uri: Uri) {
        if (_uiState.value.isExcelImportBusy) return
        if (_uiState.value.backupRestoreState == BackupRestoreState.Loading) return
        requireDangerousOps {
            when (ClubRestoreFileDetector.detect(appContext, uri)) {
                ClubRestoreFileKind.EXCEL -> onExcelFileSelectedInternal(uri)
                ClubRestoreFileKind.HNDB -> {
                    _uiState.update {
                        it.copy(
                            pendingRestoreUri = uri.toString(),
                            showRestoreConfirmDialog = true
                        )
                    }
                }
                ClubRestoreFileKind.UNKNOWN -> {
                    val name = ClubRestoreFileDetector.queryDisplayName(appContext, uri).orEmpty()
                    _uiState.update {
                        it.copy(
                            snackbarMessage = if (name.isNotBlank()) {
                                "지원하지 않는 파일입니다: $name (.xlsx 또는 .hndb만 가능)"
                            } else {
                                "지원하지 않는 파일입니다. .xlsx 또는 .hndb 파일을 선택해 주세요."
                            }
                        )
                    }
                }
            }
        }
    }

    private fun onExcelFileSelectedInternal(uri: Uri) {
        if (_uiState.value.isExcelImportBusy) return
        _uiState.update {
            it.copy(
                pendingExcelImportUri = uri.toString(),
                showExcelRestoreConfirmDialog = true
            )
        }
    }

    fun dismissExcelRestoreConfirmDialog() {
        _uiState.update {
            it.copy(
                showExcelRestoreConfirmDialog = false,
                pendingExcelImportUri = null
            )
        }
    }

    fun confirmExcelRestore() {
        if (_uiState.value.isExcelImportBusy) return
        _uiState.update {
            it.copy(
                showExcelRestoreConfirmDialog = false,
                isExcelImportBusy = true
            )
        }
        processPendingExcelImport()
    }

    fun consumeNavigateBackAfterExcelImport() {
        _uiState.update { it.copy(navigateBackAfterExcelImport = false) }
    }

    private fun processPendingExcelImport() {
        val uriString = _uiState.value.pendingExcelImportUri ?: return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val clubId = selectedClubRepository.requireSelectedClubId()
                    val clubName = clubRepository.getSelectedClubName()
                    val uri = Uri.parse(uriString)
                    val parsed = excelImporter.parse(uri)
                    val result = clubDataImportRepository.importParsedDataForClub(
                        targetClubId = clubId,
                        parsed = parsed
                    )
                    clubName to result
                }
            }.onSuccess { (clubName, result) ->
                ledgerRefreshNotifier.notifyDuesChanged()
                ledgerRefreshNotifier.notifyTransactionChanged()
                _uiState.update {
                    it.copy(
                        isExcelImportBusy = false,
                        pendingExcelImportUri = null,
                        snackbarMessage = "「$clubName」 모임 ${result.totalCount}건 복원이 완료되었습니다."
                    )
                }
            }.onFailure { error ->
                val message = when (error) {
                    is ExcelImportException -> error.message ?: "복원 실패: 파일을 읽을 수 없습니다."
                    is SecurityException -> "복원 실패: 파일 접근 권한이 없습니다."
                    else -> userFriendlyExcelImportError(error)
                }
                _uiState.update {
                    it.copy(
                        isExcelImportBusy = false,
                        pendingExcelImportUri = null,
                        snackbarMessage = message
                    )
                }
            }
        }
    }

    fun openNotificationAccessSettings() {
        NotificationAccessHelper.openNotificationAccessSettings(appContext)
    }

    fun openExportDialog() {
        requireDangerousOps {
            _uiState.update {
                it.copy(
                    showExportDialog = true,
                    exportYear = it.exportYear.takeIf { year -> year in exportYearOptions }
                        ?: LocalDate.now().year,
                    exportYearOptions = exportYearOptions
                )
            }
        }
    }

    fun dismissExportDialog() {
        if (_uiState.value.isExporting) return
        _uiState.update { it.copy(showExportDialog = false) }
    }

    fun setExportYear(year: Int) {
        if (_uiState.value.isExporting) return
        if (year !in exportYearOptions) return
        _uiState.update { it.copy(exportYear = year) }
    }

    fun exportData(format: ExportFormat) {
        if (_uiState.value.isExporting) return
        if (!_uiState.value.canDangerousOps) return

        val year = _uiState.value.exportYear
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            runCatching {
                withContext(Dispatchers.IO) {
                    when (format) {
                        ExportFormat.EXCEL -> excelExportManager.exportLedgerExcel(year)
                        ExportFormat.PDF -> pdfExportManager.saveSettlementReportToDownloads(year)
                    }
                }
            }.onSuccess { fileName ->
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        showExportDialog = false,
                        snackbarMessage = "Download 폴더에 ${fileName}이 저장되었습니다."
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        showExportDialog = false,
                        snackbarMessage = error.message ?: "내보내기에 실패했습니다."
                    )
                }
            }
        }
    }

    fun onComingSoonFeature(featureName: String) {
        _uiState.update { it.copy(snackbarMessage = "$featureName 기능은 준비 중입니다.") }
    }

    fun getGoogleSignInIntent(): Intent = googleDriveSyncManager.createSignInIntent()

    fun getFirebaseGoogleSignInIntent(): Intent = firebaseAuthRepository.createGoogleSignInIntent()

    fun onCloudSyncStarted() {
        if (_uiState.value.cloudSyncState == CloudSyncState.Uploading) return
        _uiState.update {
            it.copy(
                cloudSyncState = CloudSyncState.SigningIn,
                cloudSyncError = null
            )
        }
    }

    fun onFirebaseSignInStarted() {
        _uiState.update {
            it.copy(
                cloudSyncError = null,
                // 구글 계정 선택 UI가 뜨기 전에는 로딩을 켜지 않음 (취소 오인 방지)
                isFirebaseSyncing = false
            )
        }
    }

    fun onFirebaseSignInCancelled() {
        _uiState.update {
            it.copy(
                isFirebaseSyncing = false,
                snackbarMessage = "구글 로그인이 취소되었습니다. 계정을 선택한 뒤 안내에 따라 계속해 주세요."
            )
        }
    }

    fun handleFirebaseGoogleSignInResult(data: Intent?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isFirebaseSyncing = true, cloudSyncError = null) }
            runCatching {
                val session = firebaseAuthRepository.signInWithGoogleIntentData(data)
                session
            }.onSuccess { session ->
                _uiState.update {
                    it.copy(
                        isFirebaseSignedIn = true,
                        isFirebaseSyncing = false,
                        firebaseUid = session.uid,
                        firebaseEmail = session.email,
                        snackbarMessage = if (meetingRoleRepository.canSyncNow()) {
                            "구글 동기화가 활성화되었습니다."
                        } else {
                            "구글 계정으로 로그인했습니다. 시스템관리자 또는 모임관리자만 동기화할 수 있습니다."
                        }
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(isFirebaseSyncing = false) }
                if (isGoogleSignInCancelled(error)) {
                    _uiState.update {
                        it.copy(snackbarMessage = "구글 로그인이 취소되었습니다.")
                    }
                    return@onFailure
                }
                _uiState.update {
                    it.copy(
                        cloudSyncError = error.toFirestoreSyncMessage()
                    )
                }
            }
        }
    }

    fun requestFirebaseUpload() {
        requireSyncAccess {
            if (!_uiState.value.isFirebaseSignedIn) {
                _uiState.update {
                    it.copy(snackbarMessage = "먼저 구글 로그인을 해 주세요.")
                }
                return@requireSyncAccess
            }
            if (_uiState.value.isFirebaseSyncing) return@requireSyncAccess
            _uiState.update { it.copy(showFirebaseUploadConfirmDialog = true) }
        }
    }

    fun dismissFirebaseUploadConfirmDialog() {
        _uiState.update { it.copy(showFirebaseUploadConfirmDialog = false) }
    }

    fun confirmFirebaseUpload() {
        if (!_uiState.value.canSync) return
        _uiState.update { it.copy(showFirebaseUploadConfirmDialog = false) }
        uploadLocalDataToFirestore()
    }

    fun requestFirebaseFullMigration() {
        requireDangerousOps {
            if (!_uiState.value.isFirebaseSignedIn) {
                _uiState.update {
                    it.copy(snackbarMessage = "먼저 구글 로그인을 해 주세요.")
                }
                return@requireDangerousOps
            }
            if (_uiState.value.isFirebaseSyncing) return@requireDangerousOps
            _uiState.update { it.copy(showFirebaseFullMigrationConfirmDialog = true) }
        }
    }

    fun dismissFirebaseFullMigrationConfirmDialog() {
        _uiState.update { it.copy(showFirebaseFullMigrationConfirmDialog = false) }
    }

    fun confirmFirebaseFullMigration() {
        if (!_uiState.value.canDangerousOps) return
        _uiState.update { it.copy(showFirebaseFullMigrationConfirmDialog = false) }
        uploadFullMigrationToFirestore()
    }

    fun requestFirebaseDownload() {
        requireSyncAccess {
            if (!_uiState.value.isFirebaseSignedIn) {
                _uiState.update {
                    it.copy(snackbarMessage = "먼저 구글 로그인을 해 주세요.")
                }
                return@requireSyncAccess
            }
            if (_uiState.value.isFirebaseSyncing) return@requireSyncAccess
            _uiState.update { it.copy(showFirebaseDownloadConfirmDialog = true) }
        }
    }

    fun dismissFirebaseDownloadConfirmDialog() {
        _uiState.update { it.copy(showFirebaseDownloadConfirmDialog = false) }
    }

    fun confirmFirebaseDownload() {
        if (!_uiState.value.canSync) return
        _uiState.update { it.copy(showFirebaseDownloadConfirmDialog = false) }
        downloadCloudDataToLocal()
    }

    fun requestFirebaseSignOut() {
        if (!_uiState.value.isFirebaseSignedIn) return
        _uiState.update { it.copy(showFirebaseSignOutConfirmDialog = true) }
    }

    fun dismissFirebaseSignOutConfirmDialog() {
        _uiState.update { it.copy(showFirebaseSignOutConfirmDialog = false) }
    }

    fun confirmFirebaseSignOut() {
        _uiState.update { it.copy(showFirebaseSignOutConfirmDialog = false) }
        signOutFirebase()
    }

    fun requestDriveBackup() {
        requireDangerousOps {
            if (_uiState.value.cloudSyncState == CloudSyncState.Uploading ||
                _uiState.value.cloudSyncState == CloudSyncState.SigningIn
            ) {
                return@requireDangerousOps
            }
            _uiState.update { it.copy(showDriveBackupConfirmDialog = true) }
        }
    }

    fun dismissDriveBackupConfirmDialog() {
        _uiState.update { it.copy(showDriveBackupConfirmDialog = false) }
    }

    fun confirmDriveBackup() {
        if (!_uiState.value.canDangerousOps) return
        _uiState.update { it.copy(showDriveBackupConfirmDialog = false) }
        onCloudSyncStarted()
    }

    fun uploadLocalDataToFirestore() {
        if (!_uiState.value.isFirebaseSignedIn) {
            _uiState.update {
                it.copy(snackbarMessage = "먼저 구글 로그인을 해 주세요.")
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isFirebaseSyncing = true, cloudSyncError = null) }
            runCatching {
                cloudDataMigrationRepository.syncCurrentClubToFirestore(
                    clubName = _uiState.value.currentClubName.ifBlank { "내 모임" },
                    clubSlogan = _uiState.value.clubSlogan
                )
            }.onSuccess { migration ->
                val uploadedTotal = migration.memberCount + migration.transactionCount +
                    migration.duesCount + migration.historyCount + migration.accountCount +
                    migration.eventExpenseCount
                val cloudRealtime = cloudLedgerModeRepository.isEnabledNow()
                val snackbarMessage = when {
                    migration.syncMode == CloudSyncMode.INCREMENTAL && uploadedTotal == 0 && cloudRealtime ->
                        "올릴 로컬 변경분이 없습니다. 지금 실시간 동기화 중이라 장부·회비 편집은 이미 클라우드에 반영됩니다."
                    migration.syncMode == CloudSyncMode.INCREMENTAL && uploadedTotal == 0 ->
                        "마지막 올리기 이후 이 기기 로컬에서 바뀐 데이터가 없습니다."
                    else -> {
                        val modeLabel = when (migration.syncMode) {
                            CloudSyncMode.FULL -> "최초 전체 이행"
                            CloudSyncMode.INCREMENTAL -> "변경분 업로드"
                        }
                        "올리기 완료 ($modeLabel) · 회원 ${migration.memberCount}명, " +
                            "장부 ${migration.transactionCount}건, 회비 ${migration.duesCount}건, " +
                            "이력 ${migration.historyCount}건, 계좌 ${migration.accountCount}개 " +
                            "(이 기기 로컬 데이터는 그대로입니다)"
                    }
                }
                _uiState.update {
                    it.copy(
                        isFirebaseSyncing = false,
                        snackbarMessage = snackbarMessage
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isFirebaseSyncing = false,
                        cloudSyncError = error.toFirestoreSyncMessage()
                    )
                }
            }
        }
    }

    fun uploadFullMigrationToFirestore() {
        if (!_uiState.value.isFirebaseSignedIn) {
            _uiState.update {
                it.copy(snackbarMessage = "먼저 구글 로그인을 해 주세요.")
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isFirebaseSyncing = true, cloudSyncError = null) }
            runCatching {
                cloudDataMigrationRepository.replaceCurrentClubOnFirestore(
                    clubName = _uiState.value.currentClubName.ifBlank { "내 모임" },
                    clubSlogan = _uiState.value.clubSlogan
                )
            }.onSuccess { migration ->
                _uiState.update {
                    it.copy(
                        isFirebaseSyncing = false,
                        snackbarMessage = "초기 데이터 이행 완료 · 회원 ${migration.memberCount}명, " +
                            "장부 ${migration.transactionCount}건, 회비 ${migration.duesCount}건, " +
                            "이력 ${migration.historyCount}건, 계좌 ${migration.accountCount}개 " +
                            "(클라우드를 로컬 전체로 다시 채웠습니다)"
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isFirebaseSyncing = false,
                        cloudSyncError = error.toFirestoreSyncMessage()
                    )
                }
            }
        }
    }

    fun downloadCloudDataToLocal() {
        if (!_uiState.value.isFirebaseSignedIn) {
            _uiState.update {
                it.copy(snackbarMessage = "먼저 구글 로그인을 해 주세요.")
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isFirebaseSyncing = true, cloudSyncError = null) }
            runCatching {
                val result = cloudDataMigrationRepository.downloadCloudToCurrentClub(
                    preferredName = _uiState.value.currentClubName.ifBlank { "한우리" }
                )
                ledgerRefreshNotifier.notifyDuesChanged()
                ledgerRefreshNotifier.notifyTransactionChanged()
                result
            }.onSuccess { migration ->
                val activeHint = migration.memberCount.let { count ->
                    if (count > 0) " · 회원 ${count}명" else ""
                }
                val fmt = NumberFormat.getNumberInstance(Locale.KOREA)
                val balanceText = migration.calculatedBalance?.let { fmt.format(it) } ?: "?"
                _uiState.update {
                    it.copy(
                        isFirebaseSyncing = false,
                        snackbarMessage = "내려받기 완료$activeHint, " +
                            "거래 ${migration.transactionCount}건, 현재 잔액 ${balanceText}원"
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isFirebaseSyncing = false,
                        cloudSyncError = error.toFirestoreSyncMessage()
                    )
                }
            }
        }
    }

    fun signOutFirebase() {
        viewModelScope.launch {
            runCatching {
                cloudLedgerModeRepository.setEnabled(false)
                // 구글 로그아웃 후에도 앱 진입 게이트를 다시 열지 않음(로컬 모드 유지)
                dataStore.safeEdit(appContext) { prefs ->
                    prefs[UserPreferenceKeys.AUTH_GATE_COMPLETED] = true
                }
                firebaseAuthRepository.signOut()
            }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isFirebaseSignedIn = false,
                            firebaseUid = null,
                            firebaseEmail = null,
                            snackbarMessage = "구글 로그아웃되었습니다. 이 기기 로컬 데이터를 사용합니다."
                        )
                    }
                }
        }
    }

    fun openMeetingShareDialog() {
        if (!_uiState.value.isFirebaseSignedIn) {
            _uiState.update {
                it.copy(snackbarMessage = "모임 공유는 구글 로그인 후 사용할 수 있습니다.")
            }
            return
        }
        if (!_uiState.value.isMeetingOwner && !_uiState.value.isSystemAdmin && !_uiState.value.canEdit) {
            _uiState.update {
                it.copy(snackbarMessage = "모임 공유(초대)는 모임 관리자만 할 수 있습니다.")
            }
            return
        }
        _uiState.update {
            it.copy(
                showMeetingShareDialog = true,
                meetingShareInput = "",
                meetingShareError = null,
                isInvitingMember = false
            )
        }
    }

    fun dismissMeetingShareDialog() {
        if (_uiState.value.isInvitingMember) return
        _uiState.update {
            it.copy(
                showMeetingShareDialog = false,
                meetingShareInput = "",
                meetingShareError = null
            )
        }
    }

    fun updateMeetingShareInput(value: String) {
        _uiState.update {
            it.copy(meetingShareInput = value.trimStart().take(120), meetingShareError = null)
        }
    }

    fun submitMeetingShareInvite() {
        if (_uiState.value.isInvitingMember) return
        if (!_uiState.value.isFirebaseSignedIn) {
            _uiState.update {
                it.copy(meetingShareError = "구글 로그인이 필요합니다.")
            }
            return
        }

        viewModelScope.launch {
            val raw = _uiState.value.meetingShareInput.trim()
            if (raw.isBlank()) {
                _uiState.update { it.copy(meetingShareError = "이메일 또는 UID를 입력해 주세요.") }
                return@launch
            }
            _uiState.update { it.copy(isInvitingMember = true, meetingShareError = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    val meetingId = runCatching {
                        selectedMeetingRepository.requireSelectedMeetingId()
                    }.getOrElse {
                        selectedMeetingRepository.ensureDefaultMeeting(
                            _uiState.value.currentClubName.ifBlank { "한우리" }
                        )
                    }
                    meetingFirestoreRepository.inviteByEmailOrUid(meetingId, raw)
                }
            }.onSuccess { result ->
                val message = when (result) {
                    is MeetingInviteResult.Joined ->
                        "초대를 완료했습니다. 상대 계정 「내 모임」에 바로 표시됩니다. (UID: ${result.uid.take(8)}…)"
                    is MeetingInviteResult.PendingEmail ->
                        "「${result.email}」 명단 회원에게 초대를 예약했습니다. 같은 이메일로 로그인하면 모임이 표시됩니다."
                }
                _uiState.update {
                    it.copy(
                        isInvitingMember = false,
                        showMeetingShareDialog = false,
                        meetingShareInput = "",
                        meetingShareError = null,
                        snackbarMessage = message
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isInvitingMember = false,
                        meetingShareError = error.message
                            ?: "초대에 실패했습니다. 잠시 후 다시 시도해 주세요."
                    )
                }
            }
        }
    }


    fun openDesignateTreasurerDialog() {
        if (!_uiState.value.isMeetingOwner && !_uiState.value.isSystemAdmin) {
            _uiState.update {
                it.copy(snackbarMessage = "운영관리자 지정은 모임을 만든 관리자만 할 수 있습니다.")
            }
            return
        }
        if (!_uiState.value.isFirebaseSignedIn) {
            _uiState.update {
                it.copy(snackbarMessage = "운영관리자 지정은 구글 로그인 후 사용할 수 있습니다.")
            }
            return
        }
        _uiState.update {
            it.copy(
                showDesignateTreasurerDialog = true,
                designateTreasurerInput = "",
                designateTreasurerError = null,
                isDesignatingTreasurer = false
            )
        }
    }

    fun dismissDesignateTreasurerDialog() {
        if (_uiState.value.isDesignatingTreasurer) return
        _uiState.update {
            it.copy(
                showDesignateTreasurerDialog = false,
                designateTreasurerInput = "",
                designateTreasurerError = null
            )
        }
    }

    fun updateDesignateTreasurerInput(value: String) {
        _uiState.update {
            it.copy(designateTreasurerInput = value.trimStart().take(120), designateTreasurerError = null)
        }
    }

    fun submitDesignateTreasurer() {
        if (_uiState.value.isDesignatingTreasurer) return
        if (!_uiState.value.isMeetingOwner && !_uiState.value.isSystemAdmin) {
            _uiState.update {
                it.copy(designateTreasurerError = "모임을 만든 관리자만 지정할 수 있습니다.")
            }
            return
        }
        viewModelScope.launch {
            val raw = _uiState.value.designateTreasurerInput.trim()
            if (raw.isBlank()) {
                _uiState.update { it.copy(designateTreasurerError = "이메일 또는 UID를 입력해 주세요.") }
                return@launch
            }
            _uiState.update { it.copy(isDesignatingTreasurer = true, designateTreasurerError = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    val meetingId = runCatching {
                        selectedMeetingRepository.requireSelectedMeetingId()
                    }.getOrElse {
                        selectedMeetingRepository.ensureDefaultMeeting(
                            _uiState.value.currentClubName.ifBlank { "한우리" }
                        )
                    }
                    val treasurerUid = if (raw.contains('@')) {
                        userProfileFirestoreRepository.findUidByEmail(raw)
                            ?: throw IllegalStateException(
                                "해당 이메일의 사용자를 찾지 못했습니다. 상대방이 앱에 한 번 로그인한 뒤 다시 지정해 주세요."
                            )
                    } else {
                        raw
                    }
                    meetingFirestoreRepository.setDesignatedTreasurer(meetingId, treasurerUid)
                    treasurerUid
                }
            }.onSuccess { treasurerUid ->
                _uiState.update {
                    it.copy(
                        isDesignatingTreasurer = false,
                        showDesignateTreasurerDialog = false,
                        designateTreasurerInput = "",
                        designateTreasurerError = null,
                        designatedTreasurerUid = treasurerUid,
                        snackbarMessage = "운영관리자를 설정했습니다. (UID: ${treasurerUid.take(8)}…)"
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isDesignatingTreasurer = false,
                        designateTreasurerError = error.message
                            ?: "운영관리자 지정에 실패했습니다. 잠시 후 다시 시도해 주세요."
                    )
                }
            }
        }
    }

    fun clearDesignatedTreasurer() {
        if (!_uiState.value.isMeetingOwner && !_uiState.value.isSystemAdmin) {
            _uiState.update {
                it.copy(snackbarMessage = "운영관리자 지정 해제는 모임을 만든 관리자만 할 수 있습니다.")
            }
            return
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val meetingId = selectedMeetingRepository.requireSelectedMeetingId()
                    meetingFirestoreRepository.clearDesignatedTreasurer(meetingId)
                }
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        designatedTreasurerUid = null,
                        snackbarMessage = "운영관리자 지정을 해제했습니다."
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        snackbarMessage = error.message ?: "운영관리자 지정 해제에 실패했습니다."
                    )
                }
            }
        }
    }

    /** 계정 선택 화면에서 뒤로가기 등으로 로그인을 취소한 경우 */
    fun onCloudSyncCancelled() {
        _uiState.update {
            it.copy(
                cloudSyncState = CloudSyncState.Idle,
                cloudSyncError = null
            )
        }
    }

    fun handleGoogleSignInResult(data: Intent?) {
        viewModelScope.launch {
            runCatching {
                val account = GoogleSignIn.getSignedInAccountFromIntent(data)
                    .getResult(ApiException::class.java)
                performCloudSync(account)
            }.onFailure { error ->
                if (isGoogleSignInCancelled(error)) {
                    onCloudSyncCancelled()
                    return@onFailure
                }
                _uiState.update {
                    it.copy(
                        cloudSyncState = CloudSyncState.Error,
                        cloudSyncError = error.toCloudSyncMessage()
                    )
                }
            }
        }
    }

    private fun isGoogleSignInCancelled(error: Throwable): Boolean {
        val apiException = error as? ApiException
            ?: error.cause as? ApiException
            ?: return false
        return when (apiException.statusCode) {
            GoogleSignInStatusCodes.SIGN_IN_CANCELLED, // 12501 — 뒤로가기 등으로 취소
            CommonStatusCodes.CANCELED -> true
            else -> false
        }
    }

    private suspend fun performCloudSync(
        account: GoogleSignInAccount
    ) {
        _uiState.update { it.copy(cloudSyncState = CloudSyncState.Uploading, cloudSyncError = null) }
        withContext(Dispatchers.IO) {
            val backupFile = backupRestoreManager.createLocalBackupFile()
            googleDriveSyncManager.uploadBackupFileToDrive(backupFile, account)

            val pdfFile = pdfExportManager.exportSettlementReport(_uiState.value.exportYear)
            googleDriveSyncManager.uploadBackupFileToDrive(pdfFile, account)
        }
        _uiState.update {
            it.copy(
                cloudSyncState = CloudSyncState.Success,
                snackbarMessage = "구글 드라이브에 안전하게 동기화되었습니다."
            )
        }
    }

    fun dismissCloudSyncError() {
        _uiState.update {
            it.copy(
                cloudSyncState = CloudSyncState.Idle,
                cloudSyncError = null
            )
        }
    }

    fun resetCloudSyncState() {
        _uiState.update { it.copy(cloudSyncState = CloudSyncState.Idle) }
    }

    private fun Throwable.toCloudSyncMessage(): String {
        val detail = message?.takeIf { it.isNotBlank() }
        return if (detail != null) {
            "클라우드 동기화 중 오류가 발생했습니다.\n$detail"
        } else {
            "클라우드 동기화 중 알 수 없는 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."
        }
    }

    private fun Throwable.toFirebaseAuthMessage(): String {
        val apiException = this as? ApiException ?: cause as? ApiException
        val code = apiException?.statusCode
        return when (code) {
            GoogleSignInStatusCodes.SIGN_IN_FAILED,
            CommonStatusCodes.DEVELOPER_ERROR,
            10 -> // DEVELOPER_ERROR — SHA-1 / OAuth 클라이언트 설정 문제
                "구글 로그인 설정 오류입니다.\nFirebase에 SHA-1이 등록됐는지, " +
                    "google-services.json을 최신으로 받았는지 확인해 주세요."
            GoogleSignInStatusCodes.NETWORK_ERROR,
            CommonStatusCodes.NETWORK_ERROR ->
                "네트워크 오류로 로그인하지 못했습니다. 인터넷 연결 후 다시 시도해 주세요."
            else -> message?.takeIf { it.isNotBlank() }
                ?: "Firebase 로그인/동기화에 실패했습니다. (code=$code)"
        }
    }

    private fun Throwable.toFirestoreSyncMessage(): String {
        val text = buildString {
            append(message.orEmpty())
            cause?.message?.let { append(' ').append(it) }
        }
        if (text.contains("PERMISSION_DENIED", ignoreCase = true) ||
            text.contains("Missing or insufficient permissions", ignoreCase = true)
        ) {
            return "Firestore 권한 오류입니다.\n" +
                "Firebase 콘솔 → Firestore Database → 규칙 탭에 프로젝트의 firestore.rules를 " +
                "게시(게시)한 뒤 다시 「클라우드에 올리기」를 실행해 주세요."
        }
        return toFirebaseAuthMessage()
    }

    fun onBackupRequested(destinationUri: Uri) {
        if (_uiState.value.backupRestoreState == BackupRestoreState.Loading) return
        if (!_uiState.value.canDangerousOps) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    backupRestoreState = BackupRestoreState.Loading,
                    backupRestoreError = null
                )
            }
            runCatching {
                backupRestoreManager.backupDatabase(destinationUri)
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        backupRestoreState = BackupRestoreState.Success,
                        snackbarMessage = "데이터 백업이 완료되었습니다."
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        backupRestoreState = BackupRestoreState.Error,
                        backupRestoreError = error.toUserFriendlyMessage("백업")
                    )
                }
            }
        }
    }

    fun onRestoreFileSelected(sourceUri: Uri) {
        requireDangerousOps {
            _uiState.update {
                it.copy(
                    pendingRestoreUri = sourceUri.toString(),
                    showRestoreConfirmDialog = true
                )
            }
        }
    }

    fun dismissRestoreConfirmDialog() {
        _uiState.update {
            it.copy(
                showRestoreConfirmDialog = false,
                pendingRestoreUri = null
            )
        }
    }

    fun confirmRestore() {
        val uriString = _uiState.value.pendingRestoreUri ?: return
        if (_uiState.value.backupRestoreState == BackupRestoreState.Loading) return
        if (!_uiState.value.canDangerousOps) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    showRestoreConfirmDialog = false,
                    backupRestoreState = BackupRestoreState.Loading,
                    backupRestoreError = null
                )
            }
            runCatching {
                backupRestoreManager.restoreDatabase(Uri.parse(uriString))
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        backupRestoreState = BackupRestoreState.Success,
                        pendingRestoreUri = null
                    )
                }
                restartApp()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        backupRestoreState = BackupRestoreState.Error,
                        pendingRestoreUri = null,
                        backupRestoreError = error.toUserFriendlyMessage("복원")
                    )
                }
            }
        }
    }

    fun restartApp() {
        backupRestoreManager.restartApp()
    }

    fun dismissBackupRestoreError() {
        _uiState.update {
            it.copy(
                backupRestoreState = BackupRestoreState.Idle,
                backupRestoreError = null
            )
        }
    }

    fun resetBackupRestoreState() {
        _uiState.update { it.copy(backupRestoreState = BackupRestoreState.Idle) }
    }

    fun requestResetToDefaultSeedData() {
        if (_uiState.value.isResettingSeedData) return
        requireDangerousOps {
            _uiState.update { it.copy(showResetSeedConfirmDialog = true, seedResetError = null) }
        }
    }

    fun dismissResetSeedConfirmDialog() {
        _uiState.update { it.copy(showResetSeedConfirmDialog = false) }
    }

    fun confirmResetToDefaultSeedData() {
        if (_uiState.value.isResettingSeedData) return
        if (!_uiState.value.canDangerousOps) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    showResetSeedConfirmDialog = false,
                    isResettingSeedData = true,
                    seedResetError = null
                )
            }
            resetToDefaultSeedData()
        }
    }

    private suspend fun resetToDefaultSeedData() {
        runCatching {
            seedDataRepository.resetToDefaultSeedData()
            if (firebaseAuthRepository.currentUser() != null) {
                cloudDataMigrationRepository.replaceCurrentClubOnFirestore(
                    clubName = _uiState.value.currentClubName.ifBlank { "한우리" },
                    clubSlogan = _uiState.value.clubSlogan
                )
            } else {
                null
            }
        }.onSuccess { migration ->
            authSessionManager.clearAuthSession()
            val cloudMsg = migration?.let {
                " (Firestore 회원 ${it.memberCount}명 · 장부 ${it.transactionCount}건 반영)"
            }.orEmpty()
            _uiState.update {
                it.copy(
                    isResettingSeedData = false,
                    snackbarMessage = "현재 모임의 초기 데이터로 안전하게 복원되었습니다.$cloudMsg"
                )
            }
            restartApp()
        }.onFailure { error ->
            _uiState.update {
                it.copy(
                    isResettingSeedData = false,
                    seedResetError = error.toUserFriendlyMessage("초기 데이터 재설정")
                )
            }
        }
    }

    fun dismissSeedResetError() {
        _uiState.update { it.copy(seedResetError = null) }
    }

    fun requestDissolveCurrentClub() {
        if (_uiState.value.isClearingClubData) return
        if (!_uiState.value.isMeetingOwner && !_uiState.value.isSystemAdmin) {
            _uiState.update { it.copy(snackbarMessage = "모임을 만든 관리자만 사용할 수 있습니다.") }
            return
        }
        _uiState.update { it.copy(showClearClubDataConfirmDialog = true, clearClubDataError = null) }
    }

    fun dismissClearClubDataConfirmDialog() {
        _uiState.update { it.copy(showClearClubDataConfirmDialog = false) }
    }

    fun confirmDissolveCurrentClub() {
        if (_uiState.value.isClearingClubData) return
        if (!_uiState.value.isMeetingOwner && !_uiState.value.isSystemAdmin) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    showClearClubDataConfirmDialog = false,
                    isClearingClubData = true,
                    clearClubDataError = null
                )
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    val clubId = selectedClubRepository.requireSelectedClubId()
                    val club = clubRepository.getClub(clubId)
                        ?: throw IllegalStateException("삭제할 모임을 찾을 수 없습니다.")
                    val cloudSignedIn = firebaseAuthRepository.currentUser() != null
                    val meetingId = club.firestoreMeetingId.takeIf { it.isNotBlank() }
                        ?: selectedMeetingRepository.selectedMeetingId.first()
                            ?.takeIf { it.isNotBlank() }
                    var cloudDeleted = false
                    if (cloudSignedIn && !meetingId.isNullOrBlank()) {
                        meetingFirestoreRepository.deleteMeetingWithContents(meetingId)
                        selectedMeetingRepository.setSelectedMeetingId(null)
                        cloudDeleted = true
                    }
                    val result = clubRepository.dissolveClub(clubId)
                    result to cloudDeleted
                }
            }.onSuccess { (result, cloudDeleted) ->
                authSessionManager.clearAuthSession()
                _uiState.update {
                    it.copy(
                        isClearingClubData = false,
                        snackbarMessage = if (cloudDeleted) {
                            "「${result.dissolvedClubName}」 모임이 로컬·클라우드에서 삭제되었습니다."
                        } else {
                            "「${result.dissolvedClubName}」 모임이 삭제되었습니다."
                        }
                    )
                }
                clubListNavigationEvents.send(Unit)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isClearingClubData = false,
                        clearClubDataError = error.toUserFriendlyMessage("모임 삭제")
                    )
                }
            }
        }
    }

    fun dismissClearClubDataError() {
        _uiState.update { it.copy(clearClubDataError = null) }
    }

    private fun Throwable.toUserFriendlyMessage(operation: String): String {
        val detail = message?.takeIf { it.isNotBlank() }
        return if (detail != null) {
            "$operation 중 오류가 발생했습니다.\n$detail"
        } else {
            "$operation 중 알 수 없는 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."
        }
    }

    private fun userFriendlyExcelImportError(error: Throwable): String {
        val text = buildString {
            append(error.javaClass.name)
            error.message?.let { append(' ').append(it) }
            error.cause?.let { cause ->
                append(' ').append(cause.javaClass.name)
                cause.message?.let { append(' ').append(it) }
            }
        }
        return when {
            text.contains("CellFormat", ignoreCase = true) ||
                text.contains("DataFormatter", ignoreCase = true) ->
                "엑셀 셀 형식을 읽는 중 오류가 발생했습니다. 앱을 최신으로 빌드한 뒤 같은 파일로 다시 복원해 주세요."
            text.contains("ZipException", ignoreCase = true) ||
                text.contains("OLE2", ignoreCase = true) ||
                text.contains("OOXML", ignoreCase = true) ->
                "엑셀 파일 형식이 올바르지 않습니다. .xlsx 파일인지 확인해 주세요."
            else ->
                error.message?.takeIf {
                    it.isNotBlank() && !it.contains("org.apache.poi", ignoreCase = true)
                } ?: "복원 실패: 엑셀 파일을 읽을 수 없습니다."
        }
    }

    fun consumeSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    /** 위험 작업(백업·초기화 등)은 시스템관리자만 실행합니다. */
    fun requireDangerousOps(onGranted: () -> Unit) {
        if (_uiState.value.canDangerousOps) {
            onGranted()
            return
        }
        _uiState.update { it.copy(snackbarMessage = "시스템관리자만 사용할 수 있습니다.") }
    }

    /** 클라우드 올리기/내리기는 모임관리자(개설자)·지정 운영관리자·시스템관리자. */
    fun requireSyncAccess(onGranted: () -> Unit) {
        if (_uiState.value.canSync) {
            onGranted()
            return
        }
        _uiState.update { it.copy(snackbarMessage = "모임관리자 또는 시스템관리자만 사용할 수 있습니다.") }
    }

    /** 운영관리자 지정·모임 삭제는 개설자 또는 시스템관리자. */
    fun requireMeetingOwnerOps(onGranted: () -> Unit) {
        if (_uiState.value.isMeetingOwner || _uiState.value.isSystemAdmin) {
            onGranted()
            return
        }
        _uiState.update { it.copy(snackbarMessage = "모임을 만든 관리자만 사용할 수 있습니다.") }
    }

    companion object {
        private const val CLUB_SLOGAN_MAX_LENGTH = 80
    }
}
