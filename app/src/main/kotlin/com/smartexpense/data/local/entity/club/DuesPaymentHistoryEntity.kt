package com.smartexpense.data.local.entity.club

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 회비 반기(회차) 단위의 실제 분할 납부 이력.
 * [DuesDetailEntity.paidAmount]/[DuesDetailEntity.payDate]는 이 테이블의 집계 캐시이다.
 * [linkedTransactionId]는 장부 수입 1건과 1:1로 연결된다.
 */
@Entity(
    tableName = "dues_payment_history",
    foreignKeys = [
        ForeignKey(
            entity = DuesDetailEntity::class,
            parentColumns = ["id"],
            childColumns = ["dues_detail_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("dues_detail_id"),
        Index("club_id"),
        Index("updated_at"),
        Index("linked_transaction_id")
    ]
)
data class DuesPaymentHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "club_id")
    val clubId: Long,
    @ColumnInfo(name = "dues_detail_id")
    val duesDetailId: Long,
    @ColumnInfo(name = "pay_date")
    val payDate: String,
    val amount: Long,
    @ColumnInfo(name = "updated_at", defaultValue = "0")
    val updatedAt: Long = 0L,
    @ColumnInfo(name = "linked_transaction_id")
    val linkedTransactionId: Long? = null
)
