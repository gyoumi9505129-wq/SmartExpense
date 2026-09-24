package com.smartexpense.ui.club.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.smartexpense.ui.club.components.ClubDateField
import com.smartexpense.ui.club.components.ClubFormBottomSheet
import com.smartexpense.ui.club.components.ClubTextField
import com.smartexpense.ui.club.components.rememberClubFormFinishInput

@Composable
fun HistoryFormBottomSheet(
    form: ClubHistoryFormState,
    isSaving: Boolean,
    onDateChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onDetailsChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val finishInput = rememberClubFormFinishInput()
    val focusContent = remember { FocusRequester() }
    val focusDetails = remember { FocusRequester() }
    val focusNote = remember { FocusRequester() }

    ClubFormBottomSheet(
        title = if (form.isEditing) "모임 이력 수정" else "모임 이력 등록",
        onDismiss = onDismiss,
        onSave = {
            finishInput()
            onSave()
        },
        saveLabel = if (form.isEditing) "수정" else "저장",
        isSaving = isSaving,
        hasUnsavedChanges = form.hasDraftInput
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ClubDateField(
                value = form.date,
                onValueChange = onDateChange,
                label = "날짜",
                onDatePicked = { focusContent.requestFocus() }
            )
            ClubTextField(
                value = form.content,
                onValueChange = onContentChange,
                label = "내용",
                focusRequester = focusContent,
                imeAction = ImeAction.Next,
                onImeAction = { focusDetails.requestFocus() }
            )
            ClubTextField(
                value = form.details,
                onValueChange = onDetailsChange,
                label = "세부사항",
                focusRequester = focusDetails,
                imeAction = ImeAction.Next,
                onImeAction = { focusNote.requestFocus() }
            )
            ClubTextField(
                value = form.note,
                onValueChange = onNoteChange,
                label = "비고 (선택)",
                focusRequester = focusNote,
                imeAction = ImeAction.Done,
                onImeAction = finishInput
            )
        }
    }
}
