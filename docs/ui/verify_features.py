"""Verify selection -> 追问 bar, library menu, and import preview."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ADB = r"C:/Users/zhuquan/AppData/Local/Android/Sdk/platform-tools/adb.exe"
OUT = Path("docs/ui/v060")
OUT.mkdir(parents=True, exist_ok=True)


def adb(*a, timeout=120):
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
                            clickable=n.get("clickable") == "true",
                            b=tuple(int(x) for x in b)))
    return out


def tap(label, wait=1.6, min_y=-1, required=True):
    ns = nodes()
    hits = [n for n in ns if n["text"] == label or n["desc"] == label]
    if min_y >= 0:
        hits = [n for n in hits if n["b"][1] >= min_y] or hits
    if not hits:
        hits = [n for n in ns if label in n["text"] or label in n["desc"]]
    if not hits:
        print("!! miss", label)
        if required:
            raise SystemExit(1)
        return None
    x = (hits[0]["b"][0] + hits[0]["b"][2]) // 2
    y = (hits[0]["b"][1] + hits[0]["b"][3]) // 2
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(wait)
    print("tap", label)
    return hits[0]


def shot(name):
    Path(OUT / f"{name}.png").write_bytes(adb("exec-out", "screencap", "-p").stdout)
    print("shot", name)


def back(n=1):
    for _ in range(n):
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        time.sleep(1.2)


if __name__ == "__main__":
    # detail + selection
    tap("数学")
    tap("函数与导数")
    ns = nodes()
    rows = [n for n in ns if n["clickable"] and n["b"][1] > 620 and n["b"][3] - n["b"][1] < 320]
    rows.sort(key=lambda n: n["b"][1])
    if rows:
        x = (rows[0]["b"][0] + rows[0]["b"][2]) // 2
        y = (rows[0]["b"][1] + rows[0]["b"][3]) // 2
        adb("shell", "input", "tap", str(x), str(y))
        time.sleep(4.5)
    adb("shell", "input", "swipe", "540", "1900", "540", "900", "320")
    time.sleep(1)
    adb("shell", "input", "swipe", "540", "1900", "540", "700", "320")
    time.sleep(1)
    adb("shell", "input", "swipe", "540",  "525", "540", "525", "900")
    time.sleep(1.5)
    shot("13-selection-bar")

    tap("追问", 2.0, required=False)
    shot("14-chat-quoted")
    back()
    back()
    back()
    back()

    # library
    tap("知识库", 1.8, min_y=2100)
    shot("15-library")
    tap("更多操作", 1.2, required=False)
    shot("16-library-menu")
    tap("编辑内容", 1.5, required=False)
    shot("17-doc-edit")
