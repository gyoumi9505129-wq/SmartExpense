package com.smartexpense.data.local.entity.club

enum class DuesPaymentMethod {
    MONTHLY,
    QUARTERLY,
    HALF_YEARLY,
    YEARLY;

    companion object {
        val DEFAULT = HALF_YEARLY

        fun fromStorage(raw: String?): DuesPaymentMethod =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}
