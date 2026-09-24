package com.smartexpense.data.firebase.firestore

import com.google.firebase.firestore.DocumentSnapshot
import com.smartexpense.data.local.entity.club.ClubTransactionType
import com.smartexpense.data.local.entity.club.MemberStatus
import java.time.Instant
import java.time.ZoneId

fun DocumentSnapshot.readCreatedAtString(): String {
    getString("createdAt")?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
    runCatching { getTimestamp("createdAt") }.getOrNull()?.toDate()?.let { date ->
        return Instant.ofEpochMilli(date.time)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toString()
    }
    val millis = runCatching { getLong("createdAt") }.getOrNull() ?: return ""
    if (millis <= 0L) return ""
    return Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .toString()
}

fun DocumentSnapshot.readLedgerDateString(): String {
    getString("date")?.trim()?.takeIf { it.isNotBlank() }?.let { raw ->
        val normalized = com.smartexpense.domain.ledger.LedgerDate.toStorage(raw)
        if (normalized.isNotBlank()) return normalized
    }
    runCatching { getTimestamp("date") }.getOrNull()?.toDate()?.let { date ->
        return Instant.ofEpochMilli(date.time)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toString()
    }
    val millis = runCatching { getLong("date") }.getOrNull() ?: return ""
    if (millis <= 0L) return ""
    return Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .toString()
}

fun DocumentSnapshot.readMoney(field: String): Long {
    runCatching { getLong(field) }.getOrNull()?.let { return it }
    runCatching { getDouble(field) }.getOrNull()?.let { return it.toLong() }
    getString(field)
        ?.replace(",", "")
        ?.replace("원", "")
        ?.trim()
        ?.toLongOrNull()
        ?.let { return it }
    return 0L
}

/** Firestore에 저장하는 회원 상태. enum name을 우선하고, 한글 라벨도 읽기 호환. */
object MemberStatusLabels {
    const val ACTIVE = "활동중"
    const val DORMANT = "휴면"
    const val WITHDRAWN = "탈퇴"

    fun toFirestore(status: MemberStatus): String = status.name

    fun fromFirestore(raw: String): MemberStatus {
        val trimmed = raw.trim()
        return when (trimmed) {
            MemberStatus.ACTIVE.name, ACTIVE, "활동" -> MemberStatus.ACTIVE
            MemberStatus.DORMANT.name, DORMANT -> MemberStatus.DORMANT
            MemberStatus.WITHDRAWN.name, WITHDRAWN -> MemberStatus.WITHDRAWN
            else -> MemberStatus.ACTIVE
        }
    }

    fun toDisplayLabel(status: MemberStatus): String = when (status) {
        MemberStatus.ACTIVE -> ACTIVE
        MemberStatus.DORMANT -> DORMANT
        MemberStatus.WITHDRAWN -> WITHDRAWN
    }
}

data class MeetingDoc(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val slogan: String = "",
    val createdAt: String = "",
    val ownerUid: String = "",
    /** 지정 운영관리자 UID. 비어 있어도 개설자·시스템관리자는 편집·동기화 가능. */
    val adminUid: String = "",
    val sharedWith: List<String> = emptyList(),
    /**
     * 휴면·탈퇴로 접근이 막힌 계정 UID.
     * [toMap]에는 넣지 않고 전용 필드로만 갱신한다. 빈 목록으로 덮어쓰지 않기 위함.
     */
    val inactiveMemberUids: List<String> = emptyList(),
    /**
     * 휴면/탈퇴 사유. uid → DORMANT | WITHDRAWN.
     * [toMap]에는 넣지 않고 전용 필드로만 갱신한다.
     */
    val inactiveMemberReasons: Map<String, String> = emptyMap(),
    /** 기초 이월금. null/음수는 잔액 재계산 시 0 처리. */
    val initialBalance: Long? = null,
    /** 강제 재계산으로 유지하는 현재 잔액. */
    val currentBalance: Long? = null,
    /** 신규 회비 등록 기본 납부 방식. 비어 있으면 앱 기본값(반기별). */
    val duesPaymentMethod: String = "",
    /**
     * 아직 앱에 로그인하지 않은 기존 명단 회원 등에게 예약한 초대 이메일(소문자).
     * 로그인 시 본인 이메일과 일치하면 sharedWith에 합류한다.
     */
    val invitedEmails: List<String> = emptyList()
) {
    fun toMap(): Map<String, Any?> = buildMap {
        put("name", name)
        put("description", description)
        put("slogan", slogan)
        put("createdAt", createdAt)
        put("ownerUid", ownerUid)
        put("adminUid", adminUid)
        put("sharedWith", sharedWith)
        put("initialBalance", initialBalance)
        put("currentBalance", currentBalance)
        if (duesPaymentMethod.isNotBlank()) put("duesPaymentMethod", duesPaymentMethod)
        if (invitedEmails.isNotEmpty()) put("invitedEmails", invitedEmails.map { it.lowercase() })
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun from(snapshot: DocumentSnapshot): MeetingDoc {
            val shared = (snapshot.get("sharedWith") as? List<*>)
                ?.mapNotNull { it as? String }
                .orEmpty()
            val inactive = (snapshot.get("inactiveMemberUids") as? List<*>)
                ?.mapNotNull { it as? String }
                .orEmpty()
            val reasons = (snapshot.get("inactiveMemberReasons") as? Map<*, *>)
                ?.mapNotNull { (key, value) ->
                    val uid = key as? String ?: return@mapNotNull null
                    val reason = value as? String ?: return@mapNotNull null
                    uid to reason
                }
                ?.toMap()
                .orEmpty()
            val ownerUid = snapshot.getString("ownerUid").orEmpty()
            val adminUid = snapshot.getString("adminUid").orEmpty()
            val invited = (snapshot.get("invitedEmails") as? List<*>)
                ?.mapNotNull { (it as? String)?.trim()?.lowercase()?.takeIf { email -> email.isNotBlank() } }
                .orEmpty()
            return MeetingDoc(
                id = snapshot.id,
                name = snapshot.getString("name").orEmpty(),
                description = snapshot.getString("description").orEmpty(),
                slogan = snapshot.getString("slogan").orEmpty(),
                createdAt = snapshot.readCreatedAtString(),
                ownerUid = ownerUid,
                adminUid = adminUid,
                sharedWith = shared,
                inactiveMemberUids = inactive,
                inactiveMemberReasons = reasons,
                initialBalance = snapshot.getLong("initialBalance"),
                currentBalance = snapshot.getLong("currentBalance"),
                duesPaymentMethod = snapshot.getString("duesPaymentMethod").orEmpty(),
                invitedEmails = invited
            )
        }
    }
}

data class MemberDoc(
    val id: String = "",
    val name: String = "",
    val status: String = MemberStatus.ACTIVE.name,
    val joinDate: String = "",
    val phone: String = "",
    val birthDate: String = "",
    val address: String = "",
    val detailAddress: String = "",
    val residenceRegion: String = "",
    val email: String = "",
    val role: String = "GENERAL",
    val isLunarBirth: Boolean = false,
    val suspensionDate: String? = null,
    /** 로그인한 계정과 회원 장부를 연결. 비어 있으면 관리자가 수동 등록한 회원. */
    val linkedUid: String = "",
    /** 본인이 회원정보 입력 폼을 저장한 경우에만 true. 관리자 명단/자동 연동은 false. */
    val selfEnrolled: Boolean = false
) {
    fun toMap(): Map<String, Any?> = buildMap {
        put("name", name)
        put("status", status)
        put("joinDate", joinDate)
        put("phone", phone)
        put("birthDate", birthDate)
        put("address", address)
        put("detailAddress", detailAddress)
        put("residenceRegion", residenceRegion)
        put("email", email)
        put("role", role)
        put("isLunarBirth", isLunarBirth)
        put("suspensionDate", suspensionDate)
        if (linkedUid.isNotBlank()) put("linkedUid", linkedUid)
        if (selfEnrolled) put("selfEnrolled", true)
    }

    fun statusEnum(): MemberStatus = MemberStatusLabels.fromFirestore(status)

    fun matchesAccount(uid: String, email: String?): Boolean {
        if (uid.isNotBlank() && (linkedUid == uid || id == uid)) return true
        val memberEmail = this.email.trim()
        return memberEmail.isNotBlank() &&
            !email.isNullOrBlank() &&
            memberEmail.equals(email.trim(), ignoreCase = true)
    }

    fun linkedAccountUid(): String? {
        linkedUid.trim().takeIf { it.isNotBlank() }?.let { return it }
        return id.trim().takeIf { looksLikeFirebaseUid(it) }
    }

    companion object {
        fun looksLikeFirebaseUid(value: String): Boolean {
            val trimmed = value.trim()
            return trimmed.length >= 20 && trimmed.any { it.isLetter() }
        }

        fun from(snapshot: DocumentSnapshot): MemberDoc =
            MemberDoc(
                id = snapshot.id,
                name = snapshot.getString("name").orEmpty(),
                status = snapshot.getString("status")
                    ?: MemberStatus.ACTIVE.name,
                joinDate = snapshot.getString("joinDate").orEmpty(),
                phone = snapshot.getString("phone").orEmpty(),
                birthDate = snapshot.getString("birthDate").orEmpty(),
                address = snapshot.getString("address").orEmpty(),
                detailAddress = snapshot.getString("detailAddress").orEmpty(),
                residenceRegion = snapshot.getString("residenceRegion").orEmpty(),
                email = snapshot.getString("email").orEmpty(),
                role = snapshot.getString("role") ?: "GENERAL",
                isLunarBirth = snapshot.getBoolean("isLunarBirth") ?: false,
                suspensionDate = snapshot.getString("suspensionDate"),
                linkedUid = snapshot.getString("linkedUid").orEmpty(),
                selfEnrolled = snapshot.getBoolean("selfEnrolled") == true
            )
    }
}

data class TransactionDoc(
    val id: String = "",
    val date: String = "",
    val type: String = ClubTransactionType.EXPENSE.name,
    val category: String = "",
    val amount: Long = 0L,
    val incomeAmount: Int = 0,
    val expenseAmount: Int = 0,
    val description: String = "",
    val memberId: String? = null,
    val balanceAfter: Int? = null,
    val note: String? = null,
    /** Firestore 회비 detail 문서 id (회비↔장부 연동) */
    val linkedDuesDetailId: String? = null
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "date" to date,
        "type" to type,
        "category" to category,
        "amount" to amount,
        "incomeAmount" to incomeAmount,
        "expenseAmount" to expenseAmount,
        "description" to description,
        "memberId" to memberId,
        "balanceAfter" to balanceAfter,
        "note" to (note ?: description),
        "linkedDuesDetailId" to linkedDuesDetailId
    )

    companion object {
        fun from(snapshot: DocumentSnapshot): TransactionDoc {
            val income = snapshot.readMoney("incomeAmount")
            val expense = snapshot.readMoney("expenseAmount")
            val amount = snapshot.readMoney("amount").takeIf { it > 0L }
                ?: if (income > 0L) income else expense
            val typeRaw = snapshot.getString("type").orEmpty()
            val resolvedType = ClubTransactionType.fromStorage(typeRaw).name
            return TransactionDoc(
                id = snapshot.id,
                date = snapshot.readLedgerDateString(),
                type = resolvedType.ifBlank { ClubTransactionType.EXPENSE.name },
                category = snapshot.getString("category").orEmpty(),
                amount = amount,
                incomeAmount = income.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
                expenseAmount = expense.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
                description = snapshot.getString("description")
                    ?: snapshot.getString("note").orEmpty(),
                memberId = snapshot.getString("memberId"),
                balanceAfter = snapshot.readMoney("balanceAfter").takeIf { it != 0L }?.toInt()
                    ?: snapshot.getLong("balanceAfter")?.toInt(),
                note = snapshot.getString("note"),
                linkedDuesDetailId = snapshot.getString("linkedDuesDetailId")
            )
        }
    }
}

data class DuesPaymentDoc(
    val payDate: String = "",
    val amount: Long = 0L,
    /** Firestore 장부 거래 문서 id (분납 1건 ↔ 장부 1건) */
    val linkedTransactionId: String? = null
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "payDate" to payDate,
        "amount" to amount,
        "linkedTransactionId" to linkedTransactionId
    )

    companion object {
        fun fromMap(raw: Map<String, Any?>): DuesPaymentDoc =
            DuesPaymentDoc(
                payDate = raw["payDate"] as? String ?: "",
                amount = (raw["amount"] as? Number)?.toLong() ?: 0L,
                linkedTransactionId = raw["linkedTransactionId"] as? String
            )
    }
}

data class DuesDetailDoc(
    val id: String = "",
    val termLabel: String = "",
    val amount: Int = 0,
    val paidAmount: Long = 0L,
    val isPaid: Boolean = false,
    val isExcluded: Boolean = false,
    val payDate: String = "",
    val payments: List<DuesPaymentDoc> = emptyList()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "termLabel" to termLabel,
        "amount" to amount,
        "paidAmount" to paidAmount,
        "isPaid" to isPaid,
        "isExcluded" to isExcluded,
        "payDate" to payDate,
        "payments" to payments.map { it.toMap() }
    )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(raw: Map<String, Any?>): DuesDetailDoc {
            val paymentsRaw = raw["payments"] as? List<*>
            val payments = paymentsRaw.orEmpty().mapNotNull { item ->
                val map = item as? Map<*, *> ?: return@mapNotNull null
                DuesPaymentDoc.fromMap(map.entries.associate { (k, v) -> k.toString() to v })
            }
            return DuesDetailDoc(
                id = raw["id"] as? String ?: "",
                termLabel = raw["termLabel"] as? String ?: "",
                amount = (raw["amount"] as? Number)?.toInt() ?: 0,
                paidAmount = (raw["paidAmount"] as? Number)?.toLong() ?: 0L,
                isPaid = raw["isPaid"] as? Boolean ?: false,
                isExcluded = raw["isExcluded"] as? Boolean ?: false,
                payDate = raw["payDate"] as? String ?: "",
                payments = payments
            )
        }
    }
}

data class DuesDoc(
    val id: String = "",
    val memberId: String = "",
    val year: Int = 0,
    val totalTargetAmount: Int = 0,
    val paymentMethod: String = "YEARLY",
    val details: List<DuesDetailDoc> = emptyList()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "memberId" to memberId,
        "year" to year,
        "totalTargetAmount" to totalTargetAmount,
        "paymentMethod" to paymentMethod,
        "details" to details.map { it.toMap() }
    )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun from(snapshot: DocumentSnapshot): DuesDoc {
            val detailsRaw = snapshot.get("details") as? List<*>
            val details = detailsRaw.orEmpty().mapNotNull { item ->
                val map = item as? Map<*, *> ?: return@mapNotNull null
                DuesDetailDoc.fromMap(map.entries.associate { (k, v) -> k.toString() to v })
            }
            return DuesDoc(
                id = snapshot.id,
                memberId = snapshot.getString("memberId").orEmpty(),
                year = (snapshot.getLong("year") ?: 0L).toInt(),
                totalTargetAmount = (snapshot.getLong("totalTargetAmount") ?: 0L).toInt(),
                paymentMethod = snapshot.getString("paymentMethod") ?: "YEARLY",
                details = details
            )
        }
    }
}

data class ClubHistoryDoc(
    val id: String = "",
    val date: Long = 0L,
    val content: String = "",
    val details: String = "",
    val note: String? = null
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "date" to date,
        "content" to content,
        "details" to details,
        "note" to note
    )

    companion object {
        fun from(snapshot: DocumentSnapshot): ClubHistoryDoc =
            ClubHistoryDoc(
                id = snapshot.id,
                date = snapshot.getLong("date") ?: 0L,
                content = snapshot.getString("content").orEmpty(),
                details = snapshot.getString("details").orEmpty(),
                note = snapshot.getString("note")
            )
    }
}

data class ClubAccountDoc(
    val id: String = "",
    val bankName: String = "",
    val accountNumber: String = "",
    val holderName: String = ""
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "bankName" to bankName,
        "accountNumber" to accountNumber,
        "holderName" to holderName
    )

    companion object {
        fun from(snapshot: DocumentSnapshot): ClubAccountDoc =
            ClubAccountDoc(
                id = snapshot.id,
                bankName = snapshot.getString("bankName").orEmpty(),
                accountNumber = snapshot.getString("accountNumber").orEmpty(),
                holderName = snapshot.getString("holderName").orEmpty()
            )
    }
}

data class EventExpenseDoc(
    val id: String = "",
    val memberId: String = "",
    val date: String = "",
    val eventSubCategory: String = "",
    val amount: Int = 0,
    val note: String? = null,
    val linkedTransactionId: String? = null,
    val isSeedOnly: Boolean = false
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "memberId" to memberId,
        "date" to date,
        "eventSubCategory" to eventSubCategory,
        "amount" to amount,
        "note" to note,
        "linkedTransactionId" to linkedTransactionId,
        "isSeedOnly" to isSeedOnly
    )

    companion object {
        fun from(snapshot: DocumentSnapshot): EventExpenseDoc =
            EventExpenseDoc(
                id = snapshot.id,
                memberId = snapshot.getString("memberId").orEmpty(),
                date = snapshot.getString("date").orEmpty(),
                eventSubCategory = snapshot.getString("eventSubCategory").orEmpty(),
                amount = (snapshot.getLong("amount") ?: 0L).toInt(),
                note = snapshot.getString("note"),
                linkedTransactionId = snapshot.getString("linkedTransactionId"),
                isSeedOnly = snapshot.getBoolean("isSeedOnly") ?: false
            )
    }
}
