package com.smartexpense.ui.club.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartexpense.ui.club.components.ClubLockedModalBottomSheet
import com.smartexpense.ui.theme.BackgroundBlack
import com.smartexpense.ui.theme.DividerSubtle
import com.smartexpense.ui.theme.ExpenseRed
import com.smartexpense.ui.theme.IncomeBlue
import com.smartexpense.ui.theme.SurfaceDeepGray
import com.smartexpense.ui.theme.TextPrimary
import com.smartexpense.ui.theme.TextSecondary
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDetailBottomSheet(
    year: Int,
    category: String,
    items: List<CategoryDetailItemUi>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    ClubLockedModalBottomSheet(
        containerColor = BackgroundBlack,
        onDismiss = onDismiss,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = "${year}년 $category 상세 내역",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 18.dp)
            )

            CategoryDetailTableHeader()

            if (items.isEmpty()) {
                Text(
                    text = "해당 카테고리의 상세 내역이 없습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        CategoryDetailRow(item = item)
                        HorizontalDivider(color = DividerSubtle, thickness = 0.5.dp)
                    }
                }
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SurfaceDeepGray,
                    contentColor = TextPrimary
                )
            ) {
                Text("닫기")
            }
        }
    }
}

@Composable
private fun CategoryDetailTableHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "날짜",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            modifier = Modifier.weight(0.7f)
        )
        Text(
            text = "비고",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            modifier = Modifier.weight(1.5f),
            textAlign = TextAlign.Center
        )
        Text(
            text = "금액",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End
        )
    }
    HorizontalDivider(color = DividerSubtle, thickness = 0.5.dp)
}

@Composable
private fun CategoryDetailRow(
    item: CategoryDetailItemUi,
    modifier: Modifier = Modifier
) {
    val amountColor = if (item.isIncome) IncomeBlue else ExpenseRed
    val sign = if (item.isIncome) "+" else "−"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item.date,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            modifier = Modifier.weight(0.7f)
        )
        Text(
            text = item.note.orEmpty().ifBlank { "−" },
            style = MaterialTheme.typography.bodyMedium,
            color = if (item.note.isNullOrBlank()) TextSecondary else TextPrimary,
            modifier = Modifier
                .weight(1.5f)
                .padding(horizontal = 8.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = "$sign%,d원".format(Locale.KOREA, item.amount),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = amountColor,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End
        )
    }
}
