import pathlib

chat_old = '''      var r = caretAt(startX, startY);
      console.log('LP:' + (r ? (r.startContainer ? r.startContainer.nodeType : 'noNode') : 'null'));
      if (!r || !r.startContainer || r.startContainer.nodeType !== 3) return;
      anchorNode = r.startContainer;
      anchorOffset = r.startOffset;'''
chat_new = '''      var r = caretAt(startX, startY);
      var hit = resolveText(r);
      if (!hit) return;
      anchorNode = hit.node;
      anchorOffset = hit.offset;'''

helper = '''
  function resolveText(range) {
    if (!range || !range.startContainer) return null;
    var n = range.startContainer;
    if (n.nodeType === 3) return { node: n, offset: range.startOffset };
    var el = n;
    var child = el.childNodes[Math.min(range.startOffset, el.childNodes.length - 1)];
    while (child) {
      if (child.nodeType === 3) return { node: child, offset: 0 };
      if (child.firstChild) { child = child.firstChild; continue; }
      break;
    }
    var walker = document.createTreeWalker(el, NodeFilter.SHOW_TEXT);
    var t = walker.nextNode();
    return t ? { node: t, offset: 0 } : null;
  }
'''

for name in ['app/src/main/assets/richtext/chat.html', 'app/src/main/assets/richtext/renderer.html']:
    p = pathlib.Path(name)
    s = p.read_text(encoding='utf-8')
    # insert helper before "function wordRange"
    anchor = '  function wordRange('
    assert anchor in s, name
    s = s.replace(anchor, helper + '\n  function wordRange(', 1)

    # normalize anchors in the long-press handler
    import re
    s = re.sub(
        r"      var r = caretAt\(startX, startY\);\n(?:      console\.log\('LP:.*?\);\n)?      if \(!r \|\| !r\.startContainer \|\| r\.startContainer\.nodeType !== 3\) return;\n      anchorNode = r\.startContainer;\n      anchorOffset = r\.startOffset;",
        "      var r = caretAt(startX, startY);\n      var hit = resolveText(r);\n      if (!hit) return;\n      anchorNode = hit.node;\n      anchorOffset = hit.offset;",
        s
    )
    # drag extend: resolve element containers too
    s = s.replace('''      var r = caretAt(e.touches[0].clientX, e.touches[0].clientY);
      if (!r || !r.startContainer || r.startContainer.nodeType !== 3) return;
      var range = document.createRange();
      try {
        range.setStart(anchorNode, anchorOffset);
        if (r.startContainer === anchorNode) range.setEnd(anchorNode, r.startOffset);
        else range.setEnd(r.startContainer, r.startOffset);
        applySelection(range);
      } catch (err) {}''','''      var r = caretAt(e.touches[0].clientX, e.touches[0].clientY);
      var hit = resolveText(r);
      if (!hit) return;
      var range = document.createRange();
      try {
        range.setStart(anchorNode, anchorOffset);
        if (hit.node === anchorNode) range.setEnd(anchorNode, hit.offset);
        else range.setEnd(hit.node, hit.offset);
        applySelection(range);
      } catch (err) {}''')
    p.write_text(s, encoding='utf-8')
    print('patched', name)
