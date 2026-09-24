package com.smartexpense.data.local.entity.club

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clubs")
data class ClubEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "club_id")
    val clubId: Long = 0,
    @ColumnInfo(name = "club_name")
    val clubName: String,
    @ColumnInfo(name = "club_slogan", defaultValue = "")
    val clubSlogan: String = "",
    @ColumnInfo(name = "firestore_meeting_id", defaultValue = "")
    val firestoreMeetingId: String = "",
    @ColumnInfo(name = "membership_status", defaultValue = "NONE")
    val membershipStatus: String = ClubMembershipStatus.NONE.storageValue,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at", defaultValue = "0")
    val updatedAt: Long = 0L
)
