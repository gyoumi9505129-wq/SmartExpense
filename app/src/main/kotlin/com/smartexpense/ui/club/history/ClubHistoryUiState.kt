package com.smartexpense.ui.club.history

import com.smartexpense.data.local.entity.club.ClubHistoryEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class ClubHistoryFormState(
    val editingId: Int? = null,
    val date: String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
    val content: String = "",
    val details: String = "",
    val note: String = ""
) {
    val isEditing: Boolean get() = editingId != null

    val hasDraftInput: Boolean
        get() = content.isNotBlank() ||
            details.isNotBlank() ||
            note.isNotBlank() ||
            isEditing

    val canSave: Boolean
        get() = content.isNotBlank() &&
            details.isNotBlank() &&
            clubHistoryDateToEpochMillis(date) != null
}

data class ClubHistoryUiState(
    val items: List<ClubHistoryEntity> = emptyList(),
    val isFormVisible: Boolean = false,
    val form: ClubHistoryFormState = ClubHistoryFormState(),
    val isSaving: Boolean = false,
    val deleteTarget: ClubHistoryEntity? = null
)
