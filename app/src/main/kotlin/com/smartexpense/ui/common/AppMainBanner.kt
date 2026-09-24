package com.smartexpense.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.smartexpense.R

/**
 * 앱 메인 배너 (`R.drawable.app_main_banner`).
 * 비율을 유지하며 화면에 맞게 축소합니다.
 */
@Composable
fun AppMainBanner(
    modifier: Modifier = Modifier,
    maxHeight: Dp = 168.dp,
    contentScale: ContentScale = ContentScale.Fit
) {
    Image(
        painter = painterResource(id = R.drawable.app_main_banner),
        contentDescription = stringResource(R.string.app_name),
        contentScale = contentScale,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
    )
}

@Composable
fun AppSplashBanner(
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(id = R.drawable.app_main_banner),
        contentDescription = stringResource(R.string.app_name),
        contentScale = ContentScale.Fit,
        modifier = modifier
            .fillMaxWidth(0.86f)
            .padding(horizontal = 28.dp)
            .heightIn(max = 280.dp)
    )
}
