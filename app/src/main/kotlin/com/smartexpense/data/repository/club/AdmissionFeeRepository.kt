package com.smartexpense.data.repository.club

import com.smartexpense.data.local.entity.club.MemberStatus
import com.smartexpense.di.ClubDatabaseGateway
import com.smartexpense.domain.admission.AdmissionFeeBreakdown
import com.smartexpense.domain.admission.AdmissionFeeCalculator
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Singleton
class AdmissionFeeRepository @Inject constructor(
    private val databaseGateway: ClubDatabaseGateway,
    private val selectedClubRepository: SelectedClubRepository
) {
    private val clubTransactionDao get() = databaseGateway.clubTransactionDao()
    private val yearlyDuesDao get() = databaseGateway.yearlyDuesDao()
    private val memberDao get() = databaseGateway.memberDao()
    fun observeBreakdown(): Flow<AdmissionFeeBreakdown?> =
        selectedClubRepository.scopedFlowOrNull(databaseGateway) { clubId ->
            combine(
                clubTransactionDao.observeNetBalance(clubId),
                yearlyDuesDao.observeTotalUnpaidDues(clubId),
                memberDao.observeCountByStatus(clubId, MemberStatus.ACTIVE)
            ) { netBalance, unpaidDues, activeCount ->
                AdmissionFeeCalculator.calculate(
                    totalBalance = netBalance,
                    totalUnpaidDues = unpaidDues,
                    activeMemberCount = activeCount
                )
            }
        }
}
