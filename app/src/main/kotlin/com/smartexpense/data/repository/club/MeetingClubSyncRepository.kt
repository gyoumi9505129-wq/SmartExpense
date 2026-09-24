package com.smartexpense.data.repository.club

import com.smartexpense.data.firebase.CloudDataMigrationRepository
import com.smartexpense.data.firebase.firestore.MeetingDoc
import com.smartexpense.data.local.entity.club.ClubEntity
import com.smartexpense.data.local.entity.club.ClubMembershipStatus
import com.smartexpense.data.local.entity.club.DuesPaymentMethod
import com.smartexpense.di.ClubDatabaseGateway
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MeetingClubSyncRepository @Inject constructor(
    private val databaseGateway: ClubDatabaseGateway,
    private val clubSettingsRepository: ClubSettingsRepository,
    private val selectedClubRepository: SelectedClubRepository,
    private val selectedMeetingRepository: SelectedMeetingRepository,
    private val cloudDataMigrationRepository: CloudDataMigrationRepository
) {
    private val clubDao get() = databaseGateway.clubDao()

    suspend fun ensureLocalClubForMeeting(
        meeting: MeetingDoc,
        membershipStatus: ClubMembershipStatus,
        downloadIfEmpty: Boolean = true
    ): Long {
        val existing = clubDao.getByFirestoreMeetingId(meeting.id)
        val now = System.currentTimeMillis()
        val clubId = if (existing != null) {
            clubDao.updateMeetingLink(
                clubId = existing.clubId,
                name = meeting.name,
                slogan = meeting.slogan,
                meetingId = meeting.id,
                status = membershipStatus.storageValue,
                updatedAt = now
            )
            existing.clubId
        } else {
            val id = clubDao.insert(
                ClubEntity(
                    clubName = meeting.name,
                    clubSlogan = meeting.slogan,
                    firestoreMeetingId = meeting.id,
                    membershipStatus = membershipStatus.storageValue,
                    createdAt = now,
                    updatedAt = now
                )
            )
            clubSettingsRepository.initializeForNewClub(id)
            id
        }
        meeting.duesPaymentMethod.takeIf { it.isNotBlank() }?.let { raw ->
            clubSettingsRepository.updateDuesPaymentMethod(
                clubId,
                DuesPaymentMethod.fromStorage(raw)
            )
        }
        selectedMeetingRepository.setSelectedMeetingId(meeting.id)
        selectedClubRepository.setSelectedClubId(clubId)
        if (downloadIfEmpty &&
            (membershipStatus == ClubMembershipStatus.APPROVED ||
                membershipStatus == ClubMembershipStatus.OWNER)
        ) {
            runCatching {
                cloudDataMigrationRepository.downloadCloudToCurrentClub(meeting.name)
            }
        }
        return clubId
    }
}
