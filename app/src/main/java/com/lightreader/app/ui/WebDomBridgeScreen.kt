package com.lightreader.app.ui

import android.annotation.SuppressLint
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.lightreader.app.R
import com.lightreader.app.core.web.HtmlBridge
import com.lightreader.app.core.web.HtmlDomSnapshot

@SuppressLint("JavascriptInterface")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebDomBridgeScreen(viewModel: MainViewModel) {
    var inputUrl by rememberSaveable { mutableStateOf("https://example.com/") }
    var requestedUrl by rememberSaveable { mutableStateOf(inputUrl) }
    var status by remember { mutableStateOf<DomBridgeStatus>(DomBridgeStatus.Waiting) }
    var snapshot by remember { mutableStateOf<HtmlDomSnapshot?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val webViewRef = remember { WebViewRef() }
    val bridge: HtmlBridge = remember {
        HtmlBridge { domSnapshot ->
            mainHandler.post {
                snapshot = domSnapshot
                status = DomBridgeStatus.ReceivedHtml(domSnapshot.rawHtml.length)
            }
        }
    }
    val client = remember {
        object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                status = DomBridgeStatus.PageFinished
                view.evaluateJavascript(HtmlBridge.extractionScript, null)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                if (request.isForMainFrame) {
                    status = DomBridgeStatus.PageFailed(error.description?.toString().orEmpty())
                }
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse,
            ) {
                if (request.isForMainFrame) {
                    status = DomBridgeStatus.HttpError(errorResponse.statusCode, errorResponse.reasonPhrase.orEmpty())
                }
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                status = DomBridgeStatus.SslFailed
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef.webView?.apply {
                stopLoading()
                removeJavascriptInterface(HtmlBridge.BRIDGE_NAME)
                destroy()
            }
            webViewRef.webView = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(stringResource(R.string.dom_title)) },
                navigationIcon = {
                    IconButton(onClick = viewModel::goBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = inputUrl,
                    onValueChange = { inputUrl = it },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.dom_url_label)) },
                    singleLine = true,
                    isError = inputUrl.isNotBlank() && !inputUrl.startsWith("https://", ignoreCase = true),
                    supportingText = {
                        if (inputUrl.isNotBlank() && !inputUrl.startsWith("https://", ignoreCase = true)) {
                            Text(stringResource(R.string.dom_https_only))
                        }
                    },
                )
                Button(
                    onClick = {
                        val normalizedUrl = inputUrl.trim()
                        requestedUrl = normalizedUrl
                        status = DomBridgeStatus.Loading(normalizedUrl)
                        snapshot = null
                    },
                    enabled = inputUrl.trim().startsWith("https://", ignoreCase = true),
                ) {
                    Text(stringResource(R.string.action_load))
                }
            }

            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        webViewRef.webView = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        addJavascriptInterface(bridge, HtmlBridge.BRIDGE_NAME)
                        webViewClient = client
                    }
                },
                update = { webView ->
                    if (webView.tag != requestedUrl) {
                        webView.tag = requestedUrl
                        webView.loadUrl(requestedUrl)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(status.text(), style = MaterialTheme.typography.titleSmall)
                    snapshot?.let { domSnapshot ->
                        Text(stringResource(R.string.dom_body_preview), style = MaterialTheme.typography.labelLarge)
                        Text(
                            domSnapshot.bodyPreview(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private class WebViewRef {
    var webView: WebView? = null
}

private sealed interface DomBridgeStatus {
    data object Waiting : DomBridgeStatus
    data class ReceivedHtml(val length: Int) : DomBridgeStatus
    data object PageFinished : DomBridgeStatus
    data class PageFailed(val reason: String) : DomBridgeStatus
    data class HttpError(val code: Int, val reason: String) : DomBridgeStatus
    data object SslFailed : DomBridgeStatus
    data class Loading(val url: String) : DomBridgeStatus
}

@Composable
private fun DomBridgeStatus.text(): String = when (this) {
    DomBridgeStatus.Waiting -> stringResource(R.string.dom_waiting)
    is DomBridgeStatus.ReceivedHtml -> stringResource(R.string.dom_received_html, length)
    DomBridgeStatus.PageFinished -> stringResource(R.string.dom_page_finished)
    is DomBridgeStatus.PageFailed -> stringResource(R.string.dom_page_failed, reason)
    is DomBridgeStatus.HttpError -> stringResource(R.string.dom_http_error, code, reason)
    DomBridgeStatus.SslFailed -> stringResource(R.string.dom_ssl_failed)
    is DomBridgeStatus.Loading -> stringResource(R.string.dom_loading, url)
}
