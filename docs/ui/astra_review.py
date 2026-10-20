"""Ask Astra to review the current UI source and point out design problems."""
import json
import os
import pathlib
import urllib.request

BASE = "https://www.nexavlinks.com/v1/chat/completions"
KEY = os.environ["NEXA_KEY"]

files = {
    "ChatOverlay.kt": pathlib.Path("app/src/main/java/com/zhuquan/tijian/ui/ChatOverlay.kt").read_text(encoding="utf-8"),
    "Theme.kt": pathlib.Path("app/src/main/java/com/zhuquan/tijian/ui/Theme.kt").read_text(encoding="utf-8")[:6000],
    "ImportReview.kt": pathlib.Path("app/src/main/java/com/zhuquan/tijian/ui/ImportReview.kt").read_text(encoding="utf-8")[:9000],
}

prompt = """你在审查一个真实 Android 学习应用「题笺」的 Compose 源码。它已经有一套克制的墨白风格
（背景 #F5F6F8、卡片 #FFFFFF、墨色 #202733、钴蓝 #355CCE、浅蓝 #EDF2FF、分隔线 #E0E4EA）。
现在用户反馈：
1) 追问悬浮面板"感觉很奇怪"，题目信息显示不全、不知道在问哪道题；
2) 聊天消息上下滑动有卡顿；
3) 引用解析显示不完全；
4) 输入框在真机上会被导航栏/键盘挡住（已改为 safeDrawing，但想知道还有没有隐患）；
5) 导入校对页缺少"需要掌握哪些知识点"的设置。
下面是当前源码（节选）。请只做设计审查，不要重写整个文件：
- 指出 6~10 个具体问题（布局、层级、间距、触控、滚动、状态）；
- 每个问题给一句可执行的修改建议（Compose 层面，尽量具体到数值/结构）；
- 最后给一个"面板信息架构"的推荐顺序。
用中文，条目化，不要客套话。

源码：
"""
for name, content in files.items():
    prompt += f"\n===== {name} =====\n{content}\n"

body = json.dumps({
    "model": "gpt-6-astra",
    "messages": [
        {"role": "system", "content": "你是顶级移动端产品设计师兼 Android Compose 工程师，直言不讳。"},
        {"role": "user", "content": prompt},
    ],
    "max_tokens": 6000,
    "temperature": 0.4,
}).encode()

req = urllib.request.Request(BASE, data=body, headers={
    "Content-Type": "application/json",
    "Authorization": f"Bearer {KEY}",
})
try:
    with urllib.request.urlopen(req, timeout=240) as r:
        data = json.load(r)
    text = data["choices"][0]["message"]["content"]
    pathlib.Path("docs/ui/astra/review-v080.md").write_text(text, encoding="utf-8")
    print(text[:4000])
    print("\n--- usage:", data.get("usage"))
except Exception as e:
    print("ASTRA FAILED:", e)
