package com.smartexpense.data.local

object ClubConstants {
    const val DEFAULT_CLUB_ID = 1L
    const val DEFAULT_CLUB_NAME = "한우리(기본 모임)"
    const val HANURI_SEED_CLUB_NAME = "한우리"
    const val HANURI_SEED_SLOGAN = "友情과 信義로 하나되는 한우리"

    /**
     * 승인 전(미승인) 회원용 체험 샘플 모임 — 웹 SAMPLE_* 와 동일 성격의 로컬 전용 소량 가짜 데이터.
     * 이름에 공백을 두어 "한우리(" 로 시작하지 않도록 해 [isHanuriSeedClub]/한우리 시드 로직과 절대 섞이지 않습니다.
     */
    const val SAMPLE_CLUB_NAME = "한우리 (샘플)"
    const val SAMPLE_CLUB_SLOGAN = "승인 전 체험용 샘플 데이터 · 조회 전용"

    /** 시드(초기 데이터) 대상 모임 이름 — 한우리 전용 */
    fun isHanuriSeedClub(clubName: String): Boolean {
        val normalized = clubName.trim()
        return normalized == HANURI_SEED_CLUB_NAME ||
            normalized == DEFAULT_CLUB_NAME ||
            normalized.startsWith("$HANURI_SEED_CLUB_NAME(")
    }
}
