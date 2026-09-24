package com.smartexpense.data.repository.club

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.smartexpense.data.local.ClubConstants
import com.smartexpense.data.local.prefs.ClubPreferenceKeys
import com.smartexpense.data.local.prefs.safeEdit
import com.smartexpense.data.local.prefs.safePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class SelectedClubRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>
) {
    val selectedClubId: Flow<Long?> = dataStore.safePreferences(context).map { prefs ->
        prefs[ClubPreferenceKeys.SELECTED_CLUB_ID]
    }

    suspend fun setSelectedClubId(clubId: Long) {
        dataStore.safeEdit(context) { prefs ->
            prefs[ClubPreferenceKeys.SELECTED_CLUB_ID] = clubId
        }
    }

    suspend fun clearSelectedClubId() {
        dataStore.safeEdit(context) { prefs ->
            prefs.remove(ClubPreferenceKeys.SELECTED_CLUB_ID)
        }
    }

    companion object {
        val DEFAULT_CLUB_ID: Long = ClubConstants.DEFAULT_CLUB_ID
    }
}
