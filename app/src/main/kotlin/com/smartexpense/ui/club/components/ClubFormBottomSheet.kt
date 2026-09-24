package com.smartexpense.ui.club.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartexpense.ui.components.rememberModalBottomSheetDismissAction
import com.smartexpense.ui.theme.BackgroundBlack
import com.smartexpense.ui.theme.SurfaceDeepGray
import com.smartexpense.ui.theme.TextPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClubFormBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    saveLabel: String = "저장",
    isSaving: Boolean = false,
    hasUnsavedChanges: Boolean = false,
    showSaveButton: Boolean = true,
    extraActions: (@Composable ColumnScope.() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val hasUnsavedChangesState = rememberUpdatedState(hasUnsavedChanges)
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var discardConfirmed by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target ->
            if (target == SheetValue.Hidden &&
                hasUnsavedChangesState.value &&
                !discardConfirmed
            ) {
                showDiscardConfirm = true
                false
            } else {
                true
            }
        }
    )
    val dismissSheet = rememberModalBottomSheetDismissAction(
        sheetState = sheetState,
        onDismiss = onDismiss
    )

    val requestDismiss: () -> Unit = {
        if (hasUnsavedChangesState.value && !discardConfirmed) {
            showDiscardConfirm = true
        } else {
            dismissSheet()
        }
    }

    if (showDiscardConfirm) {
        ClubDiscardConfirmDialog(
            onConfirm = {
                discardConfirmed = true
                showDiscardConfirm = false
                dismissSheet()
            },
            onDismiss = { showDiscardConfirm = false }
        )
    }

    ModalBottomSheet(
        onDismissRequest = requestDismiss,
        sheetState = sheetState,
        containerColor = SurfaceDeepGray,
        modifier = modifier
    ) {
        BackHandler(enabled = !isSaving, onBack = requestDismiss)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(20.dp))
            content()
            if (extraActions != null) {
                Spacer(modifier = Modifier.height(16.dp))
                extraActions.invoke(this)
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = requestDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BackgroundBlack,
                        contentColor = TextPrimary
                    )
                ) {
                    Text(if (showSaveButton) "취소" else "닫기")
                }
                if (showSaveButton) {
                    Spacer(modifier = Modifier.padding(4.dp))
                    Button(
                        onClick = onSave,
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isSaving) "저장 중…" else saveLabel)
                    }
                }
            }
        }
    }
}
