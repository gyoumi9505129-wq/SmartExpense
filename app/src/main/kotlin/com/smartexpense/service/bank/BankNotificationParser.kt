package com.smartexpense.service.bank

import com.smartexpense.data.model.bank.BankNotificationDraft
import com.smartexpense.data.model.bank.BankAppMaster

object BankNotificationParser {
    private val amountRegex = Regex("""(\d{1,3}(?:,\d{3})*|\d+)\s*원""")
    private val depositNameRegex = Regex("""(.+?)님이\s*(\d{1,3}(?:,\d{3})*|\d+)\s*원""")
    private val depositSimpleRegex = Regex("""입금\s*(\d{1,3}(?:,\d{3})*|\d+)\s*원\s*(.*)""")
    private val withdrawSimpleRegex = Regex("""출금\s*(\d{1,3}(?:,\d{3})*|\d+)\s*원\s*(.*)""")

    fun parse(text: String, packageName: String, appLabel: String? = null): BankNotificationDraft? {
        val normalized = text.replace("\n", " ").replace(Regex("\\s+"), " ").trim()
        if (normalized.isBlank()) return null

        depositNameRegex.find(normalized)?.let { match ->
            val amount = match.groupValues[2].replace(",", "").toIntOrNull() ?: return null
            val memo = match.groupValues[1].trim()
            return BankNotificationDraft(amount = amount, memo = memo, sourcePackage = packageName)
        }

        depositSimpleRegex.find(normalized)?.let { match ->
            val amount = match.groupValues[1].replace(",", "").toIntOrNull() ?: return null
            val memo = match.groupValues[2].trim().ifBlank { "입금" }
            return BankNotificationDraft(amount = amount, memo = memo, sourcePackage = packageName)
        }

        withdrawSimpleRegex.find(normalized)?.let { match ->
            val amount = match.groupValues[1].replace(",", "").toIntOrNull() ?: return null
            val memo = match.groupValues[2].trim().ifBlank { "출금" }
            return BankNotificationDraft(amount = amount, memo = memo, sourcePackage = packageName)
        }

        val amountMatch = amountRegex.find(normalized) ?: return null
        val amount = amountMatch.groupValues[1].replace(",", "").toIntOrNull() ?: return null
        if (amount <= 0) return null

        val memo = normalized
            .replace(amountMatch.value, "")
            .replace(Regex("""[\[\]()|]"""), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "${appLabel ?: BankAppMaster.labelForPackage(packageName)} 알림" }

        return BankNotificationDraft(amount = amount, memo = memo, sourcePackage = packageName)
    }
}
