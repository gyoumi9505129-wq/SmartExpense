package com.smartexpense.data.local.entity.club

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dues_detail",
    foreignKeys = [
        ForeignKey(
            entity = YearlyDuesEntity::class,
            parentColumns = ["id"],
            childColumns = ["yearly_dues_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("yearly_dues_id"), Index("club_id"), Index("updated_at")]
)
data class DuesDetailEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "club_id")
    val clubId: Long,
    @ColumnInfo(name = "yearly_dues_id")
    val yearlyDuesId: Long = 0,
    @ColumnInfo(name = "term_label")
    val termLabel: String,
    val amount: Int,
    @ColumnInfo(name = "paid_amount")
    val paidAmount: Long = 0,
    @ColumnInfo(name = "is_paid")
    val isPaid: Boolean,
    @ColumnInfo(name = "is_excluded", defaultValue = "0")
    val isExcluded: Boolean = false,
    @ColumnInfo(name = "pay_date")
    val payDate: String,
    @ColumnInfo(name = "updated_at", defaultValue = "0")
    val updatedAt: Long = 0L,
    @ColumnInfo(name = "cloud_doc_id", defaultValue = "")
    val cloudDocId: String = ""
)
