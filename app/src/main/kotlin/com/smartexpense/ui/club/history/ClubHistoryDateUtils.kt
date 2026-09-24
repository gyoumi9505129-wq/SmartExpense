package com.smartexpense.ui.club.history

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val displayFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
private val timelineYearFormatter = DateTimeFormatter.ofPattern("yyyy")
private val timelineMonthDayFormatter = DateTimeFormatter.ofPattern("MM.dd")

fun clubHistoryDateToEpochMillis(date: String): Long? {
    return runCatching {
        LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }.getOrNull()
}

fun clubHistoryEpochMillisToDate(epochMillis: Long): String {
    return Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(DateTimeFormatter.ISO_LOCAL_DATE)
}

fun formatClubHistoryDisplayDate(epochMillis: Long): String {
    return Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(displayFormatter)
}

fun formatClubHistoryTimelineYear(epochMillis: Long): String {
    return Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(timelineYearFormatter)
}

fun formatClubHistoryTimelineMonthDay(epochMillis: Long): String {
    return Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(timelineMonthDayFormatter)
}
