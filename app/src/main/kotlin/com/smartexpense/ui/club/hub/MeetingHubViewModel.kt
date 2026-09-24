package com.smartexpense.ui.club.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smartexpense.data.firebase.MemberAccessDecision
import com.smartexpense.data.firebase.MemberLoginPolicy
import com.smartexpense.data.firebase.PrivilegedAuthConfig
import com.smartexpense.data.firebase.SystemAdminConfig
import com.smartexpense.data.firebase.auth.FirebaseAuthRepository
import com.smartexpense.data.firebase.auth.FirebaseAuthSession
import com.smartexpense.data.firebase.auth.toEmailAuthErrorMessage
import com.smartexpense.data.firebase.firestore.JoinRequestDoc
import com.smartexpense.data.firebase.firestore.JoinRequestFirestoreRepository
import com.smartexpense.data.firebase.firestore.JoinRequestStatus
import com.smartexpense.data.firebase.firestore.MeetingDoc
import com.smartexpense.data.firebase.firestore.MeetingFirestoreRepository
import com.smartexpense.data.firebase.firestore.MemberFirestoreRepository
import com.smartexpense.data.firebase.firestore.UserProfileFirestoreRepository
import com.smartexpense.data.local.entity.club.ClubMembershipStatus
import com.smartexpense.data.local.entity.club.MemberRole
import com.smartexpense.data.local.entity.club.MemberStatus
import com.smartexpense.data.repository.club.MeetingClubSyncRepository
import com.smartexpense.data.repository.club.MeetingMemberEnrollmentRepository
import com.smartexpense.data.session.UserSessionManager
import com.smartexpense.data.repository.security.AppLockRepository
import com.smartexpense.domain.security.AppLockUnlockMethod
import com.smartexpense.domain.user.UserRole
import com.smartexpense.security.BiometricAuthManager
import com.smartexpense.ui.club.member.MemberFormState
import com.smartexpense.ui.club.toEntity
import com.smartexpense.ui.security.AppLockViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@HiltViewModel
class MeetingHubViewModel @Inject constructor(
    private val authRepository: FirebaseAuthRepository,
    private val meetingFirestoreRepository: MeetingFirestoreRepository,
    private val memberFirestoreRepository: MemberFirestoreRepository,
    private val joinRequestRepository: JoinRequestFirestoreRepository,
    private val userProfileFirestoreRepository: UserProfileFirestoreRepository,
    private val meetingClubSyncRepository: MeetingClubSyncRepository,
    private val meetingMemberEnrollmentRepository: MeetingMemberEnrollmentRepository,
    private val memberLoginPolicy: MemberLoginPolicy,
    private val userSessionManager: UserSessionManager,
    private val appLockRepository: AppLockRepository,
    private val biometricAuthManager: BiometricAuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(MeetingHubUiState())
    val uiState: StateFlow<MeetingHubUiState> = _uiState.asStateFlow()

    private var allMeetings: List<MeetingDoc> = emptyList()
    private var joinedMeetings: List<MeetingDoc> = emptyList()
    private var restrictedMeetings: List<MeetingDoc> = emptyList()
    private var myJoinRequests: Map<String, JoinRequestDoc> = emptyMap()
    private var myMemberStatuses: Map<String, MemberStatus> = emptyMap()
    private var selfEnrolledMeetingIds: Set<String> = emptySet()
    private var locallyCompletedProfileIds: Set<String> = emptySet()
    private var pendingCountByMeeting: Map<String, Int> = emptyMap()
    private var profilePromptSeq = 0
    private var lastProfileUid: String? = null
    private var lastManagedMeetingKey: String? = null
    private var lastJoinRefreshKey: String? = null

    init {
        viewModelScope.launch {
            delay(8_000)
            if (_uiState.value.isLoading) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
        viewModelScope.launch {
            combine(
                meetingFirestoreRepository.observeMeetingDirectory().catch { emit(emptyList()) },
                meetingFirestoreRepository.observeJoinedMeetings().catch { emit(emptyList()) },
                joinRequestRepository.observeMyJoinRequests().catch { emit(emptyList()) },
                authRepository.authState.catch { emit(null) },
                meetingFirestoreRepository.observeInactiveMeetings().catch { emit(emptyList()) }
            ) { directory, joined, requests, session, restricted ->
                HubSnapshot(directory, joined, requests, session, restricted)
            }.distinctUntilChanged().collect { snapshot ->
                if (snapshot.directory.isNotEmpty()) {
                    allMeetings = mergeSearchDirectory(allMeetings, snapshot.directory)
                }
                if (snapshot.session == null) {
                    joinedMeetings = emptyList()
                } else if (snapshot.joined.isNotEmpty()) {
                    joinedMeetings = mergeJoinedKeepProfile(snapshot.joined)
                }
                restrictedMeetings = snapshot.restricted
                if (snapshot.session == null) {
                    myJoinRequests = emptyMap()
                    locallyCompletedProfileIds = emptySet()
                    selfEnrolledMeetingIds = emptySet()
                } else if (snapshot.requests.isNotEmpty()) {
                    myJoinRequests = mergeJoinRequests(
                        snapshot.requests
                            .groupBy({ it.first }, { it.second })
                            .mapValues { (_, docs) -> pickPreferredJoinRequest(docs) }
                    )
                }
                val elevated = isElevatedAccount(snapshot.session?.uid, snapshot.session?.email)
                val role = UserRole.resolve(
                    uid = snapshot.session?.uid,
                    email = snapshot.session?.email,
                    meeting = null
                )
                _uiState.update {
                    it.copy(
                        isElevated = elevated,
                        userRole = role,
                        settings = it.settings.copy(
                            email = snapshot.session?.email.orEmpty(),
                            userRole = role
                        )
                    )
                }
                val uid = snapshot.session?.uid
                if (uid != lastProfileUid) {
                    lastProfileUid = uid
                    loadProfile()
                }
                refreshMyMemberStatuses()
                rebuildLists()
                _uiState.update { it.copy(isLoading = false) }
                val joinRefreshKey = (uid.orEmpty() + "|" + snapshot.joined.map { it.id }.sorted().joinToString())
                if (snapshot.session != null && joinRefreshKey != lastJoinRefreshKey) {
                    lastJoinRefreshKey = joinRefreshKey
                    viewModelScope.launch {
                        val fetched = runCatching {
                            joinRequestRepository.getMyJoinRequestsOnce(
                                meetingIds = (
                                    snapshot.joined.map { it.id } +
                                        myJoinRequests.keys
                                    ).distinct(),
                                fromServer = true
                            )
                        }.getOrDefault(emptyMap())
                        if (fetched.isNotEmpty()) {
                            myJoinRequests = mergeJoinRequests(fetched)
                            refreshMyMemberStatuses()
                            rebuildLists()
                        }
                    }
                }
                val managedKey = managedMeetingKey(snapshot.session)
                if (managedKey != lastManagedMeetingKey) {
                    lastManagedMeetingKey = managedKey
                    refreshPendingApprovals()
                }
            }
        }
    }

    fun selectTab(tab: MeetingHubTab) {
        _uiState.update { it.copy(selectedTab = tab, dialogErrorMessage = null) }
    }

    fun refreshHub(userInitiated: Boolean = true) {
        if (_uiState.value.isRefreshing && !userInitiated) return
        viewModelScope.launch {
            refreshHubInternal(userInitiated)
        }
    }

    private suspend fun refreshHubInternal(userInitiated: Boolean) {
        if (userInitiated) {
            _uiState.update { it.copy(isRefreshing = true) }
        }
        try {
            val session = authRepository.currentSession()
            val elevated = isElevatedAccount(session?.uid, session?.email)
            val joined = runCatching {
                meetingFirestoreRepository.getMeetingsOnce(fromServer = userInitiated)
            }.getOrDefault(joinedMeetings)
            joinedMeetings = joined
            val currentUid = session?.uid
            restrictedMeetings = if (currentUid.isNullOrBlank() || elevated) {
                emptyList()
            } else {
                runCatching {
                    meetingFirestoreRepository.getMeetingsByInactiveMember(currentUid)
                }.getOrDefault(restrictedMeetings)
            }

            if (userInitiated) {
                val directory = runCatching {
                    meetingFirestoreRepository.getSearchableMeetingsOnce()
                }.getOrDefault(emptyList())
                runCatching { meetingFirestoreRepository.pruneOrphanDirectoryEntries() }
                val directoryAfterPrune = runCatching {
                    meetingFirestoreRepository.getSearchableMeetingsOnce()
                }.getOrDefault(directory)
                val allFromMeetings = runCatching {
                    meetingFirestoreRepository.getAllMeetingsOnce()
                }.getOrDefault(emptyList())
                val all = when {
                    directoryAfterPrune.isNotEmpty() -> {
                        val byId = allFromMeetings.associateBy { it.id }
                        directoryAfterPrune.map { dir -> byId[dir.id] ?: dir }
                            .sortedBy { it.name }
                    }
                    else -> emptyList()
                }
                if (elevated && allFromMeetings.isNotEmpty()) {
                    runCatching { meetingFirestoreRepository.syncMeetingDirectoryFromMeetings() }
                } else {
                    joined.filter { it.ownerUid == session?.uid }.forEach { owned ->
                        runCatching { meetingFirestoreRepository.publishMeetingDirectory(owned) }
                    }
                }
                if (all.isNotEmpty() || allMeetings.isEmpty()) {
                    allMeetings = all
                }
            } else if (allMeetings.isEmpty()) {
                val directory = runCatching {
                    meetingFirestoreRepository.getSearchableMeetingsOnce()
                }.getOrDefault(emptyList())
                if (directory.isNotEmpty()) {
                    allMeetings = directory
                }
            }

            val uid = session?.uid
            if (!uid.isNullOrBlank()) {
                runCatching {
                    meetingFirestoreRepository.claimPendingEmailInvites(uid, session.email)
                }
                val meetingIds = (
                    allMeetings.map { it.id } +
                        joined.map { it.id } +
                        myJoinRequests.keys
                    ).distinct()
                val fetched = runCatching {
                    joinRequestRepository.getMyJoinRequestsOnce(
                        meetingIds = meetingIds,
                        fromServer = true
                    )
                }.getOrNull()
                if (fetched != null) {
                    myJoinRequests = if (fetched.isEmpty()) {
                        myJoinRequests.filterKeys { it !in meetingIds }
                    } else {
                        mergeJoinRequests(fetched)
                    }
                }
            }
            refreshMyMemberStatuses()
            refreshPendingApprovals()
            rebuildLists()
            if (userInitiated) {
                val message = if (allMeetings.isEmpty()) {
                    "검색할 모임이 없습니다. 모임을 만들거나, 다른 사람이 만든 뒤 다시 새로고침해 주세요."
                } else {
                    "승인 상태와 모임 목록을 새로고침했습니다. (검색 ${allMeetings.size}개)"
                }
                showSnackbar(message)
            }
        } catch (error: Throwable) {
            val detail = error.message.orEmpty()
            val hint = if (detail.contains("PERMISSION", ignoreCase = true) ||
                detail.contains("permission", ignoreCase = true)
            ) {
                " Firestore 규칙(meetings list) 배포 여부를 확인해 주세요."
            } else {
                ""
            }
            showSnackbar((error.message ?: "모임 목록을 새로고침하지 못했습니다.") + hint)
        } finally {
            _uiState.update { it.copy(isRefreshing = false, isLoading = false) }
        }
    }

    /** 디렉터리 항목을 검색 목록에 반영. sharedWith 등 더 풍부한 기존 문서를 유지합니다. */
    private fun mergeSearchDirectory(
        current: List<MeetingDoc>,
        directory: List<MeetingDoc>
    ): List<MeetingDoc> {
        if (directory.isEmpty()) return current
        val existingById = current.associateBy { it.id }
        return directory.map { dir ->
            val existing = existingById[dir.id]
            when {
                existing == null -> dir
                existing.sharedWith.isNotEmpty() && dir.sharedWith.isEmpty() -> existing
                else -> dir.copy(
                    sharedWith = existing.sharedWith.ifEmpty { dir.sharedWith }
                )
            }
        }.sortedBy { it.name }
    }

    fun updateSearchQuery(value: String) {
        _uiState.update { it.copy(searchQuery = value) }
        rebuildLists()
    }

    fun consumeSnackbar() = _uiState.update { it.copy(snackbarMessage = null) }

    // region Settings

    fun openSettingsSheet() {
        loadProfile()
        loadSecuritySettings()
        updateSettings {
            it.copy(
                visible = true,
                feedbackMessage = null,
                errorMessage = null,
                currentPassword = "",
                newPassword = "",
                confirmPassword = ""
            )
        }
    }

    private fun currentLoginEmail(): String? {
        val fromSession = authRepository.currentSession()?.email?.trim()?.lowercase()
        if (!fromSession.isNullOrBlank()) return fromSession
        return _uiState.value.settings.email.trim().lowercase().takeIf { it.isNotBlank() }
    }

    private fun loadSecuritySettings() {
        viewModelScope.launch {
            appLockRepository.clearPendingUnlockSetup()
            val email = currentLoginEmail()
            val unlockMethod = if (email != null) {
                appLockRepository.getUnlockMethodForEmail(email)
            } else {
                AppLockUnlockMethod.resolve(
                    appLockRepository.storedUnlockMethod.first()?.storageValue
                )
            }
            val isBiometricEnabled = appLockRepository.isBiometricEnabled.first()
            val canUseBiometric = biometricAuthManager.canAuthenticateWithBiometric()
            val hasPin = email != null && appLockRepository.hasPinConfiguredOnce(email)
            val hasPattern = email != null && appLockRepository.hasPatternConfiguredOnce(email)
            updateSettings {
                it.copy(
                    unlockMethod = unlockMethod,
                    isBiometricEnabled = isBiometricEnabled,
                    canUseBiometric = canUseBiometric,
                    hasPinRegistered = hasPin,
                    hasPatternRegistered = hasPattern
                )
            }
        }
    }

    fun openUnlockMethodDialog() {
        updateSettings { it.copy(showUnlockMethodDialog = true) }
    }

    fun dismissUnlockMethodDialog() {
        updateSettings { it.copy(showUnlockMethodDialog = false) }
    }

    fun setUnlockMethod(method: AppLockUnlockMethod) {
        viewModelScope.launch {
            appLockRepository.clearPendingUnlockSetup()
            val email = currentLoginEmail()
            if (email.isNullOrBlank()) {
                updateSettings {
                    it.copy(
                        showUnlockMethodDialog = false,
                        feedbackMessage = "로그인된 계정이 없어 PIN/패턴을 저장할 수 없습니다."
                    )
                }
                return@launch
            }
            val hasCredential = when (method) {
                AppLockUnlockMethod.PIN -> appLockRepository.hasPinConfiguredOnce(email)
                AppLockUnlockMethod.PATTERN -> appLockRepository.hasPatternConfiguredOnce(email)
            }
            // 등록·변경 모두 입력 다이얼로그로 — 이미 있으면 덮어쓰기(변경)
            when (method) {
                AppLockUnlockMethod.PIN -> updateSettings {
                    it.copy(
                        showUnlockMethodDialog = false,
                        showPinSetupDialog = true,
                        isChangingCredential = hasCredential,
                        pinInput = "",
                        pinConfirmInput = "",
                        pinSetupError = null
                    )
                }
                AppLockUnlockMethod.PATTERN -> updateSettings {
                    it.copy(
                        showUnlockMethodDialog = false,
                        showPatternSetupDialog = true,
                        isChangingCredential = hasCredential,
                        patternInput = emptyList(),
                        patternConfirmInput = emptyList(),
                        patternSetupError = null
                    )
                }
            }
        }
    }

    /** 현재 방식은 유지한 채 PIN만 다시 등록 */
    fun openChangePin() = openCredentialSetup(AppLockUnlockMethod.PIN)

    /** 현재 방식은 유지한 채 패턴만 다시 등록 */
    fun openChangePattern() = openCredentialSetup(AppLockUnlockMethod.PATTERN)

    private fun openCredentialSetup(method: AppLockUnlockMethod) {
        viewModelScope.launch {
            val email = currentLoginEmail()
            if (email.isNullOrBlank()) {
                updateSettings {
                    it.copy(feedbackMessage = "로그인된 계정이 없어 변경할 수 없습니다.")
                }
                return@launch
            }
            val hasCredential = when (method) {
                AppLockUnlockMethod.PIN -> appLockRepository.hasPinConfiguredOnce(email)
                AppLockUnlockMethod.PATTERN -> appLockRepository.hasPatternConfiguredOnce(email)
            }
            when (method) {
                AppLockUnlockMethod.PIN -> updateSettings {
                    it.copy(
                        showPinSetupDialog = true,
                        isChangingCredential = hasCredential,
                        pinInput = "",
                        pinConfirmInput = "",
                        pinSetupError = null
                    )
                }
                AppLockUnlockMethod.PATTERN -> updateSettings {
                    it.copy(
                        showPatternSetupDialog = true,
                        isChangingCredential = hasCredential,
                        patternInput = emptyList(),
                        patternConfirmInput = emptyList(),
                        patternSetupError = null
                    )
                }
            }
        }
    }

    fun updatePinSetupInput(value: String) {
        updateSettings {
            it.copy(
                pinInput = value.filter { ch -> ch.isDigit() }.take(AppLockViewModel.PIN_LENGTH),
                pinSetupError = null
            )
        }
    }

    fun updatePinSetupConfirmInput(value: String) {
        updateSettings {
            it.copy(
                pinConfirmInput = value.filter { ch -> ch.isDigit() }.take(AppLockViewModel.PIN_LENGTH),
                pinSetupError = null
            )
        }
    }

    fun dismissPinSetupDialog() {
        updateSettings {
            it.copy(
                showPinSetupDialog = false,
                isChangingCredential = false,
                pinInput = "",
                pinConfirmInput = "",
                pinSetupError = null
            )
        }
    }

    fun submitPinSetup() {
        val settings = _uiState.value.settings
        val len = AppLockViewModel.PIN_LENGTH
        if (settings.pinInput.length < len || settings.pinConfirmInput.length < len) {
            updateSettings { it.copy(pinSetupError = "PIN ${len}자리를 입력해 주세요.") }
            return
        }
        if (settings.pinInput != settings.pinConfirmInput) {
            updateSettings {
                it.copy(pinSetupError = "PIN 확인이 일치하지 않습니다.", pinConfirmInput = "")
            }
            return
        }
        val email = currentLoginEmail()
        if (email.isNullOrBlank()) {
            updateSettings { it.copy(pinSetupError = "로그인된 계정이 없습니다.") }
            return
        }
        viewModelScope.launch {
            runCatching {
                appLockRepository.savePin(settings.pinInput, email)
                appLockRepository.setAppLockEnabled(true)
            }.onSuccess {
                val changing = _uiState.value.settings.isChangingCredential
                updateSettings {
                    it.copy(
                        showPinSetupDialog = false,
                        isChangingCredential = false,
                        pinInput = "",
                        pinConfirmInput = "",
                        pinSetupError = null,
                        unlockMethod = AppLockUnlockMethod.PIN,
                        hasPinRegistered = true,
                        feedbackMessage = if (changing) {
                            "「$email」계정의 PIN을 변경했습니다."
                        } else {
                            "「$email」계정에 PIN을 등록했습니다."
                        }
                    )
                }
            }.onFailure { error ->
                updateSettings {
                    it.copy(pinSetupError = error.message ?: "PIN 저장에 실패했습니다.")
                }
            }
        }
    }

    fun updatePatternSetupInput(cells: List<Int>) {
        updateSettings { it.copy(patternInput = cells, patternSetupError = null) }
    }

    fun onPatternSetupComplete(cells: List<Int>) {
        val settings = _uiState.value.settings
        if (cells.size < 4) {
            updateSettings {
                it.copy(patternInput = emptyList(), patternSetupError = "패턴은 4개 이상 연결해 주세요.")
            }
            return
        }
        if (settings.patternConfirmInput.isEmpty()) {
            updateSettings {
                it.copy(
                    patternConfirmInput = cells,
                    patternInput = emptyList(),
                    patternSetupError = "확인을 위해 같은 패턴을 한 번 더 그려 주세요."
                )
            }
            return
        }
        if (cells != settings.patternConfirmInput) {
            updateSettings {
                it.copy(
                    patternInput = emptyList(),
                    patternConfirmInput = emptyList(),
                    patternSetupError = "패턴이 일치하지 않습니다. 다시 그려 주세요."
                )
            }
            return
        }
        val email = currentLoginEmail()
        if (email.isNullOrBlank()) {
            updateSettings { it.copy(patternSetupError = "로그인된 계정이 없습니다.") }
            return
        }
        viewModelScope.launch {
            runCatching {
                appLockRepository.savePattern(cells, email)
                appLockRepository.setAppLockEnabled(true)
            }.onSuccess {
                val changing = _uiState.value.settings.isChangingCredential
                updateSettings {
                    it.copy(
                        showPatternSetupDialog = false,
                        isChangingCredential = false,
                        patternInput = emptyList(),
                        patternConfirmInput = emptyList(),
                        patternSetupError = null,
                        unlockMethod = AppLockUnlockMethod.PATTERN,
                        hasPatternRegistered = true,
                        feedbackMessage = if (changing) {
                            "「$email」계정의 패턴을 변경했습니다."
                        } else {
                            "「$email」계정에 패턴을 등록했습니다."
                        }
                    )
                }
            }.onFailure { error ->
                updateSettings {
                    it.copy(patternSetupError = error.message ?: "패턴 저장에 실패했습니다.")
                }
            }
        }
    }

    fun dismissPatternSetupDialog() {
        updateSettings {
            it.copy(
                showPatternSetupDialog = false,
                isChangingCredential = false,
                patternInput = emptyList(),
                patternConfirmInput = emptyList(),
                patternSetupError = null
            )
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        if (enabled && !biometricAuthManager.canAuthenticateWithBiometric()) {
            updateSettings {
                it.copy(feedbackMessage = "이 기기에서는 지문/생체 인증을 사용할 수 없습니다.")
            }
            return
        }
        viewModelScope.launch {
            runCatching { appLockRepository.setBiometricEnabled(enabled) }
            updateSettings {
                it.copy(
                    isBiometricEnabled = enabled,
                    feedbackMessage = if (enabled) {
                        "생체 인증을 켰습니다. (기기 공통 · 마지막 로그인 계정으로 입장)"
                    } else {
                        "생체 인증을 끄었습니다."
                    }
                )
            }
        }
    }

    fun dismissSettingsSheet() {
        val settings = _uiState.value.settings
        if (settings.isLoggingOut || _uiState.value.isSubmitting) return
        updateSettings {
            it.copy(
                visible = false,
                feedbackMessage = null,
                errorMessage = null,
                currentPassword = "",
                newPassword = "",
                confirmPassword = ""
            )
        }
    }

    fun updateProfileDisplayName(value: String) =
        updateSettings { it.copy(displayName = value, errorMessage = null, feedbackMessage = null) }

    fun updateProfilePhone(value: String) =
        updateSettings { it.copy(phone = value, errorMessage = null, feedbackMessage = null) }

    fun updateCurrentPassword(value: String) =
        updateSettings { it.copy(currentPassword = value, errorMessage = null, feedbackMessage = null) }

    fun updateNewPassword(value: String) =
        updateSettings { it.copy(newPassword = value, errorMessage = null, feedbackMessage = null) }

    fun updateConfirmPassword(value: String) =
        updateSettings { it.copy(confirmPassword = value, errorMessage = null, feedbackMessage = null) }

    fun saveProfile() {
        val settings = _uiState.value.settings
        if (_uiState.value.isSubmitting) return
        runBusy {
            try {
                authRepository.saveProfileForCurrentUser(
                    displayName = settings.displayName,
                    phone = settings.phone
                )
                showSettingsSuccess("회원 정보를 저장했습니다.")
            } catch (error: Throwable) {
                showSettingsError(error.message ?: "회원 정보 저장에 실패했습니다.")
            }
        }
    }

    fun changePassword() {
        val settings = _uiState.value.settings
        if (_uiState.value.isSubmitting || !settings.canChangePassword) return
        runBusy {
            try {
                authRepository.changePassword(
                    currentPassword = settings.currentPassword,
                    newPassword = settings.newPassword,
                    confirmPassword = settings.confirmPassword
                )
                updateSettings {
                    it.copy(
                        currentPassword = "",
                        newPassword = "",
                        confirmPassword = ""
                    )
                }
                showSettingsSuccess("비밀번호를 변경했습니다.")
            } catch (error: Throwable) {
                showSettingsError(error.toEmailAuthErrorMessage())
            }
        }
    }

    fun requestLogout() {
        updateSettings { it.copy(showLogoutConfirm = true, errorMessage = null) }
    }

    fun dismissLogoutConfirm() {
        if (_uiState.value.settings.isLoggingOut) return
        updateSettings { it.copy(showLogoutConfirm = false) }
    }

    fun confirmLogout() {
        if (_uiState.value.settings.isLoggingOut) return
        viewModelScope.launch {
            updateSettings { it.copy(isLoggingOut = true, showLogoutConfirm = false) }
            try {
                userSessionManager.logout()
            } catch (error: Throwable) {
                updateSettings { it.copy(isLoggingOut = false) }
                showSnackbar(error.message ?: "로그아웃에 실패했습니다.")
            }
        }
    }

    // endregion

    // region Create / Join

    fun openCreateDialog() {
        _uiState.update {
            it.copy(
                showCreateDialog = true,
                newMeetingName = "",
                newMeetingDescription = "",
                newMeetingSlogan = "",
                dialogErrorMessage = null
            )
        }
    }

    fun dismissCreateDialog() {
        if (_uiState.value.isSubmitting) return
        _uiState.update { it.copy(showCreateDialog = false, dialogErrorMessage = null) }
    }

    fun updateNewMeetingName(value: String) =
        _uiState.update { it.copy(newMeetingName = value, dialogErrorMessage = null) }

    fun updateNewMeetingDescription(value: String) =
        _uiState.update { it.copy(newMeetingDescription = value, dialogErrorMessage = null) }

    fun updateNewMeetingSlogan(value: String) =
        _uiState.update { it.copy(newMeetingSlogan = value, dialogErrorMessage = null) }

    fun createMeeting(onCreated: () -> Unit) {
        val state = _uiState.value
        if (state.isSubmitting) return
        runBusy {
            try {
                val meeting = meetingFirestoreRepository.createMeeting(
                    name = state.newMeetingName,
                    description = state.newMeetingDescription,
                    slogan = state.newMeetingSlogan
                )
                meetingClubSyncRepository.ensureLocalClubForMeeting(
                    meeting = meeting,
                    membershipStatus = ClubMembershipStatus.OWNER,
                    downloadIfEmpty = false
                )
                val skipOwnerMembership = SystemAdminConfig.matches(
                    authRepository.currentSession()?.uid,
                    authRepository.currentSession()?.email
                )
                val ownerRegistered = if (skipOwnerMembership) {
                    Result.success(Unit)
                } else {
                    runCatching {
                        meetingMemberEnrollmentRepository.ensureOwnerRegistered(meeting.id)
                    }
                }
                _uiState.update { it.copy(showCreateDialog = false) }
                when {
                    skipOwnerMembership ->
                        showSnackbar("「${meeting.name}」모임을 만들었습니다.")
                    ownerRegistered.isSuccess ->
                        showSnackbar("「${meeting.name}」모임을 만들었습니다. 개설자가 회원으로 등록되었습니다.")
                    else ->
                        showSnackbar("「${meeting.name}」모임을 만들었습니다. 회원 자동 등록은 입장 시 다시 시도합니다.")
                }
                onCreated()
            } catch (error: Throwable) {
                showDialogError(error.message ?: "모임 생성에 실패했습니다.")
            }
        }
    }

    fun openJoinDialog(meeting: MeetingDoc) {
        val uid = authRepository.currentSession()?.uid
        val resolved = resolvedMeeting(meeting)
        if (isAwaitingMemberProfile(resolved, uid, myJoinRequests[meeting.id])) {
            openMemberProfilePrompt(meeting, enterAfterSave = true)
            return
        }
        if (restrictionFor(resolved, uid) != null) {
            showSnackbar("활동 중지 상태입니다. 관리자에게 문의 바랍니다.")
            return
        }
        _uiState.update {
            it.copy(
                showJoinDialog = true,
                joinTarget = meeting,
                joinMessage = "",
                dialogErrorMessage = null
            )
        }
    }

    fun dismissJoinDialog() {
        if (_uiState.value.isSubmitting) return
        _uiState.update {
            it.copy(showJoinDialog = false, joinTarget = null, dialogErrorMessage = null)
        }
    }

    fun updateJoinMessage(value: String) =
        _uiState.update { it.copy(joinMessage = value) }

    fun submitJoinRequest() {
        val meeting = _uiState.value.joinTarget ?: return
        if (_uiState.value.isSubmitting) return
        runBusy {
            try {
                joinRequestRepository.submitJoinRequest(meeting.id, _uiState.value.joinMessage)
                val uid = authRepository.currentSession()?.uid
                if (!uid.isNullOrBlank()) {
                    joinRequestRepository.getMyJoinRequest(meeting.id, uid)?.let { request ->
                        myJoinRequests = myJoinRequests + (meeting.id to request)
                    }
                }
                _uiState.update {
                    it.copy(showJoinDialog = false, joinTarget = null)
                }
                rebuildLists()
                showSnackbar("가입 요청을 보냈습니다. 승인 후 「새로고침」으로 상태를 확인할 수 있습니다.")
            } catch (error: Throwable) {
                val message = error.message.orEmpty()
                if (message.contains("회원 정보를 등록")) {
                    _uiState.update { it.copy(showJoinDialog = false, joinTarget = null) }
                    openMemberProfilePrompt(meeting, enterAfterSave = true)
                } else {
                    showDialogError(error.message ?: "가입 요청에 실패했습니다.")
                }
            }
        }
    }

    // endregion

    // region Approvals / Enter

    fun openApprovalSheet() {
        _uiState.update {
            it.copy(showApprovalSheet = true, selectedTab = MeetingHubTab.APPROVALS)
        }
    }

    fun dismissApprovalSheet() {
        _uiState.update { it.copy(showApprovalSheet = false) }
    }

    fun enterMeeting(meeting: MeetingDoc, onEntered: () -> Unit) {
        if (_uiState.value.isSubmitting) return
        viewModelScope.launch {
            val session = authRepository.currentSession()
            if (session != null) {
                when (val decision = memberLoginPolicy.evaluateEnterMeeting(session, meeting)) {
                    is MemberAccessDecision.Denied -> {
                        showSnackbar(decision.message)
                        return@launch
                    }
                    MemberAccessDecision.Allowed -> Unit
                }
            }
            val uid = session?.uid
            val resolved = resolvedMeeting(meeting)
            if (isAwaitingMemberProfile(resolved, uid, myJoinRequests[meeting.id])) {
                openMemberProfilePrompt(meeting, enterAfterSave = true)
                return@launch
            }
            val elevated = isElevatedAccount(session?.uid, session?.email)
            val needsRoster = !elevated &&
                uid != null &&
                meeting.ownerUid != uid &&
                meeting.adminUid != uid &&
                (resolved.sharedWith.contains(uid) || joinedMeetings.any { it.id == meeting.id }) &&
                !meetingMemberEnrollmentRepository.hasSelfMemberRecord(meeting.id)
            if (needsRoster) {
                openMemberProfilePrompt(meeting, enterAfterSave = true)
                return@launch
            }
            _uiState.update { it.copy(isSubmitting = true) }
            try {
                enterMeetingInternal(meeting, onEntered)
            } catch (error: Throwable) {
                showSnackbar(error.message ?: "모임 입장에 실패했습니다.")
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    private suspend fun enterMeetingInternal(meeting: MeetingDoc, onEntered: () -> Unit) {
        val session = authRepository.currentSession()
        if (session != null) {
            memberLoginPolicy.requireEnterMeeting(session, meeting)
        }
        val elevated = isElevatedAccount(session?.uid, session?.email)
        val status = when {
            elevated -> ClubMembershipStatus.OWNER
            meeting.ownerUid == session?.uid -> ClubMembershipStatus.OWNER
            else -> ClubMembershipStatus.APPROVED
        }
        runCatching { meetingMemberEnrollmentRepository.pruneSystemAdminMembers(meeting.id) }
        if (meeting.ownerUid == session?.uid &&
            !SystemAdminConfig.matches(session.uid, session.email)
        ) {
            runCatching { meetingMemberEnrollmentRepository.ensureOwnerRegistered(meeting.id) }
        }
        meetingClubSyncRepository.ensureLocalClubForMeeting(
            meeting = meeting,
            membershipStatus = status,
            downloadIfEmpty = true
        )
        onEntered()
    }

    fun approveRequest(meetingId: String, requestId: String) {
        viewModelScope.launch {
            try {
                joinRequestRepository.approveJoinRequest(meetingId, requestId)
                refreshPendingApprovals()
                showSnackbar("가입을 승인했습니다. 회원에게 정보 입력을 요청했습니다.")
            } catch (error: Throwable) {
                showSnackbar(error.message ?: "승인에 실패했습니다.")
            }
        }
    }

    fun rejectRequest(meetingId: String, requestId: String) {
        viewModelScope.launch {
            try {
                joinRequestRepository.rejectJoinRequest(meetingId, requestId)
                refreshPendingApprovals()
                showSnackbar("가입 요청을 거절했습니다.")
            } catch (error: Throwable) {
                showSnackbar(error.message ?: "거절에 실패했습니다.")
            }
        }
    }

    fun openMemberProfilePrompt(meeting: MeetingDoc, enterAfterSave: Boolean = false) {
        viewModelScope.launch {
            val session = authRepository.currentSession()
            val profile = session?.uid?.let { uid ->
                runCatching { userProfileFirestoreRepository.getProfile(uid) }.getOrNull()
            }
            val request = myJoinRequests[meeting.id]
            val today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
            val name = request?.displayName?.trim().orEmpty()
                .ifBlank { profile?.displayName.orEmpty() }
                .ifBlank { session?.displayName.orEmpty() }
            val phone = request?.phone?.trim().orEmpty()
                .ifBlank { profile?.phone.orEmpty() }
            val email = request?.email?.trim().orEmpty()
                .ifBlank { session?.email.orEmpty() }
            profilePromptSeq += 1
            val promptKey = profilePromptSeq
            _uiState.update {
                it.copy(
                    memberProfilePrompt = MemberProfilePromptState(
                        meeting = meeting,
                        enterAfterSave = enterAfterSave,
                        promptKey = promptKey,
                        form = MemberFormState(
                            name = name,
                            phone = phone,
                            email = email,
                            joinDate = today,
                            role = MemberRole.GENERAL,
                            status = MemberStatus.ACTIVE
                        )
                    )
                )
            }
        }
    }

    fun dismissMemberProfilePrompt() {
        if (_uiState.value.isSubmitting) return
        _uiState.update { it.copy(memberProfilePrompt = null) }
    }

    fun updateProfileFormName(value: String) = updateProfileForm { copy(name = value) }
    fun updateProfileFormPhone(value: String) = updateProfileForm { copy(phone = value) }
    fun updateProfileFormEmail(value: String) = updateProfileForm { copy(email = value) }
    fun updateProfileFormJoinDate(value: String) = updateProfileForm { copy(joinDate = value) }
    fun updateProfileFormBirthDate(value: String) = updateProfileForm { copy(birthDate = value) }
    fun updateProfileFormIsLunarBirth(value: Boolean) =
        updateProfileForm { copy(isLunarBirth = value) }
    fun updateProfileFormResidenceRegion(value: String) =
        updateProfileForm { copy(residenceRegion = value) }
    fun updateProfileFormAddress(value: String) = updateProfileForm { copy(address = value) }
    fun updateProfileFormDetailAddress(value: String) =
        updateProfileForm { copy(detailAddress = value) }

    fun saveMemberProfile(onEntered: () -> Unit) {
        val prompt = _uiState.value.memberProfilePrompt ?: return
        if (_uiState.value.isSubmitting) return
        val form = prompt.form
        if (form.name.isBlank() || form.phone.isBlank() || form.emailError != null) {
            _uiState.update { state ->
                val current = state.memberProfilePrompt ?: return@update state
                state.copy(
                    memberProfilePrompt = current.copy(errorMessage = "이름과 핸드폰번호를 확인해 주세요.")
                )
            }
            return
        }
        runBusy {
            try {
                val entity = form.toEntity()
                meetingMemberEnrollmentRepository.registerSelfAsMember(
                    meetingId = prompt.meeting.id,
                    name = entity.name,
                    phone = entity.phone,
                    email = entity.email,
                    joinDate = entity.joinDate,
                    birthDate = entity.birthDate,
                    isLunarBirth = entity.isLunarBirth,
                    residenceRegion = entity.residenceRegion,
                    address = entity.address,
                    detailAddress = entity.detailAddress
                )
                val meetingId = prompt.meeting.id
                val uid = authRepository.currentSession()?.uid
                val serverRequest = if (!uid.isNullOrBlank()) {
                    runCatching { joinRequestRepository.getMyJoinRequest(meetingId, uid) }.getOrNull()
                } else {
                    null
                }
                val completed = (serverRequest ?: myJoinRequests[meetingId])
                    ?.copy(profileCompleted = true)
                if (completed != null) {
                    myJoinRequests = myJoinRequests + (meetingId to completed)
                } else if (uid != null) {
                    myJoinRequests = myJoinRequests + (
                        meetingId to JoinRequestDoc(
                            uid = uid,
                            status = JoinRequestStatus.APPROVED,
                            profileCompleted = true
                        )
                    )
                }
                selfEnrolledMeetingIds = selfEnrolledMeetingIds + meetingId
                locallyCompletedProfileIds = locallyCompletedProfileIds + meetingId
                myMemberStatuses = myMemberStatuses + (meetingId to MemberStatus.ACTIVE)
                _uiState.update { it.copy(memberProfilePrompt = null) }
                rebuildLists()
                showSnackbar("「${prompt.meeting.name}」 회원 정보가 등록되었습니다.")
            } catch (error: Throwable) {
                _uiState.update { state ->
                    val current = state.memberProfilePrompt ?: return@update state
                    state.copy(
                        memberProfilePrompt = current.copy(
                            errorMessage = error.message ?: "회원 정보 등록에 실패했습니다."
                        )
                    )
                }
            }
        }
    }

    private fun updateProfileForm(block: MemberFormState.() -> MemberFormState) {
        _uiState.update { state ->
            val prompt = state.memberProfilePrompt ?: return@update state
            state.copy(memberProfilePrompt = prompt.copy(form = prompt.form.block(), errorMessage = null))
        }
    }

    // endregion

    private fun loadProfile() {
        viewModelScope.launch {
            val session = authRepository.currentSession() ?: return@launch
            val profile = runCatching {
                userProfileFirestoreRepository.getProfile(session.uid)
            }.getOrNull()
            val email = session.email.orEmpty()
            val authName = session.displayName?.trim().orEmpty()
            // 시스템관리자는 역할 표시명을 고정, 그 외는 Auth 최신 이름을 Firestore와 동기화
            val displayName = if (PrivilegedAuthConfig.isPrivilegedEmail(email)) {
                PrivilegedAuthConfig.displayNameFor(email)
            } else {
                val synced = runCatching {
                    userProfileFirestoreRepository.syncDisplayNameFromAuth(
                        uid = session.uid,
                        email = email,
                        authDisplayName = authName
                    )
                }.getOrDefault(authName.ifBlank { profile?.displayName.orEmpty() })
                synced.ifBlank { profile?.displayName.orEmpty() }
            }
            val role = UserRole.resolve(uid = session.uid, email = email, meeting = null)
            updateSettings {
                it.copy(
                    email = email,
                    displayName = displayName,
                    phone = profile?.phone.orEmpty(),
                    userRole = role,
                    canChangePassword = !PrivilegedAuthConfig.isPrivilegedEmail(email)
                )
            }
            _uiState.update { it.copy(userRole = role) }
            // Firestore에도 옛 명칭이 남아 있으면 갱신
            if (
                PrivilegedAuthConfig.isPrivilegedEmail(email) &&
                profile?.displayName != displayName
            ) {
                runCatching {
                    userProfileFirestoreRepository.saveCompletedProfile(
                        uid = session.uid,
                        email = email,
                        displayName = displayName,
                        phone = profile?.phone?.takeIf { it.isNotBlank() } ?: "00000000000"
                    )
                }
            }
        }
    }

    private fun managedMeetingKey(session: FirebaseAuthSession?): String {
        val uid = session?.uid.orEmpty()
        if (uid.isBlank()) return ""
        if (isElevatedAccount(uid, session?.email)) return "elevated:$uid"
        return (joinedMeetings + allMeetings)
            .filter { it.ownerUid == uid || it.adminUid == uid }
            .map { it.id }
            .distinct()
            .sorted()
            .joinToString(separator = ",", prefix = "$uid:")
    }

    private suspend fun refreshPendingApprovals() {
        val session = authRepository.currentSession() ?: return
        val elevated = isElevatedAccount(session.uid, session.email)
        val candidates = if (elevated) {
            allMeetings
        } else {
            (joinedMeetings + allMeetings).distinctBy { it.id }
        }
        val managed = candidates.filter { meeting ->
            elevated || meeting.ownerUid == session.uid || meeting.adminUid == session.uid
        }
        val pendingPairs = managed.flatMap { meeting ->
            runCatching {
                joinRequestRepository.getPendingJoinRequestsOnce(meeting.id)
            }.getOrDefault(emptyList()).map { request -> meeting to request }
        }
        pendingCountByMeeting = pendingPairs
            .groupBy { it.first.id }
            .mapValues { it.value.size }
        _uiState.update { it.copy(pendingApprovals = pendingPairs) }
    }

    private fun rebuildLists() {
        val session = authRepository.currentSession()
        val elevated = _uiState.value.isElevated ||
            isElevatedAccount(session?.uid, session?.email)
        val uid = session?.uid
        val mySource = if (elevated) {
            allMeetings
        } else {
            val joinedIds = joinedMeetings.map { it.id }.toSet()
            val requested = allMeetings.filter { meeting ->
                if (meeting.id in joinedIds) return@filter false
                val request = myJoinRequests[meeting.id]
                request?.status == JoinRequestStatus.PENDING ||
                    isAwaitingMemberProfile(resolvedMeeting(meeting), uid, request)
            }
            val restrictedForMine = restrictedMeetings.filter { meeting ->
                !isAwaitingMemberProfile(resolvedMeeting(meeting), uid, myJoinRequests[meeting.id]) &&
                    restrictionFor(meeting, uid) != null
            }
            val rosterRestricted = allMeetings.filter { meeting ->
                !isAwaitingMemberProfile(resolvedMeeting(meeting), uid, myJoinRequests[meeting.id]) &&
                    (
                        myMemberStatuses[meeting.id] == MemberStatus.DORMANT ||
                            myMemberStatuses[meeting.id] == MemberStatus.WITHDRAWN
                        )
            }
            val rosterActive = allMeetings.filter { meeting ->
                myMemberStatuses[meeting.id] == MemberStatus.ACTIVE
            }
            (joinedMeetings + requested + restrictedForMine + rosterRestricted + rosterActive)
                .distinctBy { it.id }
        }
        val query = _uiState.value.searchQuery.trim()
        val searchable = allMeetings
            .filter { meeting -> meetingFirestoreRepository.matchesSearch(meeting, query) }
            .map { meeting -> toHubItem(meeting, uid, elevated, session?.email) }
        val canReviewJoins = elevated ||
            joinedMeetings.any { meeting ->
                meeting.ownerUid == uid || meeting.adminUid == uid
            } ||
            allMeetings.any { meeting ->
                meeting.ownerUid == uid || meeting.adminUid == uid
            }

        val role = UserRole.resolve(uid = uid, email = session?.email, meeting = null)
        _uiState.update {
            it.copy(
                isLoading = false,
                isElevated = elevated,
                userRole = role,
                canReviewJoins = canReviewJoins,
                myMeetings = mySource.map { meeting -> toHubItem(meeting, uid, elevated, session?.email) },
                searchableMeetings = searchable
            )
        }
    }

    private fun toHubItem(
        meeting: MeetingDoc,
        uid: String?,
        elevated: Boolean,
        email: String? = null
    ): MeetingHubItem {
        val resolved = resolvedMeeting(meeting)
        val request = myJoinRequests[resolved.id]
        val joinedIds = joinedMeetings.map { it.id }.toSet()
        val awaitingProfile = isAwaitingMemberProfile(resolved, uid, request)
        val restriction = if (awaitingProfile) null else restrictionFor(resolved, uid)
        val accessStatus = when {
            elevated -> MeetingAccessStatus.ELEVATED
            resolved.ownerUid == uid -> MeetingAccessStatus.OWNER
            uid != null && resolved.adminUid.isNotBlank() && resolved.adminUid == uid ->
                MeetingAccessStatus.TREASURER
            awaitingProfile -> MeetingAccessStatus.JOINED
            restriction != null -> restriction
            uid != null && (
                resolved.sharedWith.contains(uid) || resolved.id in joinedIds
                ) -> MeetingAccessStatus.JOINED
            myMemberStatuses[resolved.id] == MemberStatus.ACTIVE -> MeetingAccessStatus.JOINED
            request?.status == JoinRequestStatus.PENDING -> MeetingAccessStatus.PENDING
            request?.status == JoinRequestStatus.REJECTED -> MeetingAccessStatus.REJECTED
            request?.status == JoinRequestStatus.WITHDRAWN -> MeetingAccessStatus.AVAILABLE
            else -> MeetingAccessStatus.AVAILABLE
        }
        val roleBadge = when (accessStatus) {
            MeetingAccessStatus.ELEVATED,
            MeetingAccessStatus.OWNER,
            MeetingAccessStatus.TREASURER,
            MeetingAccessStatus.JOINED ->
                if (awaitingProfile) null
                else UserRole.resolve(uid = uid, email = email, meeting = resolved)
            else -> null
        }
        return MeetingHubItem(
            meeting = resolved,
            accessStatus = accessStatus,
            joinRequest = request,
            pendingApprovalCount = pendingCountByMeeting[resolved.id] ?: 0,
            roleBadge = roleBadge,
            needsMemberProfile = awaitingProfile
        )
    }

    private fun restrictionFor(meeting: MeetingDoc, uid: String?): MeetingAccessStatus? {
        val rosterStatus = myMemberStatuses[meeting.id]
        if (rosterStatus == MemberStatus.ACTIVE) return null
        if (rosterStatus == MemberStatus.DORMANT) return MeetingAccessStatus.SUSPENDED
        if (rosterStatus == MemberStatus.WITHDRAWN) return MeetingAccessStatus.DISABLED
        return resolvedMeeting(meeting).restrictionStatusFor(uid)
    }

    /**
     * 가입 승인 후 회원정보 폼을 아직 제출하지 않은 상태.
     * 요청 문서가 늦게 오거나 승인대기로 남아 있어도, 이미 모임에 들어가 있으면
     * 일반회원이 아니라 「회원정보 입력」으로 본다.
     */
    private fun isAwaitingMemberProfile(
        meeting: MeetingDoc,
        uid: String?,
        request: JoinRequestDoc?
    ): Boolean {
        if (uid.isNullOrBlank()) return false
        if (_uiState.value.isElevated) return false
        if (meeting.ownerUid == uid) return false
        if (meeting.adminUid.isNotBlank() && meeting.adminUid == uid) return false
        if (meeting.id in selfEnrolledMeetingIds) return false
        val roster = myMemberStatuses[meeting.id]
        if (roster == MemberStatus.DORMANT || roster == MemberStatus.WITHDRAWN) return false
        if (request?.status == JoinRequestStatus.REJECTED) return false
        if (request?.needsMemberProfile() == true) return true
        val hasRegularAccess = meeting.sharedWith.contains(uid) ||
            joinedMeetings.any { it.id == meeting.id }
        if (!hasRegularAccess) return false
        if (meeting.id in selfEnrolledMeetingIds) return false
        if (request?.status == JoinRequestStatus.APPROVED) return true
        if (request?.status == JoinRequestStatus.PENDING ||
            request?.status == JoinRequestStatus.WITHDRAWN
        ) {
            return true
        }
        return roster != MemberStatus.ACTIVE
    }

    private suspend fun refreshMyMemberStatuses() {
        val session = authRepository.currentSession()
        if (session == null || isElevatedAccount(session.uid, session.email)) {
            myMemberStatuses = emptyMap()
            selfEnrolledMeetingIds = emptySet()
            locallyCompletedProfileIds = emptySet()
            return
        }
        val meetingIds = (
            myJoinRequests.keys +
                restrictedMeetings.map { it.id } +
                joinedMeetings.map { it.id }
            ).distinct()
        if (meetingIds.isEmpty()) {
            myMemberStatuses = emptyMap()
            selfEnrolledMeetingIds = emptySet()
            locallyCompletedProfileIds = emptySet()
            return
        }
        val loaded = withTimeoutOrNull(8_000) {
            val statuses = mutableMapOf<String, MemberStatus>()
            val enrolled = mutableSetOf<String>()
            val found = mutableSetOf<String>()
            meetingIds.forEach { meetingId ->
                val member = runCatching {
                    memberFirestoreRepository.findMyMember(meetingId, session.uid, session.email)
                }.getOrNull() ?: return@forEach
                found += meetingId
                if (member.selfEnrolled) {
                    enrolled += meetingId
                }
                val status = member.statusEnum()
                if (status == MemberStatus.ACTIVE &&
                    (member.name.isBlank() || member.phone.isBlank())
                ) {
                    return@forEach
                }
                if (status == MemberStatus.ACTIVE) {
                    val meeting = restrictedMeetings.firstOrNull { it.id == meetingId }
                        ?: allMeetings.firstOrNull { it.id == meetingId }
                    if (meeting != null && session.uid in meeting.inactiveMemberUids) {
                        runCatching {
                            meetingFirestoreRepository.setRegularMemberAccess(
                                meetingId = meetingId,
                                memberUid = session.uid,
                                allowed = true
                            )
                        }
                    }
                }
                statuses[meetingId] = status
            }
            Triple(statuses.toMap(), enrolled.toSet(), found.toSet())
        }
        if (loaded != null) {
            myMemberStatuses = loaded.first
            locallyCompletedProfileIds = locallyCompletedProfileIds.intersect(loaded.third)
            selfEnrolledMeetingIds = loaded.second + locallyCompletedProfileIds
        }
    }

    private fun resolvedMeeting(meeting: MeetingDoc): MeetingDoc {
        val joined = joinedMeetings.firstOrNull { it.id == meeting.id }
        val restricted = restrictedMeetings.firstOrNull { it.id == meeting.id }
        return meeting.copy(
            sharedWith = joined?.sharedWith?.ifEmpty { meeting.sharedWith }
                ?: meeting.sharedWith,
            inactiveMemberUids = restricted?.inactiveMemberUids?.ifEmpty { meeting.inactiveMemberUids }
                ?: meeting.inactiveMemberUids,
            inactiveMemberReasons = restricted?.inactiveMemberReasons?.ifEmpty { meeting.inactiveMemberReasons }
                ?: meeting.inactiveMemberReasons
        )
    }

    private fun runBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSubmitting = true,
                    dialogErrorMessage = null,
                    settings = it.settings.copy(errorMessage = null, feedbackMessage = null)
                )
            }
            try {
                block()
            } finally {
                _uiState.update { it.copy(isSubmitting = false) }
            }
        }
    }

    private fun updateSettings(transform: (HubSettingsState) -> HubSettingsState) {
        _uiState.update { it.copy(settings = transform(it.settings)) }
    }

    private fun showSettingsSuccess(message: String) {
        updateSettings { it.copy(feedbackMessage = message, errorMessage = null) }
        showSnackbar(message)
    }

    private fun showSettingsError(message: String) {
        updateSettings { it.copy(errorMessage = message, feedbackMessage = null) }
    }

    private fun showDialogError(message: String) {
        _uiState.update { it.copy(dialogErrorMessage = message) }
    }

    private fun showSnackbar(message: String) {
        _uiState.update { it.copy(snackbarMessage = message) }
    }

    private fun mergeJoinedKeepProfile(incoming: List<MeetingDoc>): List<MeetingDoc> {
        val byId = incoming.associateBy { it.id }.toMutableMap()
        joinedMeetings.forEach { existing ->
            if (existing.id !in byId &&
                myJoinRequests[existing.id]?.needsMemberProfile() == true
            ) {
                byId[existing.id] = existing
            }
        }
        return byId.values.sortedBy { it.name }
    }

    private fun pickPreferredJoinRequest(docs: List<JoinRequestDoc>): JoinRequestDoc =
        docs.firstOrNull { it.needsMemberProfile() }
            ?: docs.firstOrNull { it.status == JoinRequestStatus.PENDING }
            ?: docs.maxByOrNull { it.requestedAt }
            ?: docs.last()

    private fun preferJoinRequest(
        existing: JoinRequestDoc?,
        incoming: JoinRequestDoc
    ): JoinRequestDoc {
        if (existing == null) return incoming
        if (incoming.needsMemberProfile()) return incoming
        if (existing.needsMemberProfile()) {
            return if (incoming.status == JoinRequestStatus.APPROVED) incoming else existing
        }
        if (incoming.status == JoinRequestStatus.APPROVED &&
            existing.status == JoinRequestStatus.PENDING
        ) {
            return incoming
        }
        if (incoming.status == JoinRequestStatus.PENDING &&
            existing.status == JoinRequestStatus.APPROVED &&
            incoming.requestedAt >= existing.requestedAt
        ) {
            return incoming
        }
        return incoming
    }

    private fun mergeJoinRequests(
        incoming: Map<String, JoinRequestDoc>
    ): Map<String, JoinRequestDoc> {
        if (incoming.isEmpty()) return myJoinRequests
        val merged = myJoinRequests.toMutableMap()
        incoming.forEach { (meetingId, picked) ->
            merged[meetingId] = preferJoinRequest(merged[meetingId], picked)
        }
        return merged
    }

    private data class HubSnapshot(
        val directory: List<MeetingDoc>,
        val joined: List<MeetingDoc>,
        val requests: List<Pair<String, JoinRequestDoc>>,
        val session: FirebaseAuthSession?,
        val restricted: List<MeetingDoc>
    )
}
