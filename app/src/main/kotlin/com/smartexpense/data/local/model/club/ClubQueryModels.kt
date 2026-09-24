package com.smartexpense.data.local.model.club

import androidx.room.ColumnInfo
import com.smartexpense.data.local.entity.club.DuesTerm
import com.smartexpense.data.local.entity.club.MemberRole
import com.smartexpense.data.local.entity.club.MemberStatus

data class MemberDuesStatusRow(
    @ColumnInfo(name = "member_id")
    val memberId: Long,
    @ColumnInfo(name = "member_name")
    val memberName: String,
    val role: MemberRole,
    val status: MemberStatus,
    val year: Int,
    val term: DuesTerm?,
    @ColumnInfo(name = "paid_amount")
    val paidAmount: Int,
    @ColumnInfo(name = "pay_date")
    val payDate: String?
)

data class YearlyDuesAggregateRow(
    val year: Int,
    val term: DuesTerm,
    @ColumnInfo(name = "total_amount")
    val totalAmount: Int,
    @ColumnInfo(name = "payer_count")
    val payerCount: Int
)

data class YearlyClubTransactionSummaryRow(
    val year: Int,
    @ColumnInfo(name = "total_income")
    val totalIncome: Int,
    @ColumnInfo(name = "total_expense")
    val totalExpense: Int
)

data class ClubTransactionCategorySummaryRow(
    val category: String,
    @ColumnInfo(name = "total_income")
    val totalIncome: Int,
    @ColumnInfo(name = "total_expense")
    val totalExpense: Int
)

data class EventExpenseSummaryRow(
    @ColumnInfo(name = "memberId")
    val memberId: Long,
    @ColumnInfo(name = "memberName")
    val memberName: String,
    @ColumnInfo(name = "memberStatus")
    val memberStatus: MemberStatus,
    @ColumnInfo(name = "totalAmount")
    val totalAmount: Long
)
