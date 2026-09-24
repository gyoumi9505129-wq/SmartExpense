package com.smartexpense.data.firebase

import com.smartexpense.data.firebase.auth.FirebaseAuthRepository
import com.smartexpense.data.repository.club.SelectedMeetingRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest

/**
 * 장부 조회(Read) 소스 결정.
 * - 로그인 + 선택 모임 소속(개설자·운영관리자·일반 회원) + 클라우드 모드 ON → Firestore
 * - 그 외(클라우드 모드 OFF·모임 미선택) → 로컬(Room)만
 *   (빈 신규 모임이 예전 meeting 클라우드 데이터로 폴백되며 크래시/혼선 나는 것을 방지)
 * 쓰기는 화면의 개설자·운영관리자 권한과 Firestore 규칙으로 막습니다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class CloudLedgerReadPolicy @Inject constructor(
    private val authRepository: FirebaseAuthRepository,
    private val cloudLedgerModeRepository: CloudLedgerModeRepository,
    private val selectedMeetingRepository: SelectedMeetingRepository
) {
    fun <T> observeList(
        cloud: () -> Flow<List<T>>,
        room: () -> Flow<List<T>>
    ): Flow<List<T>> =
        combine(
            authRepository.authState,
            cloudLedgerModeRepository.isEnabled,
            selectedMeetingRepository.selectedMeetingId
        ) { session, enabled, meetingId ->
            Triple(session != null, enabled, meetingId)
        }.flatMapLatest { (signedIn, enabled, meetingId) ->
            val hasMeeting = !meetingId.isNullOrBlank()
            val safeRoom = { room().catch { emit(emptyList()) } }
            val safeCloud = { cloud().catch { emit(emptyList()) } }
            if (signedIn && hasMeeting && enabled) {
                safeCloud()
            } else {
                safeRoom()
            }
        }.catch { emit(emptyList()) }

    suspend fun isCloudWriteMode(): Boolean {
        if (authRepository.currentUser() == null) return false
        if (!cloudLedgerModeRepository.isEnabledNow()) return false
        val meetingId = selectedMeetingRepository.selectedMeetingId.first()
        return !meetingId.isNullOrBlank()
    }
}
