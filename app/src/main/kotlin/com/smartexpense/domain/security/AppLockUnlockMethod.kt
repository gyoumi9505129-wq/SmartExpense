package com.smartexpense.domain.security

enum class AppLockUnlockMethod(val storageValue: String, val label: String) {
    PIN("pin", "PIN"),
    PATTERN("pattern", "패턴");

    companion object {
        fun fromStorage(value: String?): AppLockUnlockMethod? =
            entries.firstOrNull { it.storageValue == value }

        /** 예전 생체-전용 값은 PIN으로 내리고, 생체는 별도 옵션으로 취급한다. */
        fun resolve(stored: String?): AppLockUnlockMethod {
            return when (stored) {
                PATTERN.storageValue -> PATTERN
                else -> PIN
            }
        }

        fun biometricWasPrimary(stored: String?): Boolean = stored == "biometric"
    }
}
