package com.smartexpense.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class ClubDatabaseProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Volatile
    private var instance: ClubDatabase? = null
    private val mutex = Mutex()

    @Volatile
    var recoveredFromCorruption: Boolean = false
        private set

    private val sessionGeneration = MutableStateFlow(0)
    fun observeSessionGeneration(): StateFlow<Int> = sessionGeneration.asStateFlow()

    fun peek(): ClubDatabase? = instance

    fun getOrBuildBlocking(): ClubDatabase = runBlocking(Dispatchers.IO) {
        getOrBuild()
    }

    suspend fun getOrBuild(): ClubDatabase = mutex.withLock {
        val current = instance
        if (current != null && current.isOpen) return current
        if (current != null) {
            closeQuietly(current)
            bumpSessionGeneration()
        }
        instance = null
        buildSafely().also { instance = it }
    }

    suspend fun rebuild(): ClubDatabase = mutex.withLock {
        closeQuietly(instance)
        instance = null
        bumpSessionGeneration()
        ClubDatabaseResetHelper.deleteDatabaseFiles(context)
        buildSafely().also { instance = it }
    }

    fun closeAndInvalidate() {
        runBlocking(Dispatchers.IO) {
            mutex.withLock {
                if (instance != null) {
                    closeQuietly(instance)
                    instance = null
                    bumpSessionGeneration()
                }
            }
        }
    }

    private suspend fun buildSafely(): ClubDatabase = withContext(Dispatchers.IO) {
        ClubDatabaseResetHelper.resetIfIncompatible(context)
        createDatabaseWithRecovery()
    }

    private fun createDatabaseWithRecovery(): ClubDatabase {
        var lastError: Throwable? = null
        repeat(MAX_OPEN_ATTEMPTS) { attempt ->
            if (attempt > 0) {
                closeQuietly(instance)
                instance = null
                ClubDatabaseResetHelper.deleteDatabaseFiles(context)
                recoveredFromCorruption = true
            }
            runCatching {
                return ClubDatabaseFactory.build(context)
            }.onFailure { error ->
                lastError = error
            }
        }
        throw IllegalStateException(
            "데이터베이스를 초기화하지 못했습니다.",
            lastError
        )
    }

    private fun closeQuietly(database: ClubDatabase?) {
        runCatching { database?.close() }
    }

    private fun bumpSessionGeneration() {
        sessionGeneration.value++
    }

    companion object {
        private const val MAX_OPEN_ATTEMPTS = 3
    }
}
