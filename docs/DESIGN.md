# 「题笺」— AI 错题本 + 知识库 App 设计方案 v2

> 目标平台：Android（小米 Turbo 3 / HyperOS，minSdk 26，targetSdk 35）
> 形态：本地优先的单机 App，预留桌面端同步
> 工作名：题笺（包名占位 `com.zhuquan.tijian`），名字随时可改

---

## 0. 一句话定位

**拍 → 认 → 分 → 练 → 听**：把"一叠错题照片"变成"可复习、可朗读、可导出的个人知识系统"。

两个板块不是两个 App，而是同一套底层数据的两种视图：
- **错题本**：以「题」为单位，管分类、复习、导出。
- **知识库**：以「知识点」为单位，管沉淀、朗读、听书。

---

## 1. 结论先行：你的方案必须补的 6 个缺口

| # | 缺口 | 为什么致命 | 补法 |
|---|------|-----------|------|
| 1 | **没有"校对关"** | 视觉模型识别手写/公式/多题一定会错（5%~20%）。错一次，用户就不再信任自动分类 | 入库前加一道「识别校对页」：原图 + 识别框 + 可改文本 + 可改分类 + 低置信度高亮 |
| 2 | **只有"图"，没有"题"** | 一张试卷照片常有 3~8 道题，整体入库就没法分类、选题、导出 | 引入 `批次 batch → 题目 question`，每题保存裁剪框；一张图多题 |
| 3 | **没有复习闭环** | 错题本的价值不在"存"，在"再见到它"。只存不复习 = 图片仓库 | 掌握度 0~5 + 间隔重复（FSRS-lite）+ 每日复习队列；悬浮助手也从队列取题 |
| 4 | **自动建类会爆炸** | 20 道题让模型自由建类，会建出 20 个近义类（"导数单调性/利用导数判断单调性/单调性讨论"） | 批量归并：整批题 + 现有分类树一次给模型；相似度合并；单题不成类；新类默认「待确认」 |
| 5 | **两个板块各做一套** | 知识库也要识别、也要分类，重复造轮子，数据还不通 | 共用 `image / question / category` 三张核心表，知识库只是"另一种产物 + 另一种视图" |
| 6 | **导出被当成"把图塞进 Word"** | Word 里长截图会跨页截断、图片比页面宽、一题被拆两页，打印出来没法用 | 写一个确定性排版引擎：A4 内容区计算 + 超长图切片 + keep-together + 页码 |

另外三个产品级建议：

- **分类只做两级**：`学科（固定） → 分类（可自动增）`。知识点不做层级，做成**标签**（一题可挂多个）。深层树一定会烂，标签不会。
- **悬浮窗不是第一优先级**：Android 悬浮窗在 HyperOS 上要过 3 道权限、还会被省电策略杀。先做「通知栏每日一题 + 桌面小组件」，悬浮窗放 P4，并且必须做降级方案。
- **朗读稿 ≠ 原文**：直接把题干丢给 TTS 会读出"如图""f'(x)"这种灾难。需要一步「口语化改写」，公式转中文读法，断句 ≤ 40 字。

---

## 2. 信息架构

```
┌─ 底部三 Tab ────────────────────────────────┐
│  错题本        知识库          我的          │
└─────────────────────────────────────────────┘

错题本
 ├─ 画廊（分类卡片：封面 / 题数 / 掌握进度）
 ├─ 分类详情（表格预览 / 卡片切换 / 筛选 / 多选）
 ├─ 题目详情（原图、题干、答案、解析、笔记、错因、复习记录）
 ├─ 识别导入（相机 / 相册多选 / 其他 App 分享进来）
 │    └─ 识别校对页 → 确认入库
 └─ 导出（多选 → Word 面板 → 生成 → 分享/保存）

知识库
 ├─ 文档列表（来源、知识点数、时长）
 ├─ 文档详情（知识点卡片、原文、例题关联）
 ├─ 听书播放器（逐句高亮、倍速、定时、后台/锁屏）
 └─ 生成：从错题批次 / 从导入的笔记图片 / 手动录入

我的
 ├─ AI 接入（多服务商、角色分配、连接测试）
 ├─ 导出记录 / 备份与恢复
 ├─ 悬浮助手开关 + 权限引导
 └─ 外观（深色、字号）、存储占用
```

全局入口：
- **FAB「＋」**：识别导入（右下，拇指可达）
- **系统分享**：相册/微信/浏览器里的图片 → 分享到「题笺」→ 直接进识别队列（摩擦最低的入口，必须有）
- **迷你播放器**：一旦开始听书，悬浮在 Tab 栏上方，全局可暂停/继续

---

## 3. 数据模型（Room / SQLite）

设计原则：**ULID 主键 + 软删除 + updated_at + device_id**，为未来同步留好接口；图片按 **sha256 内容寻址**，天然去重。

```sql
-- 分类：两级。level 1 = 学科（固定几个），level 2 = 分类（可自动增）
CREATE TABLE category (
  id            TEXT PRIMARY KEY,          -- ULID
  parent_id     TEXT,                      -- level2 指向学科
  name          TEXT NOT NULL,
  aliases       TEXT,                      -- JSON ["导数单调性","单调区间"]
  level         INTEGER NOT NULL,
  auto_created  INTEGER NOT NULL DEFAULT 0,
  status        TEXT NOT NULL DEFAULT 'confirmed',  -- confirmed | pending（AI 新建待确认）
  color         TEXT,
  sort          INTEGER NOT NULL DEFAULT 0,
  created_at    INTEGER NOT NULL,
  updated_at    INTEGER NOT NULL,
  deleted_at    INTEGER
);

-- 图片：内容寻址，一张原图只存一份
CREATE TABLE image (
  id         TEXT PRIMARY KEY,   -- sha256 前 32 位
  sha256     TEXT NOT NULL UNIQUE,
  path       TEXT NOT NULL,      -- files/images/ab/cd/<sha>.jpg
  width      INTEGER NOT NULL,
  height     INTEGER NOT NULL,
  bytes      INTEGER NOT NULL,
  created_at INTEGER NOT NULL
);

-- 批次：一次导入（一组图）
CREATE TABLE batch (
  id          TEXT PRIMARY KEY,
  source      TEXT,              -- camera | gallery | share | import
  image_count INTEGER NOT NULL,
  status      TEXT NOT NULL,     -- queued | running | review | done | failed
  model       TEXT,
  tokens_in   INTEGER DEFAULT 0,
  tokens_out  INTEGER DEFAULT 0,
  created_at  INTEGER NOT NULL
);

-- 题目：核心表
CREATE TABLE question (
  id             TEXT PRIMARY KEY,
  batch_id       TEXT,
  subject        TEXT,                    -- 数学 / 物理 / 化学 …
  category_id    TEXT,
  type           TEXT,                    -- 选择 / 填空 / 解答 / 实验 …
  stem           TEXT,                    -- 题干（LaTeX 行内）
  options        TEXT,                    -- JSON 数组
  answer         TEXT,
  analysis       TEXT,
  knowledge_tags TEXT,                    -- JSON ["导数与单调性","极值点偏移"]
  difficulty     INTEGER,                 -- 1~5
  error_reasons  TEXT,                    -- JSON ["概念不清","计算失误"]
  source         TEXT,                    -- 试卷名/书名
  captured_at    INTEGER,
  mastery        INTEGER NOT NULL DEFAULT 0,   -- 0~5
  review_count   INTEGER NOT NULL DEFAULT 0,
  last_review_at INTEGER,
  next_review_at INTEGER,
  ai_confidence  REAL,
  note           TEXT,
  content_hash   TEXT,                    -- 题干 simhash，用于重复检测
  created_at     INTEGER NOT NULL,
  updated_at     INTEGER NOT NULL,
  deleted_at     INTEGER,
  device_id      TEXT
);
CREATE INDEX idx_q_cat  ON question(category_id, deleted_at);
CREATE INDEX idx_q_due  ON question(next_review_at);

-- 一题多图（大题跨页 / 原卷 + 裁剪）
CREATE TABLE question_image (
  question_id TEXT NOT NULL,
  image_id    TEXT NOT NULL,
  role        TEXT NOT NULL,   -- crop | original | answer_sheet
  crop        TEXT,            -- JSON 归一化 [x1,y1,x2,y2]
  sort        INTEGER DEFAULT 0,
  PRIMARY KEY (question_id, image_id, role)
);

-- AI 服务商与角色绑定
CREATE TABLE provider (
  id         TEXT PRIMARY KEY,
  name       TEXT NOT NULL,
  base_url   TEXT NOT NULL,
  api_key    TEXT,             -- Keystore 加密后的密文
  model      TEXT NOT NULL,
  protocol   TEXT NOT NULL,    -- openai-chat | openai-responses
  caps       TEXT,             -- JSON {"vision":true,"json":true,"tts":false}
  created_at INTEGER NOT NULL
);

-- 知识库文档
CREATE TABLE knowledge_doc (
  id            TEXT PRIMARY KEY,
  title         TEXT NOT NULL,
  category_id   TEXT,
  source_refs   TEXT,          -- JSON 题目 id 列表
  content_md    TEXT,          -- 结构化知识点（Markdown）
  script_md     TEXT,          -- 口语化朗读稿
  audio_dir     TEXT,
  duration_ms   INTEGER,
  status        TEXT,          -- draft | ready | audio_ready
  created_at    INTEGER, updated_at INTEGER, deleted_at INTEGER
);

-- 复习日志（用于统计与调度校准）
CREATE TABLE review_log (
  id          TEXT PRIMARY KEY,
  question_id TEXT NOT NULL,
  rating      INTEGER NOT NULL,   -- 0 忘了 / 1 模糊 / 2 记得
  mode        TEXT,               -- card | assistant | listening
  duration_ms INTEGER,
  reviewed_at INTEGER NOT NULL
);
```

搜索：MVP 用 `LIKE '%词%'` + 知识点标签过滤即可（几千题规模足够）；题量过万再上 FTS4（中文需 bigram 预处理）或向量检索。

---

## 4. AI 流水线

### 4.1 四次调用，四个角色

```
图片 ──①切题+识别──▶ 题目 JSON ──②分类归并──▶ 分类结果 ──▶ 入库
                                                        └─③知识抽取─▶ 知识文档 ──④朗读稿改写─▶ TTS
```

| 角色 | 输入 | 输出 | 建议模型 |
|------|------|------|---------|
| ① 识别 | 1 张图（长边压到 1600px） | 题目数组（题干/选项/答案/解析/bbox/置信度） | 视觉强模型：Gemini 3 Pro/Flash、GPT-5.x、Qwen-VL-Max |
| ② 分类 | 整批题目 + 现有分类树 | 每题分类 + 新类提议 | 便宜快模型即可（分类是文本任务） |
| ③ 知识抽取 | 一批题目或笔记 | 知识点卡片 JSON | 中等模型 |
| ④ 朗读稿 | 知识文档 | 口语句子数组 | 中等模型（可与②同） |

> 关键：**识别和分类分开调用**。识别要求模型"忠实抄写"，分类要求模型"做判断"，混在一个 prompt 里两个都做不好；而且分类可以用便宜模型，省钱。

### 4.2 识别输出 Schema

```json
{
  "questions": [
    {
      "no": "12",
      "type": "选择题",
      "stem": "已知函数 $f(x)=x^3-3x$，则 $f(x)$ 的单调递增区间是",
      "options": ["A. $(-\\infty,-1)$", "B. $(-1,1)$", "C. $(1,+\\infty)$", "D. $(-\\infty,-1)\\cup(1,+\\infty)$"],
      "answer": "D",
      "analysis": "由 $f'(x)=3x^2-3>0$ 得 $x<-1$ 或 $x>1$。",
      "knowledge_tags": ["导数与单调性"],
      "difficulty": 3,
      "bbox": [0.06, 0.31, 0.94, 0.52],
      "confidence": 0.91
    }
  ]
}
```

**诚实说明**：VLM 给的 bbox 只是"大致范围"，不能当精确裁剪用。做法是把它当**初始框**，在校对页允许拖动四角；单题图片直接跳过切分。

### 4.3 识别 Prompt 骨架

```
你是题目数字化助手。输入是一张中文试卷/练习册照片，可能包含多道题。
任务：切分每一道独立题目，逐题输出结构化 JSON。

规则：
1. 只输出 JSON，不要解释，不要 markdown 代码块。
2. 严格按模板；无法确定的字段填 null。禁止编造答案与解析——照片里没有就不要写。
3. stem 保留原文与公式（LaTeX 用 $...$），不要把选项混进题干。
4. 手写内容无法辨认时，用 ⟨?⟩ 标记该片段，不要猜。
5. bbox 为 0~1 归一化坐标 [x1,y1,x2,y2]，粗略即可。
6. 跨页/跨栏的同一题，合并为一道，bbox 取并集。

模板：{"questions":[{...}]}
```

### 4.4 分类归并（最容易被做坏的一步）

**不要**逐题调分类接口——会建出一堆近义类。正确做法是**一批一次**：

```
你是错题归档助手。下面是 N 道题的结构化信息，以及用户现有分类树（两级）。
为每题选择最合适的分类；确实没有合适的，才提议新分类。

规则：
- 优先复用现有分类，category 必须与给定名称完全一致。
- 同一批中同类题目必须落到同一分类。
- 新分类是"可复用的主题"（如"极值点偏移"），不是具体题名。
- 单题不成类：若新分类只覆盖 1 道题，改为归入最接近的现有分类。
- confidence < 0.6 时，同时给出 fallback 分类。

输出：{"assignments":[{"id":"q3","category":"数学/函数与导数","confidence":0.88,"new_category":null}]}
```

落库前再做三道闸：
1. **名称归一化**：去标点/空格，同义词表（"导数应用"≈"导数的应用"）。
2. **相似度合并**：新类名与现有类名的字符相似度 > 0.86 → 直接并入现有类（编辑距离 + 关键词重合，不需要 embedding）。
3. **新类状态**：`status = pending`，画廊里显示「AI 新建 · 待确认」小角标；用户可在「分类整理」里一键确认/合并/改名。设置里可开「全自动建类」跳过确认。

### 4.5 稳定性工程（不做这些，上线必崩）

- **JSON 修复**：剥代码围栏 → 截取第一个平衡的 `{...}` → 去尾逗号 → 失败则让模型"只修复 JSON"重试一次。
- **超时与重试**：单请求 90s，失败退避重试 2 次；仍失败 → 该题标记 `待识别`，原图保留，进队列稍后重试。
- **并发**：批次内最多 3 路并发（避免触发限流），逐题落库，随时可中断。
- **成本记录**：每批记 tokens_in/out；设置里可填单价，显示"本批约 ¥0.02"。
- **模型降级**：识别失败两次 → 提示"换用更强模型重试"，而不是无限重试同一个。
- **隐私提示**：首次使用弹一次「图片将发送至你配置的第三方模型服务」，可关闭。

---

## 5. 分类体系与治理

- **学科固定 9 个**：语文/数学/英语/物理/化学/生物/政治/历史/地理。
- **分类可自动增**（level 2），每个学科建议 ≤ 40 个。
- **知识点是标签**，不建树。一题可挂 1~4 个，标签可跨学科复用。
- **治理工具**（必须有，否则半年后没法用）：
  - 合并两个分类（题目自动迁移，别名保留）
  - 重命名 / 改颜色
  - 删除 → 题目进「未分类」收件箱，不删题
  - 排序 / 置顶
  - 「AI 新建待确认」列表

---

## 6. 交互设计要点

### 6.1 画廊（错题本首页）

- 顶部：标题 + 搜索 + 统计条（共 128 题 · 7 类 · 待复习 12）
- 一张深色「今日复习」卡：进度条 + 「开始复习（12）」
- 分类卡 2 列：封面（首题缩略图或符号）、名称、`18 题 · 已掌握 9`、细掌握条
- 「未分类」永远排最后，带数量角标
- 空状态：一个"拍照导入第一题"的大按钮，别放插画

### 6.2 分类详情（表格预览）

- 表头：`缩略图 | 题型 | 知识点 | 掌握`
- 行高 56dp，缩略图 40×48；知识点最多 2 个，超出显示 `+1`
- 四列在 390dp 宽度内放得下；若开启「日期 / 错因」等附加列，则横向滚动，并冻结缩略图列
- 顶部筛选 chips：全部 / 未掌握 / 本周新增 / 有错因
- 右上「选择」→ 行首出现圆形勾选 → 底部操作条：`已选 3 · 导出 Word / 移动 / 删除`
- 视图切换：表格 ⇄ 卡片（记住用户选择）

### 6.3 识别校对页（决定产品口碑的一屏）

```
┌──────────────────────────────┐
│ ←  识别校对            3/8   │
├──────────────────────────────┤
│  [原图，可双指缩放]           │
│   ┌─01──┐   ┌─02──┐          │
│   └─────┘   └─────┘          │
├──────────────────────────────┤
│ 01  选择题   ●高置信          │
│ 已知函数 f(x)=x³-3x，则…      │
│ [数学/函数与导数 ▾]  [AI新建] │
│ ─────────────────────────    │
│ 02  解答题   ●低置信 ⚠        │
│ …                            │
├──────────────────────────────┤
│      [重新识别]  [确认入库 8] │
└──────────────────────────────┘
```

- 点击原图上的框 → 下方卡片高亮；点卡片 → 原图滚到对应位置
- 题干可直接编辑（识别错的字比分类错更常见）
- 分类 chip 点开底部选择器：搜索 + 最近使用 + 「新建分类」
- 低置信（<0.6）默认展开，其余折叠
- 底部主按钮永远显示题数；失败题不计入

### 6.4 导出面板

- 范围：已选 N 题 / 当前分类全部 / 当前筛选结果
- 版式：`一题一页（打印友好）` / `紧凑省纸`
- 内容：题目 ✓ / 答案 ✓ / 解析 ☐ / 知识点 ✓ / 我的笔记 ☐ / 错因 ☐
- 图片：压缩（推荐，≤1600px）/ 原图
- 预估：`约 6 页 · 4.2 MB`
- 主按钮：`导出 Word`；完成后直接调系统分享（微信/打印/保存到文件）

---

## 7. Word 导出排版引擎

### 7.1 为什么自己写

Apache POI 在 Android 上要 8MB+ 且依赖冲突；docx4j 更重。**最小 OOXML 就是一个 zip + 几个 XML**，300 行 Kotlin 搞定，可控、可测、包体几乎不增。

```
[Content_Types].xml
_rels/.rels
word/document.xml
word/_rels/document.xml.rels
word/styles.xml
word/media/image1.jpg …
```

### 7.2 页面参数（A4，2cm 页边距）

| 项 | 值 |
|---|---|
| 页面 | 11906 × 16838 twips |
| 页边距 | 1134 twips（2cm） |
| 内容区 | 9638 × 14570 twips ≈ **17.0 × 25.7 cm** |
| 图片最大宽度 | 17.0 cm = 6,120,000 EMU |
| 长图切片重叠 | 8 mm（保证不切断文字行） |
| 单片最小高度 | 3 cm（避免出现细条） |
| 图片格式 | JPEG q82，长边 ≤1600px |

### 7.3 排版算法

```kotlin
val contentW = 17.0.cm; val contentH = 25.7.cm
var y = 0.0

for (q in questions) {
    val titleH = titleHeight(q)            // 题号+题型+知识点，约 1.2cm
    val imgH   = q.image.scaledHeight(contentW)

    if (titleH + imgH <= contentH) {
        val blockH = titleH + imgH + answerHeight(q)
        if (y + blockH > contentH) { pageBreak(); y = 0.0 }   // keep-together
        placeTitle(q); placeImage(q); placeAnswer(q); y += blockH
    } else {
        // 超长图：切成 n 片，每片 ≤ contentH - titleH - 1cm
        val slices = slice(q.image, maxH = contentH - titleH - 1.cm,
                           overlap = 0.8.cm, minSlice = 3.cm)
        slices.forEachIndexed { i, s ->
            if (i > 0) pageBreak()
            if (i == 0) placeTitle(q) else placeTitle(q, suffix = "（续）")
            placeImage(s); y = 0.0
        }
    }
}
```

`slice()` 按像素行切原图（不是缩放后切），保证清晰度；每片单独 `wp:extent`，宽度仍为 17cm。

### 7.4 边界情况清单

- **超宽图**（横拍）：按宽度缩放，高度自然变小，不切。
- **无图题**（手动录入/文字导入）：正常走文字流。
- **答案在另一张图**：`question_image.role = answer_sheet`，排在题目图之后。
- **一题跨页**：`w:keepNext` + 高度预判；紧凑模式才允许拆。
- **中文字体**：显式设 `w:eastAsia="宋体"`，否则打开可能变默认字体。
- **文件上限**：>30MB 时提示降级为"压缩图片"；分批导出。
- **页码**：`<w:fldSimple w:instr=" PAGE "/>`，页脚居中。
- **封面**：分类名 / 题数 / 导出日期 / 可选目录。

---

## 8. 复习引擎（你的方案里缺的闭环）

- **掌握度 0~5**，对应复习间隔：`[0, 1, 3, 7, 16, 35]` 天。
- 复习卡片：正面题干+图 → 点「看答案」→ 背面答案/解析/知识点 → 自评 `忘了 / 模糊 / 记得`。
  - 忘了：mastery = max(0, m-1)，明天再来
  - 模糊：mastery 不变，间隔减半
  - 记得：mastery = min(5, m+1)
- **每日队列**：到期题（按逾期比例排序）+ 新题配额（默认 10）→ 首页「今日复习 12」。
- **错因标签**：粗心 / 概念不清 / 计算失误 / 方法不会 / 时间不够。统计页看分布——这比分类统计有用得多。
- 复习完给一个「本组 8 题 · 正确 6」的收尾，不做花哨动画。

> 这一步做完，App 才从"错题相册"变成"学习工具"。

---

## 9. 悬浮窗 AI 助手

### 9.1 现实约束（必须先讲清）

- 需要 `SYSTEM_ALERT_WINDOW`；HyperOS 还要额外开**后台弹出界面**、**自启动**，并把省电策略设为「无限制」，否则锁屏后被杀。
- Android 14+ 前台服务必须声明类型（用 `specialUse` 并写 subtype），否则启动崩。
- 结论：**默认关闭**，开关旁直接给「去授权」三步引导 + 「检测是否生效」按钮。

### 9.2 交互

- 44dp 悬浮球，贴边吸附，可拖动；长按收起。
- 点开 → 60% 高度面板：
  - 顶部：模型名 + 当前上下文（"正在看：函数与导数 · 第 12 题"）
  - 三个快捷 chip：`讲讲这题` / `出个类似的` / `我为什么错`
  - 输入框 + 发送
- 回答里的引用可点：「《错题本》第 12 题」→ 跳到题目详情。
- 面板里可「加入今日复习」。

### 9.3 RAG 上下文怎么拼

1. 当前题目（如有）→ `stem + answer + analysis + 标签`
2. 同分类最近 5 题（去重后）→ 只取题干 + 知识点
3. 相关知识点卡片（如有知识库文档）
4. 预算 ~6k tokens，超出按相关度截断

### 9.4 降级方案（悬浮窗不生效时）

- 通知栏常驻「今日一题」，点开进复习卡
- 桌面小组件（4×2）：今日待复习数 + 一键开始
- App 内全局「问 AI」按钮

---

## 10. 知识库板块

### 10.1 流程

```
导入（错题批次 / 笔记图片 / 文本）
   → 知识点抽取（JSON 卡片）
   → 文档（Markdown 渲染 + 卡片视图）
   → 导出 Word / Markdown
   → 生成朗读稿（口语化改写）
   → 逐句 TTS → 播放器
```

### 10.2 知识点卡片 Schema

```json
{
  "title": "导数与函数单调性",
  "definition": "在区间 I 上，若 f'(x)>0 恒成立，则 f(x) 在 I 上单调递增。",
  "formulas": ["$f'(x)>0 \\Rightarrow$ 单调递增"],
  "key_points": ["先求定义域", "驻点与不可导点都要考虑"],
  "pitfalls": ["忘记定义域", "把 f'(x)=0 直接当成极值点"],
  "examples": [{"question_id": "01H...", "note": "2026 高考模拟 12 题"}]
}
```

### 10.3 朗读稿改写规则（关键差异点）

| 原文 | 朗读稿 |
|---|---|
| `x²` | `x 的平方` |
| `√3` | `根号三` |
| `f'(x)>0` | `f 一撇 x 大于零` |
| `≤ / ≥` | `小于等于 / 大于等于` |
| `∫` `∑` `Δ` | `积分` `求和` `德尔塔` |
| `如图` | `结合图形可以看到` |
| 公式整句 | `由 f 一撇 x 等于 2x 减 3 大于零，得到 x 大于二分之三` |

- 每句 ≤ 40 字；段间加过渡语（"我们来看下一个知识点"）
- 不读页码、图号、分数横线
- 两种模式：`原文朗读`（忠实） / `讲解模式`（加过渡与复述）——默认讲解模式

### 10.4 TTS 实现

- **首选系统 TTS**（`android.speech.tts.TextToSpeech`）：免费、离线、零延迟；但小米音色一般，且中文引擎可能缺失 → 启动时检测引擎列表，无中文则提示装「讯飞/Google TTS」。
- **可选云端 TTS**：OpenAI 兼容 `/v1/audio/speech`（tts-1 / gpt-4o-mini-tts），音色好、可指定语速；需要 key，按量计费。
- **逐句合成**：`files/audio/{docId}/{index}.mp3` + `durations.json`。
  - 好处：进度高亮精确、可跳句、可断点续播、可边播边合成下一句。
  - 坏处：文件多；用 1 个 doc 一目录解决。
- **播放**：Media3 / ExoPlayer 播放列表 + `MediaSessionService` → 锁屏控制、耳机按键、通知栏。
- **后台**：前台服务（mediaPlayback 类型）+ 音频焦点处理（来电自动暂停）。
- **倍速**：0.75 / 1.0 / 1.25 / 1.5 / 2.0；**定时**：15/30/60 分钟；**续播**：记住每篇最后一句。

---

## 11. 技术选型

| 层 | 选型 | 理由 |
|---|---|---|
| UI | Kotlin + Jetpack Compose + Material 3 | 复杂列表/动画效率高；你现有工具链已跑通 |
| 存储 | Room + SQLite | 关系查询、迁移可控 |
| 异步 | Coroutines + Flow + WorkManager | 批量识别/导出/TTS 预生成都要可恢复 |
| 网络 | OkHttp + kotlinx.serialization | 轻、稳 |
| 图片 | Coil + 自写缩略图缓存 | 列表流畅 |
| 音频 | Media3 (ExoPlayer + MediaSession) | 锁屏/后台标准方案 |
| 密钥 | Android Keystore + EncryptedSharedPreferences | 不明文存 API key |
| 依赖注入 | 手写 ServiceLocator | 这个规模不需要 Hilt |
| 导出 | 自写最小 OOXML（java.util.zip） | 不引重依赖 |
| 相机 | CameraX（或直接系统相机 + 分享） | 先用系统相机，省事 |

工程结构：

```
app/src/main/java/com/zhuquan/tijian/
├─ data/        db, dao, repository, model
├─ ai/          provider, client, prompt, parser, pipeline
├─ feature/
│   ├─ home/      画廊
│   ├─ category/  表格
│   ├─ detail/    题目详情
│   ├─ capture/   导入 + 校对
│   ├─ export/    面板 + 引擎
│   ├─ review/    复习卡
│   ├─ library/   知识库 + 播放器
│   └─ settings/  AI 接入 / 备份 / 悬浮窗
├─ overlay/     悬浮球 + 面板
├─ tts/         合成队列 + 播放服务
└─ core/        util, image, json, result
```

权限清单：

```
INTERNET, ACCESS_NETWORK_STATE
POST_NOTIFICATIONS (33+)
SYSTEM_ALERT_WINDOW        （悬浮窗，可选）
FOREGROUND_SERVICE + FOREGROUND_SERVICE_MEDIA_PLAYBACK / SPECIAL_USE
```

> 图片选择用 **Photo Picker（PickVisualMedia）**，不需要读存储权限；导出走 SAF，也不需要写权限。能不申请就不申请。

包体预算：Compose 4~6MB + 其余 ≈ **9~12MB**，无端侧模型。

---

## 12. 同步与桌面端：现在就要定的 5 条

1. **ID 用 ULID**，不要自增 int（离线创建不冲突）。
2. 每行带 `updated_at`（毫秒）+ `device_id`。
3. 删除用 `deleted_at` 墓碑，不物理删（否则同步会"复活"）。
4. 图片按 sha256 内容寻址（同一张图两端只传一次）。
5. 预留 `oplog` 表记录变更；同步 = 拉取对方 `updated_at > last_sync` 的行，字段级 LWW 合并。

传输顺序：**WebDAV（零后端，最省事）→ 自建 `sync.zhuquan.xyz`（复用阿里云 + CF）**。桌面端用 Tauri/Electron，直接读同一套 schema 的导出包，复习/排版/听书在桌面更好做。

这不增加 P0 工作量，但**现在不这么设计，以后迁移会很痛**。

---

## 13. 路线图

| 阶段 | 范围 | 验收标准 |
|---|---|---|
| **P0 核心闭环** | 导入/拍照/分享入口、识别+校对、分类+自动建类、画廊、表格、多选、Word 导出 | 30 张真实错题从导入到 Word 全流程无崩溃；识别文本可改；分类可改；Word 用 WPS/Office 打开不跑版 |
| **P1 复习** | 掌握度、每日队列、复习卡、错因标签、搜索、备份/恢复 | 队列跨天正确；杀进程重开不丢；备份 zip 能还原 |
| **P2 知识库** | 知识点抽取、文档视图、导出 Word/MD | 10 篇导入 → 结构化文档 → 导出可用 |
| **P3 听书** | 朗读稿、系统/云端 TTS、后台播放、倍速/定时/续播 | 锁屏可控；切后台不断；句子高亮与音频对齐 |
| **P4 悬浮助手** | 悬浮球、RAG、快捷动作、权限引导、降级方案 | 任意界面唤起；回答能引用题库；被系统杀后有兜底 |
| **P5 同步/桌面** | WebDAV 或自建服务、桌面端只读+导出 | 双端一致；删除不复活 |

**P0 明确不做**：悬浮窗、TTS、云同步、账号体系、统计图表、PDF 导入。防止 scope 爆炸。

---

## 14. 风险清单

| 风险 | 触发条件 | 缓解 |
|---|---|---|
| 手写/公式识别率低 | 字迹潦草、拍照歪斜 | 校对页 + 原图保留 + 可换更强模型重识别 |
| 分类漂移 | 长期自动建类 | 待确认队列 + 合并工具 + 单题不成类 |
| Word 跑版 | 长截图、超大图 | 切片 + keep-together + 实机用 WPS/Office 验证 |
| 悬浮窗被 MIUI 杀 | 锁屏/省电 | 引导白名单 + 通知/小组件降级 |
| TTS 音色差/无中文引擎 | 系统引擎缺失 | 引擎检测 + 云端 TTS 兜底 |
| API 成本失控 | 大量重识别 | 图片压缩、分类用便宜模型、批次去重、成本显示 |
| 数据丢失 | 换机/清数据 | 一键备份 zip + 自动周备份到 SAF 目录 |

---

## 15. 我替你补的 12 个细节（逐条确认即可）

1. **系统分享入口**：其他 App 分享图片直接进识别队列（比"打开 App 再选图"少 3 步）。
2. **图片预处理**：自动裁边 + 透视矫正 + 压到 1600px；原图另存，校对时可看原图。
3. **失败保留**：识别失败的图不丢，进「待识别」队列，可重试/换模型。
4. **重复检测**：sha256 完全相同直接跳过；题干 simhash 相似 >0.9 提示"可能重复"。
5. **一题多图**：大题跨页、题目+答案在不同页，用 `question_image` 关联。
6. **错因标签**：粗心/概念/计算/不会/时间；统计比分类更有洞察。
7. **来源识别**：从首图顶部文字猜试卷名（"2026 届高三一模"），可改。
8. **复习遮答案**：卡片默认遮住答案与解析，点一下才显示。
9. **导出历史**：每次导出留记录，可重新下载/分享，不用重复生成。
10. **备份包**：`tijian-backup-20261017.zip`（JSON + images），可导入恢复，也是桌面端的数据源。
11. **深色模式 + 字号**：晚上刷题不刺眼；题干字号可调。
12. **成本可见**：每批显示 tokens 与估算金额；月度汇总在「我的」。

---

## 16. 需要你拍板的三件事

1. **技术栈**：Kotlin + Compose（推荐，复杂列表和后续维护都更顺）——你之前的 code-canvas 是 Java，但这个项目我建议不沿用。
2. **识别主力模型**：你现有中转里哪个能稳定吃图？（Gemini 3 Flash / GPT-5.x / Qwen-VL）我会先用它跑 10 张真实错题做识别率实测，再定 prompt 与压缩参数。
3. **P0 范围**：是否按第 13 节先做「导入 → 识别校对 → 分类 → 画廊/表格 → Word」这一条闭环，其余按 P1~P5 排。
