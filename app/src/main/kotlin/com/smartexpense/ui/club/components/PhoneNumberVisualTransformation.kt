package com.smartexpense.ui.club.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

class PhoneNumberVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter { it.isDigit() }.take(PHONE_MAX_LENGTH)
        val formatted = formatPhoneDigits(digits)
        return TransformedText(
            text = AnnotatedString(formatted),
            offsetMapping = PhoneNumberOffsetMapping(digits.length)
        )
    }
}

internal const val PHONE_MAX_LENGTH = 11

internal fun formatPhoneDigits(digits: String): String {
    val d = digits.filter { it.isDigit() }.take(PHONE_MAX_LENGTH)
    return when {
        d.length <= 3 -> d
        d.length <= 7 -> "${d.take(3)}-${d.drop(3)}"
        else -> "${d.take(3)}-${d.drop(3).take(4)}-${d.drop(7)}"
    }
}

private class PhoneNumberOffsetMapping(
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
            clamped <= 3 -> clamped
            clamped <= 8 -> (clamped - 1).coerceIn(0, digitCount)
            else -> (clamped - 2).coerceIn(0, digitCount)
        }
    }

    private fun hyphenCountBefore(digitIndex: Int): Int = when {
        digitIndex <= 3 -> 0
        digitIndex <= 7 -> 1
        else -> 2
    }
}
