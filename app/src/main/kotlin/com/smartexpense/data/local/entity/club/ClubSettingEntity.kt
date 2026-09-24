package com.smartexpense.data.local.entity.club

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "club_settings",
    primaryKeys = ["club_id", "setting_key"],
    foreignKeys = [
        ForeignKey(
            entity = ClubEntity::class,
            parentColumns = ["club_id"],
            childColumns = ["club_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("club_id")]
)
data class ClubSettingEntity(
    @ColumnInfo(name = "club_id")
    val clubId: Long,
    @ColumnInfo(name = "setting_key")
    val settingKey: String,
    @ColumnInfo(name = "setting_value")
    val settingValue: String,
    @ColumnInfo(name = "updated_at", defaultValue = "0")
    val updatedAt: Long = 0L
)
