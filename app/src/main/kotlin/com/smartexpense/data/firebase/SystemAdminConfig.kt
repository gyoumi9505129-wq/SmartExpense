package com.smartexpense.data.firebase

/**
 * 시스템관리자 고정 allowlist (전체 권한: 편집·동기화·위험 메뉴·운영관리자 지정).
 * Firestore `isSystemAdmin()` 와 이메일을 동일하게 유지하세요.
 */
object SystemAdminConfig {
    /** Firebase Auth UID 목록 */
    val uids: Set<String> = emptySet()

    /** 구글 계정 이메일(소문자 비교) */
    val emails: Set<String> = setOf(
        "gyoumi9505129@gmail.com"
    )

    const val DISPLAY_NAME = "시스템관리자"

    fun matches(uid: String?, email: String?): Boolean {
        val normalizedUid = uid?.trim().orEmpty()
        if (normalizedUid.isNotEmpty() && uids.contains(normalizedUid)) return true
        val normalizedEmail = email?.trim()?.lowercase().orEmpty()
        return normalizedEmail.isNotEmpty() && emails.contains(normalizedEmail)
    }

    /** 모임 회원 명단·인원 수에서 제외할 기록인지. */
    fun isClubMembershipRecord(email: String?, uid: String? = null, name: String? = null): Boolean {
        if (matches(uid, email)) return true
        return name?.trim() == DISPLAY_NAME
    }
}
