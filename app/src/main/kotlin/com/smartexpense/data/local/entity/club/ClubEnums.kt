package com.smartexpense.data.local.entity.club

enum class MemberStatus {
    ACTIVE,
    DORMANT,
    WITHDRAWN
}

enum class MemberRole {
    GENERAL,
    PRESIDENT,
    TREASURER,
    EXECUTIVE
}

enum class DuesTerm {
    FIRST_HALF,
    SECOND_HALF
}

enum class ClubTransactionType {
    INCOME,
    EXPENSE;

    companion object {
        fun fromStorage(raw: String?): ClubTransactionType {
            val trimmed = raw.orEmpty().trim()
            return when {
                trimmed.equals("INCOME", ignoreCase = true) || trimmed == "수입" -> INCOME
                trimmed.equals("EXPENSE", ignoreCase = true) || trimmed == "지출" -> EXPENSE
                else -> runCatching { valueOf(trimmed.uppercase()) }.getOrDefault(EXPENSE)
            }
        }
    }
}

object ClubCategory {
    const val REGULAR_DUES = "정기 회비 (월/연회비)"
    const val DONATION = "찬조금/특별 회비 (기부금 등)"
    const val INTEREST_INCOME = "이자/수익"
    const val OTHER_INCOME = "기타 수입 (이월금 등)"
    const val TRANSFER = "자금 이동/계좌 이체"

    const val MEAL = "식대/다과비 (모임 식사, 카페 등)"
    const val VENUE_RENTAL = "장소 대관료 (모임 장소 대여비)"
    const val EVENT = "행사/진행비 (이벤트 기획, 상품 구매 등)"
    const val SUPPLIES = "비품/소모품비 (운영에 필요한 물품 구매)"
    const val CONDOLENCE = "경조사비 (회원 화환, 축의금/조의금 등)"
    const val TRANSPORT = "교통/통신비 (이동 경비, 문자 발송비 등)"
    const val OTHER_EXPENSE = "기타 지출 (수수료 및 예비비)"

    val incomeCategories = listOf(REGULAR_DUES, DONATION, INTEREST_INCOME, OTHER_INCOME)

    val expenseCategories = listOf(
        MEAL,
        VENUE_RENTAL,
        EVENT,
        SUPPLIES,
        CONDOLENCE,
        TRANSPORT,
        OTHER_EXPENSE
    )

    val allCategories = incomeCategories + expenseCategories + listOf(TRANSFER)

    private val legacyCategoryMapping = mapOf(
        "이월금" to OTHER_INCOME,
        "회비" to REGULAR_DUES,
        "찬조금" to DONATION,
        "이자" to INTEREST_INCOME,
        "이자/수익" to INTEREST_INCOME,
        "기타 수입 (이자, 이월금 등)" to OTHER_INCOME,
        "경비" to MEAL,
        "식비" to MEAL,
        "숙박" to OTHER_EXPENSE,
        "숙박비" to OTHER_EXPENSE,
        "경조비" to CONDOLENCE,
        "비품" to SUPPLIES,
        "교통" to TRANSPORT,
        "교통비" to TRANSPORT,
        "주유" to TRANSPORT,
        "유류비" to TRANSPORT
    )

    fun categoriesFor(type: ClubTransactionType): List<String> = when (type) {
        ClubTransactionType.INCOME -> incomeCategories
        ClubTransactionType.EXPENSE -> expenseCategories
    }

    fun isTransferCategory(category: String): Boolean =
        normalizeCategory(category) == TRANSFER

    fun isStatisticalCategory(category: String): Boolean =
        !isTransferCategory(category)

    fun defaultCategory(type: ClubTransactionType): String = when (type) {
        ClubTransactionType.INCOME -> REGULAR_DUES
        ClubTransactionType.EXPENSE -> MEAL
    }

    /** 카테고리명으로 수입/지출 유형을 자동 판별 */
    fun typeFor(category: String): ClubTransactionType? = when (normalizeCategory(category)) {
        TRANSFER -> ClubTransactionType.EXPENSE
        in incomeCategories -> ClubTransactionType.INCOME
        in expenseCategories -> ClubTransactionType.EXPENSE
        else -> null
    }

    fun normalizeCategory(raw: String, hintType: ClubTransactionType? = null): String {
        legacyCategoryMapping[raw]?.let { return it }
        if (raw == "기타" && hintType != null) {
            return if (hintType == ClubTransactionType.INCOME) OTHER_INCOME else OTHER_EXPENSE
        }
        return raw
    }

    fun resolveCategory(type: ClubTransactionType, raw: String): String {
        val normalized = normalizeCategory(raw, hintType = type)
        return normalized.takeIf { it in allCategories } ?: defaultCategory(type)
    }

    fun resolveType(category: String, fallback: ClubTransactionType): ClubTransactionType =
        typeFor(category) ?: fallback

    fun shortLabel(category: String): String {
        val trimmed = category.trim()
        val parenIndex = trimmed.indexOf('(')
        return if (parenIndex > 0) trimmed.substring(0, parenIndex).trim() else trimmed
    }

    fun isCondolenceCategory(category: String): Boolean =
        normalizeCategory(category) == CONDOLENCE
}
