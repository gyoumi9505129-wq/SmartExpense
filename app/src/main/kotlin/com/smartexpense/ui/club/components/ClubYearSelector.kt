package com.smartexpense.ui.club.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ClubYearSelector(
    yearLabel: String,
    onPreviousYear: () -> Unit,
    onNextYear: () -> Unit,
    canGoNext: Boolean,
    modifier: Modifier = Modifier,
    onYearClick: (() -> Unit)? = null,
    showReturnToCurrentYear: Boolean = false,
    returnBadgeLabel: String = java.time.LocalDate.now().year.toString(),
    onReturnToCurrentYear: () -> Unit = {}
) {
    ClubPeriodNavBar(
        title = yearLabel,
        onPrevious = onPreviousYear,
        onNext = onNextYear,
        canGoNext = canGoNext,
        onTitleClick = onYearClick,
        showReturnToCurrent = showReturnToCurrentYear,
        returnBadgeLabel = returnBadgeLabel,
        onReturnToCurrent = onReturnToCurrentYear,
        previousContentDescription = "이전 연도",
        nextContentDescription = "다음 연도",
        modifier = modifier.padding(horizontal = 8.dp, vertical = 8.dp)
    )
}
