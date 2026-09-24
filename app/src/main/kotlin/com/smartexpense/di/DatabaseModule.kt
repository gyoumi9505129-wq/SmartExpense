package com.smartexpense.di

import com.smartexpense.data.local.dao.club.ClubDao
import com.smartexpense.data.local.dao.club.ClubAccountDao
import com.smartexpense.data.local.dao.club.ClubHistoryDao
import com.smartexpense.data.local.dao.club.CustomBankDao
import com.smartexpense.data.local.dao.club.ClubTransactionDao
import com.smartexpense.data.local.dao.club.YearlyDuesDao
import com.smartexpense.data.local.dao.club.MemberDao
import com.smartexpense.data.local.dao.club.MemberStatusHistoryDao
import com.smartexpense.data.local.dao.club.ClubSettingsDao
import com.smartexpense.data.local.dao.club.DuesPaymentHistoryDao
import com.smartexpense.data.local.dao.club.EventExpenseDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    fun provideClubDao(gateway: ClubDatabaseGateway): ClubDao = gateway.clubDao()

    @Provides
    fun provideMemberDao(gateway: ClubDatabaseGateway): MemberDao = gateway.memberDao()

    @Provides
    fun provideMemberStatusHistoryDao(gateway: ClubDatabaseGateway): MemberStatusHistoryDao =
        gateway.memberStatusHistoryDao()

    @Provides
    fun provideYearlyDuesDao(gateway: ClubDatabaseGateway): YearlyDuesDao = gateway.yearlyDuesDao()

    @Provides
    fun provideDuesPaymentHistoryDao(gateway: ClubDatabaseGateway): DuesPaymentHistoryDao =
        gateway.duesPaymentHistoryDao()

    @Provides
    fun provideEventExpenseDao(gateway: ClubDatabaseGateway): EventExpenseDao =
        gateway.eventExpenseDao()

    @Provides
    fun provideClubTransactionDao(gateway: ClubDatabaseGateway): ClubTransactionDao =
        gateway.clubTransactionDao()

    @Provides
    fun provideClubHistoryDao(gateway: ClubDatabaseGateway): ClubHistoryDao =
        gateway.clubHistoryDao()

    @Provides
    fun provideClubAccountDao(gateway: ClubDatabaseGateway): ClubAccountDao =
        gateway.clubAccountDao()

    @Provides
    fun provideCustomBankDao(gateway: ClubDatabaseGateway): CustomBankDao =
        gateway.customBankDao()

    @Provides
    fun provideClubSettingsDao(gateway: ClubDatabaseGateway): ClubSettingsDao =
        gateway.clubSettingsDao()
}
