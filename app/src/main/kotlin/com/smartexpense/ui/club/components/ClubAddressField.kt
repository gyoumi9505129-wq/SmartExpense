package com.smartexpense.ui.club.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.smartexpense.ui.theme.BackgroundBlack
import com.smartexpense.ui.theme.TextPrimary

@Composable
fun ClubAddressField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String = "주소",
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    focusRequester: FocusRequester? = null,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: (() -> Unit)? = null
) {
    var showPostcodeDialog by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        ClubTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            readOnly = readOnly,
            showClearButton = false,
            focusRequester = focusRequester,
            imeAction = imeAction,
            onImeAction = onImeAction,
            modifier = Modifier.weight(1f)
        )
        if (!readOnly) {
            Button(
                onClick = { showPostcodeDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = TextPrimary,
                    contentColor = BackgroundBlack
                )
            ) {
                Text("검색")
            }
        }
    }

    if (showPostcodeDialog && !readOnly) {
        DaumPostcodeDialog(
            onAddressSelected = { address ->
                onValueChange(address)
                showPostcodeDialog = false
            },
            onDismiss = { showPostcodeDialog = false }
        )
    }
}
