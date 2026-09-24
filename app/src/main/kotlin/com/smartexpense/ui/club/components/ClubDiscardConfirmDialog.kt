package com.smartexpense.ui.club.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.smartexpense.ui.theme.TextPrimary
import com.smartexpense.ui.theme.TextSecondary

@Composable
fun ClubDiscardConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "작성 취소", color = TextPrimary)
        },
        text = {
            Text(
                text = "작성 중인 내용이 사라집니다. 취소하시겠습니까?",
                color = TextSecondary
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("네", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("계속 작성", color = TextPrimary)
            }
        }
    )
}
