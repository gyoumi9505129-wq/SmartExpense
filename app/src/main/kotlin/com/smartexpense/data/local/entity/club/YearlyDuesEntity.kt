package com.smartexpense.data.local.entity.club

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "yearly_dues",
    foreignKeys = [
        ForeignKey(
            entity = MemberEntity::class,
            parentColumns = ["id"],
            childColumns = ["member_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("member_id"),
        Index("club_id"),
        Index(value = ["club_id", "member_id", "year"], unique = true),
        Index("updated_at")
    ]
)
data class YearlyDuesEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "club_id")
    val clubId: Long,
    @ColumnInfo(name = "member_id")
    val memberId: Long,
    val year: Int,
    @ColumnInfo(name = "total_target_amount")
    val totalTargetAmount: Int,
    @ColumnInfo(name = "payment_method")
    val paymentMethod: DuesPaymentMethod,
    @ColumnInfo(name = "updated_at", defaultValue = "0")
    val updatedAt: Long = 0L,
    @ColumnInfo(name = "cloud_doc_id", defaultValue = "")
    val cloudDocId: String = ""
)
