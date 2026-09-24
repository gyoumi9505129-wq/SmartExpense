package com.smartexpense.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.smartexpense.ui.theme.BackgroundBlack
import com.smartexpense.ui.theme.ExpenseRed
import com.smartexpense.ui.theme.IncomeBlue
import com.smartexpense.ui.theme.TextPrimary
import com.smartexpense.ui.theme.TextSecondary

@Composable
fun AppSplashScreen(
    modifier: Modifier = Modifier,
    showLoading: Boolean = true,
    errorMessage: String? = null,
    onRetry: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0E1116),
                        BackgroundBlack,
                        Color(0xFF121212)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            AppSplashBanner()

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(28.dp))
                Text(
                    text = "데이터베이스를 준비하지 못했습니다.",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ExpenseRed,
                    modifier = Modifier.padding(top = 12.dp),
                    textAlign = TextAlign.Center
                )
                if (onRetry != null) {
                    TextButton(
                        onClick = onRetry,
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Text(text = "다시 시도", color = TextPrimary)
                    }
                }
            } else if (showLoading) {
                Spacer(modifier = Modifier.height(36.dp))
                CircularProgressIndicator(color = IncomeBlue)
                Text(
                    text = "잠시만 기다려 주세요",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 14.dp)
                )
            }
        }
    }
}
