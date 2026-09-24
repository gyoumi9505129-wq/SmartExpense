package com.smartexpense.data.firebase

/**
 * Firestore 문서 ID가 비어 있을 때 Room PK 기반 안정 ID를 만듭니다.
 * (증분 업로드 시 문서 중복 생성 방지)
 */
object CloudDocIds {
    fun member(clubId: Long, roomId: Long): String = "m_${clubId}_$roomId"
    fun transaction(clubId: Long, roomId: Long): String = "t_${clubId}_$roomId"
    fun dues(clubId: Long, roomId: Long): String = "d_${clubId}_$roomId"
    fun detail(clubId: Long, roomId: Long): String = "dd_${clubId}_$roomId"
    fun history(clubId: Long, roomId: Int): String = "h_${clubId}_$roomId"
    fun account(clubId: Long, roomId: Int): String = "a_${clubId}_$roomId"
    fun eventExpense(clubId: Long, roomId: Long): String = "e_${clubId}_$roomId"
}
