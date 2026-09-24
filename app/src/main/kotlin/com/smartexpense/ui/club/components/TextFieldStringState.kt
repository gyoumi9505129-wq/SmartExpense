package com.smartexpense.ui.club.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * String 변환(숫자 필터, 길이 제한 등) 후에도 커서 위치를 보존합니다.
 */
fun TextFieldValue.withTransformedText(transform: (String) -> String): TextFieldValue {
    val newText = transform(text)
    if (newText == text) return this
    val safeStart = selection.start.coerceIn(0, text.length)
    val safeEnd = selection.end.coerceIn(0, text.length)
    val newStart = transform(text.take(safeStart)).length
    val newEnd = transform(text.take(safeEnd)).length
    return TextFieldValue(
        text = newText,
        selection = TextRange(
            newStart.coerceIn(0, newText.length),
            newEnd.coerceIn(0, newText.length)
        )
    )
}

/**
 * ViewModel String과 동기화되는 [TextFieldValue] 상태.
 *
 * 외부 값이 "실제로 바뀐 경우"(폼 초기화·프로그램적 변경)에만 로컬 상태를 맞춥니다.
 * 사용자가 타이핑해서 ViewModel StateFlow로 값이 되돌아오는 비동기 왕복 도중에는
 * 절대 [TextFieldValue]를 새로 만들지 않습니다. (새로 만들면 한글 조합(composition)이
 * 끊겨 "한글 입력이 안 되는" 증상이 발생함)
 */
@Composable
fun rememberSyncedTextFieldValue(externalText: String): MutableState<TextFieldValue> {
    val state = remember {
        mutableStateOf(TextFieldValue(externalText, TextRange(externalText.length)))
    }
    // 직전 컴포지션에서 관찰한 외부 값. 이 값과 달라졌을 때만 외부 변경으로 간주한다.
    var lastExternal by remember { mutableStateOf(externalText) }
    if (externalText != lastExternal) {
        lastExternal = externalText
        if (externalText != state.value.text) {
            state.value = TextFieldValue(
                text = externalText,
                selection = TextRange(externalText.length)
            )
        }
    }
    return state
}
