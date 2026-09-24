package com.smartexpense.data.local.entity.club

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "club_accounts",
    indices = [Index("club_id"), Index("updated_at")]
)
data class ClubAccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "club_id")
    val clubId: Long,
    val bankName: String,
    val accountNumber: String,
    val holderName: String,
    @ColumnInfo(name = "updated_at", defaultValue = "0")
    val updatedAt: Long = 0L,
    @ColumnInfo(name = "cloud_doc_id", defaultValue = "")
    val cloudDocId: String = ""
)

fun ClubAccountEntity.toDisplayLabel(): String =
    "$bankName · $accountNumber ($holderName)"
