package com.smartexpense.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartexpense.domain.user.UserRole

private val BadgeShape = RoundedCornerShape(12.dp)

private data class RoleBadgeColors(
    val container: Color,
    val content: Color
)

private fun UserRole.badgeColors(): RoleBadgeColors = when (this) {
    UserRole.SYSTEM_ADMIN -> RoleBadgeColors(
        container = Color(0x403D2A5C),
        content = Color(0xFFE8C87A)
    )
    UserRole.CLUB_ADMIN -> RoleBadgeColors(
        container = Color(0x401E3D32),
        content = Color(0xFF7BC4A8)
    )
    UserRole.MEMBER -> RoleBadgeColors(
        container = Color(0x402A2A2A),
        content = Color(0xFFB0B0B0)
    )
}

@Composable
fun UserRoleBadge(
    role: UserRole,
    modifier: Modifier = Modifier
) {
    val colors = role.badgeColors()
    Surface(
        modifier = modifier,
        shape = BadgeShape,
        color = colors.container,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = role.emoji,
                fontSize = 12.sp,
                lineHeight = 14.sp
            )
            Text(
                text = role.label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.1.sp
                ),
                color = colors.content,
                maxLines = 1
            )
        }
    }
}
