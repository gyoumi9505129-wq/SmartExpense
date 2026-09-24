package com.smartexpense.ui.club.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.smartexpense.ui.theme.SurfaceDeepGray
import com.smartexpense.ui.theme.TextPrimary
import com.smartexpense.ui.theme.TextSecondary
import java.time.LocalDate

@Composable
fun YearPickerDialog(
    initialYear: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
    modifier: Modifier = Modifier,
    minYear: Int = 2011,
    maxYear: Int = LocalDate.now().year
) {
    var selectedYear by remember(initialYear) { mutableIntStateOf(initialYear) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier.fillMaxWidth(0.9f),
            shape = MaterialTheme.shapes.extraLarge,
            color = SurfaceDeepGray
        ) {
            Column(modifier = Modifier.padding(vertical = 16.dp)) {
                Text(
                    text = "연도 선택",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )

                ClubWheelPicker(
                    label = "연도",
                    values = (minYear..maxYear).map { "${it}년" },
                    initialIndex = (initialYear - minYear).coerceIn(0, maxYear - minYear),
                    onSelectedIndex = { index -> selectedYear = minYear + index },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("취소", color = TextSecondary)
                    }
                    TextButton(
                        onClick = { onConfirm(selectedYear.coerceIn(minYear, maxYear)) }
                    ) {
                        Text("확인", color = TextPrimary)
                    }
                }
            }
        }
    }
}
