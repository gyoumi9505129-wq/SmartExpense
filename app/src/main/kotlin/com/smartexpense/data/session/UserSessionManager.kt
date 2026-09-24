package com.smartexpense.data.session

import com.smartexpense.data.firebase.auth.FirebaseAuthRepository
import com.smartexpense.data.repository.club.AppNavigationGuard
import com.smartexpense.data.repository.club.SelectedClubRepository
import com.smartexpense.data.repository.club.SelectedMeetingRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@Singleton
class UserSessionManager @Inject constructor(
    private val selectedClubRepository: SelectedClubRepository,
    private val selectedMeetingRepository: SelectedMeetingRepository,
    private val firebaseAuthRepository: FirebaseAuthRepository,
    private val authSessionManager: AuthSessionManager,
    private val authRefreshGuard: AuthRefreshGuard,
    private val appNavigationGuard: AppNavigationGuard
) {
    private val _logoutCompleted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val logoutCompleted: SharedFlow<Unit> = _logoutCompleted.asSharedFlow()

    /** 사용자가 직접 로그아웃 — 앱 태스크 종료로 이어질 수 있습니다. */
    suspend fun logout() {
        clearSessionInternal(suppressRefresh = true)
        _logoutCompleted.emit(Unit)
    }

    /**
     * 미활동 세션 만료 로그아웃.
     * 앱을 종료하지 않고 로그인 화면으로 복귀할 수 있게 합니다.
     */
    suspend fun logoutDueToInactivity() {
        clearSessionInternal(suppressRefresh = false)
    }

    private suspend fun clearSessionInternal(suppressRefresh: Boolean) {
        if (suppressRefresh) {
            authRefreshGuard.onExplicitLogoutStarted()
        }
        runCatching { firebaseAuthRepository.signOut() }
        authSessionManager.clearAuthSession()
        selectedClubRepository.clearSelectedClubId()
        selectedMeetingRepository.setSelectedMeetingId(null)
        appNavigationGuard.setSuppressClubListRedirect(true)
        if (!suppressRefresh) {
            authRefreshGuard.onAuthenticatedAgain()
        }
    }
}
