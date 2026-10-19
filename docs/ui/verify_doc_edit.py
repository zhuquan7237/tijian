"""End-to-end check for knowledge doc edit + delete on the release build."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ADB = r"C:/Users/zhuquan/AppData/Local/Android/Sdk/platform-tools/adb.exe"


def adb(*a, timeout=180):
    return subprocess.run([ADB, *a], capture_output=True, timeout=timeout)


def nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    raw = adb("exec-out", "cat", "/sdcard/ui.xml").stdout.decode("utf-8", "replace")
    raw = re.sub(r'standalone="yes" \?', 'standalone="yes"?', raw)
    try:
        root = ET.fromstring(raw)
    except ET.ParseError:
        return []
    out = []
    for n in root.iter("node"):
        b = re.findall(r"-?\d+", n.get("bounds", ""))
        if len(b) == 4:
            out.append(dict(text=n.get("text") or "", desc=n.get("content-desc") or "",
                            cls=n.get("class") or "", clickable=n.get("clickable") == "true",
                            b=tuple(int(x) for x in b)))
    return out


def tap(label, wait=1.6, contains=False, cls=None):
    for n in nodes():
        ok = (n["text"] == label) or (n["desc"] == label)
        if contains:
            ok = label in n["text"] or label in n["desc"]
        if cls:
            ok = ok and cls in n["cls"]
        if ok:
            x = (n["b"][0] + n["b"][2]) // 2
            y = (n["b"][1] + n["b"][3]) // 2
            adb("shell", "input", "tap", str(x), str(y))
            time.sleep(wait)
            print("tap", label, n["b"])
            return n
    print("!! miss", label)
    return None


def shot(name):
    open(f"docs/ui/v060/{name}.png", "wb").write(adb("exec-out", "screencap", "-p").stdout)
    print("shot", name)


if __name__ == "__main__":
    tap("知识库")
    tap("选择分类")
    tap("数学/函数与导数")
    tap("生成知识文档", 6.0, contains=True)
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(1.5)
    ns = nodes()
    titles = [n["text"] for n in ns if n["text"] and len(n["text"]) > 3 and n["clickable"]]
    print("doc rows:", titles[:6])

    tap("更多操作")
    shot("50-release-menu")
    tap("编辑内容", 2.0)
    # append to the title
    edits = [n for n in nodes() if "EditText" in n["cls"]]
    print("edits", [(e["b"], e["text"][:20]) for e in edits])
    if edits:
        e = edits[0]
        adb("shell", "input", "tap", str((e["b"][0] + e["b"][2]) // 2), str((e["b"][1] + e["b"][3]) // 2))
        time.sleep(0.8)
        adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
        adb("shell", "input", "text", "%20v2")
        time.sleep(0.8)
    shot("51-release-edit")
    tap("保存", 3.0)
    ns = nodes()
    has_v2 = any("v2" in n["text"] for n in ns)
    print("saved title contains v2:", has_v2)
    shot("52-release-edited")

    tap("更多操作")
    tap("删除", 1.5)
    shot("53-release-delete-confirm")
    # confirm dialog button
    for n in nodes():
        if n["text"] in ("删除", "确认删除") and n["clickable"]:
            adb("shell", "input", "tap", str((n["b"][0] + n["b"][2]) // 2), str((n["b"][1] + n["b"][3]) // 2))
            time.sleep(2.5)
            break
    ns = nodes()
    print("empty state:", [n["text"] for n in ns if "篇" in n["text"] or "生成" in n["text"]][:6])
    shot("54-release-deleted")
