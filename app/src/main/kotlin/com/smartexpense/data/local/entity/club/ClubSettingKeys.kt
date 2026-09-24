package com.smartexpense.data.local.entity.club

object ClubSettingKeys {
    const val BANK_PARSE_ENABLED_PACKAGES = "bank_parse_enabled_packages_json"
    const val DUES_PAYMENT_FILTER_YEAR = "dues_payment_filter_year"
    const val DUES_PAYMENT_FILTER_MEMBER_ID = "dues_payment_filter_member_id"
    /** 기초 이월금/초기 자산. null·음수면 잔액 재계산 시 0으로 취급. */
    const val INITIAL_BALANCE = "initial_balance"
    /** 강제 재계산으로 덮어쓰는 현재 잔액 스냅샷. */
    const val CURRENT_BALANCE = "current_balance"
    /** 신규 회비 등록 시 기본 납부 방식. */
    const val DUES_PAYMENT_METHOD = "dues_payment_method"
}
