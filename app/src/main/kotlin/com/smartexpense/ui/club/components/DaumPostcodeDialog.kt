package com.smartexpense.ui.club.components

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.smartexpense.ui.theme.BackgroundBlack
import com.smartexpense.ui.theme.TextPrimary
import com.smartexpense.ui.theme.TextSecondary

private const val JS_BRIDGE_NAME = "Android"
private const val POSTCODE_BASE_URL = "https://localhost/"

private val postcodeShellHtml = """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
    <style>
        html, body { margin: 0; padding: 0; width: 100%; height: 100%; background: #fff; }
        #layer { width: 100%; height: 100%; }
    </style>
</head>
<body>
    <div id="layer"></div>
</body>
</html>
""".trimIndent()

private const val INIT_POSTCODE_JS = """
(function() {
    if (window.__daumPostcodeInit) return;
    window.__daumPostcodeInit = true;
    function embedPostcode() {
        new daum.Postcode({
            oncomplete: function(data) {
                window.Android.processDATA(data.roadAddress);
            },
            width: '100%',
            height: '100%'
        }).embed(document.getElementById('layer'));
    }
    var script = document.createElement('script');
    script.src = 'https://t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js';
    script.onload = embedPostcode;
    script.onerror = function() {
        document.getElementById('layer').innerHTML =
            '<p style="padding:16px;font-family:sans-serif;color:#333;">' +
            '우편번호 서비스를 불러오지 못했습니다.</p>';
    };
    document.head.appendChild(script);
})();
"""

class AndroidBridge(
    private val onAddressReceived: (String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun processDATA(address: String) {
        mainHandler.post {
            if (address.isNotBlank()) {
                onAddressReceived(address)
            }
        }
    }
}

@Composable
fun DaumPostcodeDialog(
    onAddressSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val onAddressState = rememberUpdatedState(onAddressSelected)
    val onDismissState = rememberUpdatedState(onDismiss)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = BackgroundBlack
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("닫기", color = TextSecondary)
                }
                Text(
                    text = "주소 검색",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                DaumPostcodeWebView(
                    onAddressSelected = { address ->
                        onAddressState.value(address)
                        onDismissState.value()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
@Composable
private fun DaumPostcodeWebView(
    onAddressSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val onAddressState = rememberUpdatedState(onAddressSelected)
    val bridge = remember {
        AndroidBridge { address ->
            onAddressState.value(address)
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { _ ->
            val webViewContext = context.findActivity() ?: context
            WebView(webViewContext).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(android.graphics.Color.WHITE)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    javaScriptCanOpenWindowsAutomatically = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    loadsImagesAutomatically = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    setSupportMultipleWindows(true)
                    allowFileAccess = true
                    allowContentAccess = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    databaseEnabled = true
                }
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean = false

                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)
                        view.evaluateJavascript(INIT_POSTCODE_JS, null)
                    }
                }
                addJavascriptInterface(bridge, JS_BRIDGE_NAME)
                loadDataWithBaseURL(
                    POSTCODE_BASE_URL,
                    postcodeShellHtml,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.removeJavascriptInterface(JS_BRIDGE_NAME)
            webView.destroy()
        }
    )
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
