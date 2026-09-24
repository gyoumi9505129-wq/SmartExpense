package com.smartexpense.domain.dues

import com.smartexpense.data.local.ClubConstants
import com.smartexpense.data.local.entity.club.DuesPaymentMethod
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object DuesTermSchedule {
    val HANURI_FOUNDING_DATE: LocalDate = LocalDate.of(2001, 10, 1)

    fun termLabelsFor(method: DuesPaymentMethod): List<String> = when (method) {
        DuesPaymentMethod.MONTHLY -> (1..12).map { "${it}월" }
        DuesPaymentMethod.QUARTERLY -> (1..4).map { "${it}분기" }
        DuesPaymentMethod.HALF_YEARLY -> listOf("상반기", "하반기")
        DuesPaymentMethod.YEARLY -> listOf("연간")
    }

    fun parseMeetingCreatedAt(raw: String?): LocalDate? = parseMembershipDate(raw)

    fun parseMembershipDate(raw: String?): LocalDate? {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return null
        runCatching { LocalDate.parse(value.take(10)) }.getOrNull()?.let { return it }
        runCatching { Instant.parse(value).atZone(ZoneId.systemDefault()).toLocalDate() }
            .getOrNull()
            ?.let { return it }
        val digits = value.filter { it.isDigit() }
        if (digits.length >= 8) {
            runCatching {
                LocalDate.parse(digits.take(8), DateTimeFormatter.BASIC_ISO_DATE)
            }.getOrNull()?.let { return it }
        }
        digits.toLongOrNull()?.takeIf { it > 1_000_000_000_000L }?.let { millis ->
            return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
        }
        return null
    }

    /**
     * 한우리는 2001-10-01 창단. 앱에서 모임을 다시 만들면 createdAt 이 오늘이 되어
     * 과거 연도 하반기까지 제외되는 것을 막는다.
     */
    fun effectiveMeetingCreatedAt(meetingName: String?, createdAtRaw: String?): LocalDate? {
        val parsed = parseMembershipDate(createdAtRaw)
        if (ClubConstants.isHanuriSeedClub(meetingName.orEmpty())) {
            return when {
                parsed == null -> HANURI_FOUNDING_DATE
                parsed.isAfter(HANURI_FOUNDING_DATE) -> HANURI_FOUNDING_DATE
                else -> parsed
            }
        }
        return parsed
    }

    /** 회원 가입일과 모임 시작일 중, 실제로 회비가 시작되는 날. */
    fun effectiveMembershipStart(
        joinDateRaw: String?,
        meetingName: String?,
        meetingCreatedAtRaw: String?
    ): LocalDate? {
        val joinDate = parseMembershipDate(joinDateRaw)
        val meetingStart = effectiveMeetingCreatedAt(meetingName, meetingCreatedAtRaw)
        return when {
            joinDate == null -> meetingStart
            meetingStart == null -> joinDate
            joinDate.isBefore(meetingStart) -> meetingStart
            else -> joinDate
        }
    }

    fun effectiveMembershipStart(
        joinDate: LocalDate?,
        meetingCreatedAt: LocalDate?
    ): LocalDate? = when {
        joinDate == null -> meetingCreatedAt
        meetingCreatedAt == null -> joinDate
        joinDate.isBefore(meetingCreatedAt) -> meetingCreatedAt
        else -> joinDate
    }

    /**
     * 가입·개설 이전에 해당하는 회차인지.
     * 하반기(7월 이후) 가입이면 그 해 상반기만 제외하고, 하반기(7~12월·3~4분기)는 납부 가능하게 둔다.
     */
    fun isTermBeforeMembershipStart(
        duesYear: Int,
        method: DuesPaymentMethod,
        termLabel: String,
        membershipStart: LocalDate?
    ): Boolean {
        if (membershipStart == null) return false
        if (duesYear < membershipStart.year) return true
        if (duesYear > membershipStart.year) return false
        val startMonth = membershipStart.monthValue
        val endMonth = termEndMonth(method, termLabel)
        return if (startMonth >= 7) {
            endMonth <= 6
        } else {
            endMonth < startMonth
        }
    }

    fun isTermBeforeMeetingStart(
        duesYear: Int,
        method: DuesPaymentMethod,
        termLabel: String,
        meetingCreatedAt: LocalDate?
    ): Boolean = isTermBeforeMembershipStart(duesYear, method, termLabel, meetingCreatedAt)

    fun termEndMonth(method: DuesPaymentMethod, termLabel: String): Int {
        val label = termLabel.trim()
        return when (method) {
            DuesPaymentMethod.MONTHLY ->
                label.removeSuffix("월").toIntOrNull()?.coerceIn(1, 12) ?: 12
            DuesPaymentMethod.QUARTERLY -> when (label) {
                "1분기" -> 3
                "2분기" -> 6
                "3분기" -> 9
                "4분기" -> 12
                else -> 12
            }
            DuesPaymentMethod.HALF_YEARLY -> if (label == "상반기") 6 else 12
            DuesPaymentMethod.YEARLY -> 12
        }
    }
}
