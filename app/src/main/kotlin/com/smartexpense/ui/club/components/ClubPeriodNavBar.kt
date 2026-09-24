package com.smartexpense.ui.club.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smartexpense.ui.theme.TextPrimary
import com.smartexpense.ui.theme.TextSecondary

@Composable
fun ClubReturnToCurrentBadge(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        border = BorderStroke(1.dp, TextSecondary.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = TextPrimary.copy(alpha = 0.04f),
            contentColor = TextSecondary
        )
    ) {
        Icon(
            imageVector = Icons.Default.Today,
            contentDescription = label,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
fun ClubPeriodNavBar(
    title: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    canGoNext: Boolean = true,
    onTitleClick: (() -> Unit)? = null,
    showReturnToCurrent: Boolean = false,
    returnBadgeLabel: String = "",
    onReturnToCurrent: () -> Unit = {},
    previousContentDescription: String = "이전",
    nextContentDescription: String = "다음"
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = previousContentDescription,
                tint = TextPrimary
            )
        }

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            val titleModifier = if (onTitleClick != null) {
                Modifier.clickable(onClick = onTitleClick)
            } else {
                Modifier
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = titleModifier
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showReturnToCurrent) {
                ClubReturnToCurrentBadge(
                    label = returnBadgeLabel,
                    onClick = onReturnToCurrent,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
            IconButton(onClick = onNext, enabled = canGoNext) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = nextContentDescription,
                    tint = if (canGoNext) TextPrimary else TextSecondary
                )
            }
        }
    }
}
