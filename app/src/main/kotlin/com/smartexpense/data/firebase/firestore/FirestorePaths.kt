package com.smartexpense.data.firebase.firestore

object FirestorePaths {
    const val MEETINGS = "meetings"
    const val MEMBERS = "members"
    const val TRANSACTIONS = "transactions"
    const val DUES = "dues"
    const val ACCOUNTS = "accounts"
    const val EVENT_EXPENSES = "eventExpenses"
    const val HISTORIES = "histories"
    const val SECURITY = "security"
    const val JOIN_REQUESTS = "joinRequests"
    const val USER_PROFILES = "userProfiles"
    /** 모든 로그인 사용자가 검색할 수 있는 모임 공개 목록 */
    const val MEETING_DIRECTORY = "meetingDirectory"

    fun meetings() = MEETINGS
    fun meeting(meetingId: String) = "$MEETINGS/$meetingId"
    fun meetingDirectory() = MEETING_DIRECTORY
    fun meetingDirectoryEntry(meetingId: String) = "$MEETING_DIRECTORY/$meetingId"
    fun members(meetingId: String) = "$MEETINGS/$meetingId/$MEMBERS"
    fun transactions(meetingId: String) = "$MEETINGS/$meetingId/$TRANSACTIONS"
    fun dues(meetingId: String) = "$MEETINGS/$meetingId/$DUES"
    fun accounts(meetingId: String) = "$MEETINGS/$meetingId/$ACCOUNTS"
    fun eventExpenses(meetingId: String) = "$MEETINGS/$meetingId/$EVENT_EXPENSES"
    fun histories(meetingId: String) = "$MEETINGS/$meetingId/$HISTORIES"
    fun joinRequests(meetingId: String) = "$MEETINGS/$meetingId/$JOIN_REQUESTS"
    /** 레거시 PIN 문서 정리용 (신규 저장 없음) */
    fun security(meetingId: String) = "$MEETINGS/$meetingId/$SECURITY"
    fun userProfile(uid: String) = "$USER_PROFILES/$uid"
}
