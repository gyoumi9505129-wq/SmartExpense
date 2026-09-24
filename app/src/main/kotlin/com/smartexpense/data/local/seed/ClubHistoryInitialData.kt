package com.smartexpense.data.local.seed

import com.smartexpense.data.local.entity.club.ClubHistoryEntity
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 엑셀 `◆ 한우리 모임 이력` 시트 기초 데이터.
 */
object ClubHistoryInitialData {

    private val isoDate = DateTimeFormatter.ISO_LOCAL_DATE

    data class ClubHistorySeedRecord(
        val date: String,
        val content: String,
        val details: String,
        val note: String? = null
    )

    val records: List<ClubHistorySeedRecord> = listOf(
        ClubHistorySeedRecord("2001-10-01", "한우리 모임 창단", "초대회장 : 박성남"),
        ClubHistorySeedRecord("2003-01-01", "2대 회장 선출", "회장 : 유락준"),
        ClubHistorySeedRecord("2005-01-01", "3대 회장 선출", "회장 : 강대화"),
        ClubHistorySeedRecord("2007-01-01", "4대 회장 선출", "회장 : 이용운"),
        ClubHistorySeedRecord("2009-01-01", "5대 회장 선출", "회장 : 안영준"),
        ClubHistorySeedRecord("2009-10-01", "회원추가 영입", "신태섭, 김성겸, 김민석"),
        ClubHistorySeedRecord("2011-01-01", "6대 회장 선출", "회장 : 이세희"),
        ClubHistorySeedRecord("2013-11-09", "7대 회장 선출", "회장 : 김성환"),
        ClubHistorySeedRecord("2015-01-17", "8대 회장 선출", "회장 : 유병학"),
        ClubHistorySeedRecord("2016-10-08", "9대 회장 선출", "회장 : 이충렬"),
        ClubHistorySeedRecord("2016-10-08", "회원추가 영입", "전상환"),
        ClubHistorySeedRecord("2019-04-06", "10대 회장 선출", "회장 : 김성겸"),
        ClubHistorySeedRecord(
            date = "2022-01-01",
            content = "회원 탈퇴",
            details = "이충렬(탈퇴)",
            note = "2022년 부터 탈퇴함."
        ),
        ClubHistorySeedRecord(
            date = "2022-05-21",
            content = "11대 회장 선출",
            details = "회장 : 김민석",
            note = "2022년 상반기모임에 선출됨"
        ),
        ClubHistorySeedRecord(
            date = "2022-12-17",
            content = "회원추가 영입",
            details = "최창국",
            note = "2022년 하반기모임에 영입"
        ),
        ClubHistorySeedRecord(
            date = "2026-01-01",
            content = "회원 사망",
            details = "유병학(사망)",
            note = "2026-01-01 사망 함."
        ),
        ClubHistorySeedRecord(
            date = "2026-06-27",
            content = "12대 회장 선출",
            details = "회장 : 김영섭",
            note = "2026년 상반기모임에서 선출"
        ),
        ClubHistorySeedRecord(
            date = "2026-06-27",
            content = "총무 선출",
            details = "총무 : 김성겸",
            note = "2026년 상반기모임에서 선출"
        )
    )

    fun toEntities(clubId: Long): List<ClubHistoryEntity> = records.map { record ->
        ClubHistoryEntity(
            clubId = clubId,
            date = parseDateToEpochMillis(record.date),
            content = record.content,
            details = record.details,
            note = record.note
        )
    }

    private fun parseDateToEpochMillis(date: String): Long =
        LocalDate.parse(date, isoDate)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
}
