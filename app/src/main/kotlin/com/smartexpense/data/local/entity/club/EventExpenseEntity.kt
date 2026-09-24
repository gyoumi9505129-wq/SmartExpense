package com.smartexpense.data.local.entity.club

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 경조사비 도메인 이력.
 * 장부(`club_transactions`)와 분리되며, 통장 잔액 계산에 사용하지 않습니다.
 * 신규 장부 경조 기장 시 [linkedTransactionId]로 1:1 연결합니다.
 */
@Entity(
    tableName = "event_expenses",
    indices = [
        Index("club_id"),
        Index("member_id"),
        Index("linked_transaction_id"),
        Index("updated_at")
    ]
)
data class EventExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "club_id")
    val clubId: Long,
    @ColumnInfo(name = "member_id")
    val memberId: Long,
    val date: String,
    @ColumnInfo(name = "event_sub_category")
    val eventSubCategory: String,
    val amount: Int,
    val note: String? = null,
    @ColumnInfo(name = "linked_transaction_id")
    val linkedTransactionId: Long? = null,
    /** 시드(Sheet2 등) 이력은 true — 장부 잔액과 무관 */
    @ColumnInfo(name = "is_seed_only", defaultValue = "0")
    val isSeedOnly: Boolean = false,
    @ColumnInfo(name = "updated_at", defaultValue = "0")
    val updatedAt: Long = 0L,
    @ColumnInfo(name = "cloud_doc_id", defaultValue = "")
    val cloudDocId: String = ""
)
