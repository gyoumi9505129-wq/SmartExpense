package com.smartexpense.data.local.prefs

import com.smartexpense.data.repository.club.ClubSettingsRepository
import com.smartexpense.data.repository.club.SelectedClubRepository
import com.smartexpense.data.repository.club.requireSelectedClubId
import com.smartexpense.data.repository.club.scopedFlow
import com.smartexpense.di.ClubDatabaseGateway
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class DuesPaymentFilterPreferences @Inject constructor(
    private val clubSettingsRepository: ClubSettingsRepository,
    private val databaseGateway: ClubDatabaseGateway,
    private val selectedClubRepository: SelectedClubRepository
) {
    data class Filters(
        val year: Int?,
        val memberId: Long?
    )

    val filters: Flow<Filters> = selectedClubRepository.scopedFlow(databaseGateway) { clubId ->
        clubSettingsRepository.observeSettingsForClub(clubId).map { settings ->
            Filters(
                year = settings.duesPaymentFilterYear,
                memberId = settings.duesPaymentFilterMemberId
            )
        }
    }

    suspend fun setYear(year: Int?) {
        val clubId = selectedClubRepository.requireSelectedClubId()
        val current = clubSettingsRepository.getSettingsOnce(clubId)
        clubSettingsRepository.updateDuesPaymentFilter(
            clubId = clubId,
            year = year,
            memberId = current.duesPaymentFilterMemberId
        )
    }

    suspend fun setMemberId(memberId: Long?) {
        val clubId = selectedClubRepository.requireSelectedClubId()
        val current = clubSettingsRepository.getSettingsOnce(clubId)
        clubSettingsRepository.updateDuesPaymentFilter(
            clubId = clubId,
            year = current.duesPaymentFilterYear,
            memberId = memberId
        )
    }

    suspend fun setFilters(year: Int?, memberId: Long?) {
        val clubId = selectedClubRepository.requireSelectedClubId()
        clubSettingsRepository.updateDuesPaymentFilter(clubId, year, memberId)
    }
}
