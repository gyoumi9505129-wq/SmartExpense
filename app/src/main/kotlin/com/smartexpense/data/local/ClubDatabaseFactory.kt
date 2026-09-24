package com.smartexpense.data.local

import android.content.Context
import androidx.room.Room
import com.smartexpense.data.local.migration.MIGRATION_24_25
import com.smartexpense.data.local.migration.MIGRATION_25_26
import com.smartexpense.data.local.migration.MIGRATION_27_28
import com.smartexpense.data.local.migration.MIGRATION_28_29
import com.smartexpense.data.local.migration.MIGRATION_29_30

object ClubDatabaseFactory {
    fun build(context: Context): ClubDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            ClubDatabase::class.java,
            ClubDatabase.DATABASE_NAME
        )
            .addMigrations(
                MIGRATION_24_25,
                MIGRATION_25_26,
                MIGRATION_27_28,
                MIGRATION_28_29,
                MIGRATION_29_30
            )
            .fallbackToDestructiveMigration()
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }
}
