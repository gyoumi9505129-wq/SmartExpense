package com.smartexpense.data.firebase

import com.smartexpense.data.firebase.auth.GmailAuthValidator

/**
 * 시스템관리자 전용 로그인.
 * 회원가입 없이 이메일 + 고정 비밀번호로 로그인하며,
 * Firebase에 계정이 없으면 자동 생성합니다.
 */
object PrivilegedAuthConfig {
    /** 시스템관리자 고정 비밀번호 */
    const val FIXED_PASSWORD = "gksdnfl2001!"

    /** 예전에 앱에서 쓰던 비밀번호 — 로그인만 호환 */
    private const val LEGACY_FIXED_PASSWORD = "gksdnfl2001@"

    val acceptedPasswords: Set<String> = setOf(FIXED_PASSWORD, LEGACY_FIXED_PASSWORD)

    fun isAcceptedPassword(password: String): Boolean =
        acceptedPasswords.contains(password)

    fun isPrivilegedEmail(email: String?): Boolean {
        val normalized = GmailAuthValidator.normalizeEmail(email.orEmpty())
        if (normalized.isBlank()) return false
        return SystemAdminConfig.emails.contains(normalized)
    }

    /**
     * 한 글자 오타(예: gyoumi950512 → gyoumi9505129)를 시스템관리자 이메일로 맞춘다.
     * 관리자 고정 비밀번호로 로그인할 때만 사용한다.
     */
    fun resolvePrivilegedLoginEmail(typedEmail: String): String? {
        val normalized = GmailAuthValidator.normalizeEmail(typedEmail)
        if (normalized.isBlank()) return null
        if (SystemAdminConfig.emails.contains(normalized)) return normalized
        val typedLocal = normalized.substringBefore('@')
        val typedDomain = normalized.substringAfter('@', "")
        if (typedLocal.isBlank() || typedDomain.isBlank()) return null
        return SystemAdminConfig.emails.firstOrNull { admin ->
            val adminLocal = admin.substringBefore('@')
            val adminDomain = admin.substringAfter('@')
            domainsCompatible(typedDomain, adminDomain) && isOneCharLocalTypo(typedLocal, adminLocal)
        }
    }

    private fun domainsCompatible(left: String, right: String): Boolean {
        if (left == right) return true
        val gmail = setOf("gmail.com", "googlemail.com")
        return left in gmail && right in gmail
    }

    private fun isOneCharLocalTypo(typed: String, expected: String): Boolean {
        if (typed == expected) return true
        if (expected.startsWith(typed) && expected.length == typed.length + 1) return true
        if (typed.startsWith(expected) && typed.length == expected.length + 1) return true
        if (typed.length == expected.length) {
            return typed.zip(expected).count { it.first != it.second } == 1
        }
        val (shorter, longer) = if (typed.length < expected.length) typed to expected else expected to typed
        if (longer.length != shorter.length + 1) return false
        var skipped = false
        var i = 0
        var j = 0
        while (i < shorter.length && j < longer.length) {
            if (shorter[i] == longer[j]) {
                i++
                j++
            } else if (!skipped) {
                skipped = true
                j++
            } else {
                return false
            }
        }
        return true
    }

    /** 시스템관리자 (UID 또는 이메일 allowlist) */
    fun isPrivilegedAccount(uid: String?, email: String?): Boolean =
        SystemAdminConfig.matches(uid, email)

    fun displayNameFor(email: String): String {
        val normalized = GmailAuthValidator.normalizeEmail(email)
        return if (SystemAdminConfig.emails.contains(normalized)) {
            SystemAdminConfig.DISPLAY_NAME
        } else {
            "관리자"
        }
    }
}
