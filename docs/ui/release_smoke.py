"""Install the signed release APK, smoke-test the main flows, capture screenshots."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ADB = r"C:/Users/zhuquan/AppData/Local/Android/Sdk/platform-tools/adb.exe"
OUT = Path("docs/validation/0.5.0/release")
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


def find(label, min_y=-1):
    ns = nodes()
    hits = [n for n in ns if n["text"] == label or n["desc"] == label]
    if min_y >= 0:
        hits = [n for n in hits if n["b"][1] >= min_y] or hits
    if not hits:
        hits = [n for n in ns if label in n["text"] or label in n["desc"]]
    return hits[0] if hits else None


def tap(label, wait=1.6, min_y=-1, required=True):
    n = find(label, min_y)
    if not n:
        print("!! not found:", label)
        if required:
            raise SystemExit(1)
        return None
    x = (n["b"][0] + n["b"][2]) // 2
    y = (n["b"][1] + n["b"][3]) // 2
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(wait)
    print("tap", label, n["b"])
    return n


def shot(name):
    Path(OUT / f"{name}.png").write_bytes(adb("exec-out", "screencap", "-p").stdout)
    print("shot", name)


def back(wait=1.2):
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(wait)


if __name__ == "__main__":
    print(adb("install", "-r", "build/tijian-0.5.0.apk").stdout.decode("utf-8", "replace").strip())
    ver = adb("shell", "dumpsys", "package", "com.zhuquan.tijian").stdout.decode("utf-8", "replace")
    m = re.search(r"versionName=([\d.]+)", ver)
    print("installed version:", m.group(1) if m else "?")

    adb("shell", "am", "force-stop", "com.zhuquan.tijian")
    adb("shell", "am", "start", "-n", "com.zhuquan.tijian/.MainActivity")
    time.sleep(4)
    shot("01-home")

    tap("数学")
    tap("函数与导数")
    ns = nodes()
    rows = [n for n in ns if n["clickable"] and n["b"][1] > 620 and n["b"][3] - n["b"][1] < 320]
    rows.sort(key=lambda n: n["b"][1])
    if rows:
        x = (rows[0]["b"][0] + rows[0]["b"][2]) // 2
        y = (rows[0]["b"][1] + rows[0]["b"][3]) // 2
        adb("shell", "input", "tap", str(x), str(y))
        time.sleep(2.0)
        print("tap question row")
    shot("02-detail")

    # scroll to mastery card and set level 3
    adb("shell", "input", "swipe", "540", "1900", "540", "900", "320")
    time.sleep(1.0)
    tap("掌握度 3", required=False)
    shot("03-detail-mastery")

    back()
    shot("04-category")
    back()
    back()

    tap("我的", min_y=2100)
    shot("05-settings")
    tap("AI 接入")
    shot("06-provider")
    back()
    tap("知识库", min_y=2100)
    shot("07-library")
