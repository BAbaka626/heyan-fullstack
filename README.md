# 化工园区 AI 问数与预警 Demo

这是按你当前资料快速落地的业务闭环 Demo，重点不是自训练神经网络，而是先把本地化数据库、规则预警、知识问答、企业档案和平板端巡检打通。

## 目录结构

- `server/`：Node.js + Express + SQLite 后端
- `android-app/`：Kotlin + Compose 安卓原生平板端
- 根目录 `doc/xlsx`：你提供的需求资料和表单样例

## 当前已实现

### 后端

- SQLite 数据库初始化
- Excel/Word 文档导入
- 园区基础画像样例入库
- 一企一档核心表入库
- 知识库分块检索
- DeepSeek/OpenAI 兼容模型接入
- 重大危险源四色预警规则
- 双重预防机制评分公式接口
- 平台月度评分接口
- 周报文本生成接口
- 月报文本生成接口
- 企业列表与企业详情联查
- 知识文档列表与原文分块查看
- 巡检路线、巡检记录、问题登记、工作记录接口

### 安卓端

- 总览页：园区画像、访问量、评分
- 问答页：输入问题、查看知识文档片段
- 企业页：企业检索、一企一档详情
- 巡检页：巡检登记、问题单、工作记录
- 预警页：显示当前预警列表
- 评分页：展示月度评分、周报、月报
- 可修改后端 API 地址，方便接模拟器或平板
- 巡检/问题/工作记录草稿本地持久化

## 后端启动

```bash
cd server
npm install
npm run bootstrap
npm start
```

默认接口地址：

```text
http://127.0.0.1:3100
```

### 主要接口

- `GET /api/dashboard`
- `GET /api/enterprises`
- `GET /api/enterprises/:id`
- `GET /api/knowledge/docs`
- `GET /api/knowledge/docs/:id`
- `POST /api/qa/ask`
- `GET /api/warnings`
- `GET /api/inspection/routes`
- `GET/POST /api/inspection/records`
- `GET/POST /api/issues`
- `GET/POST /api/work-records`
- `GET /api/reports/weekly`
- `GET /api/reports/monthly`

### 模型与密钥配置

密钥配置位置：`server/.env`

先复制 `server/.env.example` 为 `server/.env`，再按需填入：

```env
DEEPSEEK_API_KEY=你的密钥
DEEPSEEK_BASE_URL=https://api.deepseek.com
DEEPSEEK_MODEL=deepseek-v4-flash
```

说明：

- 当前默认按 DeepSeek 配置启动
- 代码仍兼容旧的 `OPENAI_API_KEY / OPENAI_BASE_URL / OPENAI_MODEL`
- 如果不填密钥，系统仍可运行，但知识问答会退化为“结构化回答 + 原文片段检索”

## 安卓端说明

```bash
cd android-app
./gradlew assembleDebug
```

已在当前机器实际编译成功，APK 产物路径：

[`android-app/app/build/outputs/apk/debug/app-debug.apk`](/Users/qgnix/Downloads/heyan/android-app/app/build/outputs/apk/debug/app-debug.apk)

默认基地址为：

```text
http://10.0.2.2:3100/
```

说明：

- 安卓模拟器访问本机后端时用 `10.0.2.2`
- 实际平板部署时，把首页里的 API 地址改成你电脑或本地服务器的局域网 IP，例如 `http://192.168.1.20:3100/`
- 当前机器已验证 `./gradlew assembleDebug` 成功，说明工程可实际生成 APK

## 已接入的数据来源

- `4月16日会议记录.docx`
- `化工园区AI智能问数实施方案.docx`
- `双重预防机制数字化系统运行效果评估模型.docx`
- `重大危险风险预警模型的算法说明.doc`
- `（3月10日第2稿）化工园区平台运行工作周报.docx`
- `一企一档表结构.xlsx`
- `广东省化工园区安全风险与管理能力普查表.xlsx`
- `重点化工产业聚集区重大安全风险防控项目技.xlsx`

## 当前简化策略

- 园区统计主画像先按你截图中的南沙小虎化工区样例入库
- 平台月度评分按“上月分数 + 访问量加分 + 加分项 - 扣分项”实现
- 双重预防和重大危险源预警先按规则引擎实现，不做训练模型
- AI 问答先走“规则查询 + 检索增强 + 通用大模型”，满足快速上线
- 巡检、问题和工作记录当前先做文本化登记，未接拍照、语音 SDK、定位硬件
- 园区与企业真实实时接口还未逐个对接，当前以文档导入和样例数据为主
