package com.smartexpense.data.model.bank

data class BankNotificationDraft(
    val amount: Int,
    val memo: String,
    val sourcePackage: String,
    val receivedAtMillis: Long = System.currentTimeMillis()
)
