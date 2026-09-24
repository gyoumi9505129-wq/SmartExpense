package com.smartexpense.data.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.smartexpense.data.firebase.PrivilegedAuthConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

data class SavedLoginCredentials(
    val email: String,
    val password: String,
    val remember: Boolean
)

data class SavedLoginAccount(
    val email: String,
    val password: String,
    val label: String = ""
)

/**
 * 로그인에 쓴 계정을 여러 개 암호화 저장합니다.
 * 삼성 Pass 대신 앱에서 계정 목록을 고를 수 있게 합니다.
 */
@Singleton
class LoginCredentialsStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = runCatching {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            FILE_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrElse {
        context.getSharedPreferences(FILE_NAME_FALLBACK, Context.MODE_PRIVATE)
    }

    fun load(): SavedLoginCredentials {
        val remember = prefs.getBoolean(KEY_REMEMBER, true)
        val accounts = loadAccounts()
        val lastEmail = prefs.getString(KEY_LAST_EMAIL, "").orEmpty()
        val selected = accounts.firstOrNull {
            it.email.equals(lastEmail, ignoreCase = true)
        } ?: accounts.firstOrNull()
        if (!remember) {
            return SavedLoginCredentials(
                email = selected?.email.orEmpty(),
                password = "",
                remember = false
            )
        }
        return SavedLoginCredentials(
            email = selected?.email.orEmpty(),
            password = selected?.password.orEmpty(),
            remember = true
        )
    }

    fun loadAccounts(): List<SavedLoginAccount> {
        migrateLegacyIfNeeded()
        val raw = prefs.getString(KEY_ACCOUNTS_JSON, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val email = obj.optString("email").trim()
                    if (email.isBlank()) continue
                    val storedLabel = obj.optString("label")
                    // 시스템관리자 라벨은 최신 역할명으로 표시
                    val label = if (PrivilegedAuthConfig.isPrivilegedEmail(email)) {
                        PrivilegedAuthConfig.displayNameFor(email)
                    } else {
                        storedLabel
                    }
                    add(
                        SavedLoginAccount(
                            email = email,
                            password = obj.optString("password"),
                            label = label
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(email: String, password: String, remember: Boolean, label: String = "") {
        prefs.edit().apply {
            putBoolean(KEY_REMEMBER, remember)
            putString(KEY_LAST_EMAIL, email.trim())
            if (remember) {
                putString(KEY_EMAIL, email.trim())
                putString(KEY_PASSWORD, password)
            } else {
                remove(KEY_EMAIL)
                remove(KEY_PASSWORD)
            }
            apply()
        }
        if (remember && email.isNotBlank() && password.isNotBlank()) {
            upsertAccount(email = email, password = password, label = label)
        }
    }

    fun upsertAccount(email: String, password: String, label: String = "") {
        val normalized = email.trim()
        if (normalized.isBlank() || password.isBlank()) return
        val current = loadAccounts().toMutableList()
        current.removeAll { it.email.equals(normalized, ignoreCase = true) }
        current.add(
            0,
            SavedLoginAccount(
                email = normalized,
                password = password,
                label = label.trim()
            )
        )
        while (current.size > MAX_ACCOUNTS) {
            current.removeAt(current.lastIndex)
        }
        persistAccounts(current)
        prefs.edit().putString(KEY_LAST_EMAIL, normalized).apply()
    }

    fun removeAccount(email: String) {
        val normalized = email.trim()
        val next = loadAccounts().filterNot { it.email.equals(normalized, ignoreCase = true) }
        persistAccounts(next)
        val last = prefs.getString(KEY_LAST_EMAIL, "").orEmpty()
        if (last.equals(normalized, ignoreCase = true)) {
            prefs.edit().putString(KEY_LAST_EMAIL, next.firstOrNull()?.email.orEmpty()).apply()
        }
    }

    fun markGoogleSignInEmail(email: String) {
        val normalized = email.trim().lowercase()
        if (normalized.isBlank()) return
        val emails = googleSignInEmails().toMutableSet()
        emails.add(normalized)
        persistGoogleEmails(emails)
        prefs.edit().putString(KEY_LAST_EMAIL, email.trim()).apply()
    }

    /** 저장된 계정 목록의 표시 라벨(이름)만 갱신 */
    fun updateAccountLabel(email: String, label: String) {
        val normalized = email.trim()
        if (normalized.isBlank()) return
        val nextLabel = label.trim()
        if (nextLabel.isBlank()) return
        val current = loadAccounts().toMutableList()
        val index = current.indexOfFirst { it.email.equals(normalized, ignoreCase = true) }
        if (index < 0) return
        val existing = current[index]
        if (existing.label == nextLabel) return
        current[index] = existing.copy(label = nextLabel)
        persistAccounts(current)
    }

    fun isGoogleSignInEmail(email: String): Boolean {
        val normalized = email.trim().lowercase()
        if (normalized.isBlank()) return false
        return googleSignInEmails().any { it.equals(normalized, ignoreCase = true) }
    }

    private fun googleSignInEmails(): Set<String> {
        val raw = prefs.getString(KEY_GOOGLE_EMAILS, null) ?: return emptySet()
        return runCatching {
            val array = JSONArray(raw)
            buildSet {
                for (i in 0 until array.length()) {
                    val value = array.optString(i).trim().lowercase()
                    if (value.isNotBlank()) add(value)
                }
            }
        }.getOrDefault(emptySet())
    }

    private fun persistGoogleEmails(emails: Set<String>) {
        val array = JSONArray()
        emails.forEach { array.put(it) }
        prefs.edit().putString(KEY_GOOGLE_EMAILS, array.toString()).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun persistAccounts(accounts: List<SavedLoginAccount>) {
        val array = JSONArray()
        accounts.forEach { account ->
            array.put(
                JSONObject()
                    .put("email", account.email)
                    .put("password", account.password)
                    .put("label", account.label)
            )
        }
        prefs.edit().putString(KEY_ACCOUNTS_JSON, array.toString()).apply()
    }

    /** 예전 단일 계정 저장 → 목록으로 이전 */
    private fun migrateLegacyIfNeeded() {
        if (prefs.contains(KEY_ACCOUNTS_JSON)) return
        val email = prefs.getString(KEY_EMAIL, "").orEmpty().trim()
        val password = prefs.getString(KEY_PASSWORD, "").orEmpty()
        if (email.isBlank() || password.isBlank()) {
            prefs.edit().putString(KEY_ACCOUNTS_JSON, "[]").apply()
            return
        }
        persistAccounts(listOf(SavedLoginAccount(email = email, password = password)))
        prefs.edit().putString(KEY_LAST_EMAIL, email).apply()
    }

    companion object {
        private const val FILE_NAME = "smart_expense_login_creds"
        private const val FILE_NAME_FALLBACK = "smart_expense_login_creds_fallback"
        private const val KEY_EMAIL = "email"
        private const val KEY_PASSWORD = "password"
        private const val KEY_REMEMBER = "remember"
        private const val KEY_ACCOUNTS_JSON = "accounts_json"
        private const val KEY_LAST_EMAIL = "last_email"
        private const val KEY_GOOGLE_EMAILS = "google_emails_json"
        private const val MAX_ACCOUNTS = 8
    }
}
