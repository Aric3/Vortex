<template>
  <div class="card">
    <div class="head">
      <div class="title">成交分布图（当前用户）</div>
      <div class="meta">
        <span class="pill">股东号：{{ shareholderId }}</span>
        <span class="pill">股票：{{ securityId }}</span>
        <span v-if="status" class="pill muted">{{ status }}</span>
      </div>
    </div>

    <div class="controls">
      <label>
        分桶步长
        <select v-model.number="bucketStep">
          <option :value="0.01">0.01</option>
          <option :value="0.05">0.05</option>
          <option :value="0.1">0.10</option>
        </select>
      </label>
      <label>
        保留成交条数
        <select v-model.number="maxTrades">
          <option :value="200">200</option>
          <option :value="500">500</option>
          <option :value="1000">1000</option>
        </select>
      </label>
      <button class="btn" @click="reset">清空统计</button>
    </div>

    <div ref="el" class="chart"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from "vue";
import * as echarts from "echarts";
import { createSSE, safeJsonParse } from "../../services/sse";
import type { OrderReportEnvelope, ExecutionReport } from "../../types/vortex";

const props = defineProps<{
  shareholderId: string;
  securityId: string;
}>();

const el = ref<HTMLDivElement | null>(null);
const status = ref<string>("连接中...");
const bucketStep = ref<number>(0.05);
const maxTrades = ref<number>(500);

let chart: echarts.ECharts | null = null;
let es: EventSource | null = null;
const executions = ref<ExecutionReport[]>([]);

function reset() {
  executions.value = [];
  render();
}

function bucketPrice(price: number): number {
  const step = bucketStep.value;
  if (step <= 0) return price;
  return Math.round(price / step) * step;
}

function render() {
  if (!chart) return;

  const agg = new Map<number, number>();
  for (const ex of executions.value) {
    const p = bucketPrice(Number(ex.execPrice));
    const q = Number(ex.execQty);
    if (!Number.isFinite(p) || !Number.isFinite(q)) continue;
    agg.set(p, (agg.get(p) ?? 0) + q);
  }

  const prices = Array.from(agg.keys()).sort((a, b) => a - b);
  const qtys = prices.map(p => agg.get(p) ?? 0);

  const option: echarts.EChartsOption = {
    grid: { left: 50, right: 20, top: 24, bottom: 50, containLabel: true },
    tooltip: {
      trigger: "axis",
      axisPointer: { type: "shadow" },
      formatter: (params: any) => {
        const p = params?.[0]?.axisValue;
        const v = params?.[0]?.value ?? 0;
        return `价格桶：${p}<br/>成交量：${v}`;
      },
    },
    xAxis: {
      type: "category",
      data: prices.map(p => p.toFixed(2)),
      axisLabel: { rotate: 30 },
    },
    yAxis: { type: "value" },
    series: [{ name: "成交量", type: "bar", data: qtys, barMaxWidth: 18 }],
  };

  chart.setOption(option, { notMerge: true });
}

function cleanup() {
  if (es) { try { es.close(); } catch {} es = null; }
}

function connect() {
  cleanup();
  const sh = props.shareholderId?.trim();
  if (!sh) { status.value = "请输入股东号"; return; }
  status.value = "连接中...";

  const path = `/v1/vclient/stream/reports?shareholderId=${encodeURIComponent(sh)}`;
  es = createSSE(path);

  es.onopen = () => (status.value = "已连接（等待成交回报）");
  es.onerror = () => (status.value = "连接异常（自动重连中）");
  es.onmessage = (evt) => {
    const env = safeJsonParse<OrderReportEnvelope>(evt.data);
    if (!env) return;

    if (env.reportType !== "ORDER_EXECUTION") return;

    const d = env.data as any;
    const ex: ExecutionReport = {
      execId: d.execId,
      execQty: Number(d.execQty),
      execPrice: Number(d.execPrice),
      securityId: d.securityId,
      market: d.market,
      clOrderId: d.clOrderId,
      shareholderId: d.shareholderId,
      ts: d.ts ?? Date.now(),
    };

    const sid = props.securityId?.trim();
    if (sid && ex.securityId && String(ex.securityId) !== sid) return;

    if (Number.isFinite(ex.execQty) && Number.isFinite(ex.execPrice)) {
      executions.value.push(ex);
      if (executions.value.length > maxTrades.value) {
        executions.value.splice(0, executions.value.length - maxTrades.value);
      }
      render();
    }
  };
}

function onResize() { chart?.resize(); }

onMounted(() => {
  if (!el.value) return;
  chart = echarts.init(el.value);
  window.addEventListener("resize", onResize);
  connect();
  render();
});

onBeforeUnmount(() => {
  cleanup();
  window.removeEventListener("resize", onResize);
  if (chart) { chart.dispose(); chart = null; }
});

watch(() => [props.shareholderId, props.securityId], () => {
  reset();
  connect();
});

watch(() => [bucketStep.value, maxTrades.value], () => render());
</script>

<style scoped>
.card{ background:#fff; border:1px solid #e5e7eb; border-radius:10px; padding:12px; }
.head{ display:flex; justify-content:space-between; gap:10px; margin-bottom:8px; align-items:flex-end; }
.title{ font-weight:650; }
.meta{ display:flex; gap:8px; flex-wrap:wrap; }
.pill{ font-size:12px; padding:2px 8px; border-radius:999px; background:#f3f4f6; border:1px solid #e5e7eb; }
.pill.muted{ color:#6b7280; }
.controls{ display:flex; gap:10px; flex-wrap:wrap; align-items:center; margin:8px 0 10px; font-size:13px; }
.controls label{ display:flex; gap:6px; align-items:center; }

select{
  border:1px solid #e5e7eb;
  border-radius:6px;
  padding:4px 6px;
  background:#fff;
  color:#111827;           /* 下拉框文字可见 */
}
.btn{ border:1px solid #e5e7eb; background:#fff; border-radius:8px; padding:6px 10px; cursor:pointer; }
.btn:hover{ background:#f9fafb; }
.chart{ width:100%; height:360px; }
</style>
