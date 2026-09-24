package com.smartexpense.ui.club.components

import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.Dp
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.smartexpense.ui.theme.BorderLine
import com.smartexpense.ui.theme.ExpenseRed
import com.smartexpense.ui.theme.SurfaceDeepGray
import com.smartexpense.ui.theme.TextPrimary
import com.smartexpense.ui.theme.TextSecondary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.clubFormFocusTarget(focusRequester: FocusRequester?): Modifier {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    return this
        .clubFormFocus(focusRequester)
        .bringIntoViewRequester(bringIntoViewRequester)
        .onFocusEvent { event ->
            if (event.isFocused) {
                coroutineScope.launch {
                    bringIntoViewRequester.bringIntoView()
                }
            }
        }
}

val ClubFormFieldColors
    @Composable get() = OutlinedTextFieldDefaults.colors(
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        disabledTextColor = TextSecondary,
        focusedBorderColor = TextSecondary,
        unfocusedBorderColor = BorderLine,
        disabledBorderColor = BorderLine,
        focusedLabelColor = TextSecondary,
        unfocusedLabelColor = TextSecondary,
        cursorColor = TextPrimary,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent
    )

@Composable
fun ClubTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    readOnly: Boolean = false,
    showClearButton: Boolean = true,
    focusRequester: FocusRequester? = null,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: (() -> Unit)? = null,
    isError: Boolean = false,
    errorMessage: String? = null
) {
    val textFieldState = rememberSyncedTextFieldValue(value)
    OutlinedTextField(
        value = textFieldState.value,
        onValueChange = { updated ->
            textFieldState.value = updated
            onValueChange(updated.text)
        },
        label = { Text(label) },
        readOnly = readOnly,
        singleLine = singleLine && minLines == 1,
        minLines = minLines,
        isError = isError,
        supportingText = errorMessage?.let { message ->
            { Text(message, color = ExpenseRed) }
        },
        keyboardOptions = clubFormKeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = clubFormKeyboardActions(onImeAction),
        trailingIcon = if (showClearButton && !readOnly && value.isNotEmpty()) {
            { ClubClearTrailingIcon(onClear = { onValueChange("") }) }
        } else {
            null
        },
        colors = ClubFormFieldColors,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
            .fillMaxWidth()
            .clubFormFocusTarget(focusRequester)
    )
}

@Composable
fun ClubEmailField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String = "이메일",
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    focusRequester: FocusRequester? = null,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: (() -> Unit)? = null,
    errorMessage: String? = null
) {
    ClubTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        keyboardType = KeyboardType.Email,
        readOnly = readOnly,
        focusRequester = focusRequester,
        imeAction = imeAction,
        onImeAction = onImeAction,
        isError = errorMessage != null,
        errorMessage = errorMessage,
        modifier = modifier
    )
}

@Composable
fun ClubNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    maxLength: Int? = null,
    formatWithComma: Boolean = false,
    readOnly: Boolean = false,
    focusRequester: FocusRequester? = null,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: (() -> Unit)? = null,
    textStyle: TextStyle? = null
) {
    val textFieldState = rememberSyncedTextFieldValue(value)
    OutlinedTextField(
        value = textFieldState.value,
        onValueChange = { raw ->
            if (readOnly) return@OutlinedTextField
            val filtered = raw.withTransformedText { input ->
                val digits = input.filter { it.isDigit() }
                if (maxLength != null) digits.take(maxLength) else digits
            }
            textFieldState.value = filtered
            onValueChange(filtered.text)
        },
        label = { Text(label) },
        readOnly = readOnly,
        suffix = suffix?.let { { Text(it, color = TextSecondary) } },
        singleLine = true,
        textStyle = textStyle ?: MaterialTheme.typography.bodyLarge,
        keyboardOptions = clubFormKeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = imeAction
        ),
        keyboardActions = clubFormKeyboardActions(onImeAction),
        visualTransformation = if (formatWithComma) ThousandsSeparatorVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (!readOnly && value.isNotEmpty()) {
            { ClubClearTrailingIcon(onClear = { onValueChange("") }) }
        } else {
            null
        },
        colors = ClubFormFieldColors,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
            .fillMaxWidth()
            .clubFormFocusTarget(focusRequester)
    )
}

private class ThousandsSeparatorVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text.filter { it.isDigit() }
        if (raw.isEmpty()) return TransformedText(AnnotatedString(""), OffsetMapping.Identity)

        val formatted = raw.toLongOrNull()?.let { "%,d".format(it) } ?: raw
        return TransformedText(
            text = AnnotatedString(formatted),
            offsetMapping = object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int {
                    val clamped = offset.coerceIn(0, raw.length)
                    val leftRaw = raw.take(clamped)
                    val leftFormatted = leftRaw.toLongOrNull()?.let { "%,d".format(it) } ?: leftRaw
                    return leftFormatted.length
                }

                override fun transformedToOriginal(offset: Int): Int {
                    val clamped = offset.coerceIn(0, formatted.length)
                    val leftFormatted = formatted.take(clamped)
                    return leftFormatted.count { it.isDigit() }
                }
            }
        )
    }
}

@Composable
fun ClubPhoneField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    focusRequester: FocusRequester? = null,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: (() -> Unit)? = null
) {
    val textFieldState = rememberSyncedTextFieldValue(value)
    OutlinedTextField(
        value = textFieldState.value,
        onValueChange = { raw ->
            if (readOnly) return@OutlinedTextField
            val filtered = raw.withTransformedText { input ->
                input.filter { it.isDigit() }.take(PHONE_MAX_LENGTH)
            }
            textFieldState.value = filtered
            onValueChange(filtered.text)
        },
        label = { Text(label) },
        readOnly = readOnly,
        singleLine = true,
        keyboardOptions = clubFormKeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = imeAction
        ),
        keyboardActions = clubFormKeyboardActions(onImeAction),
        visualTransformation = PhoneNumberVisualTransformation(),
        trailingIcon = if (!readOnly && value.isNotEmpty()) {
            { ClubClearTrailingIcon(onClear = { onValueChange("") }) }
        } else {
            null
        },
        colors = ClubFormFieldColors,
        shape = MaterialTheme.shapes.small,
        modifier = modifier
            .fillMaxWidth()
            .clubFormFocusTarget(focusRequester)
    )
}

@Composable
private fun ClubClearTrailingIcon(
    onClear: () -> Unit
) {
    IconButton(onClick = onClear) {
        Icon(
            imageVector = Icons.Default.Clear,
            contentDescription = "입력 지우기",
            tint = TextSecondary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClubDateField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    onDatePicked: (() -> Unit)? = null,
    textStyle: TextStyle? = null,
    compact: Boolean = false,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: (() -> Unit)? = null
) {
    var showPicker by remember { mutableStateOf(false) }
    val digits = dateStorageToDigits(value)
    val textFieldState = rememberSyncedTextFieldValue(digits)
    val initialDate = remember(digits) {
        if (digits.length == DATE_DIGIT_LENGTH) {
            runCatching {
                LocalDate.parse(formatDateDigits(digits), dateFormatter)
            }.getOrDefault(LocalDate.now())
        } else {
            LocalDate.now()
        }
    }
    val fieldTextStyle = textStyle ?: MaterialTheme.typography.bodyLarge
    val openPicker = { if (enabled) showPicker = true }

    if (compact) {
        OutlinedTextField(
            value = textFieldState.value,
            onValueChange = { raw ->
                if (!enabled) return@OutlinedTextField
                val filtered = raw.withTransformedText { input ->
                    input.filter { it.isDigit() }.take(DATE_DIGIT_LENGTH)
                }
                textFieldState.value = filtered
                onValueChange(dateDigitsToStorage(filtered.text))
            },
            readOnly = !enabled,
            enabled = enabled,
            placeholder = {
                Text(
                    text = label,
                    style = fieldTextStyle.copy(fontSize = 12.sp),
                    color = TextSecondary,
                    maxLines = 1
                )
            },
            singleLine = true,
            minLines = 1,
            maxLines = 1,
            textStyle = fieldTextStyle.copy(fontSize = 13.sp),
            keyboardOptions = clubFormKeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = imeAction
            ),
            keyboardActions = clubFormKeyboardActions(onImeAction ?: onDatePicked),
            visualTransformation = DateVisualTransformation(),
            trailingIcon = {
                IconButton(
                    onClick = openPicker,
                    enabled = enabled,
                    modifier = Modifier.width(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = "날짜 선택",
                        tint = if (enabled) TextSecondary else TextSecondary.copy(alpha = 0.4f)
                    )
                }
            },
            colors = ClubFormFieldColors,
            shape = MaterialTheme.shapes.small,
            modifier = modifier
                .fillMaxWidth()
                .clubFormFocus(focusRequester)
        )
    } else {
        OutlinedTextField(
            value = textFieldState.value,
            onValueChange = { raw ->
                if (!enabled) return@OutlinedTextField
                val filtered = raw.withTransformedText { input ->
                    input.filter { it.isDigit() }.take(DATE_DIGIT_LENGTH)
                }
                textFieldState.value = filtered
                onValueChange(dateDigitsToStorage(filtered.text))
            },
            readOnly = !enabled,
            enabled = enabled,
            label = { Text(label, style = fieldTextStyle.copy(fontSize = 12.sp)) },
            placeholder = { Text("YYYY-MM-DD", color = TextSecondary, style = fieldTextStyle) },
            singleLine = true,
            minLines = 1,
            maxLines = 1,
            textStyle = fieldTextStyle,
            keyboardOptions = clubFormKeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = imeAction
            ),
            keyboardActions = clubFormKeyboardActions(onImeAction ?: onDatePicked),
            visualTransformation = DateVisualTransformation(),
            colors = ClubFormFieldColors,
            shape = MaterialTheme.shapes.small,
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (enabled && digits.isNotEmpty()) {
                        ClubClearTrailingIcon(onClear = {
                            textFieldState.value = textFieldState.value.copy(text = "")
                            onValueChange("")
                        })
                    }
                    IconButton(
                        onClick = openPicker,
                        enabled = enabled
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "날짜 선택",
                            tint = if (enabled) TextSecondary else TextSecondary.copy(alpha = 0.4f)
                        )
                    }
                }
            },
            modifier = modifier
                .fillMaxWidth()
                .clubFormFocusTarget(focusRequester)
        )
    }

    if (showPicker) {
        ClubDatePickerDialog(
            initialDate = initialDate,
            onDismiss = { showPicker = false },
            onConfirm = { formattedDate ->
                onValueChange(formattedDate)
                showPicker = false
                onDatePicked?.invoke()
            },
            onClear = {
                onValueChange("")
                showPicker = false
            },
            canClear = digits.isNotEmpty()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClubBirthDateField(
    value: String,
    onValueChange: (String) -> Unit,
    isLunar: Boolean,
    onLunarChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        ClubDateField(
            value = value,
            onValueChange = onValueChange,
            label = "생년월일",
            enabled = enabled,
            modifier = Modifier.weight(1f),
            focusRequester = focusRequester,
            imeAction = imeAction,
            onImeAction = onImeAction,
            onDatePicked = onImeAction
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.width(112.dp)) {
                SegmentedButton(
                    selected = !isLunar,
                    onClick = { if (enabled) onLunarChange(false) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    label = {
                        Text("양력", style = MaterialTheme.typography.labelSmall)
                    }
                )
                SegmentedButton(
                    selected = isLunar,
                    onClick = { if (enabled) onLunarChange(true) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    label = {
                        Text("음력", style = MaterialTheme.typography.labelSmall)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClubDatePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onClear: () -> Unit,
    initialDate: LocalDate = LocalDate.now(),
    canClear: Boolean = false
) {
    val datePickerZone = remember { ZoneOffset.UTC }
    val initialMillis = remember(initialDate) {
        initialDate.atStartOfDay(datePickerZone).toInstant().toEpochMilli()
    }
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = SurfaceDeepGray,
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                DatePicker(
                    state = state,
                    showModeToggle = false
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (canClear) {
                        TextButton(onClick = onClear) {
                            Text("지우기", color = TextSecondary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    TextButton(onClick = onDismiss) {
                        Text("취소", color = TextSecondary)
                    }
                    TextButton(
                        onClick = {
                            state.selectedDateMillis?.let { millis ->
                                // Material3 DatePicker millis는 UTC 자정 기준 → 로컬 포맷 시 하루 밀림 방지
                                val utcDate = Instant.ofEpochMilli(millis)
                                    .atZone(ZoneOffset.UTC)
                                    .toLocalDate()
                                val formatted = utcDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                                onConfirm(formatted)
                            }
                        }
                    ) {
                        Text("확인", color = TextPrimary)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun <T> ClubDropdownField(
    label: String,
    options: List<Pair<T, String>>,
    selected: T?,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "선택",
    menuMaxHeight: Dp? = null,
    focusRequester: FocusRequester? = null,
    requestExpand: Boolean = false,
    onExpandHandled: () -> Unit = {},
    /** 메뉴를 열 때 이 값이 보이도록 스크롤 (연도 콤보 → 현재 연도) */
    scrollToValueOnExpand: T? = null
) {
    var expanded by remember { mutableStateOf(false) }
    val displayText = options.firstOrNull { it.first == selected }?.second ?: placeholder
    val menuModifier = Modifier.heightIn(max = menuMaxHeight ?: 280.dp)
    val bringRequesters = remember(options) {
        List(options.size) { BringIntoViewRequester() }
    }

    LaunchedEffect(requestExpand) {
        if (requestExpand && enabled) {
            expanded = true
            focusRequester?.requestFocus()
            onExpandHandled()
        }
    }

    LaunchedEffect(expanded, scrollToValueOnExpand, options) {
        if (!expanded || scrollToValueOnExpand == null) return@LaunchedEffect
        val idx = options.indexOfFirst { it.first == scrollToValueOnExpand }
        if (idx in bringRequesters.indices) {
            // 메뉴 레이아웃 후 스크롤
            delay(50)
            bringRequesters[idx].bringIntoView()
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = {
                if (enabled) {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            colors = ClubFormFieldColors,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
                .clubFormFocusTarget(focusRequester)
        )
        DropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
            modifier = menuModifier
        ) {
            options.forEachIndexed { index, (value, text) ->
                DropdownMenuItem(
                    text = { Text(text, color = TextPrimary) },
                    onClick = {
                        onSelected(value)
                        expanded = false
                    },
                    modifier = Modifier.bringIntoViewRequester(bringRequesters[index])
                )
            }
        }
    }
}

@Composable
fun ClubFormSection(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        content = content
    )
}
