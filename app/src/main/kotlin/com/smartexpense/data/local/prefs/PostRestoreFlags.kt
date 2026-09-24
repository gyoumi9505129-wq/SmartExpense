package com.smartexpense.data.local.prefs

import android.content.Context

object PostRestoreFlags {
    private const val PREFS_NAME = "restore_settings"
    private const val KEY_PENDING = "pending_post_restore"

    fun isPending(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PENDING, false)

    fun markPending(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PENDING, true)
            .apply()
    }

    fun clearPending(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PENDING)
            .apply()
    }
}
