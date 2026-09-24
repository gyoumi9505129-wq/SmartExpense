package com.smartexpense.data.local.entity.club

enum class ClubMembershipStatus(val storageValue: String) {
    NONE("NONE"),
    PENDING("PENDING"),
    APPROVED("APPROVED"),
    OWNER("OWNER");

    companion object {
        fun from(raw: String?): ClubMembershipStatus =
            entries.firstOrNull { it.storageValue.equals(raw?.trim(), ignoreCase = true) } ?: NONE
    }
}
