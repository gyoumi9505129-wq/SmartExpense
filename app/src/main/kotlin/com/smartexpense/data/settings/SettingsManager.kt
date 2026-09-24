package com.smartexpense.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.smartexpense.di.ClubDatabaseGateway
import com.smartexpense.data.local.prefs.AppLockPreferenceKeys
import com.smartexpense.data.local.prefs.ClubPreferenceKeys
import com.smartexpense.data.local.prefs.PostRestoreFlags
import com.smartexpense.data.local.prefs.safeEdit
import com.smartexpense.data.local.prefs.safePreferencesFirst
import com.smartexpense.data.repository.club.ClubRepository
import com.smartexpense.data.repository.club.SelectedClubRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SettingsSnapshot(
    val selectedClubId: Long? = null,
    val appLockEnabled: Boolean? = null,
    val lockGracePeriodMs: Long? = null,
    val unlockMethod: String? = null,
    val clubIdsBeforeRestore: List<Long> = emptyList()
)

@Singleton
class SettingsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
    private val databaseGateway: ClubDatabaseGateway,
    private val selectedClubRepository: SelectedClubRepository,
    private val clubRepository: ClubRepository
) {
    private val clubDao get() = databaseGateway.clubDao()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun backupBeforeRestore() {
        val prefs = dataStore.safePreferencesFirst(context)
        val clubs = runCatching { clubDao.getAllOnce() }.getOrDefault(emptyList())
        val snapshot = SettingsSnapshot(
            selectedClubId = prefs[ClubPreferenceKeys.SELECTED_CLUB_ID],
            appLockEnabled = prefs[AppLockPreferenceKeys.APP_LOCK_ENABLED],
            lockGracePeriodMs = prefs[AppLockPreferenceKeys.LOCK_GRACE_PERIOD_MS],
            unlockMethod = prefs[AppLockPreferenceKeys.UNLOCK_METHOD],
            clubIdsBeforeRestore = clubs.map { it.clubId }
        )
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SNAPSHOT, json.encodeToString(snapshot))
            .apply()
        PostRestoreFlags.markPending(context)
    }

    suspend fun syncAfterRestore() {
        if (!PostRestoreFlags.isPending(context)) return

        val snapshot = loadSnapshot()
        if (snapshot != null) {
            restoreDataStoreSettings(snapshot)
            restoreSelectedClub(snapshot)
        }
        clubRepository.reconcileSelectedClub()
        PostRestoreFlags.clearPending(context)
        clearSnapshot()
    }

    private suspend fun restoreDataStoreSettings(snapshot: SettingsSnapshot) {
        dataStore.safeEdit(context) { prefs ->
            snapshot.appLockEnabled?.let { prefs[AppLockPreferenceKeys.APP_LOCK_ENABLED] = it }
            snapshot.lockGracePeriodMs?.let { prefs[AppLockPreferenceKeys.LOCK_GRACE_PERIOD_MS] = it }
            snapshot.unlockMethod?.let { prefs[AppLockPreferenceKeys.UNLOCK_METHOD] = it }
        }
    }

    private suspend fun restoreSelectedClub(snapshot: SettingsSnapshot) {
        val restoredClubs = clubDao.getAllOnce()
        if (restoredClubs.isEmpty()) return

        val preferredId = snapshot.selectedClubId
        val matchedClub = when {
            preferredId != null && restoredClubs.any { it.clubId == preferredId } ->
                preferredId
            snapshot.clubIdsBeforeRestore.isNotEmpty() -> {
                snapshot.clubIdsBeforeRestore
                    .firstOrNull { id -> restoredClubs.any { club -> club.clubId == id } }
                    ?: restoredClubs.first().clubId
            }
            else -> restoredClubs.first().clubId
        }
        selectedClubRepository.setSelectedClubId(matchedClub)
    }

    private fun loadSnapshot(): SettingsSnapshot? {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SNAPSHOT, null)
            ?: return null
        return runCatching { json.decodeFromString<SettingsSnapshot>(raw) }.getOrNull()
    }

    private fun clearSnapshot() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_SNAPSHOT)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "restore_settings"
        private const val KEY_SNAPSHOT = "settings_snapshot_json"
    }
}
