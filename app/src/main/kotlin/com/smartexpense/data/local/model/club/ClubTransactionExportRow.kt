package com.smartexpense.data.local.model.club

import androidx.room.ColumnInfo

data class ClubTransactionExportRow(
    val date: String,
    val category: String,
    @ColumnInfo(name = "income_amount")
    val incomeAmount: Int,
    @ColumnInfo(name = "expense_amount")
    val expenseAmount: Int,
    val note: String?,
    @ColumnInfo(name = "member_name")
    val memberName: String?
)
