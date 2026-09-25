package com.smartexpense.ui.club.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartexpense.ui.club.ClubNameViewModel
import com.smartexpense.ui.common.UserRoleBadge
import com.smartexpense.ui.theme.BackgroundBlack
import com.smartexpense.ui.theme.TextPrimary
import com.smartexpense.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClubMainTopAppBar(
    onOpenSettings: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onSwitchClub: () -> Unit,
    modifier: Modifier = Modifier,
    additionalActions: @Composable RowScope.() -> Unit = {}
) {
    val clubNameViewModel: ClubNameViewModel = hiltViewModel()
    val clubName by clubNameViewModel.clubName.collectAsStateWithLifecycle()
    val clubSlogan by clubNameViewModel.clubSlogan.collectAsStateWithLifecycle()
    val userRole by clubNameViewModel.userRole.collectAsStateWithLifecycle()
    val hasSlogan = clubSlogan.isNotBlank()

    CenterAlignedTopAppBar(
        // 단일 모임(한우리) 자동 입장 — 상단 뒤로가기(모임 목록 복귀) 불필요
        title = {
            Text(
                text = if (hasSlogan) {
                    styledClubSlogan(clubSlogan, TextPrimary)
                } else {
                    AnnotatedString(clubName)
                },
                style = if (hasSlogan) {
                    MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontStyle = FontStyle.Italic,
                        fontSize = 19.sp,
                        lineHeight = 24.sp,
                        letterSpacing = 0.6.sp
                    )
                } else {
                    MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.2.sp
                    )
                },
                color = TextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        },
        actions = {
            UserRoleBadge(
                role = userRole,
                modifier = Modifier.padding(end = 2.dp)
            )
            additionalActions()
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "설정",
                    tint = TextSecondary
                )
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = BackgroundBlack,
            titleContentColor = TextPrimary
        ),
        modifier = modifier
    )
}
