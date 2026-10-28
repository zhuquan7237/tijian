package com.zhuquan.tijian.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
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
 * - 上下滚动、长公式左右滚动都走 WebView 原生滚动；
 * - 文字选取走**系统原生选区**（手柄、放大镜由系统提供），长按弹出的系统菜单
 *   在原生层替换为应用自己的「复制 / 追问」，与题目详情页的选字体验一致。
 */
private class ChatSelectionWebView(ctx: android.content.Context) : WebView(ctx) {
    var onCopyText: ((String) -> Unit)? = null
    var onAskText: ((String) -> Unit)? = null

    override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? =
        super.startActionMode(SelectionMenu(), type)

    @Suppress("OVERRIDE_DEPRECATION")
    override fun startActionMode(callback: ActionMode.Callback?): ActionMode? =
        super.startActionMode(SelectionMenu())

    private inner class SelectionMenu : ActionMode.Callback {
        private fun build(menu: Menu) {
            menu.clear()
            menu.add(Menu.NONE, MENU_COPY, 0, "复制")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            menu.add(Menu.NONE, MENU_ASK, 1, "追问")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean { build(menu); return true }
        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean { build(menu); return true }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            if (item.itemId != MENU_COPY && item.itemId != MENU_ASK) return false
            // 选区跨过公式时取原始 TeX，否则复制/引用到追问里只剩零散字符
            evaluateJavascript(
                "(function(){var s=window.getSelection();var t=(s&&s.rangeCount&&window.tjMath)?window.tjMath.selectionText(s.getRangeAt(0)):'';return t;})()"
            ) { raw ->
                val text = decodeJsString(raw)
                if (text.isNotBlank()) {
                    if (item.itemId == MENU_COPY) onCopyText?.invoke(text)
                    else onAskText?.invoke(text)
                }
                evaluateJavascript("window.getSelection().removeAllRanges()", null)
                mode.finish()
            }
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode) {}
    }

    companion object {
        const val MENU_COPY = 1
        const val MENU_ASK = 2
    }
}

private fun decodeJsString(raw: String?): String {
    if (raw.isNullOrBlank() || raw == "null") return ""
    return try {
        JSONArray("[$raw]").optString(0, "")
    } catch (e: Exception) {
        raw.trim('"')
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ChatTranscript(
    messages: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    onAsk: (String) -> Unit = {},
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
    val askCallback = rememberUpdatedState(onAsk)
    val latestJson = rememberUpdatedState(json)
    // 组合期读取主题：夜/昼切换会触发重组，进而重发 __setNight
    val nightNow = isNight

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            ChatSelectionWebView(ctx).apply {
                setBackgroundColor(Color.TRANSPARENT)
                onCopyText = { text ->
                    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("题笺", text))
                    android.widget.Toast.makeText(ctx, "已复制", android.widget.Toast.LENGTH_SHORT).show()
                }
                onAskText = { text -> askCallback.value(text) }
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.setSupportZoom(false)
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                settings.loadsImagesAutomatically = true
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                // 原生选区需要可聚焦、可长按
                isFocusable = true
                isFocusableInTouchMode = true
                isLongClickable = true
                isNestedScrollingEnabled = true
                webChromeClient = object : android.webkit.WebChromeClient() {
                    override fun onConsoleMessage(m: android.webkit.ConsoleMessage): Boolean {
                        android.util.Log.d("ChatJS", "${m.message()} @${m.lineNumber()}")
                        return true
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        view.evaluateJavascript(
                            "window.__setNight && window.__setNight(" + nightNow + ")", null
                        )
                        view.evaluateJavascript(
                            "window.__setMessages(" + JSONObject.quote(latestJson.value) + ")", null
                        )
                    }
                }
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun copy(text: String) {
                        post {
                            val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("题笺", text))
                            android.widget.Toast.makeText(ctx, "已复制", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }, "AndroidChat")
                loadUrl("file:///android_asset/richtext/chat.html")
            }
        },
        onRelease = { view -> view.removeJavascriptInterface("AndroidChat"); view.destroy() },
        update = { view ->
            view.evaluateJavascript("window.__setNight && window.__setNight(" + nightNow + ")", null)
            view.evaluateJavascript("window.__setMessages(" + JSONObject.quote(json) + ")", null)
        }
    )
}
