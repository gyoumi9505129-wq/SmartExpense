package com.smartexpense.data.repository.club

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** 선택 모임의 표시 정보(슬로건 등) 변경을 앱 전역 UI에 즉시 전파합니다. */
@Singleton
class SelectedClubDisplayNotifier @Inject constructor() {
    private val _sloganUpdates = MutableSharedFlow<SloganUpdate>(extraBufferCapacity = 1)
    val sloganUpdates: SharedFlow<SloganUpdate> = _sloganUpdates.asSharedFlow()

    fun notifySloganChanged(clubId: Long, slogan: String) {
        _sloganUpdates.tryEmit(SloganUpdate(clubId = clubId, slogan = slogan.trim()))
    }

    data class SloganUpdate(val clubId: Long, val slogan: String)
}
