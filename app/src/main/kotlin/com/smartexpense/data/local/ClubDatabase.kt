package com.smartexpense.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.smartexpense.data.local.converter.ClubConverters
import com.smartexpense.data.local.dao.club.ClubAccountDao
import com.smartexpense.data.local.dao.club.ClubDao
import com.smartexpense.data.local.dao.club.ClubHistoryDao
import com.smartexpense.data.local.dao.club.ClubSettingsDao
import com.smartexpense.data.local.dao.club.CustomBankDao
import com.smartexpense.data.local.dao.club.ClubTransactionDao
import com.smartexpense.data.local.dao.club.DuesPaymentHistoryDao
import com.smartexpense.data.local.dao.club.EventExpenseDao
import com.smartexpense.data.local.dao.club.MemberDao
import com.smartexpense.data.local.dao.club.MemberStatusHistoryDao
import com.smartexpense.data.local.dao.club.YearlyDuesDao
import com.smartexpense.data.local.entity.club.ClubAccountEntity
import com.smartexpense.data.local.entity.club.ClubEntity
import com.smartexpense.data.local.entity.club.ClubHistoryEntity
import com.smartexpense.data.local.entity.club.ClubSettingEntity
import com.smartexpense.data.local.entity.club.CustomBankEntity
import com.smartexpense.data.local.entity.club.ClubTransactionEntity
import com.smartexpense.data.local.entity.club.DuesDetailEntity
import com.smartexpense.data.local.entity.club.DuesPaymentHistoryEntity
import com.smartexpense.data.local.entity.club.EventExpenseEntity
import com.smartexpense.data.local.entity.club.MemberEntity
import com.smartexpense.data.local.entity.club.MemberStatusHistoryEntity
import com.smartexpense.data.local.entity.club.YearlyDuesEntity

@Database(
    entities = [
        ClubEntity::class,
        MemberEntity::class,
        MemberStatusHistoryEntity::class,
        YearlyDuesEntity::class,
        DuesDetailEntity::class,
        DuesPaymentHistoryEntity::class,
        ClubTransactionEntity::class,
        EventExpenseEntity::class,
        ClubHistoryEntity::class,
        ClubAccountEntity::class,
        CustomBankEntity::class,
        ClubSettingEntity::class
    ],
    version = ClubDatabase.SCHEMA_VERSION,
    exportSchema = true
)
@TypeConverters(ClubConverters::class)
abstract class ClubDatabase : RoomDatabase() {
    abstract fun clubDao(): ClubDao
    abstract fun memberDao(): MemberDao
    abstract fun memberStatusHistoryDao(): MemberStatusHistoryDao
    abstract fun yearlyDuesDao(): YearlyDuesDao
    abstract fun duesPaymentHistoryDao(): DuesPaymentHistoryDao
    abstract fun clubTransactionDao(): ClubTransactionDao
    abstract fun eventExpenseDao(): EventExpenseDao
    abstract fun clubHistoryDao(): ClubHistoryDao
    abstract fun clubAccountDao(): ClubAccountDao
    abstract fun customBankDao(): CustomBankDao
    abstract fun clubSettingsDao(): ClubSettingsDao

    companion object {
        const val DATABASE_NAME = "club_management.db"
        const val SCHEMA_VERSION = 30
    }
}
