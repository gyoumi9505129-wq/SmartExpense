package com.smartexpense.data.local.prefs

import android.content.Context

object AppRestartFlags {
    const val EXTRA_SKIP_INITIAL_AUTH = "com.smartexpense.EXTRA_SKIP_INITIAL_AUTH"
    const val AUTH_GRACE_PERIOD_MS = 15_000L

    private const val PREFS_NAME = "app_restart"
    private const val KEY_PENDING = "pending_process_restart"
    private const val KEY_SKIP_INITIAL_AUTH = "skip_initial_auth"
    private const val KEY_LAST_AUTH_MS = "last_authenticated_at_ms"

    data class RestartState(
        val pendingRestart: Boolean,
        val skipInitialAuth: Boolean
    )

    fun markPendingRestart(context: Context, skipInitialAuth: Boolean = false) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PENDING, true)
            .putBoolean(KEY_SKIP_INITIAL_AUTH, skipInitialAuth)
            .apply()
    }

    fun markSkipInitialAuth(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SKIP_INITIAL_AUTH, true)
            .apply()
    }

    fun consumeRestartState(context: Context): RestartState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val pending = prefs.getBoolean(KEY_PENDING, false)
        val skipAuth = prefs.getBoolean(KEY_SKIP_INITIAL_AUTH, false)
        prefs.edit()
            .remove(KEY_PENDING)
            .remove(KEY_SKIP_INITIAL_AUTH)
            .apply()
        return RestartState(pendingRestart = pending, skipInitialAuth = skipAuth)
    }

    fun recordAuthentication(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_AUTH_MS, System.currentTimeMillis())
            .apply()
    }

    fun isWithinAuthGracePeriod(context: Context, graceMs: Long = AUTH_GRACE_PERIOD_MS): Boolean {
        val last = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_AUTH_MS, 0L)
        return last > 0L && System.currentTimeMillis() - last < graceMs
    }

    fun clearAuthGracePeriod(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LAST_AUTH_MS)
            .apply()
    }

    /** 인증 세션·재시작 우회·Grace Period 플래그를 모두 삭제합니다. */
    fun clearAllAuthSession(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PENDING)
            .remove(KEY_SKIP_INITIAL_AUTH)
            .remove(KEY_LAST_AUTH_MS)
            .apply()
    }
}
