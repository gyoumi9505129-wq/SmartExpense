package com.smartexpense.domain.user

import com.smartexpense.data.firebase.SystemAdminConfig
import com.smartexpense.data.firebase.firestore.MeetingDoc

/**
 * 앱에 표시하는 로그인 사용자 권한.
 * 우선순위: 시스템관리자 > 모임관리자 > 일반회원
 */
enum class UserRole(
    val emoji: String,
    val label: String
) {
    SYSTEM_ADMIN(emoji = "👑", label = "시스템관리자"),
    CLUB_ADMIN(emoji = "⚡", label = "모임관리자"),
    MEMBER(emoji = "👤", label = "일반회원");

    companion object {
        fun resolve(
            uid: String?,
            email: String?,
            meeting: MeetingDoc? = null
        ): UserRole {
            if (SystemAdminConfig.matches(uid, email)) return SYSTEM_ADMIN
            val normalizedUid = uid?.trim().orEmpty()
            if (normalizedUid.isNotEmpty() && meeting != null) {
                if (meeting.ownerUid == normalizedUid) return CLUB_ADMIN
                if (meeting.adminUid.isNotBlank() && meeting.adminUid == normalizedUid) {
                    return CLUB_ADMIN
                }
            }
            return MEMBER
        }

        fun resolve(
            isSystemAdmin: Boolean,
            isClubAdmin: Boolean
        ): UserRole = when {
            isSystemAdmin -> SYSTEM_ADMIN
            isClubAdmin -> CLUB_ADMIN
            else -> MEMBER
        }
    }
}
