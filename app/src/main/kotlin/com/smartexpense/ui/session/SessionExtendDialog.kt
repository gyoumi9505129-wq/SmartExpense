package com.smartexpense.ui.session

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.smartexpense.ui.theme.ExpenseRed
import com.smartexpense.ui.theme.IncomeBlue
import com.smartexpense.ui.theme.SurfaceDeepGray
import com.smartexpense.ui.theme.TextPrimary
import com.smartexpense.ui.theme.TextSecondary

@Composable
fun SessionExtendDialog(
    secondsRemaining: Int,
    onExtend: () -> Unit,
    onLogout: () -> Unit
) {
    val minutes = secondsRemaining / 60
    val seconds = secondsRemaining % 60
    val countdownLabel = "자동 로그아웃까지 %d:%02d".format(minutes, seconds)

    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        title = {
            Text(
                text = "로그인 연장 안내",
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column {
                Text(
                    text = "개인정보 보호를 위해 로그인 후 10분 동안 이용이 없어 자동 로그아웃 예정입니다. 연장하시겠습니까?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = countdownLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = IncomeBlue,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onExtend) {
                Text("로그인 연장", color = IncomeBlue)
            }
        },
        dismissButton = {
            TextButton(onClick = onLogout) {
                Text("로그아웃", color = ExpenseRed)
            }
        },
        containerColor = SurfaceDeepGray
    )
}
