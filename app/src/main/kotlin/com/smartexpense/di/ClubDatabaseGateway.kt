package com.smartexpense.di

import com.smartexpense.data.local.ClubDatabase
import com.smartexpense.data.local.ClubDatabaseProvider
import com.smartexpense.data.local.dao.club.ClubAccountDao
import com.smartexpense.data.local.dao.club.ClubDao
import com.smartexpense.data.local.dao.club.ClubHistoryDao
import com.smartexpense.data.local.dao.club.ClubSettingsDao
import com.smartexpense.data.local.dao.club.ClubTransactionDao
import com.smartexpense.data.local.dao.club.CustomBankDao
import com.smartexpense.data.local.dao.club.DuesPaymentHistoryDao
import com.smartexpense.data.local.dao.club.EventExpenseDao
import com.smartexpense.data.local.dao.club.MemberDao
import com.smartexpense.data.local.dao.club.MemberStatusHistoryDao
import com.smartexpense.data.local.dao.club.YearlyDuesDao
import com.smartexpense.data.local.entity.club.ClubEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ClubDatabaseGateway @Inject constructor(
    private val provider: ClubDatabaseProvider
) {
    fun database(): ClubDatabase = provider.getOrBuildBlocking()

    fun clubDao(): ClubDao = openDatabase().clubDao()

    fun memberDao(): MemberDao = openDatabase().memberDao()

    fun memberStatusHistoryDao(): MemberStatusHistoryDao = openDatabase().memberStatusHistoryDao()

    fun yearlyDuesDao(): YearlyDuesDao = openDatabase().yearlyDuesDao()

    fun duesPaymentHistoryDao(): DuesPaymentHistoryDao = openDatabase().duesPaymentHistoryDao()

    fun clubTransactionDao(): ClubTransactionDao = openDatabase().clubTransactionDao()

    fun eventExpenseDao(): EventExpenseDao = openDatabase().eventExpenseDao()

    fun clubHistoryDao(): ClubHistoryDao = openDatabase().clubHistoryDao()

    fun clubAccountDao(): ClubAccountDao = openDatabase().clubAccountDao()

    fun customBankDao(): CustomBankDao = openDatabase().customBankDao()

    fun clubSettingsDao(): ClubSettingsDao = openDatabase().clubSettingsDao()

    fun observeSessionGeneration(): StateFlow<Int> = provider.observeSessionGeneration()

    /** DB 세션 교체 시 Flow를 재구독하고, 동일 세션에서는 Room invalidation으로 목록을 갱신합니다. */
    fun observeAllClubs(): Flow<List<ClubEntity>> =
        observeSessionGeneration().flatMapLatest {
            openDatabase().clubDao().getAllClubs()
        }

    fun roomFlow(block: () -> Flow<*>): Flow<*> =
        observeSessionGeneration().flatMapLatest { block() }

    @Suppress("UNCHECKED_CAST")
    fun <T> roomFlowTyped(block: () -> Flow<T>): Flow<T> =
        observeSessionGeneration().flatMapLatest { block() } as Flow<T>

    private fun openDatabase(): ClubDatabase = provider.getOrBuildBlocking()
}
