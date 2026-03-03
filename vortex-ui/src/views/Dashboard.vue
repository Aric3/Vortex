<template>
  <div class="page">
    <header class="header">
      <div>
        <h1 class="h1">Vortex 交易看板（最小版）</h1>
        <p class="sub">订单簿（实时） + 当前用户成交分布（实时）。后续可直接嵌入管理后台框架。</p>
      </div>
    </header>

    <section class="panel">
      <div class="field">
        <label>股东号 shareholderId</label>
        <input v-model.trim="shareholderId" placeholder="例如 A001" />
        <div class="hint">用于订阅回报 SSE（成交分布图）。</div>
      </div>

      <div class="field">
        <label>股票代码 securityId</label>
        <input v-model.trim="securityId" placeholder="例如 600030" />
        <div class="hint">用于订阅订单簿 SSE（买卖盘图）并过滤成交统计。</div>
      </div>

      <div class="field small">
        <label>深度 depth</label>
        <input v-model.number="depth" type="number" min="1" max="50" />
        <div class="hint">订单簿显示 TopN 档。</div>
      </div>
    </section>

    <main class="grid">
      <OrderBookCompareChart :security-id="securityId" :depth="depth" />
      <TradeDistributionChart :shareholder-id="shareholderId" :security-id="securityId" />
    </main>

    <section class="grid secondary">
      <TradingConsole :shareholder-id="shareholderId" :security-id="securityId" />
      <AnalyticsPanel />
    </section>

    <footer class="footer">
      <div class="tip">
        提示：如果你看到“连接异常”，先确认后端已启动（vortex-core），并且 vite 代理已配置（/api → 后端端口）。
      </div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import OrderBookCompareChart from "../components/charts/OrderBookCompareChart.vue";
import TradeDistributionChart from "../components/charts/TradeDistributionChart.vue";
import TradingConsole from "../components/TradingConsole.vue";
import AnalyticsPanel from "../components/AnalyticsPanel.vue";

const shareholderId = ref<string>("A001");
const securityId = ref<string>("600030");
const depth = ref<number>(10);
</script>

<style scoped>
.page{
  padding: 16px;
  font-family: ui-sans-serif, system-ui, -apple-system, Segoe UI, Roboto, Helvetica, Arial, "Apple Color Emoji","Segoe UI Emoji";
  color: #111827;
  background: #f8fafc;
  min-height: 100vh;
}
.header{
  display:flex;
  justify-content:space-between;
  align-items:flex-end;
  gap: 12px;
  margin-bottom: 12px;
}
.h1{ margin: 0; font-size: 20px; }
.sub{ margin: 6px 0 0; color:#6b7280; font-size: 13px; }
.panel{
  display:flex;
  gap: 12px;
  flex-wrap: wrap;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 12px;
  margin-bottom: 12px;
}
.field{ flex: 1 1 220px; min-width: 200px; }
.field.small{ flex: 0 0 140px; }
label{ display:block; font-weight: 600; font-size: 12px; color:#374151; margin-bottom: 6px; }
input{
  width: 100%;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 8px 10px;
  font-size: 14px;
  background: #fff;
  color: #111827;          /* 避免白字白底 */
}
input::placeholder{
  color: #9ca3af;          /* 可选：placeholder 更清楚 */
}
.hint{ margin-top: 6px; color:#6b7280; font-size: 12px; }
.grid{
  display:grid;
  grid-template-columns: 1fr;
  gap: 12px;
}
.grid.secondary{
  margin-top: 12px;
}
@media (min-width: 1024px){
  .grid{ grid-template-columns: 1fr 1fr; }
}
.footer{ margin-top: 12px; color:#6b7280; font-size: 12px; }
.tip{ padding: 8px 10px; }
</style>
