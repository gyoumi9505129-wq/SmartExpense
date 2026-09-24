package com.smartexpense.data.repository.club

import com.smartexpense.data.local.entity.club.ClubAccountEntity
import com.smartexpense.data.local.entity.club.EntityTimestamps
import com.smartexpense.di.ClubDatabaseGateway
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class ClubAccountRepository @Inject constructor(
    private val databaseGateway: ClubDatabaseGateway,
    private val selectedClubRepository: SelectedClubRepository
) {
    private val clubAccountDao get() = databaseGateway.clubAccountDao()

    fun observeAll(): Flow<List<ClubAccountEntity>> =
        selectedClubRepository.scopedListFlow(databaseGateway) { clubId -> clubAccountDao.observeAll(clubId) }

    suspend fun getAllOnce(): List<ClubAccountEntity> {
        val clubId = selectedClubRepository.requireSelectedClubId()
        return clubAccountDao.getAllOnce(clubId)
    }

    suspend fun insert(account: ClubAccountEntity): Long {
        val clubId = selectedClubRepository.requireSelectedClubId()
        return clubAccountDao.insert(
            account.copy(clubId = clubId, updatedAt = EntityTimestamps.now())
        )
    }

    suspend fun delete(account: ClubAccountEntity) {
        val clubId = selectedClubRepository.requireSelectedClubId()
        clubAccountDao.delete(account.copy(clubId = clubId))
    }
}
