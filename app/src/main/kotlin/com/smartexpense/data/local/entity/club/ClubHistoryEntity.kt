package com.smartexpense.data.local.entity.club

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "club_history",
    indices = [Index("date"), Index("club_id"), Index("updated_at")]
)
data class ClubHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "club_id")
    val clubId: Long,
    val date: Long,
    val content: String,
    val details: String,
    val note: String? = null,
    @ColumnInfo(name = "updated_at", defaultValue = "0")
    val updatedAt: Long = 0L,
    @ColumnInfo(name = "cloud_doc_id", defaultValue = "")
    val cloudDocId: String = ""
)
