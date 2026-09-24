package com.smartexpense.data.local.prefs

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

object DataStoreResetHelper {
    private const val DATASTORE_DIR = "datastore"
    private const val PREFS_NAME = "smart_expense_prefs"

    fun clearPreferences(context: Context) {
        runCatching {
            val datastoreDir = File(context.filesDir, DATASTORE_DIR)
            if (!datastoreDir.exists()) return
            datastoreDir.listFiles()?.forEach { file ->
                if (file.name.startsWith(PREFS_NAME)) {
                    file.delete()
                }
            }
        }
    }

    fun clearIfCorrupted(context: Context, error: Throwable) {
        if (isCorruptionError(error)) {
            clearPreferences(context)
        }
    }

    private fun isCorruptionError(error: Throwable): Boolean =
        generateSequence(error) { it.cause }.any { throwable ->
            // IllegalStateException 은 비즈니스 예외와 혼동되므로 포함하지 않음.
            // (잘못된 wipe 시 SELECTED_MEETING_ID 등 세션 키가 통째로 사라질 수 있음)
            throwable is CorruptionException
        }
}

fun DataStore<Preferences>.safePreferences(context: Context): Flow<Preferences> =
    data.catch { error ->
        DataStoreResetHelper.clearIfCorrupted(context, error)
        emit(emptyPreferences())
    }

suspend fun DataStore<Preferences>.safePreferencesFirst(context: Context): Preferences =
    runCatching { data.first() }.getOrElse { error ->
        DataStoreResetHelper.clearIfCorrupted(context, error)
        emptyPreferences()
    }

suspend fun DataStore<Preferences>.safeEdit(
    context: Context,
    transform: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit
) {
    runCatching {
        edit(transform)
    }.onFailure { error ->
        DataStoreResetHelper.clearIfCorrupted(context, error)
        runCatching { edit(transform) }
    }
}
