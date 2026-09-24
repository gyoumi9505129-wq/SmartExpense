package com.smartexpense.data.local.seed

/**
 * 경조사비 이력 시드 — 엑셀 `한우리_초기데이터_이행.xlsx` [경조사비] 시트.
 * 도메인 테이블 `event_expenses` 전용. 장부(club_transactions)에 넣지 않으며 통장 잔액과 무관.
 */
object EventExpenseInitialData {

    const val SEED_DATE = "2025-01-01"

    data class EventExpenseSeedRecord(
        val memberName: String,
        val rawSubCategory: String,
        val amount: Int
    )

    val records: List<EventExpenseSeedRecord> = listOf(
        EventExpenseSeedRecord("강대화", "결혼", 900000),
        EventExpenseSeedRecord("강대화", "부모회갑", 400000),
        EventExpenseSeedRecord("강대화", "자녀돌", 100000),
        EventExpenseSeedRecord("강대화", "상조(부모)", 1000000),
        EventExpenseSeedRecord("김민석", "결혼", 1000000),
        EventExpenseSeedRecord("김민석", "부모회갑", 500000),
        EventExpenseSeedRecord("김성겸", "부모회갑", 500000),
        EventExpenseSeedRecord("김성겸", "부모고희", 700000),
        EventExpenseSeedRecord("김성환", "결혼", 800000),
        EventExpenseSeedRecord("김성환", "부모회갑", 300000),
        EventExpenseSeedRecord("김성환", "자녀돌", 100000),
        EventExpenseSeedRecord("김성환", "상조(부모)", 2000000),
        EventExpenseSeedRecord("김성환", "상조(배우자부모)", 500000),
        EventExpenseSeedRecord("김영섭", "결혼", 1000000),
        EventExpenseSeedRecord("김영섭", "부모회갑", 400000),
        EventExpenseSeedRecord("박성남", "결혼", 1000000),
        EventExpenseSeedRecord("박성남", "부모회갑", 400000),
        EventExpenseSeedRecord("박성남", "상조(부모)", 1000000),
        EventExpenseSeedRecord("신태섭", "부모고희", 400000),
        EventExpenseSeedRecord("유락준", "결혼", 1000000),
        EventExpenseSeedRecord("유락준", "부모회갑", 400000),
        EventExpenseSeedRecord("유락준", "출산", 50000),
        EventExpenseSeedRecord("유락준", "자녀돌", 150000),
        EventExpenseSeedRecord("유락준", "상조(부모)", 1000000),
        EventExpenseSeedRecord("유병학", "결혼", 800000),
        EventExpenseSeedRecord("유병학", "부모회갑", 400000),
        EventExpenseSeedRecord("유병학", "부모고희", 500000),
        EventExpenseSeedRecord("유병학", "자녀돌", 100000),
        EventExpenseSeedRecord("유병학", "상조(부모)", 1000000),
        EventExpenseSeedRecord("유병학", "상조(본인)", 1200000),
        EventExpenseSeedRecord("유병학", "상조(배우자부모)", 500000),
        EventExpenseSeedRecord("유진호", "결혼", 800000),
        EventExpenseSeedRecord("유진호", "부모회갑", 300000),
        EventExpenseSeedRecord("이충열", "부모회갑", 400000),
        EventExpenseSeedRecord("조갑선", "부모회갑", 600000),
        EventExpenseSeedRecord("조갑선", "부모고희", 300000),
        EventExpenseSeedRecord("최낙선", "결혼", 1000000),
        EventExpenseSeedRecord("최낙선", "부모회갑", 400000),
        EventExpenseSeedRecord("최낙선", "부모고희", 200000),
        EventExpenseSeedRecord("최낙선", "출산", 100000),
        EventExpenseSeedRecord("안영준", "결혼", 1000000),
        EventExpenseSeedRecord("안영준", "부모회갑", 300000),
        EventExpenseSeedRecord("박상수", "부모회갑", 300000),
        EventExpenseSeedRecord("이용운", "결혼", 800000),
        EventExpenseSeedRecord("이용운", "부모회갑", 400000),
        EventExpenseSeedRecord("이용운", "자녀돌", 100000),
        EventExpenseSeedRecord("황재헌", "결혼", 900000),
        EventExpenseSeedRecord("황재헌", "부모회갑", 400000),
        EventExpenseSeedRecord("황재헌", "자녀돌", 150000),
        EventExpenseSeedRecord("황재헌", "상조(배우자부모)", 100000),
        EventExpenseSeedRecord("이세희", "결혼", 800000),
        EventExpenseSeedRecord("이세희", "자녀돌", 100000),
        EventExpenseSeedRecord("이세희", "상조(부모)", 500000),
        EventExpenseSeedRecord("이세희", "상조(본인)", 1100000)
    )
}
