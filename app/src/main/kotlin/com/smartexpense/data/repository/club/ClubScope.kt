package com.smartexpense.data.repository.club

import com.smartexpense.di.ClubDatabaseGateway
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

suspend fun SelectedClubRepository.requireSelectedClubId(): Long =
    selectedClubId.first() ?: error("선택된 모임이 없습니다.")

@OptIn(ExperimentalCoroutinesApi::class)
fun <T> SelectedClubRepository.scopedFlow(
    gateway: ClubDatabaseGateway,
    block: (Long) -> Flow<T>
): Flow<T> = gateway.roomFlowTyped {
    selectedClubId.flatMapLatest { clubId ->
        if (clubId == null) flowOf() else block(clubId)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
fun <T> SelectedClubRepository.scopedFlowOrDefault(
    gateway: ClubDatabaseGateway,
    defaultValue: T,
    block: (Long) -> Flow<T>
): Flow<T> = gateway.roomFlowTyped {
    selectedClubId.flatMapLatest { clubId ->
        if (clubId == null) flowOf(defaultValue) else block(clubId)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
fun <T> SelectedClubRepository.scopedListFlow(
    gateway: ClubDatabaseGateway,
    block: (Long) -> Flow<List<T>>
): Flow<List<T>> = gateway.roomFlowTyped {
    selectedClubId.flatMapLatest { clubId ->
        if (clubId == null) flowOf(emptyList()) else block(clubId)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
fun <T> SelectedClubRepository.scopedFlowOrNull(
    gateway: ClubDatabaseGateway,
    block: (Long) -> Flow<T>
): Flow<T?> = gateway.roomFlowTyped {
    selectedClubId.flatMapLatest { clubId ->
        if (clubId == null) flowOf(null) else block(clubId).map { it }
    }
}
