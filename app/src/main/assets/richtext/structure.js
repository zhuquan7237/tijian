/* 解析结构化排版：把「思路 / 1. 2. 3. / 易错 / 结论」识别成固定版式。
 * renderer.html（详情页解析）与 chat.html（追问回答）共用。
 * 识别不到结构（< 2 步且没有标签）时返回 null，调用方回退到普通 Markdown。 */
(function () {
  var LABEL = /^\s*(?:\*\*)?\s*(思路|分析|解题思路|易错点?|注意|结论|综上|最终答案)\s*(?:\*\*)?\s*[：:]\s*(?:\*\*)?\s*/;
  var STEP = /^\s*(?:\*\*)?\s*(?:(\d{1,2})\s*[.、．)）](?!\d)|[（(](\d{1,2})[)）]|([①②③④⑤⑥⑦⑧⑨⑩])|第([一二三四五六七八九十]{1,3})步\s*[：:，,]?|步骤\s*(\d{1,2})\s*[：:.、]?)\s*(?:\*\*)?\s*/;
  var THEREFORE = /^\s*(所以|故|因此|综上所述|综上)[，,：:\s]/;
  var TAG = { think: '思路', pit: '易错', concl: '结论' };

  function kindOf(label) {
    if (/思路|分析/.test(label)) return 'think';
    if (/易错|注意/.test(label)) return 'pit';
    return 'concl';
  }
  function esc(s) { return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;'); }

  window.tjStructure = function (md, render) {
    if (!md || !md.trim()) return null;
    var lines = md.replace(/\r/g, '').split('\n');
    var parts = [], cur = null, inMath = false, prevBlank = false;
    function push(kind, text) { cur = { kind: kind, lines: [text] }; parts.push(cur); }
    for (var i = 0; i < lines.length; i++) {
      var line = lines[i];
      var odd = ((line.match(/\$\$/g) || []).length % 2) === 1;
      if (!inMath) {
        var m = line.match(LABEL);
        if (m) { push(kindOf(m[1]), line.slice(m[0].length)); if (odd) inMath = true; prevBlank = false; continue; }
        var s = line.match(STEP);
        if (s && line.slice(s[0].length).trim()) { push('step', line.slice(s[0].length)); if (odd) inMath = true; prevBlank = false; continue; }
        if (cur && cur.kind === 'step' && prevBlank && THEREFORE.test(line)) { push('concl', line); prevBlank = false; continue; }
      }
      if (odd) inMath = !inMath;
      if (!cur) push('lead', line); else cur.lines.push(line);
      prevBlank = !line.trim();
    }
    var steps = 0, labeled = 0;
    parts.forEach(function (p) { if (p.kind === 'step') steps++; else if (p.kind !== 'lead') labeled++; });
    if (steps < 2 && labeled === 0) return null;

    var html = [], items = [], k = 0;
    function flush() { if (items.length) { html.push('<ol class="an-steps">' + items.join('') + '</ol>'); items = []; } }
    parts.forEach(function (p) {
      var text = p.lines.join('\n').trim();
      if (!text) return;
      if (p.kind === 'step') {
        k++;
        var lead = '', body = text;
        var t = text.match(/^([^$：:\n]{1,18})[：:]\s*/);
        if (t) { lead = t[1].replace(/\*\*/g, '').trim(); body = text.slice(t[0].length); }
        items.push('<li class="an-step"><span class="an-n">' + k + '</span><div class="an-body">' +
          (lead ? '<div class="an-lead">' + esc(lead) + '</div>' : '') + (body.trim() ? render(body) : '') + '</div></li>');
        return;
      }
      flush();
      if (p.kind === 'lead') { html.push('<div class="an-prose">' + render(text) + '</div>'); return; }
      html.push('<div class="an-box an-' + p.kind + '"><span class="an-tag">' + TAG[p.kind] + '</span><div class="an-body">' + render(text) + '</div></div>');
    });
    flush();
    return html.join('');
  };
})();
