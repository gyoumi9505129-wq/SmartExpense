package com.smartexpense.ui.club.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

class DateVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter { it.isDigit() }.take(DATE_DIGIT_LENGTH)
        val formatted = formatDateDigits(digits)
        return TransformedText(
            text = AnnotatedString(formatted),
            offsetMapping = DateOffsetMapping(digits.length)
        )
    }
}

internal const val DATE_DIGIT_LENGTH = 8

internal fun formatDateDigits(digits: String): String {
    val d = digits.filter { it.isDigit() }.take(DATE_DIGIT_LENGTH)
    return when {
        d.length <= 4 -> d
        d.length <= 6 -> "${d.take(4)}-${d.drop(4)}"
        else -> "${d.take(4)}-${d.drop(4).take(2)}-${d.drop(6)}"
    }
}

internal fun dateDigitsToStorage(digits: String): String = formatDateDigits(digits)

internal fun dateStorageToDigits(stored: String): String =
    stored.filter { it.isDigit() }.take(DATE_DIGIT_LENGTH)

private class DateOffsetMapping(
    private val digitCount: Int
) : OffsetMapping {
    override fun originalToTransformed(offset: Int): Int {
        val clamped = offset.coerceIn(0, digitCount)
        return clamped + hyphenCountBefore(clamped)
    }

    override fun transformedToOriginal(offset: Int): Int {
        val transformedLength = digitCount + hyphenCountBefore(digitCount)
        val clamped = offset.coerceIn(0, transformedLength)
        return when {
            clamped <= 4 -> clamped
            clamped <= 7 -> (clamped - 1).coerceIn(0, digitCount)
            else -> (clamped - 2).coerceIn(0, digitCount)
        }
    }

    private fun hyphenCountBefore(digitIndex: Int): Int = when {
        digitIndex <= 4 -> 0
        digitIndex <= 6 -> 1
        else -> 2
    }
}
