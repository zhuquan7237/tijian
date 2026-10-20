import pathlib

p = pathlib.Path('app/src/main/assets/richtext/renderer.html')
s = p.read_text(encoding='utf-8')
start = s.index("  /* ---------- 长按选择：自己接管")
end = s.index("  document.title = 'richtext-' + RENDER_ID;")
new = '''  /* ---------- 长按选词 / 拖动扩选：只回传选中文字，不弹系统菜单 ---------- */
  if (window.__RT_SELECTABLE) {
    document.body.classList.add('rt-selectable');
    var pressTimer = null, startX = 0, startY = 0;
    var anchorNode = null, anchorOffset = 0, selecting = false, lastText = '';

    function caretAt(x, y) {
      if (document.caretRangeFromPoint) return document.caretRangeFromPoint(x, y);
      if (document.caretPositionFromPoint) {
        var p = document.caretPositionFromPoint(x, y);
        if (!p) return null;
        var r = document.createRange();
        r.setStart(p.offsetNode, p.offset);
        r.collapse(true);
        return r;
      }
      return null;
    }

    function isWordChar(ch) { return /[A-Za-z0-9\\u4e00-\\u9fff]/.test(ch); }

    function wordRange(node, offset) {
      var text = node.data || '';
      var s = Math.min(offset, text.length);
      var e = s;
      while (s > 0 && isWordChar(text[s - 1])) s--;
      while (e < text.length && isWordChar(text[e])) e++;
      if (s === e) {
        s = Math.max(0, Math.min(offset, text.length - 1));
        e = Math.min(text.length, s + 1);
      }
      var r = document.createRange();
      r.setStart(node, s);
      r.setEnd(node, e);
      return r;
    }

    function pushSelection(text) {
      text = (text || '').replace(/\\s+/g, ' ').trim();
      if (text === lastText) return;
      lastText = text;
      if (window.AndroidRichText) {
        if (text && window.AndroidRichText.select) window.AndroidRichText.select(text);
        else if (!text && window.AndroidRichText.clearSelection) window.AndroidRichText.clearSelection();
      }
    }

    function applySelection(range) {
      var sel = window.getSelection();
      sel.removeAllRanges();
      sel.addRange(range);
      pushSelection(range.toString());
    }

    document.addEventListener('touchstart', function (e) {
      if (e.touches.length !== 1) return;
      startX = e.touches[0].clientX;
      startY = e.touches[0].clientY;
      clearTimeout(pressTimer);
      selecting = false;
      pressTimer = setTimeout(function () {
        var r = caretAt(startX, startY);
        if (!r || !r.startContainer || r.startContainer.nodeType !== 3) return;
        anchorNode = r.startContainer;
        anchorOffset = r.startOffset;
        selecting = true;
        applySelection(wordRange(anchorNode, anchorOffset));
        if (navigator.vibrate) navigator.vibrate(8);
      }, 380);
    }, { passive: true });

    document.addEventListener('touchmove', function (e) {
      if (!e.touches.length) return;
      if (!selecting) {
        var dx = e.touches[0].clientX - startX;
        var dy = e.touches[0].clientY - startY;
        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) clearTimeout(pressTimer);
        return;
      }
      e.preventDefault();
      var r = caretAt(e.touches[0].clientX, e.touches[0].clientY);
      if (!r || !r.startContainer || r.startContainer.nodeType !== 3) return;
      var range = document.createRange();
      try {
        range.setStart(anchorNode, anchorOffset);
        if (r.startContainer === anchorNode) range.setEnd(anchorNode, r.startOffset);
        else range.setEnd(r.startContainer, r.startOffset);
        applySelection(range);
      } catch (err) {}
    }, { passive: false });

    document.addEventListener('touchend', function () {
      clearTimeout(pressTimer);
      selecting = false;
    }, { passive: true });
    document.addEventListener('touchcancel', function () {
      clearTimeout(pressTimer);
      selecting = false;
    }, { passive: true });

    document.addEventListener('contextmenu', function (e) { e.preventDefault(); return false; });
    document.addEventListener('selectstart', function (e) { e.preventDefault(); });
  }

'''
s = s[:start] + new + s[end:]
p.write_text(s, encoding='utf-8')

# JS syntax check
import re
blocks = re.findall(r'<script>(.*?)</script>', s, re.S)
pathlib.Path('build/renderer_check2.js').write_text(blocks[-1], encoding='utf-8')
print('renderer word-level selection')
