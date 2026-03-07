<template>
  <div class="page">
    <header class="header">
      <div>
        <h1 class="h1">Vortex 交易看板</h1>
      </div>
    </header>

    <section class="panel">
      <div class="field">
        <label>股东号 shareholderId</label>
        <input v-model.trim="shareholderId" placeholder="例如 A000000001（10位）" />
      </div>

      <div class="field">
        <label>股票代码 securityId</label>
        <input v-model.trim="securityId" placeholder="例如 600030" />
      </div>

      <div class="field small">
        <label>深度 depth</label>
        <input v-model.number="depth" type="number" min="1" max="50" />
      </div>
    </section>

    <main class="grid">
      <OrderBookCompareChart :security-id="securityId" :depth="depth" />
      <TradeDistributionChart :shareholder-id="shareholderId" :security-id="securityId" />
    </main>

    <section class="grid secondary">
      <TradingConsole :shareholder-id="shareholderId" :security-id="securityId"
        @order-submitted="onOrderSubmitted" @report="onReport" />
      <OrderStatusTable :shareholder-id="shareholderId" :security-id="securityId"
        :refresh-key="refreshKey" :local-orders="localOrders" />
      <AnalyticsPanel />
    </section>

    <footer class="footer">
    </footer>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import OrderBookCompareChart from "../components/charts/OrderBookCompareChart.vue";
import TradeDistributionChart from "../components/charts/TradeDistributionChart.vue";
import TradingConsole from "../components/TradingConsole.vue";
import OrderStatusTable from "../components/OrderStatusTable.vue";
import AnalyticsPanel from "../components/AnalyticsPanel.vue";

const shareholderId = ref<string>("A000000001");
const securityId = ref<string>("600030");
const depth = ref<number>(10);

// order status refresh trigger
const refreshKey = ref<number>(0);
// keep a lightweight local list so newly submitted orders show up immediately
const localOrders = ref<any[]>([]);

function onOrderSubmitted(payload: any) {
  // payload: { clOrderId, market, securityId, side, qty, price, shareholderId }
  refreshKey.value += 1;
  localOrders.value.unshift({
    ...payload,
    status: "SUBMITTED",
    updatedAt: Date.now(),
  });
}

function onReport(env: any) {
  // Update local order status based on reports.
  const d = env?.data || {};
  const cl = d.clOrderId;
  const orig = d.origClOrderId;
  const now = Date.now();
  const apply = (id: string | undefined, status: string) => {
    if (!id) return;
    const it = localOrders.value.find((x) => x.clOrderId === id);
    if (it) {
      it.status = status;
      it.updatedAt = now;
      it.lastReport = env.reportType;
    }
  };

  switch (env?.reportType) {
    case "ORDER_CONFIRM":
      apply(cl, "CONFIRMED");
      refreshKey.value += 1;
      break;
    case "ORDER_REJECT":
      apply(cl, "REJECTED");
      break;
    case "ORDER_EXECUTION":
      apply(cl, "EXECUTED");
      refreshKey.value += 1;
      break;
    case "CANCEL_CONFIRM":
      apply(orig, "CANCELED");
      refreshKey.value += 1;
      break;
    case "CANCEL_REJECT":
      apply(orig, "CANCEL_REJECTED");
      break;
    default:
      break;
  }
}

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
