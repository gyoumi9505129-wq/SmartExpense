package com.smartexpense.data.local

object ClubConstants {
    const val DEFAULT_CLUB_ID = 1L
    const val DEFAULT_CLUB_NAME = "한우리(기본 모임)"
    const val HANURI_SEED_CLUB_NAME = "한우리"
    const val HANURI_SEED_SLOGAN = "友情과 信義로 하나되는 한우리"

    /** 시드(초기 데이터) 대상 모임 이름 — 한우리 전용 */
    fun isHanuriSeedClub(clubName: String): Boolean {
        val normalized = clubName.trim()
        return normalized == HANURI_SEED_CLUB_NAME ||
            normalized == DEFAULT_CLUB_NAME ||
            normalized.startsWith("$HANURI_SEED_CLUB_NAME(")
    }
}
