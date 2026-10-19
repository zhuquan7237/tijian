import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ADB = r"C:/Users/zhuquan/AppData/Local/Android/Sdk/platform-tools/adb.exe"
OUT = Path("docs/ui/v050")


def adb(*args, timeout=60):
    return subprocess.run([ADB, *args], capture_output=True, timeout=timeout)


def nodes():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    raw = adb("exec-out", "cat", "/sdcard/ui.xml").stdout.decode("utf-8", "replace")
    raw = re.sub(r'standalone="yes" \?', 'standalone="yes"?', raw)
    root = ET.fromstring(raw)
    out = []
    for n in root.iter("node"):
        b = re.findall(r"-?\d+", n.get("bounds", ""))
        if len(b) != 4:
            continue
        out.append(dict(text=n.get("text") or "", desc=n.get("content-desc") or "",
                        clickable=n.get("clickable") == "true",
                        b=tuple(int(x) for x in b)))
    return out


def center(n):
    x1, y1, x2, y2 = n["b"]
    return (x1 + x2) // 2, (y1 + y2) // 2


def tap_node(n, wait=1.6, label=""):
    x, y = center(n)
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(wait)
    print("tap", label or n["text"] or n["desc"], n["b"])


def tap(label, wait=1.6):
    ns = nodes()
    hits = [n for n in ns if n["text"] == label or n["desc"] == label]
    if not hits:
        hits = [n for n in ns if label in n["text"] or label in n["desc"]]
    assert hits, f"not found {label}"
    tap_node(hits[0], wait, label)


def shot(name):
    Path(OUT / f"{name}.png").write_bytes(adb("exec-out", "screencap", "-p").stdout)
    (OUT / f"{name}.xml").write_text(
        adb("exec-out", "cat", "/sdcard/ui.xml").stdout.decode("utf-8", "replace"), encoding="utf-8")
    print("shot", name)


def go_home():
    for _ in range(8):
        ns = nodes()
        texts = {n["text"] for n in ns}
        if "学科" in texts and "错题本" in texts:
            return
        tabs = [n for n in ns if n["text"] == "错题本" and n["b"][1] > 2100]
        if tabs:
            tap_node(tabs[0], 1.4, "tab 错题本")
            continue
        back = [n for n in ns if n["desc"] == "返回"]
        if back:
            tap_node(back[0], 1.0, "返回")
        else:
            return
    return


if __name__ == "__main__":
    adb("shell", "am", "force-stop", "com.zhuquan.tijian")
    adb("shell", "am", "start", "-n", "com.zhuquan.tijian/.MainActivity")
    time.sleep(4)
    shot("01-home")

    tap("数学")
    shot("02-subject")

    ns = nodes()
    cards = [n for n in ns if n["desc"] == "函数与导数"]
    assert cards, "child category card not found"
    tap_node(cards[0], 1.8, "函数与导数")
    shot("03-category")

    ns = nodes()
    # 表格第一行：位于表头(约 y700)之下、高度 ~200px 的可点节点
    rows = [n for n in ns if n["clickable"] and n["b"][1] > 620 and n["b"][3] - n["b"][1] < 300]
    rows.sort(key=lambda n: n["b"][1])
    if rows:
        tap_node(rows[0], 2.2, "question row")

    time.sleep(1.5)
    shot("04-detail")

    go_home()
    tap("我的")
    shot("05-settings")
    tap("AI 接入")
    shot("06-provider")

    go_home()
    tap("知识库")
    shot("07-library")
