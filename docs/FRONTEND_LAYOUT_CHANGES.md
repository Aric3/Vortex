# 前端布局修改说明（模块分页 + 左侧导航）

本文档说明将原先「单页四模块」改为「左侧导航 + 每模块独立页面」的修改位置，便于代码评审与查看。

---

## 一、修改与新增文件一览

### 1. 新增文件

| 文件路径 | 说明 |
|---------|------|
| `vortex-ui/src/router/index.ts` | Vue Router 配置：根路径使用 `Layout`，子路由为四个模块页面；默认重定向到 `/orderbook`。 |
| `vortex-ui/src/stores/filter.ts` | Pinia 全局筛选 store：`shareholderId`、`securityId`、`depth`，供各页面与左侧栏「全局筛选」共用。 |
| `vortex-ui/src/views/Layout.vue` | 主布局：左侧固定宽度侧栏（菜单 + 全局筛选 + 底部提示），右侧 `<router-view>` 渲染当前模块。 |
| `vortex-ui/src/views/OrderBookPage.vue` | 「订单簿」独立页：仅包含 `OrderBookCompareChart`，筛选来自 store。 |
| `vortex-ui/src/views/TradeDistributionPage.vue` | 「成交分布」独立页：仅包含 `TradeDistributionChart`，筛选来自 store。 |
| `vortex-ui/src/views/TradingConsolePage.vue` | 「下单撤单」独立页：仅包含 `TradingConsole`，筛选来自 store。 |
| `vortex-ui/src/views/AnalyticsPage.vue` | 「风控指标」独立页：仅包含 `AnalyticsPanel`。 |
| `docs/FRONTEND_LAYOUT_CHANGES.md` | 本说明文档。 |

### 2. 修改文件

| 文件路径 | 修改内容 |
|---------|----------|
| `vortex-ui/package.json` | 在 `dependencies` 中新增 `"vue-router": "^4.5.0"`。 |
| `vortex-ui/src/main.ts` | 引入 `createPinia`、`router`；依次 `app.use(createPinia())`、`app.use(router)`、`app.use(ElementPlus)` 后挂载。 |
| `vortex-ui/src/App.vue` | 根组件由 `<Dashboard />` 改为 `<router-view />`，不再直接引用 `Dashboard.vue`。 |

### 3. 未改动的相关文件（仅作参考）

- `vortex-ui/src/views/Dashboard.vue`：保留但当前未再被入口使用；若需「总览单页」可后续通过路由再次挂载。
- 各模块组件（如 `OrderBookCompareChart.vue`、`TradeDistributionChart.vue`、`TradingConsole.vue`、`AnalyticsPanel.vue`）未改，仅由新页面引用。

---

## 二、路由与菜单对应关系

| 路由路径 | 菜单项 | 对应页面组件 |
|---------|--------|--------------|
| `/` | （默认跳转） | → `/orderbook` |
| `/orderbook` | 订单簿 | `OrderBookPage.vue` |
| `/trades` | 成交分布 | `TradeDistributionPage.vue` |
| `/console` | 下单撤单 | `TradingConsolePage.vue` |
| `/analytics` | 风控指标 | `AnalyticsPage.vue` |

左侧菜单使用 Element Plus `el-menu` 的 `router` 模式，点击菜单项即跳转到对应路径。

---

## 三、全局筛选与数据流

- **左侧栏「全局筛选」**：绑定 Pinia store `useFilterStore()` 的 `shareholderId`、`securityId`、`depth`。
- **各模块页面**：通过 `useFilterStore()` 读取同一份筛选条件，传给各自子组件（如 `OrderBookCompareChart` 的 `securityId`、`depth`，`TradeDistributionChart` 的 `shareholderId`、`securityId` 等）。
- 在任意页面或侧栏修改筛选后，切换到其他模块会使用最新筛选值，无需每页单独填写。

---

## 四、如何快速定位修改

1. **看路由与入口**：`vortex-ui/src/router/index.ts`、`vortex-ui/src/main.ts`、`vortex-ui/src/App.vue`。
2. **看布局与导航**：`vortex-ui/src/views/Layout.vue`（侧栏结构、菜单项、全局筛选表单项）。
3. **看各模块页面**：`vortex-ui/src/views/OrderBookPage.vue`、`TradeDistributionPage.vue`、`TradingConsolePage.vue`、`AnalyticsPage.vue`。
4. **看全局状态**：`vortex-ui/src/stores/filter.ts`。

以上为本次「四模块分页 + 左侧选择栏目」相关修改的完整位置说明。
