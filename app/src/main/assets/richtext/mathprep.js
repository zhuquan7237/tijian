/* 公式保护：把 Markdown 里的公式统一换成占位符，避免 markdown-it 吃掉
 * 反斜杠、下划线、星号，也避免模型漏写闭合 $ 时把 LaTeX 源码漏到界面上。
 * renderer.html 与 chat.html 共用，改这里两边同时生效。 */
(function () {
  function wrapBareLine(line, stash) {
    // 列表符号 / 标题 / 引用符号必须留在公式外面，否则整行会被当成公式，
    // markdown 列表结构消失，五个公式会粘成一段。
    var m = /^(\s*(?:[-*+]\s+|\d{1,2}[.)]\s+|>\s*|#{1,6}\s+))([\s\S]*)$/.exec(line);
    var prefix = m ? m[1] : '';
    var body = m ? m[2] : line;
    if (!body || body.indexOf('\\') === -1) return line;
    if (/^\s*(```|~~~|\||<)/.test(body)) return line;

    var chunks = body.split(/(@@M\d+@@)/);
    for (var i = 0; i < chunks.length; i++) {
      var part = chunks[i];
      if (!part || part.indexOf('@@M') === 0) continue;
      if (part.indexOf('\\') === -1) continue;
      chunks[i] = part.replace(/[^\u4e00-\u9fff]+/g, function (seg) {
        if (!/\\[a-zA-Z]+/.test(seg)) return seg;
        if (/^\s*$/.test(seg)) return seg;
        return stash(seg, false);
      });
    }
    return prefix + chunks.join('');
  }

  function prepare(raw, stash) {
    var src = String(raw == null ? '' : raw).replace(/\r\n?/g, '\n');

    // 用户/模型写的 \$ 是字面美元符号，先藏起来，最后还原。
    src = src.replace(/\\\$/g, '\uE000');

    // 多层 JSON 转义：\\sin → \sin。矩阵换行 \\ 后通常跟 & 或换行，不受影响。
    src = src.replace(/\\\\(?=[A-Za-z])/g, '\\');

    // \( \) → $；\[ \] → $$
    src = src.replace(/\\\[/g, '$$$$').replace(/\\\]/g, '$$$$');
    src = src.replace(/\\\(/g, '$').replace(/\\\)/g, '$');

    // 跨行块公式
    src = src.replace(/\$\$([\s\S]+?)\$\$/g, function (m, tex) { return stash(tex, true); });
    // 模型输出被截断时，未闭合的 $$ 后面整段都按块公式处理
    src = src.replace(/\$\$([\s\S]*)$/g, function (m, tex) { return stash(tex, true); });

    // 行内公式：逐行配对。模型经常只写一个 $，这里把落单的 $ 后面
    // 剩余内容按公式处理，绝不再把 \sin、\dfrac 这类源码留在正文里。
    src = src.split('\n').map(function (line) {
      var out = '', i = 0;
      while (i < line.length) {
        var open = line.indexOf('$', i);
        if (open < 0) { out += line.slice(i); break; }
        out += line.slice(i, open);
        var close = line.indexOf('$', open + 1);
        if (close < 0) {
          var rest = line.slice(open + 1);
          if (rest && /\\[a-zA-Z]|[\^_{}=]/.test(rest)) out += stash(rest, false);
          else if (rest) out += '$' + rest;
          break;
        }
        out += stash(line.slice(open + 1, close), false);
        i = close + 1;
      }
      return out;
    }).join('\n');

    // 没有 $ 定界符的裸 LaTeX（例如 AI 直接返回 \sin x\sim x）
    src = src.split('\n').map(function (line) { return wrapBareLine(line, stash); }).join('\n');

    return src.replace(/\uE000/g, '\\$');
  }

  window.tjMath = { prepare: prepare, wrapBareLine: wrapBareLine };
})();
