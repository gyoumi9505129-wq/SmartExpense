package com.smartexpense.ui.common

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.smartexpense.ApplicationEntryPoint
import com.smartexpense.data.session.AuthRefreshGuard
import dagger.hilt.android.EntryPointAccessors

/**
 * 화면이 사용자에게 다시 보일 때 [onRefresh]를 호출합니다.
 *
 * - [isScreenActive]: HorizontalPager 탭 등에서 현재 선택된 화면만 true로 전달
 * - Activity ON_RESUME: 설정·백스택 복귀 등 Activity가 다시 활성화될 때
 * - [isScreenActive]가 true로 전환될 때: 탭 전환 직후
 *
 * 인증(잠금 해제)되지 않았거나 명시적 로그아웃 직후에는 [onRefresh]를 호출하지 않습니다.
 */
@Composable
fun RefreshOnScreenVisible(
    isScreenActive: Boolean = true,
    onRefresh: () -> Unit
) {
    val authRefreshGuard = rememberAuthRefreshGuard()
    val lastRefreshAt = remember { mutableLongStateOf(0L) }
    val guardedRefresh = remember(authRefreshGuard, onRefresh) {
        {
            val now = SystemClock.elapsedRealtime()
            if (now - lastRefreshAt.longValue >= 2_000L &&
                authRefreshGuard.shouldAllowDataRefresh()
            ) {
                lastRefreshAt.longValue = now
                onRefresh()
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, isScreenActive, guardedRefresh) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && isScreenActive) {
                guardedRefresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(isScreenActive, guardedRefresh) {
        if (isScreenActive) {
            guardedRefresh()
        }
    }
}

@Composable
fun rememberAuthRefreshGuard(): AuthRefreshGuard {
    val context = LocalContext.current
    return remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            ApplicationEntryPoint::class.java
        ).authRefreshGuard()
    }
}
