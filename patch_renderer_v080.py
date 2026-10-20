import pathlib

p = pathlib.Path('app/src/main/assets/richtext/renderer.html')
s = p.read_text(encoding='utf-8')

# 1) selectable CSS: no native selection / callout (we implement long-press ourselves)
s = s.replace(
    "  body.rt-selectable { -webkit-user-select: text; user-select: text; }\n  body.rt-selectable * { -webkit-user-select: text; user-select: text; }",
    "  body.rt-selectable { -webkit-user-select: none; user-select: none; -webkit-touch-callout: none; }\n  body.rt-selectable * { -webkit-user-select: none; user-select: none; -webkit-touch-callout: none; }\n  .rt-press { background: rgba(47,75,216,.10); border-radius: 4px; }"
)

# 2) math stashing: rescue unclosed $$ / \[ / $
old_stash = '''  src = src.replace(/\\$\\$([\\s\\S]+?)\\$\\$/g, function (m, p1) { return stash(p1, true); });
  src = src.replace(/\\\\\\[([\\s\\S]+?)\\\\\\]/g, function (m, p1) { return stash(p1, true); });
  src = src.replace(/\\\\\\(([\\s\\S]+?)\\\\\\)/g, function (m, p1) { return stash(p1, false); });
  src = src.replace(/(^|[^\\\\$])\\$(?!\\$)([^\\n$]+?)\\$/g, function (m, pre, p1) {
    return pre + stash(p1, false);
  });
'''
new_stash = '''  // 未闭合的 $$ / \\[ 通常出现在回答被截断时：把剩余内容整段当公式，避免原样漏出
  src = src.replace(/\\$\\$([\\s\\S]*)$/g, function (m, p1) { return stash(p1, true); });
  src = src.replace(/\\\\\\[([\\s\\S]*)$/g, function (m, p1) { return stash(p1, true); });
  src = src.replace(/\\\\\\(([\\s\\S]*)$/g, function (m, p1) { return stash(p1, false); });
  src = src.replace(/(^|[^\\\\$])\\$(?!\\$)([^\\n$]*)$/g, function (m, pre, p1) {
    return p1 && p1.trim() ? pre + stash(p1, false) : m;
  });
  // 剩下孤立的分隔符直接去掉，别让用户看到 $$ / $
  src = src.replace(/\\$\\$/g, '');
'''
assert old_stash in s, 'stash block not found'
s = s.replace(old_stash, new_stash, 1)

# 3) replace selectionchange with custom long-press block selection
start = s.index("  /* ---------- 选中文本：把渲染后的文字回传给原生层 ---------- */")
end = s.index("  document.title = 'richtext-' + RENDER_ID;")
new_block = '''  /* ---------- 长按选择：自己接管，避免系统弹出的“翻译/分享/全选”菜单 ---------- */
  if (window.__RT_SELECTABLE) {
    document.body.classList.add('rt-selectable');
    var pressTimer = null, startX = 0, startY = 0, pressedEl = null;

    function blockAt(x, y) {
      var node = null;
      if (document.caretRangeFromPoint) {
        var r = document.caretRangeFromPoint(x, y);
        node = r && r.startContainer;
      } else if (document.elementFromPoint) {
        node = document.elementFromPoint(x, y);
      }
      if (!node) return null;
      var el = node.nodeType === 1 ? node : node.parentElement;
      if (!el) return null;
      return el.closest('p,li,td,th,h1,h2,h3,h4,h5,blockquote,pre,div.math-display') || el;
    }

    function clearPress() {
      if (pressedEl) { pressedEl.classList.remove('rt-press'); pressedEl = null; }
    }

    function fire() {
      var el = blockAt(startX, startY);
      if (!el) return;
      var text = (el.innerText || el.textContent || '').replace(/\\s+/g, ' ').trim();
      if (!text) return;
      clearPress();
      pressedEl = el;
      el.classList.add('rt-press');
      if (window.AndroidRichText && window.AndroidRichText.select) {
        window.AndroidRichText.select(text);
      }
    }

    document.addEventListener('touchstart', function (e) {
      if (e.touches.length !== 1) return;
      startX = e.touches[0].clientX;
      startY = e.touches[0].clientY;
      clearTimeout(pressTimer);
      pressTimer = setTimeout(fire, 420);
    }, { passive: true });

    document.addEventListener('touchmove', function (e) {
      if (!e.touches.length) return;
      var dx = e.touches[0].clientX - startX;
      var dy = e.touches[0].clientY - startY;
      if (Math.abs(dx) > 10 || Math.abs(dy) > 10) clearTimeout(pressTimer);
    }, { passive: true });

    document.addEventListener('touchend', function () { clearTimeout(pressTimer); }, { passive: true });
    document.addEventListener('touchcancel', function () { clearTimeout(pressTimer); }, { passive: true });

    // 让 WebView 不弹系统上下文菜单（翻译 / 全选 / 网页搜索）
    document.addEventListener('contextmenu', function (e) { e.preventDefault(); return false; });
    document.addEventListener('selectstart', function (e) { e.preventDefault(); });
    window.__rtClearPress = clearPress;
  }

'''
s = s[:start] + new_block + s[end:]
p.write_text(s, encoding='utf-8')
print('renderer: custom long-press + unclosed math fallback')
