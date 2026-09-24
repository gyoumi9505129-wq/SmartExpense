package com.smartexpense.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.launch

@Composable
fun rememberClearInputOverlayAction(onAfterClear: () -> Unit): () -> Unit {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    return remember(onAfterClear) {
        {
            keyboardController?.hide()
            focusManager.clearFocus()
            onAfterClear()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberModalBottomSheetDismissAction(
    sheetState: SheetState,
    onDismiss: () -> Unit
): () -> Unit {
    val scope = rememberCoroutineScope()
    val clearInput = rememberClearInputOverlayAction(onAfterClear = {})

    return remember(sheetState, onDismiss) {
        {
            clearInput()
            scope.launch {
                if (sheetState.isVisible) {
                    sheetState.hide()
                }
                onDismiss()
            }
        }
    }
}
