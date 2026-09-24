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
import java.time.YearMonth

@Composable
fun YearMonthPickerDialog(
    initialYearMonth: YearMonth,
    onDismiss: () -> Unit,
    onConfirm: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
    minYear: Int = 2011,
    maxYear: Int = YearMonth.now().year
) {
    var selectedYear by remember(initialYearMonth) { mutableIntStateOf(initialYearMonth.year) }
    var selectedMonth by remember(initialYearMonth) { mutableIntStateOf(initialYearMonth.monthValue) }

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
                    text = "연·월 선택",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    ClubWheelPicker(
                        label = "연도",
                        values = (minYear..maxYear).map { "${it}년" },
                        initialIndex = (initialYearMonth.year - minYear).coerceIn(0, maxYear - minYear),
                        onSelectedIndex = { index -> selectedYear = minYear + index },
                        modifier = Modifier.weight(1f)
                    )
                    ClubWheelPicker(
                        label = "월",
                        values = (1..12).map { "${it}월" },
                        initialIndex = initialYearMonth.monthValue - 1,
                        onSelectedIndex = { index -> selectedMonth = index + 1 },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("취소", color = TextSecondary)
                    }
                    TextButton(
                        onClick = {
                            onConfirm(YearMonth.of(selectedYear, selectedMonth))
                        }
                    ) {
                        Text("확인", color = TextPrimary)
                    }
                }
            }
        }
    }
}
