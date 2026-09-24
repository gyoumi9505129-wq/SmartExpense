package com.smartexpense.util

import com.smartexpense.data.model.bank.BankAppMaster

object FinanceAppMatcher {

    private val nameKeywords = listOf(
        "은행", "뱅크", "bank", "Bank",
        "증권", "투자", "자산", "capital", "Capital",
        "카드", "card", "Card",
        "금융", "finance", "Finance",
        "저축", "credit", "Credit",
        "캐피탈", "Capital",
        "페이", "Pay", "pay",
        "토스", "Toss",
        "뱅킹", "banking", "Banking",
        "증권사", "선물", "채권",
        "KB", "NH", "IBK", "SC", "KB국민", "신한", "하나", "우리",
        "카카오", "케이", "K뱅크", "kbank",
        "OK", "수협", "새마을", "신협", "MG", "MG새마을",
        "미래에셋", "삼성증권", "키움", "한국투자", "NH투자",
        "메리츠", "대신", "교보", "유안타", "하이투자"
    )

    private val packageKeywords = listOf(
        "bank", "banking", "card", "capital", "finance", "invest",
        "securities", "stock", "broker", "asset", "pay", "toss",
        "kbstar", "shinhan", "kebhana", "wooribank", "kakaobank",
        "kakaopay", "nhqv", "kiwoom", "mirae", "samsungsecurities",
        "hanaw", "eugenefn", "truefriend", "kbsec", "hyundaicapital",
        "oksavings", "suhyup", "kfcc", "cu.money", "kjbank", "jbbank",
        "dgb", "bnk", "epost", "kbankwith", "scbank", "citibank",
        "ibk", "ionebank", "smartbank", "mobilebank"
    )

    fun isLikelyFinanceApp(appName: String, packageName: String): Boolean {
        if (packageName in BankAppMaster.allPackageNames) return true
        if (packageKeywords.any { keyword -> packageName.contains(keyword, ignoreCase = true) }) {
            return true
        }
        return nameKeywords.any { keyword -> appName.contains(keyword, ignoreCase = true) }
    }
}
