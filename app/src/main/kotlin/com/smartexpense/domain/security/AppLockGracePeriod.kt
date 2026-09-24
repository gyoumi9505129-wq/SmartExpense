package com.smartexpense.domain.security

enum class AppLockGracePeriod(val millis: Long, val label: String) {
    IMMEDIATE(0L, "즉시"),
    ONE_MINUTE(60_000L, "1분 후"),
    FIVE_MINUTES(300_000L, "5분 후"),
    ONE_HOUR(3_600_000L, "1시간 후");

    companion object {
        val DEFAULT = FIVE_MINUTES

        fun fromMillis(value: Long): AppLockGracePeriod =
            entries.firstOrNull { it.millis == value } ?: DEFAULT
    }
}
