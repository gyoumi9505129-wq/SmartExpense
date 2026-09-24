package com.smartexpense.data.repository.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import com.smartexpense.data.local.prefs.AppLockPreferenceKeys
import com.smartexpense.data.local.prefs.safeEdit
import com.smartexpense.data.local.prefs.safePreferences
import com.smartexpense.data.local.prefs.safePreferencesFirst
import com.smartexpense.domain.security.AppLockUnlockMethod
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

data class AccountLockCredential(
    val email: String,
    val pinHash: String? = null,
    val patternHash: String? = null,
    val unlockMethod: AppLockUnlockMethod = AppLockUnlockMethod.PIN
) {
    val hasPin: Boolean get() = !pinHash.isNullOrBlank()
    val hasPattern: Boolean get() = !patternHash.isNullOrBlank()
    val hasAny: Boolean get() = hasPin || hasPattern
}

/**
 * PIN/패턴은 **이메일 계정별**로 저장합니다.
 * 생체 인증은 OS 기기 단위라 계정 구분이 불가합니다.
 */
@Singleton
class AppLockRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>
) {
    private val safePrefs = dataStore.safePreferences(context)

    val hasPinRegistered: Flow<Boolean> = safePrefs.map { prefs ->
        allCredentials(prefs).any { it.hasPin }
    }

    val hasPatternRegistered: Flow<Boolean> = safePrefs.map { prefs ->
        allCredentials(prefs).any { it.hasPattern }
    }

    val isCredentialConfigured: Flow<Boolean> = safePrefs.map { prefs ->
        allCredentials(prefs).any { it.hasAny }
    }

    val storedUnlockMethod: Flow<AppLockUnlockMethod?> = safePrefs.map { prefs ->
        allCredentials(prefs).firstOrNull()?.unlockMethod
            ?: AppLockUnlockMethod.fromStorage(prefs[AppLockPreferenceKeys.UNLOCK_METHOD])
    }

    /** 생체는 기기 공통 */
    val isBiometricEnabled: Flow<Boolean> = safePrefs.map { prefs ->
        prefs[AppLockPreferenceKeys.BIOMETRIC_ENABLED] == true ||
            AppLockUnlockMethod.biometricWasPrimary(prefs[AppLockPreferenceKeys.UNLOCK_METHOD])
    }

    suspend fun clearPendingUnlockSetup() {
        dataStore.safeEdit(context) { prefs ->
            prefs.remove(AppLockPreferenceKeys.PENDING_UNLOCK_SETUP)
        }
    }

    suspend fun getUnlockMethodForEmail(email: String): AppLockUnlockMethod {
        val prefs = dataStore.safePreferencesFirst(context)
        return credentialFor(prefs, email)?.unlockMethod
            ?: AppLockUnlockMethod.resolve(prefs[AppLockPreferenceKeys.UNLOCK_METHOD])
    }

    suspend fun setUnlockMethodForEmail(email: String, method: AppLockUnlockMethod) {
        val normalized = normalizeEmail(email) ?: return
        dataStore.safeEdit(context) { prefs ->
            migrateLegacyIntoMap(prefs)
            val map = credentialsMap(prefs).toMutableMap()
            val current = map[normalized] ?: AccountLockCredential(email = normalized)
            map[normalized] = current.copy(unlockMethod = method)
            writeMap(prefs, map)
            prefs[AppLockPreferenceKeys.UNLOCK_METHOD] = method.storageValue
            prefs.remove(AppLockPreferenceKeys.PENDING_UNLOCK_SETUP)
        }
    }

    suspend fun setUnlockMethod(method: AppLockUnlockMethod) {
        dataStore.safeEdit(context) { prefs ->
            prefs[AppLockPreferenceKeys.UNLOCK_METHOD] = method.storageValue
        }
    }

    suspend fun hasPinConfiguredOnce(email: String? = null): Boolean {
        val prefs = dataStore.safePreferencesFirst(context)
        return if (email.isNullOrBlank()) {
            allCredentials(prefs).any { it.hasPin }
        } else {
            credentialFor(prefs, email)?.hasPin == true
        }
    }

    suspend fun hasPatternConfiguredOnce(email: String? = null): Boolean {
        val prefs = dataStore.safePreferencesFirst(context)
        return if (email.isNullOrBlank()) {
            allCredentials(prefs).any { it.hasPattern }
        } else {
            credentialFor(prefs, email)?.hasPattern == true
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        dataStore.safeEdit(context) { prefs ->
            prefs[AppLockPreferenceKeys.BIOMETRIC_ENABLED] = enabled
        }
    }

    suspend fun isBiometricEnabledOnce(): Boolean {
        val prefs = dataStore.safePreferencesFirst(context)
        return prefs[AppLockPreferenceKeys.BIOMETRIC_ENABLED] == true ||
            AppLockUnlockMethod.biometricWasPrimary(prefs[AppLockPreferenceKeys.UNLOCK_METHOD])
    }

    suspend fun clearBackgroundTime() {
        dataStore.safeEdit(context) { prefs ->
            prefs.remove(AppLockPreferenceKeys.LAST_BACKGROUND_AT_MS)
        }
    }

    suspend fun isCredentialConfiguredOnce(): Boolean {
        val prefs = dataStore.safePreferencesFirst(context)
        return allCredentials(prefs).any { it.hasAny }
    }

    /** PIN이 맞는 계정 이메일. `_legacy_` 이면 마지막 저장 계정으로 로그인하면 됩니다. */
    suspend fun findEmailByPin(pin: String): String? {
        val hash = hashSecret(pin)
        val prefs = dataStore.safePreferencesFirst(context)
        return allCredentials(prefs).firstOrNull { it.pinHash == hash }?.email
    }

    suspend fun findEmailByPattern(cells: List<Int>): String? {
        val hash = hashSecret(patternKey(cells))
        val prefs = dataStore.safePreferencesFirst(context)
        return allCredentials(prefs).firstOrNull { it.patternHash == hash }?.email
    }

    suspend fun savePin(pin: String, email: String) {
        val normalized = normalizeEmail(email)
            ?: throw IllegalArgumentException("PIN을 저장할 이메일이 없습니다. 로그인 후 설정해 주세요.")
        dataStore.safeEdit(context) { prefs ->
            migrateLegacyIntoMap(prefs)
            val map = credentialsMap(prefs).toMutableMap()
            val current = map[normalized] ?: AccountLockCredential(email = normalized)
            map[normalized] = current.copy(
                pinHash = hashSecret(pin),
                unlockMethod = AppLockUnlockMethod.PIN
            )
            writeMap(prefs, map)
            prefs[AppLockPreferenceKeys.UNLOCK_METHOD] = AppLockUnlockMethod.PIN.storageValue
            prefs[AppLockPreferenceKeys.APP_LOCK_ENABLED] = true
            prefs.remove(AppLockPreferenceKeys.PENDING_UNLOCK_SETUP)
            prefs.remove(AppLockPreferenceKeys.PIN_HASH)
        }
    }

    suspend fun savePattern(cells: List<Int>, email: String) {
        val normalized = normalizeEmail(email)
            ?: throw IllegalArgumentException("패턴을 저장할 이메일이 없습니다. 로그인 후 설정해 주세요.")
        dataStore.safeEdit(context) { prefs ->
            migrateLegacyIntoMap(prefs)
            val map = credentialsMap(prefs).toMutableMap()
            val current = map[normalized] ?: AccountLockCredential(email = normalized)
            map[normalized] = current.copy(
                patternHash = hashSecret(patternKey(cells)),
                unlockMethod = AppLockUnlockMethod.PATTERN
            )
            writeMap(prefs, map)
            prefs[AppLockPreferenceKeys.UNLOCK_METHOD] = AppLockUnlockMethod.PATTERN.storageValue
            prefs[AppLockPreferenceKeys.APP_LOCK_ENABLED] = true
            prefs.remove(AppLockPreferenceKeys.PENDING_UNLOCK_SETUP)
            prefs.remove(AppLockPreferenceKeys.PATTERN_HASH)
        }
    }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        dataStore.safeEdit(context) { prefs ->
            prefs[AppLockPreferenceKeys.APP_LOCK_ENABLED] = enabled
        }
    }

    private fun normalizeEmail(email: String?): String? =
        email?.trim()?.lowercase()?.takeIf { it.isNotBlank() }

    private fun credentialFor(prefs: Preferences, email: String): AccountLockCredential? {
        val normalized = normalizeEmail(email) ?: return null
        return credentialsMap(prefs)[normalized]
    }

    private fun allCredentials(prefs: Preferences): List<AccountLockCredential> {
        val fromMap = credentialsMap(prefs).values.toList()
        if (fromMap.isNotEmpty()) return fromMap
        val legacyPin = prefs[AppLockPreferenceKeys.PIN_HASH]
        val legacyPattern = prefs[AppLockPreferenceKeys.PATTERN_HASH]
        if (legacyPin.isNullOrBlank() && legacyPattern.isNullOrBlank()) return emptyList()
        return listOf(
            AccountLockCredential(
                email = LEGACY_EMAIL,
                pinHash = legacyPin,
                patternHash = legacyPattern,
                unlockMethod = AppLockUnlockMethod.resolve(prefs[AppLockPreferenceKeys.UNLOCK_METHOD])
            )
        )
    }

    private fun credentialsMap(prefs: Preferences): Map<String, AccountLockCredential> {
        val raw = prefs[AppLockPreferenceKeys.CREDENTIALS_BY_EMAIL] ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap {
                obj.keys().forEach { email ->
                    val item = obj.optJSONObject(email) ?: return@forEach
                    put(
                        email,
                        AccountLockCredential(
                            email = email,
                            pinHash = item.optString("pinHash").takeIf { it.isNotBlank() },
                            patternHash = item.optString("patternHash").takeIf { it.isNotBlank() },
                            unlockMethod = AppLockUnlockMethod.resolve(item.optString("method"))
                        )
                    )
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun writeMap(prefs: MutablePreferences, map: Map<String, AccountLockCredential>) {
        val root = JSONObject()
        map.forEach { (email, cred) ->
            root.put(
                email,
                JSONObject().apply {
                    cred.pinHash?.let { put("pinHash", it) }
                    cred.patternHash?.let { put("patternHash", it) }
                    put("method", cred.unlockMethod.storageValue)
                }
            )
        }
        prefs[AppLockPreferenceKeys.CREDENTIALS_BY_EMAIL] = root.toString()
    }

    private fun migrateLegacyIntoMap(prefs: MutablePreferences) {
        if (!prefs[AppLockPreferenceKeys.CREDENTIALS_BY_EMAIL].isNullOrBlank()) return
        val legacyPin = prefs[AppLockPreferenceKeys.PIN_HASH]
        val legacyPattern = prefs[AppLockPreferenceKeys.PATTERN_HASH]
        if (legacyPin.isNullOrBlank() && legacyPattern.isNullOrBlank()) return
        writeMap(
            prefs,
            mapOf(
                LEGACY_EMAIL to AccountLockCredential(
                    email = LEGACY_EMAIL,
                    pinHash = legacyPin,
                    patternHash = legacyPattern,
                    unlockMethod = AppLockUnlockMethod.resolve(prefs[AppLockPreferenceKeys.UNLOCK_METHOD])
                )
            )
        )
        prefs.remove(AppLockPreferenceKeys.PIN_HASH)
        prefs.remove(AppLockPreferenceKeys.PATTERN_HASH)
    }

    private fun patternKey(cells: List<Int>): String = cells.joinToString("-")

    private fun hashSecret(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest("$value:smartexpense-club".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val LEGACY_EMAIL = "_legacy_"
    }
}
