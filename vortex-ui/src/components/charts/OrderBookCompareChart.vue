<template>
  <div class="card">
    <div class="head">
      <div class="title">买卖盘对比图（订单簿 Top{{ depth }}）</div>
      <div class="meta">
        <span class="pill">股票：{{ securityId }}</span>
        <span v-if="status" class="pill muted">{{ status }}</span>
      </div>
    </div>
    <div ref="el" class="chart"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from "vue";
import * as echarts from "echarts";
import { createSSE, safeJsonParse } from "../../services/sse";
import type { OrderBookSnapshot, PriceLevel } from "../../types/vortex";

const props = defineProps<{
  securityId: string;
  depth: number;
}>();

const el = ref<HTMLDivElement | null>(null);
const status = ref<string>("连接中...");
let chart: echarts.ECharts | null = null;
let es: EventSource | null = null;

function toLevel(x: any): PriceLevel | null {
  if (!x) return null;
  const p = Number(x.price ?? x[0]);
  const q = Number(x.totalQty ?? x.qty ?? x.size ?? x[1]);
  if (!Number.isFinite(p) || !Number.isFinite(q)) return null;
  return { price: p, totalQty: q };
}

function normalize(snapshot: OrderBookSnapshot): { bids: PriceLevel[]; asks: PriceLevel[] } {
  const bids = snapshot.bids || snapshot.bidLevels || snapshot.buy || (snapshot as any).bid || [];
  const asks = snapshot.asks || snapshot.askLevels || snapshot.sell || (snapshot as any).ask || [];
  return {
    bids: (Array.isArray(bids) ? bids : []).map(toLevel).filter(Boolean) as PriceLevel[],
    asks: (Array.isArray(asks) ? asks : []).map(toLevel).filter(Boolean) as PriceLevel[],
  };
}

function render(snapshot: OrderBookSnapshot) {
  if (!chart) return;
  const { bids, asks } = normalize(snapshot);

  const depth = Math.max(1, Number(props.depth) || 10);
  const bidsSorted = [...bids].sort((a, b) => b.price - a.price).slice(0, depth);
  const asksSorted = [...asks].sort((a, b) => a.price - b.price).slice(0, depth);

  const askPrices = [...asksSorted].sort((a, b) => b.price - a.price).map(x => x.price.toFixed(2));
  const bidPrices = [...bidsSorted].sort((a, b) => b.price - a.price).map(x => x.price.toFixed(2));
  const y = [...askPrices, ...bidPrices];

  const bidMap = new Map(bidsSorted.map(x => [x.price.toFixed(2), Number(x.totalQty ?? x.qty ?? 0)]));
  const askMap = new Map(asksSorted.map(x => [x.price.toFixed(2), Number(x.totalQty ?? x.qty ?? 0)]));

  const bidSeries = y.map(p => -(bidMap.get(p) ?? 0));
  const askSeries = y.map(p => (askMap.get(p) ?? 0));

  const option: echarts.EChartsOption = {
    grid: { left: 60, right: 60, top: 24, bottom: 24, containLabel: true },
    tooltip: {
      trigger: "axis",
      axisPointer: { type: "shadow" },
      formatter: (params: any) => {
        const price = params?.[0]?.axisValue;
        const buy = Math.abs(params?.find((x: any) => x.seriesName === "买盘")?.value ?? 0);
        const sell = params?.find((x: any) => x.seriesName === "卖盘")?.value ?? 0;
        return `价格：${price}<br/>买盘量：${buy}<br/>卖盘量：${sell}`;
      },
    },
    xAxis: {
      type: "value",
      axisLabel: { formatter: (v: any) => Math.abs(Number(v)).toString() },
      splitLine: { show: true },
    },
    yAxis: { type: "category", data: y, axisTick: { show: false } },
    series: [
      { name: "买盘", type: "bar", stack: "s", data: bidSeries, barMaxWidth: 14 },
      { name: "卖盘", type: "bar", stack: "s", data: askSeries, barMaxWidth: 14 },
    ],
  };

  chart.setOption(option, { notMerge: true });
}

function cleanup() {
  if (es) { try { es.close(); } catch {} es = null; }
}

function connect() {
  cleanup();
  const sid = props.securityId?.trim();
  if (!sid) { status.value = "请输入股票代码"; return; }
  status.value = "连接中...";
  const depth = Math.max(1, Number(props.depth) || 10);
  const path = `/v1/vclient/orderbook/${encodeURIComponent(sid)}/stream?depth=${encodeURIComponent(String(depth))}`;
  es = createSSE(path);
  es.onopen = () => (status.value = "已连接（实时更新）");
  es.onerror = () => (status.value = "连接异常（自动重连中）");
  es.onmessage = (evt) => {
    const obj = safeJsonParse<OrderBookSnapshot>(evt.data);
    if (obj) render(obj);
  };
}

function onResize() { chart?.resize(); }

onMounted(() => {
  if (!el.value) return;
  chart = echarts.init(el.value);
  window.addEventListener("resize", onResize);
  connect();
});

onBeforeUnmount(() => {
  cleanup();
  window.removeEventListener("resize", onResize);
  if (chart) { chart.dispose(); chart = null; }
});

watch(() => [props.securityId, props.depth], () => connect());
</script>

<style scoped>
.card{ background:#fff; border:1px solid #e5e7eb; border-radius:10px; padding:12px; }
.head{ display:flex; justify-content:space-between; gap:10px; margin-bottom:8px; align-items:flex-end; }
.title{ font-weight:650; }
.meta{ display:flex; gap:8px; flex-wrap:wrap; }
.pill{ font-size:12px; padding:2px 8px; border-radius:999px; background:#f3f4f6; border:1px solid #e5e7eb; }
.pill.muted{ color:#6b7280; }
.chart{ width:100%; height:360px; }
</style>
