package com.smartexpense.data.firebase.firestore

import com.google.firebase.firestore.DocumentSnapshot

enum class JoinRequestStatus {
    PENDING,
    APPROVED,
    REJECTED,
    WITHDRAWN;

    companion object {
        fun from(raw: String?): JoinRequestStatus =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: PENDING
    }
}

data class JoinRequestDoc(
    val id: String = "",
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val phone: String = "",
    val message: String = "",
    val status: JoinRequestStatus = JoinRequestStatus.PENDING,
    val requestedAt: String = "",
    val decidedAt: String = "",
    val decidedBy: String = "",
    /** 승인 후 회원 장부 등록 여부. 필드가 없거나 false면 아직 미등록. */
    val profileCompleted: Boolean = false
) {
    fun needsMemberProfile(): Boolean =
        status == JoinRequestStatus.APPROVED && !profileCompleted

    fun toMap(): Map<String, Any?> = mapOf(
        "uid" to uid,
        "email" to email,
        "displayName" to displayName,
        "phone" to phone,
        "message" to message,
        "status" to status.name,
        "requestedAt" to requestedAt,
        "decidedAt" to decidedAt,
        "decidedBy" to decidedBy,
        "profileCompleted" to profileCompleted
    )

    companion object {
        fun from(snapshot: DocumentSnapshot): JoinRequestDoc =
            JoinRequestDoc(
                id = snapshot.id,
                uid = snapshot.getString("uid").orEmpty(),
                email = snapshot.getString("email").orEmpty(),
                displayName = snapshot.getString("displayName").orEmpty(),
                phone = snapshot.getString("phone").orEmpty(),
                message = snapshot.getString("message").orEmpty(),
                status = JoinRequestStatus.from(snapshot.getString("status")),
                requestedAt = snapshot.getString("requestedAt").orEmpty(),
                decidedAt = snapshot.getString("decidedAt").orEmpty(),
                decidedBy = snapshot.getString("decidedBy").orEmpty(),
                profileCompleted = snapshot.getBoolean("profileCompleted") == true
            )
    }
}
