package com.smartexpense.data.firebase

import com.smartexpense.data.firebase.auth.GmailAuthValidator

/**
 * 한우리 모임 공용(조회 전용) 계정.
 * 승인 요청 없이 일반회원 권한으로 한우리 실데이터를 열람할 수 있다.
 * 시스템관리자·모임관리자와 달리 편집·동기화·승인 처리는 불가.
 */
object HanuriSharedViewerConfig {
    val emails: Set<String> = setOf(
        "hanuri20011001@gmail.com"
    )

    const val DISPLAY_NAME = "한우리 공용"

    fun matches(uid: String?, email: String?): Boolean {
        val normalized = GmailAuthValidator.normalizeEmail(email.orEmpty())
        return normalized.isNotEmpty() && emails.contains(normalized)
    }

    fun isHanuriMeetingName(name: String?): Boolean {
        val n = name?.trim().orEmpty()
        if (n.isEmpty()) return false
        return n == "한우리" || n.startsWith("한우리(")
    }
}
