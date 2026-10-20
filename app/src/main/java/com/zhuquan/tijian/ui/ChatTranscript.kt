package com.zhuquan.tijian.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONArray
import org.json.JSONObject

/**
 * 整段对话渲染在同一个 WebView 里：
 * - 上下滚动、长公式左右滚动都走 WebView 原生滚动，不和 Compose 抢手势；
 * - 长按选词由 chat.html 里的 JS 处理，只把选中的文字回传给原生，不弹系统菜单。
 */
@Composable
fun ChatTranscript(
    messages: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
) {
    val json = remember(messages) {
        val arr = JSONArray()
        messages.forEach { (role, text) ->
            arr.put(JSONObject().apply {
                put("role", role)
                put("text", text)
            })
        }
        arr.toString()
    }
    val selectCallback = rememberUpdatedState(onSelect)
    val latestJson = rememberUpdatedState(json)

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            @SuppressLint("SetJavaScriptEnabled")
            WebView(ctx).apply {
                setBackgroundColor(Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.allowFileAccess = true
                settings.allowContentAccess = false
                settings.setSupportZoom(false)
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                settings.loadsImagesAutomatically = true
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                isFocusable = false
                isFocusableInTouchMode = false
                isNestedScrollingEnabled = true
                webChromeClient = object : android.webkit.WebChromeClient() {
                    override fun onConsoleMessage(m: android.webkit.ConsoleMessage): Boolean {
                        android.util.Log.d("ChatJS", "${m.message()} @${m.lineNumber()}")
                        return true
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        android.util.Log.d("ChatJS", "page finished: $url")
                        view.evaluateJavascript(
                            "window.__setMessages(" + JSONObject.quote(latestJson.value) + ")", null
                        )
                    }
                }
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun select(text: String) {
                        post { selectCallback.value(text) }
                    }

                    @JavascriptInterface
                    fun clearSelection() {
                        post { selectCallback.value("") }
                    }
                }, "AndroidChat")
                loadUrl("file:///android_asset/richtext/chat.html")
            }
        },
        onRelease = { view -> view.removeJavascriptInterface("AndroidChat"); view.destroy() },
        update = { view ->
            view.evaluateJavascript("window.__setMessages(" + JSONObject.quote(json) + ")", null)
        }
    )
}
