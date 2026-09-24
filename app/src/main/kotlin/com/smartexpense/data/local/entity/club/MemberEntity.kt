package com.smartexpense.data.local.entity.club

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "members",
    indices = [Index("club_id"), Index("updated_at")]
)
data class MemberEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "club_id")
    val clubId: Long,
    @ColumnInfo(name = "join_date")
    val joinDate: String,
    val name: String,
    @ColumnInfo(name = "birth_date")
    val birthDate: String,
    val address: String,
    @ColumnInfo(name = "detail_address", defaultValue = "")
    val detailAddress: String = "",
    @ColumnInfo(name = "residence_region", defaultValue = "")
    val residenceRegion: String = "",
    val phone: String,
    @ColumnInfo(defaultValue = "")
    val email: String = "",
    val status: MemberStatus,
    val role: MemberRole,
    @ColumnInfo(name = "is_lunar_birth", defaultValue = "0")
    val isLunarBirth: Boolean = false,
    @ColumnInfo(name = "suspension_date")
    val suspensionDate: String? = null,
    @ColumnInfo(name = "updated_at", defaultValue = "0")
    val updatedAt: Long = 0L,
    @ColumnInfo(name = "cloud_doc_id", defaultValue = "")
    val cloudDocId: String = ""
)
