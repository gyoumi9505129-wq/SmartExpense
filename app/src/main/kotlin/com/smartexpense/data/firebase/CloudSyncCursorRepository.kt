package com.smartexpense.data.firebase

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import com.smartexpense.data.local.prefs.safeEdit
import com.smartexpense.data.local.prefs.safePreferencesFirst
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 모임(meeting)별 마지막 클라우드 올리기 성공 시각.
 * 0이면 아직 최초 전체 이행이 완료되지 않은 것으로 봅니다.
 */
@Singleton
class CloudSyncCursorRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>
) {
    suspend fun getLastSyncedAt(meetingId: String): Long {
        if (meetingId.isBlank()) return 0L
        return dataStore.safePreferencesFirst(context)[key(meetingId)] ?: 0L
    }

    suspend fun setLastSyncedAt(meetingId: String, syncedAt: Long) {
        if (meetingId.isBlank()) return
        dataStore.safeEdit(context) { prefs ->
            prefs[key(meetingId)] = syncedAt
        }
    }

    suspend fun clearLastSyncedAt(meetingId: String) {
        if (meetingId.isBlank()) return
        dataStore.safeEdit(context) { prefs ->
            prefs.remove(key(meetingId))
        }
    }

    private fun key(meetingId: String) =
        longPreferencesKey("cloud_last_synced_at_$meetingId")
}
