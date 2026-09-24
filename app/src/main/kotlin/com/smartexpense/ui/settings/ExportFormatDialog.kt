package com.smartexpense.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartexpense.ui.theme.SurfaceDeepGray
import com.smartexpense.ui.theme.TextPrimary
import com.smartexpense.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportFormatDialog(
    isExporting: Boolean,
    exportYear: Int,
    yearOptions: List<Int>,
    onExportYearChange: (Int) -> Unit,
    onExportExcel: () -> Unit,
    onExportPdf: () -> Unit,
    onDismiss: () -> Unit
) {
    var yearMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isExporting) onDismiss() },
        title = {
            Text("데이터 내보내기", color = TextPrimary)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ExposedDropdownMenuBox(
                    expanded = yearMenuExpanded && !isExporting,
                    onExpandedChange = { if (!isExporting) yearMenuExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = "${exportYear}년",
                        onValueChange = {},
                        readOnly = true,
                        enabled = !isExporting,
                        label = { Text("연도 선택") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(
                                expanded = yearMenuExpanded && !isExporting
                            )
                        },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = yearMenuExpanded && !isExporting,
                        onDismissRequest = { yearMenuExpanded = false }
                    ) {
                        yearOptions.forEach { year ->
                            DropdownMenuItem(
                                text = { Text("${year}년", color = TextPrimary) },
                                onClick = {
                                    onExportYearChange(year)
                                    yearMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "${exportYear}년 기준 장부·결산 데이터를 파일로 저장합니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                if (isExporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(top = 20.dp),
                        color = TextPrimary
                    )
                    Text(
                        text = "파일 생성 중…",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onExportExcel, enabled = !isExporting) {
                Text("Excel로 내보내기", color = TextPrimary)
            }
        },
        dismissButton = {
            Column {
                TextButton(onClick = onExportPdf, enabled = !isExporting) {
                    Text("PDF 보고서", color = TextPrimary)
                }
                TextButton(onClick = onDismiss, enabled = !isExporting) {
                    Text("취소", color = TextSecondary)
                }
            }
        },
        containerColor = SurfaceDeepGray
    )
}
