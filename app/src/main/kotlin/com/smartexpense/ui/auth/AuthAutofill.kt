@file:OptIn(ExperimentalComposeUiApi::class)

package com.smartexpense.ui.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.autofill.AutofillManager
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree

/**
 * 삼성 Pass / Google 비밀번호 관리자 등 시스템 Autofill이
 * 이메일·비밀번호 필드를 인식하도록 연결합니다.
 */
fun Modifier.authAutofill(
    vararg types: AutofillType,
    onFill: (String) -> Unit
): Modifier = composed {
    val autofill = LocalAutofill.current
    val autofillTree = LocalAutofillTree.current
    val typeList = types.toList()
    val autofillNode = remember(typeList) {
        AutofillNode(
            autofillTypes = typeList,
            onFill = onFill
        )
    }

    DisposableEffect(autofillNode) {
        autofillTree += autofillNode
        onDispose {
            // AutofillTree에는 remove API가 없어 노드만 해제합니다.
        }
    }

    this
        .onGloballyPositioned { coordinates ->
            autofillNode.boundingBox = coordinates.boundsInWindow()
        }
        .onFocusChanged { focusState ->
            autofill?.run {
                if (focusState.isFocused) {
                    requestAutofillForNode(autofillNode)
                } else {
                    cancelAutofillForNode(autofillNode)
                }
            }
        }
}

/** 로그인 성공 후 시스템 비밀번호 저장 팝업을 띄웁니다. */
fun Context.commitAuthAutofill() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val activity = findActivity() ?: return
    runCatching {
        activity.getSystemService(AutofillManager::class.java)?.commit()
    }
}

fun Context.cancelAuthAutofill() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val activity = findActivity() ?: return
    runCatching {
        activity.getSystemService(AutofillManager::class.java)?.cancel()
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
