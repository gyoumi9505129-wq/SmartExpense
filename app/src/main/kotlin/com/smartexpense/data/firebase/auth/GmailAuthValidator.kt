package com.smartexpense.data.firebase.auth

object GmailAuthValidator {
    private val gmailPattern = Regex(
        "^[a-z0-9._%+-]+@(gmail\\.com|googlemail\\.com)$",
        RegexOption.IGNORE_CASE
    )

    fun normalizeEmail(raw: String): String = raw.trim().lowercase()

    fun validateEmail(raw: String): String? {
        val email = normalizeEmail(raw)
        if (email.isBlank()) return "Gmail 주소를 입력해 주세요."
        if (!gmailPattern.matches(email)) return "Gmail(@gmail.com) 주소만 사용할 수 있습니다."
        return null
    }

    fun validatePassword(raw: String): String? {
        if (raw.length < 8) return "비밀번호는 8자 이상이어야 합니다."
        return null
    }

    fun validatePasswordConfirm(password: String, confirm: String): String? {
        if (password != confirm) return "비밀번호 확인이 일치하지 않습니다."
        return null
    }

    fun validateDisplayName(raw: String): String? {
        if (raw.trim().length < 2) return "이름을 2자 이상 입력해 주세요."
        return null
    }

    fun validatePhone(raw: String): String? {
        val digits = raw.filter { it.isDigit() }
        if (digits.length < 10 || digits.length > 11) {
            return "연락처를 10~11자리 숫자로 입력해 주세요."
        }
        return null
    }
}
