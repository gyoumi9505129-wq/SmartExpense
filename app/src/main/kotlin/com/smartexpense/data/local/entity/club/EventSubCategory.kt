package com.smartexpense.data.local.entity.club

/** 경조사비 세부 항목 (엑셀 `개인별 경조사비 지출` 시트 기준) */
object EventSubCategory {
    const val SELF_MARRIAGE = "본인 결혼"
    const val CHILD_MARRIAGE = "자녀 결혼"
    const val PARENT_CELEBRATION = "부모 회갑/고희"
    const val BIRTH = "출산"
    const val CHILD_FIRST_BIRTHDAY = "자녀 돌"
    const val PARENT_FUNERAL = "부모상"
    const val SELF_FUNERAL = "본인상"
    const val SPOUSE_FUNERAL = "배우자상"
    const val OTHER = "기타"

    val all: List<String> = listOf(
        SELF_MARRIAGE,
        CHILD_MARRIAGE,
        PARENT_CELEBRATION,
        BIRTH,
        CHILD_FIRST_BIRTHDAY,
        PARENT_FUNERAL,
        SELF_FUNERAL,
        SPOUSE_FUNERAL,
        OTHER
    )

    /** 엑셀 `개인별 경조사비 지출` / Sheet2 분류명 → 앱 세부 항목 */
    fun fromExcelLabel(label: String): String = when (label.trim()) {
        "부모회갑", "부모고희" -> PARENT_CELEBRATION
        "결혼" -> SELF_MARRIAGE
        "자녀돌" -> CHILD_FIRST_BIRTHDAY
        "출산" -> BIRTH
        "상조(부모)" -> PARENT_FUNERAL
        "상조(본인)" -> SELF_FUNERAL
        "상조(배우자부모)" -> OTHER
        else -> OTHER
    }
}
