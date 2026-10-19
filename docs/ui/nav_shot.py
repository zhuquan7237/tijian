"""Drive the debug build on the emulator and capture every screen.

Usage: python docs/ui/nav_shot.py
"""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ADB = r"C:/Users/zhuquan/AppData/Local/Android/Sdk/platform-tools/adb.exe"
OUT = Path("docs/ui/v050")
OUT.mkdir(parents=True, exist_ok=True)


def adb(*args, timeout=60):
    return subprocess.run([ADB, *args], capture_output=True, timeout=timeout)


def dump():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    raw = adb("exec-out", "cat", "/sdcard/ui.xml").stdout.decode("utf-8", "replace")
    return raw


def nodes(xml_text):
    xml_text = re.sub(r'standalone="yes" \?', 'standalone="yes"?', xml_text)
    try:
        root = ET.fromstring(xml_text)
    except ET.ParseError as e:
        print("parse error", e)
        return []
    out = []
    for n in root.iter("node"):
        b = n.get("bounds", "")
        m = re.findall(r"-?\d+", b)
        if len(m) != 4:
            continue
        out.append({
            "text": n.get("text") or "",
            "desc": n.get("content-desc") or "",
            "clickable": n.get("clickable") == "true",
            "bounds": tuple(int(x) for x in m),
        })
    return out


def find(label, clickable=None, prefer_big=False):
    ns = nodes(dump())
    hits = [n for n in ns if (n["text"] == label or n["desc"] == label)]
    if clickable is not None:
        hits = [n for n in hits if n["clickable"] == clickable] or hits
    if not hits:
        hits = [n for n in ns if label in n["text"] or label in n["desc"]]
    if not hits:
        raise SystemExit(f"NOT FOUND: {label}\n" + "\n".join(
            f'{n["bounds"]} {n["text"]!r} {n["desc"]!r} click={n["clickable"]}' for n in ns if n["text"] or n["desc"]))
    if prefer_big:
        hits.sort(key=lambda n: (n["bounds"][2] - n["bounds"][0]) * (n["bounds"][3] - n["bounds"][1]), reverse=True)
    return hits[0]


def tap(label, clickable=None, prefer_big=False, wait=1.5):
    n = find(label, clickable, prefer_big)
    x1, y1, x2, y2 = n["bounds"]
    adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
    time.sleep(wait)
    print("tapped", label, n["bounds"])


def shot(name):
    Path(OUT / f"{name}.png").write_bytes(adb("exec-out", "screencap", "-p").stdout)
    (OUT / f"{name}.xml").write_text(dump(), encoding="utf-8")
    print("shot", name)


def back(n=1, wait=1.2):
    for _ in range(n):
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(wait)


if __name__ == "__main__":
    adb("shell", "am", "force-stop", "com.zhuquan.tijian")
    adb("shell", "am", "start", "-n", "com.zhuquan.tijian/.MainActivity")
    time.sleep(4)
    shot("01-home")

    tap("数学", prefer_big=True)
    shot("02-subject")

    # first child category card
    ns = nodes(dump())
    kids = [n for n in ns if n["clickable"] and n["desc"] and n["desc"] not in
            ("返回", "新增子分类", "数学", "错题本", "知识库", "我的", "导入错题", "搜索")
            and not n["desc"].startswith("删除")]
    if kids:
        x1, y1, x2, y2 = kids[0]["bounds"]
        adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
        time.sleep(1.5)
        print("tapped child", kids[0]["desc"])
    shot("03-category")

    # first question row: tap the row around 1/3 screen height
    ns = nodes(dump())
    rows = [n for n in ns if n["clickable"] and n["bounds"][1] > 700 and n["bounds"][3] - n["bounds"][1] > 120]
    if rows:
        rows.sort(key=lambda n: n["bounds"][1])
        x1, y1, x2, y2 = rows[0]["bounds"]
        adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
        time.sleep(2)
        print("tapped row", rows[0]["bounds"])
    shot("04-detail")

    adb("shell", "am", "start", "-n", "com.zhuquan.tijian/.MainActivity")
    time.sleep(1.5)
    tap("我的")
    shot("05-settings")
    tap("AI 接入")
    shot("06-provider")
    back()
    tap("知识库")
    shot("07-library")
    back()
