import json, re
from http.server import BaseHTTPRequestHandler, HTTPServer

BS = chr(92)

BS = chr(92)
def CMD(n): return BS + n
Q2_STEM = ("求 $" + CMD("lim") + "_{x" + CMD("to") + " 1}" + CMD("dfrac") +
           "{e^{" + CMD("ln") + " x}-e^{x" + CMD("ln") + " x}}{1-x+x" + CMD("ln") + " x}$ 的值。")
BS = chr(92)
def CMD(n): return BS + n

# 解析统一用新结构：思路 / 1. 2. 3. / 易错 / 结论。用于验证 structure.js 的结构化排版。
Q1_ANALYSIS = (
    "思路：求导后解 $f'(x)>0$。\n\n"
    "1. 求导：$f'(x)=3x^2-3=3(x-1)(x+1)$\n\n"
    "2. 解不等式：$f'(x)>0$ 得 $x<-1$ 或 $x>1$\n\n"
    "易错：把 $f'(x)\\ge 0$ 的等号带上，端点会多出来。\n\n"
    "结论：单调递增区间为 $(-\\infty,-1)\\cup(1,+\\infty)$，选 D。"
)

Q2_ANALYSIS = (
    "思路：分子分母同时趋于 0，先整理再用洛必达。\n\n"
    "1. 约去 $(1-x)$，原式化为 $" + CMD("lim") + "_{x" + CMD("to") + " 1}" +
    CMD("dfrac") + "{" + CMD("ln") + " x}{1-x+x" + CMD("ln") + " x}$\n\n"
    "2. 第一次求导：$" + CMD("dfrac") + "{1/x-1-" + CMD("ln") + " x}{" + CMD("ln") + " x}$\n\n"
    "3. 第二次求导：$" + CMD("dfrac") + "{-1/x^2-1/x}{1/x}$，代入 $x=1$\n\n"
    "$$" + CMD("lim") + "_{x" + CMD("to") + " 1}" + CMD("dfrac") +
    "{-1/x^2-1/x}{1/x}" + CMD("Big") + "|_{x=1}=-2$$\n\n"
    "易错：每次用洛必达前都要确认仍是 $" + CMD("dfrac") + "{0}{0}$ 型。\n\n"
    "结论：极限值为 $-2$。"
)

Q3_ANALYSIS = (
    "思路：先求导得切线斜率，再用点斜式。\n\n"
    "1. 求导：$y'=3x^2-2$\n\n"
    "2. 代入切点横坐标：$k=y'(1)=1$\n\n"
    "$$y-(-1)=1\\cdot(x-1)$$\n\n"
    "3. 整理：$y=x-2$\n\n"
    "易错：切点纵坐标要用 $f(1)=-1$，不要代成 0。\n\n"
    "结论：切线方程为 $y=x-2$。"
)

Q4_ANALYSIS = (
    "思路：恒成立问题优先分离参数。\n\n"
    "1. 分离：$a\\le \\dfrac{e^x}{x}$ 对 $x>0$ 恒成立\n\n"
    "2. 设 $g(x)=\\dfrac{e^x}{x}$，求导得 $g'(x)=\\dfrac{e^x(x-1)}{x^2}$\n\n"
    "3. 极小值点 $x=1$，$g(1)=e$\n\n"
    "易错：只验证了充分条件，忘了 $x<0$ 时也要讨论。\n\n"
    "结论：$a\\le e$。"
)

NEEDS1 = {"need_formulas": ["导数与单调性 $f'(x)>0\\Rightarrow f$ 递增"],
          "need_concepts": ["导数为零的点不一定是极值点"],
          "need_methods": ["求导后解不等式定单调区间"]}
NEEDS2 = {"need_formulas": ["洛必达法则 $" + CMD("lim") + CMD("dfrac") + "{f}{g}=" + CMD("lim") + CMD("dfrac") + "{f'}{g'}$"],
          "need_concepts": ["$" + CMD("dfrac") + "{0}{0}$ 型与 $" + CMD("dfrac") + CMD("infty") + "{" + CMD("infty") + "}$ 型的判定"],
          "need_methods": ["等价无穷小替换（仅乘除）", "两次洛必达"]}
NEEDS3 = {"need_formulas": ["导数几何意义 $k=f'(x_0)$"],
          "need_concepts": ["切点既在曲线上又在切线上"],
          "need_methods": ["点斜式求切线方程", "代点校验"]}
NEEDS4 = {"need_formulas": ["$e^x\\ge x+1$"],
          "need_concepts": ["恒成立等价于最值不等式"],
          "need_methods": ["分离参数", "求导讨论最值"]}

QUESTIONS = [
    {"no": "1", "type": "选择题",
     "stem": "已知函数 $f(x)=x^3-3x$，则 $f(x)$ 的单调递增区间是（  ）",
     "options": ["A. $(-\\infty,-1)$", "B. $(-1,1)$", "C. $(1,+\\infty)$", "D. $(-\\infty,-1)\\cup(1,+\\infty)$"],
     "answer": "D", "analysis": Q1_ANALYSIS,
     "knowledge_tags": ["导数与单调性"], "difficulty": 3,
     "category": "函数与导数", "confidence": 0.93, "bbox": [0.05, 0.115, 0.95, 0.255], **NEEDS1},
    {"no": "2", "type": "解答题",
     "stem": Q2_STEM,
     "options": [], "answer": r"$-2$",
     "analysis": Q2_ANALYSIS,
     "knowledge_tags": ["极限", "洛必达法则"], "difficulty": 4,
     "category": "函数与导数", "confidence": 0.54, "bbox": [0.05, 0.345, 0.95, 0.505], **NEEDS2},
    {"no": "3", "type": "填空题",
     "stem": "曲线 $y=x^3-2x$ 在点 $(1,-1)$ 处的切线方程为 ______。",
     "options": [], "answer": "$y=x-2$", "analysis": Q3_ANALYSIS,
     "knowledge_tags": ["切线方程"], "difficulty": 2,
     "category": "函数与导数", "confidence": 0.89, "bbox": [0.05, 0.548, 0.95, 0.615], **NEEDS3},
    {"no": "4", "type": "解答题",
     "stem": "已知函数 $f(x)=e^x-ax$，若 $f(x)\\ge 0$ 恒成立，求 $a$ 的取值范围。",
     "options": [], "answer": "$a\\le e$", "analysis": Q4_ANALYSIS,
     "knowledge_tags": ["恒成立", "导数应用"], "difficulty": 4,
     "category": "极值点偏移", "confidence": 0.87, "bbox": [0.05, 0.63, 0.95, 0.705], **NEEDS4},
]

KNOWLEDGE = {
    "title": "导数应用 · 核心知识点",
    "points": [
        {"title": "导数与单调性", "definition": "在区间 I 上，若 f'(x)>0 恒成立，则 f(x) 在 I 上单调递增。",
         "key_points": ["先求定义域", "解 f'(x)>0 得单调增区间", "端点是否取到要看定义域"],
         "pitfalls": ["忘记定义域", "把 f'(x)=0 的点直接当成极值点"]},
        {"title": "切线方程", "definition": "曲线 y=f(x) 在点 (x0,f(x0)) 处的切线为 y-f(x0)=f'(x0)(x-x0)。",
         "key_points": ["先求导", "代入切点横坐标得斜率", "点斜式写方程"],
         "pitfalls": ["把切点横坐标代错", "在非切点处误用点斜式"]},
        {"title": "两个重要极限", "definition": r"$\lim_{x\to 0}\dfrac{\sin x}{x}=1$，$\lim_{x\to\infty}(1+1/x)^x=e$",
         "key_points": [r"等价无穷小替换只适用于乘除", r"$\dfrac{0}{0}$ 型可考虑洛必达"],
         "pitfalls": ["加减法里直接替换等价无穷小"]},
        {"title": "恒成立问题", "definition": "f(x)≥a 恒成立等价于 f(x) 的最小值 ≥ a。",
         "key_points": ["优先分离参数", "不能分离时求导讨论最值", "注意端点与极限"],
         "pitfalls": ["只验证了充分条件", "忽略定义域端点"]},
    ],
}


def extract_text(body):
    try:
        if isinstance(body.get("messages"), list):
            parts = []
            for m in body["messages"]:
                c = m.get("content")
                if isinstance(c, str):
                    parts.append(c)
                elif isinstance(c, list):
                    for x in c:
                        if isinstance(x, dict) and x.get("type") == "text":
                            parts.append(x.get("text", ""))
            return "\n".join(parts)
        if isinstance(body.get("input"), str):
            return body["input"]
        if isinstance(body.get("input"), list):
            parts = []
            for item in body["input"]:
                c = item.get("content") if isinstance(item, dict) else None
                if isinstance(c, list):
                    for x in c:
                        if isinstance(x, dict) and x.get("type") == "input_text":
                            parts.append(x.get("text", ""))
            return "\n".join(parts)
    except Exception:
        pass
    return ""


def reply_for(text):
    if "错题归档助手" in text:
        ids = re.findall(r'"id"\s*:\s*"([^"]+)"', text)
        cats = ["函数与导数", "函数与导数", "函数与导数", "极值点偏移"]
        items = [{"id": qid, "category": cats[i % len(cats)], "confidence": 0.9}
                 for i, qid in enumerate(ids)]
        return json.dumps({"assignments": items}, ensure_ascii=False)
    if "错题教练" in text:
        return json.dumps({
            "formulas": ["$y'=3x^2-2$", "切线斜率 $k=f'(x_0)$"],
            "concepts": ["导数的几何意义", "切点既在曲线上又在切线上"],
            "methods": ["先求导再代点", "点斜式写切线方程"],
        }, ensure_ascii=False)
    if "知识点整理助手" in text:
        return json.dumps(KNOWLEDGE, ensure_ascii=False)
    if "题目数字化助手" in text:
        return json.dumps({"questions": QUESTIONS}, ensure_ascii=False)
    if "错题辅导老师" in text or "题目资料" in text:
        return """### 这一步在做什么

你选的这段是**求导**：$y'=3x^2-2$。

把 $x=1$ 代进去得到斜率 $k=1$，再用点斜式 $y-y_0=k(x-x_0)$ 写出切线。

**你缺的那块**：导数的几何意义——某点的导数值就是该点切线的斜率。

| 步骤 | 式子 |
|---|---|
| 求导 | $y'=3x^2-2$ |
| 代点 | $k=1$ |"""
    return "正常"


class H(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def do_POST(self):
      try:
        self._post()
      except Exception as e:
        data = json.dumps({"error": str(e)}).encode()
        self.send_response(200); self.send_header("Content-Type","application/json")
        self.send_header("Content-Length", str(len(data))); self.end_headers()
        self.wfile.write(data)

    def _post(self):
        n = int(self.headers.get("Content-Length", 0))
        raw = self.rfile.read(n) or b"{}"
        try:
            body = json.loads(raw.decode("utf-8", errors="replace"))
        except Exception:
            body = {}
        text = extract_text(body)
        content = reply_for(text)
        if "responses" in self.path:
            payload = {"id": "resp_mock", "object": "response", "output_text": content,
                       "output": [{"type": "message", "content": [{"type": "output_text", "text": content}]}]}
        else:
            payload = {"id": "chatcmpl-mock", "debug_text": text[:60], "object": "chat.completion",
                       "choices": [{"index": 0, "message": {"role": "assistant", "content": content},
                                    "finish_reason": "stop"}],
                       "usage": {"prompt_tokens": 1800, "completion_tokens": 600}}
        data = json.dumps(payload, ensure_ascii=False).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path.rstrip('/').endswith('/models'):
            data = json.dumps({"object": "list", "data": [
                {"id": "mock-vision", "object": "model"},
                {"id": "mock-fast", "object": "model"},
                {"id": "mock-reasoning", "object": "model"},
            ]}, ensure_ascii=False).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
            return
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b'{"ok":true,"v":"needs1"}')


if __name__ == "__main__":
    HTTPServer(("0.0.0.0", 9008), H).serve_forever()
