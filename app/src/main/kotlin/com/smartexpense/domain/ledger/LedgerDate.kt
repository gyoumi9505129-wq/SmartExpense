package com.smartexpense.domain.ledger

/**
 * 장부 날짜 저장 형식은 `yyyy-MM-dd`.
 * Firestore에 `yyyy.MM.dd`·숫자만·타임스탬프가 섞여 있어도 같은 키로 비교한다.
 */
object LedgerDate {
    fun toStorage(raw: String?): String {
        val digits = raw.orEmpty().filter { it.isDigit() }
        if (digits.length >= 8) {
            return "${digits.substring(0, 4)}-${digits.substring(4, 6)}-${digits.substring(6, 8)}"
        }
        return raw.orEmpty().trim()
    }

    fun sortKey(raw: String?): String = toStorage(raw).filter { it.isDigit() }.take(8)
}
