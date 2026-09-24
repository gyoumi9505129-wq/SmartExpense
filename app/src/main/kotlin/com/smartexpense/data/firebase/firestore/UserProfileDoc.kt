package com.smartexpense.data.firebase.firestore

import com.google.firebase.firestore.DocumentSnapshot

data class UserProfileDoc(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val phone: String = "",
    val profileCompleted: Boolean = false,
    val createdAt: String = "",
    val updatedAt: String = ""
) {
    fun isComplete(): Boolean =
        profileCompleted && displayName.isNotBlank() && phone.isNotBlank()

    fun toMap(): Map<String, Any?> = mapOf(
        "uid" to uid,
        "email" to email,
        "displayName" to displayName,
        "phone" to phone,
        "profileCompleted" to profileCompleted,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )

    companion object {
        fun from(snapshot: DocumentSnapshot): UserProfileDoc =
            UserProfileDoc(
                uid = snapshot.getString("uid") ?: snapshot.id,
                email = snapshot.getString("email").orEmpty(),
                displayName = snapshot.getString("displayName").orEmpty(),
                phone = snapshot.getString("phone").orEmpty(),
                profileCompleted = snapshot.getBoolean("profileCompleted") == true,
                createdAt = snapshot.getString("createdAt").orEmpty(),
                updatedAt = snapshot.getString("updatedAt").orEmpty()
            )
    }
}
