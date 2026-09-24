package com.smartexpense.data.repository.club

import androidx.room.withTransaction
import com.smartexpense.data.local.entity.club.ClubEntity
import com.smartexpense.di.ClubDatabaseGateway
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data class DissolveClubResult(
    val dissolvedClubName: String,
    val nextClubId: Long?
)

@Singleton
class ClubRepository @Inject constructor(
    private val databaseGateway: ClubDatabaseGateway,
    private val selectedClubRepository: SelectedClubRepository,
    private val clubSettingsRepository: ClubSettingsRepository,
    private val displayNotifier: SelectedClubDisplayNotifier
) {
    private val clubDao get() = databaseGateway.clubDao()
    private val memberDao get() = databaseGateway.memberDao()
    private val yearlyDuesDao get() = databaseGateway.yearlyDuesDao()
    private val clubTransactionDao get() = databaseGateway.clubTransactionDao()
    private val clubHistoryDao get() = databaseGateway.clubHistoryDao()
    private val clubAccountDao get() = databaseGateway.clubAccountDao()
    private val customBankDao get() = databaseGateway.customBankDao()
    private val clubSettingsDao get() = databaseGateway.clubSettingsDao()
    private val database get() = databaseGateway.database()

    fun observeAllClubs(): Flow<List<ClubEntity>> = databaseGateway.observeAllClubs()

    suspend fun getAllClubsOnce(): List<ClubEntity> = clubDao.getAllOnce()

    suspend fun getClub(clubId: Long): ClubEntity? = clubDao.getById(clubId)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeSelectedClubSlogan(): Flow<String> =
        databaseGateway.roomFlowTyped {
            selectedClubRepository.selectedClubId.flatMapLatest { clubId ->
                if (clubId == null) {
                    flowOf("")
                } else {
                    clubDao.observeById(clubId).map { entity ->
                        entity?.clubSlogan.orEmpty().trim()
                    }
                }
            }
        }

    suspend fun updateClubSlogan(clubId: Long, slogan: String) {
        require(clubDao.getById(clubId) != null) { "선택된 모임을 찾을 수 없습니다." }
        val trimmed = slogan.trim()
        clubDao.updateSlogan(clubId, trimmed)
        displayNotifier.notifySloganChanged(clubId, trimmed)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeSelectedClubName(): Flow<String> =
        databaseGateway.roomFlowTyped {
            selectedClubRepository.selectedClubId.flatMapLatest { clubId ->
                if (clubId == null) {
                    flowOf("모임")
                } else {
                    flow {
                        emit(clubDao.getById(clubId)?.clubName ?: "모임")
                    }
                }
            }
        }

    suspend fun getSelectedClubName(): String {
        val clubId = selectedClubRepository.selectedClubId.first() ?: return "모임"
        return clubDao.getById(clubId)?.clubName ?: "모임"
    }

    suspend fun isDatabaseEmpty(): Boolean = clubDao.count() == 0

    suspend fun reconcileSelectedClub() {
        val clubId = selectedClubRepository.selectedClubId.first()
        if (clubId != null && clubDao.getById(clubId) != null) return

        val clubs = clubDao.getAllOnce()
        if (clubs.isEmpty()) {
            if (clubId != null) {
                selectedClubRepository.clearSelectedClubId()
            }
            return
        }
        selectedClubRepository.setSelectedClubId(clubs.first().clubId)
    }

    suspend fun deleteClub(clubId: Long) {
        require(clubDao.getById(clubId) != null) { "삭제할 모임을 찾을 수 없습니다." }
        database.withTransaction {
            deleteAllClubScopedData(clubId)
            clubDao.deleteById(clubId)
        }
        if (selectedClubRepository.selectedClubId.first() == clubId) {
            selectedClubRepository.clearSelectedClubId()
        }
    }

    /** Smart Overwrite 복원 전: 모임 엔티티는 유지하고 하위 데이터만 삭제합니다. */
    suspend fun clearClubScopedDataForRestore(clubId: Long) {
        require(clubDao.getById(clubId) != null) { "복원 대상 모임을 찾을 수 없습니다." }
        deleteAllClubScopedData(clubId)
    }

    /**
     * 현재 모임을 해체합니다. 하위 데이터와 ClubEntity를 삭제하고,
     * 남은 모임이 있으면 첫 번째 모임으로 선택 ID를 전환합니다.
     */
    suspend fun dissolveClub(clubId: Long): DissolveClubResult {
        val club = clubDao.getById(clubId)
            ?: throw IllegalArgumentException("삭제할 모임을 찾을 수 없습니다.")
        database.withTransaction {
            deleteAllClubScopedData(clubId)
            clubDao.deleteById(clubId)
        }
        val wasSelected = selectedClubRepository.selectedClubId.first() == clubId
        val remaining = clubDao.getAllOnce()
        val nextClubId = remaining.firstOrNull()?.clubId
        if (wasSelected) {
            if (nextClubId != null) {
                selectedClubRepository.setSelectedClubId(nextClubId)
            } else {
                selectedClubRepository.clearSelectedClubId()
            }
        }
        return DissolveClubResult(
            dissolvedClubName = club.clubName,
            nextClubId = nextClubId
        )
    }

    private suspend fun deleteAllClubScopedData(clubId: Long) {
        clubTransactionDao.deleteAll(clubId)
        databaseGateway.duesPaymentHistoryDao().deleteAll(clubId)
        yearlyDuesDao.deleteAllDetails(clubId)
        yearlyDuesDao.deleteAllYearlyDues(clubId)
        memberDao.deleteAllStatusHistory(clubId)
        memberDao.deleteAllMembers(clubId)
        clubHistoryDao.deleteAll(clubId)
        clubAccountDao.deleteAll(clubId)
        customBankDao.deleteAllByClubId(clubId)
        clubSettingsDao.deleteAllByClubId(clubId)
    }

    suspend fun createClub(name: String): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "모임 이름을 입력해 주세요." }
        val clubId = clubDao.insert(
            ClubEntity(
                clubName = trimmed,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
        clubSettingsRepository.initializeForNewClub(clubId)
        return clubId
    }
}
