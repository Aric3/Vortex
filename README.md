# 🌀 Vortex - 高性能交易风控撮合系统
Kimiha (基米哈) 小组作品

## 环境依赖 (Environment)

### 后端 (vortex-core)
JDK: 21 (必须，开启虚拟线程支持)

Maven: 3.9.9

Database: SQLite 3


### 前端 (vortex-ui)
Node.js: 22.15.0 (LTS)

Package Manager: npm 10.9.2

Framework: Vue 3.5 + Vite 5

### 模拟器 (vortex-simulators)

Python: 3.12

Locust: 用于压力测试

## 项目结构
vortex-core/: Java 后端核心逻辑

vortex-ui/: Vue 3 前端管理后台

vortex-simulators/: Python 下单模拟与行情脚本

docs/: API 接口文档与设计方案

### 开发协作规范 (Workflow)
见飞书知识库规范文档。

### 快速启动
克隆项目：git clone git@github.com:Aric3/Vortex.git

启动后端：进入 vortex-core 执行 mvn spring-boot:run

启动前端：进入 vortex-ui 执行 npm install && npm run dev

## 前端使用指南（vortex-ui）

### 1. 技术栈

- **框架**：Vue 3 + Vite 7
- **UI 组件库**：Element Plus
- **图表**：Apache ECharts
- **通信**：
  - REST：`axios` 访问 `/api/v1/vclient/*` 与 `/api/v1/analytics/*`
  - 实时：浏览器原生 SSE（`EventSource`）订阅订单簿与订单回报流

开发环境下通过 `vite.config.ts` 代理 `/api` 到 `http://localhost:8081`，因此前端统一访问 `/api/...`，后端默认监听在 8081 端口（可在 `vortex-core` 的 `application.yml` 中修改 `server.port`）。

### 2. 启动顺序

1. **启动后端**
   - 在 `vortex-core` 目录执行：
     - `mvn spring-boot:run`
   - 确认后端启动在 `http://localhost:8081`（或你配置的端口），并暴露以下接口（详见 `docs/api_spec.md`）：
     - `/api/v1/vclient/orders`
     - `/api/v1/vclient/orders/cancel`
     - `/api/v1/vclient/stream/reports`
     - `/api/v1/vclient/orderbook/{securityId}/stream`
     - `/api/v1/analytics/metrics`

2. **启动前端**
   - 在 `vortex-ui` 目录执行：
     - 首次：`npm install`
     - 之后：`npm run dev`
   - 浏览器访问 Vite 提示的地址（默认为 `http://localhost:5173`），进入交易看板。

### 3. 页面布局与模块说明

访问前端后，会进入单页面的**交易看板**，自上而下分为四块：

1. **顶部过滤面板**
   - **股东号 `shareholderId`**：用于
     - 订阅订单回报流 SSE（成交分布图 + 回报流面板）
     - 下单/撤单时自动带入股东号
   - **股票代码 `securityId`**：用于
     - 订阅订单簿 SSE（买卖盘图）
     - 过滤成交分布图与订单回报
   - **深度 `depth`**：订单簿展示的 `TopN` 档位。
   - **切换**：在两组股东号/股票代码间切换（两组均持久化到本地）。

2. **实时订单簿对比图（左上）**
   - 组件：`OrderBookCompareChart`
   - 功能：
     - 通过 SSE 订阅 `/api/v1/vclient/orderbook/{securityId}/stream?depth=...`
     - 将买一、卖一等多档数据绘制为**双向条形图**：
       - 上半部分：卖盘（`asks`）
       - 下半部分：买盘（`bids`，数量取负数左右对比）
   - 使用方式：
     - 在顶部输入股票代码与深度
     - 确认后端已启动，即可看到买卖盘随行情实时变化。

3. **当前用户成交分布图（右上）**
   - 组件：`TradeDistributionChart`
   - 功能：
     - 通过 SSE 订阅 `/api/v1/vclient/stream/reports?shareholderId=...`
     - 只处理 `ORDER_EXECUTION` 类型回报，将成交按照**价格桶**聚合：
       - 可选择分桶步长 `0.01 / 0.05 / 0.10`
       - 可设置保留的成交条数上限（例如 500 条）
   - 使用方式：
     - 在顶部输入股东号与股票代码
     - 撮合产生成交后，可看到该股东在不同价格上的成交量柱状图。

4. **下单 / 撤单控制台 + 风控指标（下方两列）**
   - **左侧：下单与撤单控制台 `TradingConsole`**
     - 支持三块功能：
       1. **下单表单**
          - 字段：
            - 市场 `market`：`XSHG` / `XSHE` / `BJSE`
            - 买卖方向 `side`：买 `B` / 卖 `S`
            - 股东号 `shareholderId`：默认从顶部面板带入，可手工修改
            - 股票代码 `securityId`
            - 数量 `qty`
            - 价格 `price`
          - 行为：
            - 点击“提交订单”后，通过 POST `/api/v1/vclient/orders` 下单
            - `clOrderId` 由前端按时间戳+随机数自动生成 16 位字符串
            - 基础参数校验不通过时，会直接在前端弹出错误提示
            - 后端接收成功后，通过 Element Plus `Message` 提示“下单已提交”
       2. **撤单表单**
          - 字段：
            - 市场 `market`
            - 股东号 `shareholderId`
            - 股票代码 `securityId`
            - 买卖方向 `side`
            - **原订单号 `origClOrderId`**：待撤订单对应的 `clOrderId`
          - 行为：
            - 点击“提交撤单”后，通过 POST `/api/v1/vclient/orders/cancel` 发送撤单请求
            - 撤单请求本身是否受理，会以标准 `Result` 返回；最终是否撤单成功，通过 SSE 回报流体现。
       3. **订单回报流面板**
          - 内部使用 SSE 连接 `/api/v1/vclient/stream/reports?shareholderId=...`
          - 将以下类型的回报以时间轴形式滚动展示：
            - 订单确认 `ORDER_CONFIRM`
            - 订单拒绝 `ORDER_REJECT`
            - 订单成交 `ORDER_EXECUTION`
            - 撤单确认 `CANCEL_CONFIRM`
            - 撤单拒绝 `CANCEL_REJECT`
          - 提供“清空”按钮快速清理历史回报，仅影响前端展示，不影响后端状态。
   - **右侧：风控与性能指标面板 `AnalyticsPanel`**
     - 周期性（默认每 2 秒）通过 GET `/api/v1/analytics/metrics` 拉取最新指标：
       - `washRejects`：对敲拒绝订单数
       - `totalOrders`：订单总量（含成功、拒绝）
       - `washRatio`：对敲占比
       - `latencyBuckets`：成交延时分桶统计
     - 页面上方以卡片形式展示：
       - 总订单数
       - 对敲拒绝数
       - 对敲占比（百分比格式）
     - 下方通过 ECharts 绘制延时分布柱状图，横轴为延时区间，纵轴为订单数量。

### 4. 推荐操作流程（人工联调）

1. 启动后端 `vortex-core`。
2. 启动前端 `vortex-ui`。
3. 在看板顶部设置：
   - 股东号：如 `A001`
   - 股票代码：如 `600030`
   - 深度：如 `10`
4. 在“下单与撤单控制台”中：
   - 选择合适的市场、买卖方向、数量、价格
   - 提交订单
5. 观察：
   - 订单簿对比图是否根据新订单变化（如买一/卖一档数量变化）
   - 成交产生后，右上角“成交分布图”是否更新柱状图
   - 下方订单回报流时间轴是否出现：
     - 订单确认 / 拒绝
     - 成交回报
     - 撤单确认 / 拒绝
   - 右下角“风控与性能指标”中：
     - 总订单数是否增加
     - 对敲拒绝数与占比是否按预期变化
     - 成交延时分布是否更新对应桶的数值

### 5. 新增 / 修改内容一览（本次前端补全）

> 方便组内成员代码评审，以下为本次自动生成或改动的前端文件：

- **新建文件**
  - `vortex-ui/src/services/http.ts`：统一封装 `axios` 实例与通用 `ApiResult` 类型。
  - `vortex-ui/src/components/TradingConsole.vue`：下单 / 撤单控制台 + 订单回报流时间轴。
  - `vortex-ui/src/components/AnalyticsPanel.vue`：拉取并展示 `/api/v1/analytics/metrics` 的风控与性能指标。
- **修改文件**
  - `vortex-ui/src/main.ts`：挂载 Element Plus，全局引入其样式。
  - `vortex-ui/src/style.css`：重置全局布局为浅色后台风格，去除默认居中卡片样式。
  - `vortex-ui/src/types/vortex.ts`：补充订单请求、撤单请求、订单回报类型与分析指标类型定义。
  - `vortex-ui/src/views/Dashboard.vue`：在原有订单簿和成交分布图基础上，集成 `TradingConsole` 与 `AnalyticsPanel` 两个新模块。

### 6. 模块分页与左侧导航（近期修改）

四个功能模块已拆分为独立页面，左侧为固定导航栏，可点击切换模块。**修改位置与说明见：`docs/FRONTEND_LAYOUT_CHANGES.md`**。该文档中列出了本次布局相关的新增/修改文件及路由与菜单对应关系，便于按文件查看改动。