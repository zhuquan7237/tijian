"""Ask gpt-6-astra for UI concept boards for 题笺.

Usage: python docs/ui/astra_gen.py 1 "方向提示"
Writes docs/ui/astra/vN.html and prints token usage.
"""
import json
import os
import pathlib
import sys
import urllib.request

BASE = "https://www.nexavlinks.com/v1/chat/completions"
KEY = os.environ["NEXA_KEY"]
OUT = pathlib.Path("docs/ui/astra")
OUT.mkdir(parents=True, exist_ok=True)

BRIEF = """你在为一个真实的 Android 应用「题笺」做 UI 重设计。它是一款本地优先的 AI 错题本：
拍照/相册导入试卷 → 视觉模型切题识别 → 人工校对 → 自动归类 → 间隔复习 → 知识库沉淀。

应用现状（必须全部体现在界面里）：
1. 首页：大标题「错题本」；今日复习深色卡（到期题数、掌握进度、开始复习按钮）；五个学科入口（数学/专业课/英语/政治/其他，显示题数与掌握进度，无题为禁用态）；最近导入的题目缩略图横滑；底部三 tab（错题本/知识库/我的）。
2. 题目详情：顶栏（题型/分类，编辑/删除）；题目原图（可缩放）；题干；答案；解析；「要做出这道题，需要掌握」区块（公式/概念/方法三组标签）；掌握度 0–5 分段选择器。
3. 知识库：生成知识文档卡（从分类提炼）；文档列表（标题/知识点数/日期），每项有编辑与删除入口。
4. 全局追问悬浮球：可拖动，点击展开对话面板；面板顶部显示当前题目；用户可以对解析里不懂的一段内容长按选中后「追问」，选中文本会作为引用出现在输入框上方；AI 回答基于这道题和用户当前的掌握度。
5. 复习页：题目、答案折叠、三档自评（忘了/模糊/记得）。

真实数据（请用这些文案，不要 Lorem ipsum）：
- 共 5 道错题，4 道待复习，已掌握 0
- 数学 3 道（分类「函数与导数」），其他 2 道（分类「极值点偏移」）
- 题目示例（填空题）：「曲线 y = x³ − 2x 在点 (1, −1) 处的切线方程为 ______。」
- 答案：y = x − 2
- 解析：y′ = 3x² − 2，在 x = 1 处斜率为 1，故切线为 y + 1 = 1·(x − 1)，即 y = x − 2。
- 需要掌握：公式「导数的几何意义 k = f′(x₀)」；概念「切点既在曲线上又在切线上」；方法「点斜式求切线方程」
- 知识库文档：函数与导数 · 4 个知识点；极值点偏移 · 2 个知识点

设计要求：
- 移动端 390 × 844 CSS px，Android（Material 3 之上做定制，不是 iOS 仿制）。
- 专业学习工具气质：克制、清晰、耐看；允许有辨识度的排版与色彩，但不要玩具感、不要霓虹渐变堆砌。
- 正文与背景对比度 ≥ 4.5:1，次要文字也要清楚；所有可点元素视觉上明确、触控高度 ≥ 48px。
- 字号至少 5 档层级（页面标题/区块标题/卡片标题/正文/元信息）。
- 关键交互要在静态图里可读：悬浮球、对话面板、选中文本后的「复制 / 追问」菜单、知识库项的编辑/删除按钮。
- 不要用希腊字母/emoji 当装饰占位；用真实内容或语义图标（可用内联 SVG）。
- 只用 HTML + 内联 CSS，无外部字体、图片、JS 依赖。

输出：只输出一个完整 HTML 文件，不要 markdown 代码围栏。页面里并排放 4 个 390×844 的手机屏（首页 / 题目详情+选中追问菜单 / 追问对话面板打开 / 知识库含编辑删除），屏幕之间留 24px 间距，页面背景 #E9ECF2。每个手机屏上方用 13px 字标注屏幕名。
"""


def main():
    idx = sys.argv[1] if len(sys.argv) > 1 else "1"
    hint = sys.argv[2] if len(sys.argv) > 2 else "请自行提出一个鲜明的视觉方向，并给它起名。"
    prompt = BRIEF + f"\n\n本次方向：{hint}\n请在 HTML 顶部（手机屏上方）写出方案名与一句设计说明。"
    body = json.dumps({
        "model": "gpt-6-astra",
        "messages": [
            {"role": "system", "content": "你是顶级移动端产品设计师，擅长高信息密度、长时阅读舒适的学习工具。你输出的 HTML 必须可直接在浏览器渲染。"},
            {"role": "user", "content": prompt},
        ],
        "max_tokens": 12000,
        "temperature": 0.8,
        "stream": True,
    }).encode("utf-8")
    req = urllib.request.Request(BASE, data=body, headers={
        "Authorization": f"Bearer {KEY}",
        "Content-Type": "application/json",
        "User-Agent": "curl/8.5.0",
        "Accept": "text/event-stream",
    })
    chunks = []
    with urllib.request.urlopen(req, timeout=900) as r:
        for raw in r:
            line = raw.decode("utf-8", "replace").strip()
            if not line.startswith("data:"):
                continue
            payload = line[5:].strip()
            if payload == "[DONE]":
                break
            try:
                obj = json.loads(payload)
            except json.JSONDecodeError:
                continue
            for ch in obj.get("choices", []):
                piece = (ch.get("delta") or {}).get("content")
                if piece:
                    chunks.append(piece)
            if len(chunks) % 200 == 0:
                print("...", sum(len(c) for c in chunks), "chars", flush=True)
    text = "".join(chunks).strip()
    if text.startswith("```"):
        text = text.split("\n", 1)[1]
        if text.rstrip().endswith("```"):
            text = text.rstrip()[:-3]
    path = OUT / f"v{idx}.html"
    path.write_text(text, encoding="utf-8")
    print("wrote", path, len(text), "chars")


if __name__ == "__main__":
    main()
