package com.smartexpense.data.local.bootstrap

import android.content.Context
import com.smartexpense.data.local.ClubDatabaseProvider
import com.smartexpense.data.local.prefs.PostRestoreFlags
import com.smartexpense.data.local.seed.DatabaseSeeder
import com.smartexpense.data.repository.club.ClubSettingsLegacyMigrator
import com.smartexpense.data.settings.SettingsManager
import com.smartexpense.ui.startup.AppStartManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.EntryPointAccessors
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DatabaseBootstrapEntryPoint {
    fun databaseSeeder(): DatabaseSeeder
    fun clubSettingsLegacyMigrator(): ClubSettingsLegacyMigrator
}

@Singleton
class DatabaseBootstrap @Inject constructor(
    @ApplicationContext private val context: Context,
    private val databaseProvider: ClubDatabaseProvider,
    private val settingsManager: SettingsManager,
    private val appStartManager: AppStartManager
) {
    private val initMutex = Mutex()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _initializationError = MutableStateFlow<String?>(null)
    val initializationError: StateFlow<String?> = _initializationError.asStateFlow()

    private val _recoveryNotice = MutableStateFlow<String?>(null)
    val recoveryNotice: StateFlow<String?> = _recoveryNotice.asStateFlow()

    suspend fun initialize() {
        if (_isReady.value) return
        initMutex.withLock {
            if (_isReady.value) return
            runCatching {
                openDatabaseWithRecovery()
                runPostOpenTasks()
                markStartupComplete()
            }.onSuccess {
                _initializationError.value = null
                _isReady.value = true
            }.onFailure {
                lastResortRecovery()
            }
        }
    }

    suspend fun awaitReady() {
        if (_isReady.value) return
        initialize()
    }

    fun consumeRecoveryNotice() {
        _recoveryNotice.value = null
    }

    fun continueAfterInitializationFailure() {
        _initializationError.value = null
        _isReady.value = true
        appStartManager.markBootstrapComplete()
    }

    private suspend fun openDatabaseWithRecovery() {
        withContext(Dispatchers.IO) {
            runCatching {
                databaseProvider.getOrBuild()
            }.onFailure {
                notifyRecoveryIfNeeded()
                databaseProvider.rebuild()
            }
            if (databaseProvider.recoveredFromCorruption) {
                notifyRecoveryIfNeeded()
            }
        }
    }

    private suspend fun runPostOpenTasks() {
        val entryPoint = EntryPointAccessors.fromApplication(
            context,
            DatabaseBootstrapEntryPoint::class.java
        )
        withContext(Dispatchers.IO) {
            runCatching { entryPoint.databaseSeeder().bootstrapOnStartup() }
            runCatching { settingsManager.syncAfterRestore() }
            runCatching { entryPoint.clubSettingsLegacyMigrator().migrateIfNeeded() }
        }
    }

    private suspend fun lastResortRecovery() {
        withContext(Dispatchers.IO) {
            runCatching {
                databaseProvider.rebuild()
                runPostOpenTasks()
            }
        }
        notifyRecoveryIfNeeded()
        markStartupComplete()
        _initializationError.value = null
        _isReady.value = true
    }

    private fun markStartupComplete() {
        appStartManager.markBootstrapComplete()
    }

    private fun notifyRecoveryIfNeeded() {
        _recoveryNotice.value = RECOVERY_MESSAGE
    }

    private fun isRecoverableDatabaseError(error: Throwable): Boolean {
        if (error is IllegalStateException || error is RuntimeException) return true
        val message = generateSequence(error) { it.cause }
            .mapNotNull { it.message?.lowercase() }
            .joinToString(" ")
        return "migration" in message ||
            "corrupt" in message ||
            "database" in message ||
            "sqlite" in message
    }

    companion object {
        const val RECOVERY_MESSAGE = "데이터 파일에 오류가 있어 초기화합니다."
    }
}
