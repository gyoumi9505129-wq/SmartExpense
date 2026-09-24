package com.smartexpense.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.smartexpense.domain.settings.ClubSettingDefaults

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE club_transactions ADD COLUMN target_member_id INTEGER"
        )
        db.execSQL(
            "ALTER TABLE club_transactions ADD COLUMN event_sub_category TEXT"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_club_transactions_target_member_id " +
                "ON club_transactions(target_member_id)"
        )
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE members ADD COLUMN residence_region TEXT NOT NULL DEFAULT ''"
        )
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS club_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                date INTEGER NOT NULL,
                content TEXT NOT NULL,
                details TEXT NOT NULL,
                note TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_club_history_date ON club_history(date)"
        )
    }
}

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS club_accounts (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                bankName TEXT NOT NULL,
                accountNumber TEXT NOT NULL,
                holderName TEXT NOT NULL
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS custom_banks (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                app_name TEXT NOT NULL,
                package_name TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_custom_banks_package_name ON custom_banks(package_name)"
        )
    }
}

val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE club_transactions ADD COLUMN account_id INTEGER")
        db.execSQL("ALTER TABLE club_transactions ADD COLUMN transfer_to_account_id INTEGER")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_club_transactions_account_id ON club_transactions(account_id)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_club_transactions_transfer_to_account_id " +
                "ON club_transactions(transfer_to_account_id)"
        )
    }
}

val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE dues_detail ADD COLUMN paid_amount INTEGER NOT NULL DEFAULT 0"
        )
        db.execSQL(
            "UPDATE dues_detail SET paid_amount = amount WHERE is_paid = 1"
        )
    }
}

val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val createdAt = System.currentTimeMillis()
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS clubs (
                club_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                club_name TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO clubs (club_id, club_name, created_at)
            VALUES (1, '한우리(기본 모임)', $createdAt)
            """.trimIndent()
        )

        val clubScopedTables = listOf(
            "members",
            "member_status_history",
            "yearly_dues",
            "dues_detail",
            "club_transactions",
            "club_history",
            "club_accounts"
        )
        clubScopedTables.forEach { table ->
            db.execSQL("ALTER TABLE $table ADD COLUMN club_id INTEGER NOT NULL DEFAULT 1")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_${table}_club_id ON $table(club_id)"
            )
        }

        db.execSQL("DROP INDEX IF EXISTS index_yearly_dues_member_id_year")
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS index_yearly_dues_club_id_member_id_year
            ON yearly_dues(club_id, member_id, year)
            """.trimIndent()
        )
    }
}

val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val defaultPackagesJson = ClubSettingDefaults.defaultEnabledPackagesJson
            .replace("'", "''")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS club_settings (
                club_id INTEGER NOT NULL PRIMARY KEY,
                bank_parse_enabled_packages_json TEXT NOT NULL,
                dues_payment_filter_year INTEGER,
                dues_payment_filter_member_id INTEGER,
                FOREIGN KEY(club_id) REFERENCES clubs(club_id) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            INSERT INTO club_settings (club_id, bank_parse_enabled_packages_json)
            SELECT club_id, '$defaultPackagesJson' FROM clubs
            """.trimIndent()
        )

        db.execSQL("ALTER TABLE custom_banks ADD COLUMN club_id INTEGER NOT NULL DEFAULT 1")
        db.execSQL("DROP INDEX IF EXISTS index_custom_banks_package_name")
        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS index_custom_banks_club_id_package_name
            ON custom_banks(club_id, package_name)
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_custom_banks_club_id ON custom_banks(club_id)"
        )
    }
}

val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val defaultPackagesJson = ClubSettingDefaults.defaultEnabledPackagesJson
            .replace("'", "''")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS club_settings_new (
                club_id INTEGER NOT NULL,
                setting_key TEXT NOT NULL,
                setting_value TEXT NOT NULL,
                PRIMARY KEY(club_id, setting_key),
                FOREIGN KEY(club_id) REFERENCES clubs(club_id) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            INSERT INTO club_settings_new (club_id, setting_key, setting_value)
            SELECT club_id, 'bank_parse_enabled_packages_json', bank_parse_enabled_packages_json
            FROM club_settings
            """.trimIndent()
        )

        db.execSQL(
            """
            INSERT INTO club_settings_new (club_id, setting_key, setting_value)
            SELECT club_id, 'dues_payment_filter_year', CAST(dues_payment_filter_year AS TEXT)
            FROM club_settings
            WHERE dues_payment_filter_year IS NOT NULL
            """.trimIndent()
        )

        db.execSQL(
            """
            INSERT INTO club_settings_new (club_id, setting_key, setting_value)
            SELECT club_id, 'dues_payment_filter_member_id', CAST(dues_payment_filter_member_id AS TEXT)
            FROM club_settings
            WHERE dues_payment_filter_member_id IS NOT NULL
            """.trimIndent()
        )

        db.execSQL(
            """
            INSERT OR IGNORE INTO club_settings_new (club_id, setting_key, setting_value)
            SELECT c.club_id, 'bank_parse_enabled_packages_json', '$defaultPackagesJson'
            FROM clubs c
            WHERE NOT EXISTS (
                SELECT 1 FROM club_settings_new n WHERE n.club_id = c.club_id
            )
            """.trimIndent()
        )

        db.execSQL("DROP TABLE club_settings")
        db.execSQL("ALTER TABLE club_settings_new RENAME TO club_settings")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_club_settings_club_id ON club_settings(club_id)"
        )
    }
}

val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE clubs ADD COLUMN club_slogan TEXT NOT NULL DEFAULT ''"
        )
    }
}

val MIGRATION_24_25 = object : Migration(24, 25) {
    override fun migrate(db: SupportSQLiteDatabase) {
        fun addUpdatedAt(table: String) {
            db.execSQL(
                "ALTER TABLE $table ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0"
            )
        }
        fun addCloudDocId(table: String) {
            db.execSQL(
                "ALTER TABLE $table ADD COLUMN cloud_doc_id TEXT NOT NULL DEFAULT ''"
            )
        }

        addUpdatedAt("clubs")
        addUpdatedAt("members")
        addCloudDocId("members")
        addUpdatedAt("member_status_history")
        addUpdatedAt("yearly_dues")
        addCloudDocId("yearly_dues")
        addUpdatedAt("dues_detail")
        addCloudDocId("dues_detail")
        addUpdatedAt("dues_payment_history")
        addUpdatedAt("club_transactions")
        addCloudDocId("club_transactions")
        addUpdatedAt("club_history")
        addCloudDocId("club_history")
        addUpdatedAt("club_accounts")
        addCloudDocId("club_accounts")
        addUpdatedAt("custom_banks")
        addUpdatedAt("club_settings")

        db.execSQL("CREATE INDEX IF NOT EXISTS index_members_updated_at ON members(updated_at)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_club_transactions_updated_at ON club_transactions(updated_at)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_yearly_dues_updated_at ON yearly_dues(updated_at)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_dues_detail_updated_at ON dues_detail(updated_at)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_dues_payment_history_updated_at ON dues_payment_history(updated_at)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_club_history_updated_at ON club_history(updated_at)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_club_accounts_updated_at ON club_accounts(updated_at)"
        )
    }
}

val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE dues_payment_history ADD COLUMN linked_transaction_id INTEGER"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_dues_payment_history_linked_transaction_id " +
                "ON dues_payment_history(linked_transaction_id)"
        )
    }
}

val MIGRATION_27_28 = object : Migration(27, 28) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE event_expenses ADD COLUMN cloud_doc_id TEXT NOT NULL DEFAULT ''"
        )
    }
}

val MIGRATION_28_29 = object : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE clubs ADD COLUMN firestore_meeting_id TEXT NOT NULL DEFAULT ''"
        )
        db.execSQL(
            "ALTER TABLE clubs ADD COLUMN membership_status TEXT NOT NULL DEFAULT 'NONE'"
        )
    }
}

val MIGRATION_29_30 = object : Migration(29, 30) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE dues_detail ADD COLUMN is_excluded INTEGER NOT NULL DEFAULT 0"
        )
    }
}
