package com.smartexpense.data.local.entity.club

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "member_status_history",
    foreignKeys = [
        ForeignKey(
            entity = MemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["member_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("member_id"), Index("club_id")]
)
data class MemberStatusHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "club_id")
    val clubId: Long,
    @ColumnInfo(name = "member_id")
    val memberId: Long,
    @ColumnInfo(name = "old_status")
    val oldStatus: String,
    @ColumnInfo(name = "new_status")
    val newStatus: String,
    @ColumnInfo(name = "change_date")
    val changeDate: String,
    val reason: String? = null,
    @ColumnInfo(name = "suspension_date")
    val suspensionDate: String? = null,
    @ColumnInfo(name = "updated_at", defaultValue = "0")
    val updatedAt: Long = 0L
)
