# Vortex

[![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.2-6DB33F?logo=springboot)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5-4FC08D?logo=vue.js)](https://vuejs.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**高性能交易风控与撮合系统** — 订单簿撮合、对敲风控、行情接入与实时推送，配套 Web 管理看板。  
Kimiha（基米哈）小组作品。

---

## 目录

- [功能特性](#功能特性)
- [技术架构](#技术架构)
- [项目结构](#项目结构)
- [环境要求](#环境要求)
- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [API 概览](#api-概览)
- [前端使用](#前端使用)
- [文档与测试](#文档与测试)
- [许可证](#许可证)

---

## 功能特性

- **订单撮合**：按标的（securityId）维护订单簿，价格时间优先撮合，支持市价触及的限价单成交
- **对敲风控**：同一股东在买卖两侧可成交时拒绝订单（防洗钱/对敲），可配置价格偏离校验
- **行情接入**：支持 AllTick 真实行情（WebSocket + HTTP 快照）与内置模拟行情（可配置走势模式）
- **实时推送**：SSE 推送订单回报、订单簿快照、行情 tick/盘口，前端可实时展示
- **持久化**：基于 Disruptor 的异步持久化，订单/成交/撤单等事件落库（SQLite）
- **分析指标**：对敲拒绝数、订单量、成交延时分桶等，通过 REST 与前端看板展示

---

## 技术架构

| 层级 | 技术 |
|------|------|
| 后端 | Java 21、Spring Boot 4、虚拟线程、WebFlux（SSE）、Disruptor、Caffeine、JPA + SQLite |
| 前端 | Vue 3、Vite 7、TypeScript、Element Plus、ECharts、Axios、EventSource（SSE） |
| 行情 | AllTick WebSocket/HTTP、模拟引擎（flat / random_walk / mean_reversion / trend） |
| 撮合 | 按标的分片订单簿、价格时间优先、股东维度对敲检测 |

---

## 项目结构

```
Vortex/
├── vortex-core/          # 后端：撮合、风控、行情、SSE、持久化
├── vortex-ui/            # 前端：Vue 3 交易看板（订单簿、成交分布、下单/撤单、风控指标）
├── vortex-simulators/    # Python 模拟器：下单脚本、压力测试（Locust）
├── docs/                 # API 规范（api_spec.md）与设计文档
├── LICENSE
└── README.md
```

---

## 环境要求

| 组件 | 要求 |
|------|------|
| 后端 (vortex-core) | JDK **21**（需虚拟线程）、Maven 3.9+、SQLite 3 |
| 前端 (vortex-ui) | Node.js 22 LTS、npm 10+ |
| 模拟器 (vortex-simulators) | Python 3.12、Locust（压测） |

---

## 快速开始

**1. 克隆仓库**

```bash
git clone git@github.com:Aric3/Vortex.git
cd Vortex
```

**2. 启动后端**

```bash
cd vortex-core
mvn spring-boot:run
```

默认监听 `http://localhost:8080`。

**3. 启动前端**

```bash
cd vortex-ui
npm install
npm run dev
```

浏览器访问 Vite 提示的地址（默认 `http://localhost:5173`）。  
前端通过代理将 `/api` 转发到后端 8080，无需改端口。

**4. 验证**

- 打开交易看板，设置股东号（如 `A001`）、股票代码（如 `600030`）
- 若使用模拟行情（`quotation.source: simulated_only`），无需外网即可看到行情与订单簿
- 提交订单后，在回报流与订单簿图中观察确认/成交/撤单

---

## 配置说明

### 行情来源（application.yml）

- **quotation.source**  
  - `real_only`：仅 AllTick 真实行情  
  - `simulated_only`：仅模拟行情  
  - `real_then_simulated`：先真实，断线或未订阅时由模拟推进  

### 模拟行情走势（quotation.simulation）

- **trend**：`flat`（横盘）| `random_walk`（随机游走）| `mean_reversion`（均值回归）| `trend_up` | `trend_down`
- **volatility**：每步波动幅度，如 `0.01` 约 ±1%，股票常用 `0.005`～`0.02`
- **subscription-mode**：`tick`（仅最新价）| `both`（最新价 + 买卖五档）
- **initial-price**：各标的初始价；未配置的用 **default-initial-price**

详见 `vortex-core/src/main/resources/application.yml` 内注释。

### 风控与撮合（vortex.matching）

- **price-deviation-check-enabled**：是否启用价格偏离校验（相对行情参考价）
- **price-deviation-max**：最大允许偏离比例，如 `0.02` 表示 2%

---

## API 概览

基础路径：`/api/v1/vclient`（客户端）、`/api/v1/analytics`（分析）。

| 类型 | 路径 | 说明 |
|------|------|------|
| POST | `/orders` | 下单 |
| POST | `/orders/cancel` | 撤单 |
| GET | `/orders`, `/orders/{clOrderId}` | 订单查询 |
| GET | `/orderbook/{securityId}?depth=10` | 订单簿快照 |
| GET | `/orderbook/{securityId}/stream?depth=10` | 订单簿 SSE |
| GET | `/quote/tick/{securityId}` | 最新成交价 |
| GET | `/quote/handicap/{securityId}` | 买卖五档 |
| GET | `/quote/stream/tick/{securityId}` | 行情 tick SSE |
| GET | `/quote/stream/handicap/{securityId}` | 行情盘口 SSE |
| GET | `/stream/reports?shareholderId=xxx` | 订单回报 SSE |
| GET | `/api/v1/analytics/metrics` | 风控与延时指标 |

完整请求/响应格式见 [docs/api_spec.md](docs/api_spec.md)。

---

## 前端使用

### 技术栈

- **框架**：Vue 3 + Vite 7 + TypeScript  
- **UI**：Element Plus  
- **图表**：Apache ECharts  
- **通信**：REST（axios）+ SSE（EventSource）订阅订单簿、订单回报、行情

### 看板布局（自上而下）

1. **顶部过滤**：股东号、股票代码、订单簿深度 — 用于 SSE 订阅与下单默认值  
2. **订单簿对比图**：SSE 订阅 `orderbook/{securityId}/stream`，买卖盘双向条形图  
3. **成交分布图**：SSE 订阅 `stream/reports`，按价格桶聚合该股东成交  
4. **下单/撤单控制台**：提交订单、撤单，并展示该股东的回报流（确认/拒绝/成交/撤单确认）  
5. **风控与性能指标**：轮询 `analytics/metrics`，对敲拒绝数、占比、延时分桶

### 推荐联调流程

1. 启动后端 → 启动前端  
2. 设置股东号、股票代码、深度  
3. 下单/撤单，观察订单簿图、成交分布图、回报流与风控指标是否更新  

---

## 文档与测试

- **API 规范**：[docs/api_spec.md](docs/api_spec.md)  
- **开发协作**：见团队知识库（如飞书）规范  
- **模拟器与压测**：见 [vortex-simulators/README.md](vortex-simulators/README.md)

---

## 许可证

[MIT License](LICENSE) — Copyright (c) 2026 Aric Chen
