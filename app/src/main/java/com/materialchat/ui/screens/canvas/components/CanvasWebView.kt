package com.materialchat.ui.screens.canvas.components

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Renders HTML content in a WebView within Compose.
 *
 * Configures the WebView with:
 * - JavaScript enabled for Mermaid.js and KaTeX rendering
 * - DOM storage enabled for library requirements
 * - Background color matching the M3 surface color scheme
 * - A simple [WebViewClient] to keep navigation within the view
 *
 * @param html The full HTML document string to render
 * @param modifier Modifier for the WebView container
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CanvasWebView(
    html: String,
    modifier: Modifier = Modifier
) {
    val backgroundColor = MaterialTheme.colorScheme.surface.toArgb()

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webViewClient = WebViewClient()

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = false
                    allowContentAccess = false
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                    loadWithOverviewMode = true
                    useWideViewPort = true
                }

                setBackgroundColor(backgroundColor)
                isVerticalScrollBarEnabled = true
                isHorizontalScrollBarEnabled = true

                loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            webView.setBackgroundColor(backgroundColor)
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        },
        modifier = modifier
    )
}
