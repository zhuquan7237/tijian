"""Final release screenshots: home, detail (loaded), mastery card, settings."""
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


def tap(label, wait=1.8, min_y=-1, required=True):
    ns = nodes()
    hits = [n for n in ns if n["text"] == label or n["desc"] == label]
    if min_y >= 0:
        hits = [n for n in hits if n["b"][1] >= min_y] or hits
    if not hits:
        hits = [n for n in ns if label in n["text"] or label in n["desc"]]
    if not hits:
        print("!! not found", label)
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
        x = (rows[0]["b"][0] + rows[0]["b"][2]) // 2
        y = (rows[0]["b"][1] + rows[0]["b"][3]) // 2
        adb("shell", "input", "tap", str(x), str(y))
        time.sleep(4.5)
        print("tap row")
    shot("04-detail")

    # scroll to mastery + note
    adb("shell", "input", "swipe", "540", "2000", "540", "700", "350")
    time.sleep(1.2)
    shot("05-detail-mastery")

    # set mastery 3 then capture feedback
    tap("掌握度 3", 1.2, required=False)
    shot("06-detail-mastery-3")

    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(1.2)
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(1.2)
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(1.5)

    tap("我的", 1.6, min_y=2100)
    shot("07-settings")
    tap("AI 接入", 1.6)
    shot("08-provider")
    adb("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(1.2)
    tap("知识库", 1.6, min_y=2100)
    shot("09-library")
