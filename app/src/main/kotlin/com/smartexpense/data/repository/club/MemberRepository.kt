package com.smartexpense.data.repository.club

import androidx.room.withTransaction
import com.smartexpense.data.firebase.CloudLedgerGateway
import com.smartexpense.data.firebase.CloudLedgerReadPolicy
import com.smartexpense.data.firebase.SystemAdminConfig
import com.smartexpense.data.local.entity.club.EntityTimestamps
import com.smartexpense.data.local.entity.club.MemberEntity
import com.smartexpense.data.local.entity.club.MemberStatus
import com.smartexpense.data.local.entity.club.MemberStatusHistoryEntity
import com.smartexpense.di.ClubDatabaseGateway
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class MemberRepository @Inject constructor(
    private val databaseGateway: ClubDatabaseGateway,
    private val selectedClubRepository: SelectedClubRepository,
    private val cloudLedgerGateway: CloudLedgerGateway,
    private val cloudLedgerReadPolicy: CloudLedgerReadPolicy
) {
    private val database get() = databaseGateway.database()
    private val memberDao get() = databaseGateway.memberDao()
    private val memberStatusHistoryDao get() = databaseGateway.memberStatusHistoryDao()

    private suspend fun isCloudMode(): Boolean = cloudLedgerReadPolicy.isCloudWriteMode()

    fun observeAllMembers(): Flow<List<MemberEntity>> =
        cloudLedgerReadPolicy.observeList(
            cloud = { cloudLedgerGateway.observeMembersAsEntities() },
            room = {
                selectedClubRepository.scopedListFlow(databaseGateway) { clubId ->
                    memberDao.observeAll(clubId)
                }
            }
        ).map { it.withoutSystemAdminMembership() }

    fun observeActiveMembers(): Flow<List<MemberEntity>> =
        cloudLedgerReadPolicy.observeList(
            cloud = { cloudLedgerGateway.observeActiveMembersAsEntities() },
            room = {
                selectedClubRepository.scopedListFlow(databaseGateway) { clubId ->
                    memberDao.observeActiveMembers(clubId)
                }
            }
        ).map { it.withoutSystemAdminMembership() }

    fun observeCountByStatus(status: MemberStatus): Flow<Int> =
        observeAllMembers().map { list -> list.count { it.status == status } }

    fun observeStatusHistory(memberId: Long): Flow<List<MemberStatusHistoryEntity>> =
        selectedClubRepository.scopedFlow(databaseGateway) { clubId ->
            memberDao.observeMemberHistory(memberId, clubId)
        }

    suspend fun getMember(id: Long): MemberEntity? {
        // 목록 구독에 이미 있는 행을 우선 사용한다. 문서 ID 매핑 누락으로 단건 조회가
        // null을 돌려 수정 화면이 열리지 않는 경우를 막는다.
        val streamed = runCatching { observeAllMembers().first() }.getOrNull()
            ?.find { it.id == id }
        if (streamed != null) return streamed
        if (isCloudMode()) {
            return cloudLedgerGateway.getMember(id)?.takeUnless { it.isSystemAdminClubMembership() }
        }
        val clubId = runCatching { selectedClubRepository.requireSelectedClubId() }.getOrNull()
        val local = clubId?.let { memberDao.getById(id, it) }
        if (local != null) return local.takeUnless { it.isSystemAdminClubMembership() }
        return runCatching { cloudLedgerGateway.getMember(id) }.getOrNull()
            ?.takeUnless { it.isSystemAdminClubMembership() }
    }

    suspend fun getStatusHistory(memberId: Long): List<MemberStatusHistoryEntity> {
        if (isCloudMode()) return emptyList()
        val clubId = selectedClubRepository.requireSelectedClubId()
        return memberDao.getMemberHistory(memberId, clubId)
    }

    suspend fun insertMember(member: MemberEntity): Long {
        if (member.isSystemAdminClubMembership()) {
            throw IllegalStateException("시스템관리자는 모임 회원으로 등록되지 않습니다.")
        }
        if (isCloudMode()) {
            return cloudLedgerGateway.insertMember(member)
        }
        val clubId = selectedClubRepository.requireSelectedClubId()
        return memberDao.insert(
            member.copy(clubId = clubId, updatedAt = EntityTimestamps.now())
        )
    }

    suspend fun updateMember(member: MemberEntity) {
        if (isCloudMode()) {
            cloudLedgerGateway.updateMember(member)
            return
        }
        val clubId = selectedClubRepository.requireSelectedClubId()
        memberDao.update(member.copy(clubId = clubId, updatedAt = EntityTimestamps.now()))
    }

    suspend fun updateMemberWithStatusHistory(
        member: MemberEntity,
        statusHistory: MemberStatusHistoryEntity?
    ) {
        if (isCloudMode()) {
            cloudLedgerGateway.updateMember(member)
            return
        }
        val clubId = selectedClubRepository.requireSelectedClubId()
        val now = EntityTimestamps.now()
        database.withTransaction {
            memberDao.update(member.copy(clubId = clubId, updatedAt = now))
            statusHistory?.let {
                memberStatusHistoryDao.insert(it.copy(clubId = clubId, updatedAt = now))
            }
        }
    }

    suspend fun deleteMember(member: MemberEntity) {
        if (isCloudMode()) {
            cloudLedgerGateway.deleteMember(member)
            return
        }
        val clubId = selectedClubRepository.requireSelectedClubId()
        memberDao.delete(member.copy(clubId = clubId))
    }
}

private fun List<MemberEntity>.withoutSystemAdminMembership(): List<MemberEntity> =
    filterNot { it.isSystemAdminClubMembership() }

private fun MemberEntity.isSystemAdminClubMembership(): Boolean =
    SystemAdminConfig.isClubMembershipRecord(email = email, name = name)
