package com.smartexpense.domain.dues

/**
 * 장부↔회비 동기화 출처.
 * - [FROM_DUES_PAYMENT]: 회비 화면에서 납부 처리 → 장부 자동 기장 가능
 * - [FROM_LEDGER_ENTRY]: 장부에서 회비 수입 등록 → 회비만 갱신, 장부 자동 기장 생략
 */
enum class DuesLedgerSyncSource {
    FROM_DUES_PAYMENT,
    FROM_LEDGER_ENTRY
}
