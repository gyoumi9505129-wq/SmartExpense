package com.smartexpense.data.model.bank

data class BankAppTemplate(
    val id: String,
    val name: String,
    val packageNames: Set<String>
)

object BankAppMaster {
    val all: List<BankAppTemplate> = listOf(
        BankAppTemplate("toss", "토스", setOf("viva.republica.toss")),
        BankAppTemplate("shinhan", "신한 SOL뱅크", setOf("com.shinhan.sbanking")),
        BankAppTemplate("nh", "NH농협", setOf("nh.smart.banking", "nh.smart.nhallonepay")),
        BankAppTemplate("kb", "국민은행", setOf("com.kbstar.kbank")),
        BankAppTemplate("kakao", "카카오뱅크", setOf("com.kakaobank.channel")),
        BankAppTemplate("woori", "우리은행", setOf("com.wooribank.smart.npib")),
        BankAppTemplate("hana", "하나은행", setOf("com.kebhana.hanapush")),
        BankAppTemplate("ibk", "IBK기업은행", setOf("com.ibk.android.ionebank")),
        BankAppTemplate("sc", "SC제일은행", setOf("com.scbank.ma30")),
        BankAppTemplate("citi", "씨티은행", setOf("kr.co.citibank.citimobile"))
    )

    val allPackageNames: Set<String> = all.flatMap { it.packageNames }.toSet()

    val bankNames: List<String> = all.map { it.name }

    fun templateById(id: String): BankAppTemplate? = all.firstOrNull { it.id == id }

    fun templateByPackage(packageName: String): BankAppTemplate? =
        all.firstOrNull { packageName in it.packageNames }

    fun labelForPackage(packageName: String): String =
        templateByPackage(packageName)?.name ?: "은행"

    /** 기존 사용자 하위 호환: 토스·신한·NH 기본 활성화 */
    val defaultEnabledPackageNames: Set<String> = setOf(
        "toss",
        "shinhan",
        "nh"
    ).flatMap { id -> templateById(id)?.packageNames.orEmpty() }.toSet()
}
