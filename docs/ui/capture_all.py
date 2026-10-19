"""Capture every primary screen of the 0.5.0 build for visual QA."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ADB = r"C:/Users/zhuquan/AppData/Local/Android/Sdk/platform-tools/adb.exe"
OUT = Path("docs/ui/v050")
OUT.mkdir(parents=True, exist_ok=True)


def adb(*args, timeout=90):
    return subprocess.run([ADB, *args], capture_output=True, timeout=timeout)


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
        if len(b) != 4:
            continue
        out.append(dict(text=n.get("text") or "", desc=n.get("content-desc") or "",
                        clickable=n.get("clickable") == "true",
                        b=tuple(int(x) for x in b)))
    return out


def tap_node(n, wait=1.5, label=""):
    x = (n["b"][0] + n["b"][2]) // 2
    y = (n["b"][1] + n["b"][3]) // 2
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(wait)
    print("  tap", label or n["text"] or n["desc"], n["b"])


def tap(label, wait=1.5, min_y=-1):
    ns = nodes()
    hits = [n for n in ns if n["text"] == label or n["desc"] == label]
    if min_y >= 0:
        hits = [n for n in hits if n["b"][1] >= min_y] or hits
    if not hits:
        hits = [n for n in ns if label in n["text"] or label in n["desc"]]
    if not hits:
        print("  !! not found:", label)
        return False
    tap_node(hits[0], wait, label)
    return True


def shot(name):
    Path(OUT / f"{name}.png").write_bytes(adb("exec-out", "screencap", "-p").stdout)
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    (OUT / f"{name}.xml").write_text(
        adb("exec-out", "cat", "/sdcard/ui.xml").stdout.decode("utf-8", "replace"), encoding="utf-8")
    print("shot", name)


def back(wait=1.2):
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(wait)


def home():
    for _ in range(6):
        ns = nodes()
        if any(n["text"] == "学科" for n in ns):
            return
        tabs = [n for n in ns if n["text"] == "错题本" and n["b"][1] > 2100]
        if tabs:
            tap_node(tabs[0], 1.2, "tab")
        else:
            back(1.0)
    return


if __name__ == "__main__":
    adb("shell", "am", "force-stop", "com.zhuquan.tijian")
    adb("shell", "am", "start", "-n", "com.zhuquan.tijian/.MainActivity")
    time.sleep(4)
    shot("01-home")

    tap("数学")
    shot("02-subject")
    tap("函数与导数")
    shot("03-category")
    ns = nodes()
    rows = [n for n in ns if n["clickable"] and n["b"][1] > 620 and n["b"][3] - n["b"][1] < 320]
    rows.sort(key=lambda n: n["b"][1])
    if rows:
        tap_node(rows[0], 2.0, "question row")
    shot("04-detail")
    back()
    shot("04b-category-return")

    home()
    # 复习
    if tap("开始复习", 2.0):
        shot("08-review")
        back()
    home()
    tap("知识库", 1.6, min_y=2100)
    shot("07-library")
    home()
    if tap("导入错题", 1.5):
        shot("09-import-sheet")
        back()
    home()
    tap("我的", 1.5, min_y=2100)
    shot("05-settings")
    tap("AI 接入", 1.5)
    shot("06-provider")
