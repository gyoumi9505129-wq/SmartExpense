package com.smartexpense.data.local.entity.club

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "club_transactions",
    indices = [
        Index("date"),
        Index("type"),
        Index("target_member_id"),
        Index("account_id"),
        Index("transfer_to_account_id"),
        Index("club_id"),
        Index("linked_dues_detail_id"),
        Index("updated_at")
    ]
)
data class ClubTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "club_id")
    val clubId: Long,
    val date: String,
    val type: ClubTransactionType,
    val category: String,
    @ColumnInfo(name = "income_amount")
    val incomeAmount: Int = 0,
    @ColumnInfo(name = "expense_amount")
    val expenseAmount: Int = 0,
    val note: String? = null,
    @ColumnInfo(name = "balance_after")
    val balanceAfter: Int? = null,
    @ColumnInfo(name = "target_member_id")
    val targetMemberId: Long? = null,
    @ColumnInfo(name = "event_sub_category")
    val eventSubCategory: String? = null,
    @ColumnInfo(name = "receipt_path")
    val receiptPath: String? = null,
    @ColumnInfo(name = "account_id")
    val accountId: Int? = null,
    @ColumnInfo(name = "transfer_to_account_id")
    val transferToAccountId: Int? = null,
    @ColumnInfo(name = "linked_dues_detail_id")
    val linkedDuesDetailId: Long? = null,
    @ColumnInfo(name = "updated_at", defaultValue = "0")
    val updatedAt: Long = 0L,
    @ColumnInfo(name = "cloud_doc_id", defaultValue = "")
    val cloudDocId: String = ""
)
