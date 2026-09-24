package com.smartexpense.ui.settings.account

data class ClubAccountFormState(
    val bankName: String = "",
    val accountNumber: String = "",
    val holderName: String = ""
) {
    val canSave: Boolean
        get() = bankName.isNotBlank() &&
            accountNumber.isNotBlank() &&
            holderName.isNotBlank()
}

data class ClubAccountUiState(
    val accounts: List<com.smartexpense.data.local.entity.club.ClubAccountEntity> = emptyList(),
    val showAddDialog: Boolean = false,
    val form: ClubAccountFormState = ClubAccountFormState(),
    val isSaving: Boolean = false,
    val saveError: String? = null
)
