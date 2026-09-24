package com.smartexpense.data.repository.club

import com.smartexpense.data.local.entity.club.ClubHistoryEntity
import com.smartexpense.data.local.entity.club.EntityTimestamps
import com.smartexpense.di.ClubDatabaseGateway
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class ClubHistoryRepository @Inject constructor(
    private val databaseGateway: ClubDatabaseGateway,
    private val selectedClubRepository: SelectedClubRepository
) {
    private val clubHistoryDao get() = databaseGateway.clubHistoryDao()

    fun observeAllAsc(): Flow<List<ClubHistoryEntity>> =
        selectedClubRepository.scopedListFlow(databaseGateway) { clubId -> clubHistoryDao.observeAllAsc(clubId) }

    fun observeAllDesc(): Flow<List<ClubHistoryEntity>> =
        selectedClubRepository.scopedListFlow(databaseGateway) { clubId -> clubHistoryDao.observeAllDesc(clubId) }

    suspend fun getById(id: Int): ClubHistoryEntity? {
        val clubId = selectedClubRepository.requireSelectedClubId()
        return clubHistoryDao.getById(id, clubId)
    }

    suspend fun insert(history: ClubHistoryEntity): Long {
        val clubId = selectedClubRepository.requireSelectedClubId()
        return clubHistoryDao.insert(
            history.copy(clubId = clubId, updatedAt = EntityTimestamps.now())
        )
    }

    suspend fun update(history: ClubHistoryEntity) {
        val clubId = selectedClubRepository.requireSelectedClubId()
        clubHistoryDao.update(
            history.copy(clubId = clubId, updatedAt = EntityTimestamps.now())
        )
    }

    suspend fun delete(history: ClubHistoryEntity) {
        val clubId = selectedClubRepository.requireSelectedClubId()
        clubHistoryDao.delete(history.copy(clubId = clubId))
    }
}
