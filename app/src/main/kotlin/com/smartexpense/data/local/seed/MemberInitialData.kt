package com.smartexpense.data.local.seed

import com.smartexpense.data.local.entity.club.MemberEntity
import com.smartexpense.data.local.entity.club.MemberRole
import com.smartexpense.data.local.entity.club.MemberStatus

private data class MemberSeed(
    val name: String,
    val phone: String,
    val joinDate: String,
    val residenceRegion: String,
    val status: MemberStatus = MemberStatus.ACTIVE,
    val role: MemberRole = MemberRole.GENERAL,
    val suspensionDate: String? = null,
)

private fun formatJoinDate(yyyymmdd: String): String {
    val digits = yyyymmdd.filter { it.isDigit() }
    if (digits.length < 8) return ""
    return "${digits.take(4)}-${digits.drop(4).take(2)}-${digits.drop(6)}"
}

private fun MemberSeed.toEntity(clubId: Long): MemberEntity = MemberEntity(
    clubId = clubId,
    joinDate = formatJoinDate(joinDate),
    name = name,
    birthDate = "",
    address = "",
    detailAddress = "",
    residenceRegion = residenceRegion,
    phone = phone,
    email = "",
    status = status,
    role = role,
    suspensionDate = suspensionDate,
    updatedAt = System.currentTimeMillis()
)

object MemberInitialData {
    val residenceByName: Map<String, String> = mapOf(
        "강대화" to "대전",
        "김민석" to "경기도 광명",
        "김성겸" to "경기도 화성",
        "김성환" to "인천",
        "김영섭" to "충남 온양",
        "박성남" to "충남 서천",
        "유락준" to "경기도 동탄",
        "유진호" to "전라도 광주",
        "최낙선" to "경기도 수원",
        "전상환" to "인천",
        "최창국" to "충남 서천",
    )

    fun members(clubId: Long): List<MemberEntity> = listOf(
        // 활동 회원
        MemberSeed(name = "강대화", phone = "01043000667", joinDate = "20011001", residenceRegion = "대전"),
        MemberSeed(name = "김민석", phone = "01039097013", joinDate = "20091001", residenceRegion = "경기도 광명", role = MemberRole.PRESIDENT),
        MemberSeed(name = "김성겸", phone = "01091282930", joinDate = "20091001", residenceRegion = "경기도 화성", role = MemberRole.TREASURER),
        MemberSeed(name = "김성환", phone = "01054238295", joinDate = "20011001", residenceRegion = "인천"),
        MemberSeed(name = "김영섭", phone = "01089933027", joinDate = "20011001", residenceRegion = "충남 온양"),
        MemberSeed(name = "박성남", phone = "01074437411", joinDate = "20011001", residenceRegion = "충남 서천"),
        MemberSeed(name = "유락준", phone = "01075358448", joinDate = "20011001", residenceRegion = "경기도 동탄"),
        MemberSeed(name = "유진호", phone = "01047651031", joinDate = "20011001", residenceRegion = "전라도 광주"),
        MemberSeed(name = "최낙선", phone = "01028434950", joinDate = "20011001", residenceRegion = "경기도 수원"),
        MemberSeed(name = "전상환", phone = "01037479926", joinDate = "20161008", residenceRegion = "인천"),
        MemberSeed(name = "최창국", phone = "01086262646", joinDate = "20221217", residenceRegion = "충남 서천"),
        // Sheet2 과거 이력 회원 (상태: E열)
        MemberSeed(name = "유병학", phone = "", joinDate = "20011001", residenceRegion = "", status = MemberStatus.DORMANT),
        MemberSeed(name = "박상수", phone = "", joinDate = "20011001", residenceRegion = "", status = MemberStatus.DORMANT),
        MemberSeed(name = "이세희", phone = "", joinDate = "20011001", residenceRegion = "", status = MemberStatus.DORMANT),
        MemberSeed(name = "신태섭", phone = "", joinDate = "20091001", residenceRegion = "", status = MemberStatus.WITHDRAWN),
        MemberSeed(name = "이충열", phone = "", joinDate = "20011001", residenceRegion = "", status = MemberStatus.WITHDRAWN, suspensionDate = "2022-01-01"),
        MemberSeed(name = "조갑선", phone = "", joinDate = "20011001", residenceRegion = "", status = MemberStatus.WITHDRAWN),
        MemberSeed(name = "안영준", phone = "", joinDate = "20011001", residenceRegion = "", status = MemberStatus.WITHDRAWN),
        MemberSeed(name = "이용운", phone = "", joinDate = "20011001", residenceRegion = "", status = MemberStatus.WITHDRAWN),
        MemberSeed(name = "황재헌", phone = "", joinDate = "20011001", residenceRegion = "", status = MemberStatus.WITHDRAWN),
    ).map { it.toEntity(clubId) }
}
