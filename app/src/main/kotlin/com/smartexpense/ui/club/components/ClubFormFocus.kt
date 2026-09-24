package com.smartexpense.ui.club.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

internal fun Modifier.clubFormFocus(focusRequester: FocusRequester?): Modifier =
    if (focusRequester != null) focusRequester(focusRequester) else this

@Composable
internal fun clubFormKeyboardOptions(
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default
): KeyboardOptions = KeyboardOptions(
    keyboardType = keyboardType,
    imeAction = imeAction
)

@Composable
internal fun clubFormKeyboardActions(
    onImeAction: (() -> Unit)? = null
): KeyboardActions {
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    fun finishInput() {
        // ModalBottomSheet 안에서는 windowToken 불일치로 IMM 직접 호출이 무시되므로
        // 윈도우를 스스로 해석하는 WindowInsetsController로 IME를 숨긴다.
        ViewCompat.getWindowInsetsController(view)?.hide(WindowInsetsCompat.Type.ime())
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
    }

    fun moveToNextField() {
        if (onImeAction != null) {
            onImeAction()
        } else {
            focusManager.moveFocus(FocusDirection.Down)
        }
    }

    return KeyboardActions(
        onNext = { moveToNextField() },
        onDone = {
            if (onImeAction != null) {
                onImeAction()
            } else {
                finishInput()
            }
        },
        onGo = {
            if (onImeAction != null) {
                onImeAction()
            } else {
                finishInput()
            }
        },
        onSearch = {
            if (onImeAction != null) {
                onImeAction()
            } else {
                finishInput()
            }
        }
    )
}

@Composable
internal fun rememberClubFormFinishInput(): () -> Unit {
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    return {
        ViewCompat.getWindowInsetsController(view)?.hide(WindowInsetsCompat.Type.ime())
        keyboardController?.hide()
        focusManager.clearFocus(force = true)
    }
}
