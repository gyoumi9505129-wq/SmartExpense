package com.smartexpense.ui.club.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.smartexpense.data.local.ClubConstants

/** 한우리 슬로건 강조색 — 캡처 기준 (友情·信義=파랑, 한우리=빨강) */
object ClubSloganColors {
    val HanjaBlue = Color(0xFF4A90D9)
    val BrandRed = Color(0xFFE53935)
}

/**
 * 「友情과 信義로 하나되는 한우리」 형태면 구간별 색을 입히고,
 * 그 외 슬로건은 [baseColor] 단색으로 표시합니다.
 */
fun styledClubSlogan(
    slogan: String,
    baseColor: Color
): AnnotatedString {
    val raw = slogan.trim()
    if (raw.isEmpty()) return AnnotatedString("")

    val leadingQuote = raw.firstOrNull()?.takeIf { it == '"' || it == '“' || it == '「' }
    val trailingQuote = raw.lastOrNull()?.takeIf { it == '"' || it == '”' || it == '」' }
    val core = raw
        .removePrefix("\"")
        .removePrefix("“")
        .removePrefix("「")
        .removeSuffix("\"")
        .removeSuffix("”")
        .removeSuffix("」")
        .trim()

    val isHanuriSlogan = core == ClubConstants.HANURI_SEED_SLOGAN ||
        (core.contains("友情") && core.contains("信義") && core.contains("한우리"))

    if (!isHanuriSlogan) {
        return AnnotatedString(raw)
    }

    return buildAnnotatedString {
        if (leadingQuote != null) {
            withStyle(SpanStyle(color = baseColor)) { append(leadingQuote) }
        }
        // 友情과 信義로 하나되는 한우리
        val friendship = "友情"
        val faith = "信義"
        val brand = "한우리"
        val friendshipIdx = core.indexOf(friendship)
        val faithIdx = core.indexOf(faith)
        val brandIdx = core.lastIndexOf(brand)
        if (friendshipIdx < 0 || faithIdx < 0 || brandIdx < 0) {
            withStyle(SpanStyle(color = baseColor)) { append(core) }
        } else {
            var cursor = 0
            fun appendBase(end: Int) {
                if (cursor < end) {
                    withStyle(SpanStyle(color = baseColor)) {
                        append(core.substring(cursor, end))
                    }
                    cursor = end
                }
            }
            appendBase(friendshipIdx)
            withStyle(SpanStyle(color = ClubSloganColors.HanjaBlue)) {
                append(friendship)
            }
            cursor = friendshipIdx + friendship.length
            appendBase(faithIdx)
            withStyle(SpanStyle(color = ClubSloganColors.HanjaBlue)) {
                append(faith)
            }
            cursor = faithIdx + faith.length
            appendBase(brandIdx)
            withStyle(SpanStyle(color = ClubSloganColors.BrandRed)) {
                append(brand)
            }
            cursor = brandIdx + brand.length
            appendBase(core.length)
        }
        if (trailingQuote != null) {
            withStyle(SpanStyle(color = baseColor)) { append(trailingQuote) }
        }
    }
}
