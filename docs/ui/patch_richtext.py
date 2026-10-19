import pathlib

p = pathlib.Path('app/src/main/java/com/zhuquan/tijian/ui/RichText.kt')
s = p.read_text(encoding='utf-8').replace('\r\n', '\n')


def rep(old, new):
    global s
    assert old in s, "NOT FOUND: " + old[:90]
    s = s.replace(old, new, 1)


rep('''    fun html(context: Context, markdown: String, fontSize: Int, widthDp: Int = 360, renderId: Int = 0): String {
        val tpl = template ?: context.assets.open("richtext/renderer.html")
            .bufferedReader().use { it.readText() }.also { template = it }
        return tpl
            .replace("__FONTSIZE__", fontSize.toString())
            .replace("__WIDTH__", widthDp.toString())
            .replace("__RENDER_ID__", renderId.toString())
            .replace("__CONTENT__", JSONObject.quote(markdown))
    }''',
    '''    fun html(
        context: Context,
        markdown: String,
        fontSize: Int,
        widthDp: Int = 360,
        renderId: Int = 0,
        selectable: Boolean = false,
    ): String {
        val tpl = template ?: context.assets.open("richtext/renderer.html")
            .bufferedReader().use { it.readText() }.also { template = it }
        return tpl
            .replace("__FONTSIZE__", fontSize.toString())
            .replace("__WIDTH__", widthDp.toString())
            .replace("__RENDER_ID__", renderId.toString())
            .replace("__SELECTABLE__", if (selectable) "true" else "false")
            .replace("__CONTENT__", JSONObject.quote(markdown))
    }''')

rep('''fun RichText(
    markdown: String,
    modifier: Modifier = Modifier,
    fontSize: Int = 15,
    onReady: () -> Unit = {},
) {''',
    '''fun RichText(
    markdown: String,
    modifier: Modifier = Modifier,
    fontSize: Int = 15,
    selectable: Boolean = false,
    onAsk: (String) -> Unit = {},
    onReady: () -> Unit = {},
) {''')

rep('''    val renderId = remember(normalized, scaledFontSize, widthDp) { RichTextRenderer.nextGeneration() }''',
    '''    val renderId = remember(normalized, scaledFontSize, widthDp, selectable) { RichTextRenderer.nextGeneration() }''')

rep('''    val html = remember(normalized, scaledFontSize, widthDp, renderId) {
        RichTextRenderer.html(context, normalized, scaledFontSize, widthDp, renderId)
    }''',
    '''    val html = remember(normalized, scaledFontSize, widthDp, renderId) {
        RichTextRenderer.html(context, normalized, scaledFontSize, widthDp, renderId, selectable)
    }''')

rep('''    val readyCallback = rememberUpdatedState(onReady)''',
    '''    val readyCallback = rememberUpdatedState(onReady)
    val askCallback = rememberUpdatedState(onAsk)''')

rep('''                isLongClickable = false''',
    '''                isLongClickable = selectable''')

rep('''                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun height(h: Int, callbackRenderId: Int) {
                        post { receiveHeight.value(h, callbackRenderId) }
                    }
                }, "AndroidRichText")''',
    '''                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun height(h: Int, callbackRenderId: Int) {
                        post { receiveHeight.value(h, callbackRenderId) }
                    }

                    @JavascriptInterface
                    fun copy(text: String) {
                        post {
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("题笺", text))
                            android.widget.Toast.makeText(ctx, "已复制", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }

                    @JavascriptInterface
                    fun ask(text: String) {
                        post { askCallback.value(text) }
                    }
                }, "AndroidRichText")''')

# update() reload must include selectable changes: html differs so tag comparison handles it.
p.write_text(s, encoding='utf-8')
print('RichText patched')
