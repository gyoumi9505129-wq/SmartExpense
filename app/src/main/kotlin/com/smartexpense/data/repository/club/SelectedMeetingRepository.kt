package com.smartexpense.data.repository.club

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import android.content.Context
import com.smartexpense.data.firebase.firestore.MeetingDoc
import com.smartexpense.data.firebase.firestore.MeetingFirestoreRepository
import com.smartexpense.data.firebase.firestore.MemberFirestoreRepository
import com.smartexpense.data.firebase.firestore.TransactionFirestoreRepository
import com.smartexpense.data.local.prefs.UserPreferenceKeys
import com.smartexpense.data.local.prefs.safeEdit
import com.smartexpense.data.local.prefs.safePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Firestore 모임(meeting) 선택 상태.
 * Room [SelectedClubRepository]의 Long clubId와 병행하며, 클라우드 모드에서 사용합니다.
 */
@Singleton
class SelectedMeetingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
    private val meetingFirestoreRepository: MeetingFirestoreRepository,
    private val memberFirestoreRepository: MemberFirestoreRepository,
    private val transactionFirestoreRepository: TransactionFirestoreRepository
) {
    val selectedMeetingId: Flow<String?> =
        dataStore.safePreferences(context).map { it[UserPreferenceKeys.SELECTED_MEETING_ID] }

    val meetings: Flow<List<MeetingDoc>> = meetingFirestoreRepository.observeMeetings()

    suspend fun setSelectedMeetingId(meetingId: String?) {
        dataStore.safeEdit(context) { prefs ->
            if (meetingId.isNullOrBlank()) {
                prefs.remove(UserPreferenceKeys.SELECTED_MEETING_ID)
            } else {
                prefs[UserPreferenceKeys.SELECTED_MEETING_ID] = meetingId
            }
        }
    }

    suspend fun requireSelectedMeetingId(): String =
        selectedMeetingId.first()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("선택된 모임이 없습니다.")

    suspend fun createMeeting(name: String, slogan: String = ""): String {
        val id = meetingFirestoreRepository.upsertMeeting(
            MeetingDoc(
                name = name,
                slogan = slogan,
                createdAt = LocalDate.now().toString()
            )
        )
        setSelectedMeetingId(id)
        return id
    }

    /**
     * 로그인 직후 선택된 모임이 없으면 기존 모임을 고르거나 기본 모임을 생성합니다.
     */
    suspend fun ensureDefaultMeeting(defaultName: String = "내 모임"): String {
        val current = selectedMeetingId.first()?.takeIf { it.isNotBlank() }
        if (current != null) return current
        return bindToRichestCloudMeeting(preferredName = defaultName).meetingId
    }

    /**
     * 로그인 시 호출: 클라우드에 데이터가 있는 모임을 우선 선택합니다.
     * - 이미 선택된 모임에 데이터가 있으면 유지 (내려받기/이행 직후 다른 모임으로 튀는 것 방지)
     * - 없으면 회원 수 → 거래 수 → 이름 일치 순으로 고름
     */
    suspend fun bindToRichestCloudMeeting(preferredName: String = "한우리"): CloudMeetingBindResult {
        val existing = meetingFirestoreRepository.getMeetingsOnce()
        if (existing.isEmpty()) {
            val id = createMeeting(preferredName)
            return CloudMeetingBindResult(
                meetingId = id,
                meetingName = preferredName,
                memberCount = 0,
                transactionCount = 0,
                createdNew = true
            )
        }

        data class Scored(val meeting: MeetingDoc, val members: Int, val txs: Int) {
            val total: Int get() = members + txs
            val nameMatch: Boolean
                get() = meeting.name.trim() == preferredName.trim()
        }

        val scored = existing.map { meeting ->
            Scored(
                meeting = meeting,
                members = memberFirestoreRepository.getMemberCount(meeting.id),
                txs = transactionFirestoreRepository.getTransactionCount(meeting.id)
            )
        }

        val currentId = selectedMeetingId.first()?.takeIf { it.isNotBlank() }
        val currentScored = currentId?.let { id -> scored.firstOrNull { it.meeting.id == id } }
        if (currentScored != null && currentScored.total > 0) {
            return CloudMeetingBindResult(
                meetingId = currentScored.meeting.id,
                meetingName = currentScored.meeting.name,
                memberCount = currentScored.members,
                transactionCount = currentScored.txs,
                createdNew = false
            )
        }

        val best = scored
            .sortedWith(
                compareByDescending<Scored> { it.members }
                    .thenByDescending { it.txs }
                    .thenByDescending { it.nameMatch }
            )
            .first()

        setSelectedMeetingId(best.meeting.id)
        return CloudMeetingBindResult(
            meetingId = best.meeting.id,
            meetingName = best.meeting.name,
            memberCount = best.members,
            transactionCount = best.txs,
            createdNew = false
        )
    }
}

data class CloudMeetingBindResult(
    val meetingId: String,
    val meetingName: String,
    val memberCount: Int,
    val transactionCount: Int,
    val createdNew: Boolean
)
