# 题笺 · AI 错题本

拍 → 认 → 分 → 练。Android 本地优先的错题本：AI 识别题目、自动分类、画廊/表格浏览、PDF 导出、间隔复习、知识库整理。

## 功能

- **识别导入**：相册多选 / 拍照 / 其他 App 分享进来，最多一次 9 张；一张试卷自动切分成多道题
- **识别校对**：AI 结果可改题干、答案、解析、分类；低置信度高亮；识别失败保留原图可手动补录
- **自动分类**：整批题一次归并，优先复用现有分类，必要时新建（默认「待确认」，可一键确认）
- **画廊 + 表格**：分类卡片显示题数与掌握进度；分类内表格支持筛选、多选
- **PDF 导出**：A4 / 2cm 页边距；长截图按页切片；一题一页或紧凑版式；可分享或保存
- **复习闭环**：掌握度 0~5，间隔 0/1/3/7/16/35 天；每日到期队列；三档自评
- **知识库**：从某个分类的错题里提炼知识点，生成可导出的文档
- **备份**：数据库 + 图片整包 zip，可恢复
- **自动更新**：检查 GitHub Release，应用内下载、sha256 校验、系统安装器

## AI 接入

应用内「我的 → AI 接入」填写任意 OpenAI 兼容接口：

- Base URL（一般以 `/v1` 结尾）
- API Key
- 模型名
- 协议：`chat/completions` 或 `responses`

识别需要模型支持图片输入（vision）。分类、知识点整理走同一个模型。API Key 只保存在本机数据库。

## 构建

```bash
export JAVA_HOME="<JDK 17>"
export ANDROID_HOME="<Android SDK>"
./gradlew assembleRelease
```

产物：`app/build/outputs/apk/release/app-release.apk`

Release 使用 debug 签名，方便个人设备直接覆盖安装与自动更新。

## 发版

`scripts/release.sh <version> "<notes>"` 会构建、生成 `update.json`、创建 GitHub Release。详见脚本。

## 设计

界面草案见 `docs/mockup.html`，完整方案见 `docs/DESIGN.md`。
