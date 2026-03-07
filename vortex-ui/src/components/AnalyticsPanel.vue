<template>
  <div class="card">
    <div class="head">
      <div>
        <div class="title">风控与成交时延指标</div>
      </div>
      <div class="status">
        <span v-if="error" class="error">{{ error }}</span>
        <span v-else>最近更新时间：{{ lastUpdateText || '尚未获取' }}</span>
      </div>
    </div>

    <el-row :gutter="12" class="stats-row">
      <el-col :xs="12" :sm="8" :md="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">总订单数</div>
          <div class="stat-value">{{ metrics?.totalOrders ?? '-' }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">对敲拒绝数</div>
          <div class="stat-value">{{ metrics?.washRejects ?? '-' }}</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="8" :md="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">对敲占比</div>
          <div class="stat-value">
            <span v-if="metrics">{{ (metrics.washRatio * 100).toFixed(2) }}%</span>
            <span v-else>-</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <div class="chart-title">成交延时分布（订单首次成交耗时）</div>
    <div ref="chartEl" class="chart"></div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';
import * as echarts from 'echarts';
import { http } from '../services/http';
import type { AnalyticsMetrics } from '../types/vortex';

const metrics = ref<AnalyticsMetrics | null>(null);
const error = ref<string | ''>('');
const lastUpdateText = ref('');

const chartEl = ref<HTMLDivElement | null>(null);
let chart: echarts.ECharts | null = null;
let timer: number | null = null;

async function fetchMetrics() {
  try {
    // 后端 /api/v1/analytics/metrics 直接返回 AnalyticsMetrics，不包在 Result 里
    const res = await http.get<AnalyticsMetrics>('/v1/analytics/metrics');
    const body = res.data as unknown as AnalyticsMetrics;
    if (!body || typeof body !== 'object') {
      error.value = '响应格式异常';
      return;
    }
    error.value = '';
    metrics.value = body;
    if (metrics.value?.timestamp) {
      const d = new Date(metrics.value.timestamp);
      lastUpdateText.value = d.toLocaleTimeString('zh-CN', { hour12: false });
    }
    renderChart();
  } catch (e: any) {
    const status = e?.response?.status;
    const msg = e?.response?.data?.message || e?.message;
    error.value = status === 500 || !status ? (msg || '请确认后端已启动（端口 8081）') : (msg || '网络错误');
  }
}

function renderChart() {
  if (!chart || !metrics.value) return;
  const buckets = metrics.value.latencyBuckets || [];
  const x = buckets.map((b) => b.bucket);
  const y = buckets.map((b) => b.count);

  const option: echarts.EChartsOption = {
    grid: { left: 50, right: 10, top: 44, bottom: 50, containLabel: true },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
    },
    xAxis: {
      type: 'category',
      data: x,
      axisLabel: { rotate: 30 },
    },
    yAxis: {
      type: 'value',
      name: '订单数',
    },
    series: [
      {
        type: 'bar',
        data: y,
        barMaxWidth: 30,
      },
    ],
  };

  chart.setOption(option, { notMerge: true });
}

function onResize() {
  chart?.resize();
}

onMounted(() => {
  if (chartEl.value) {
    chart = echarts.init(chartEl.value);
  }
  fetchMetrics();
  timer = window.setInterval(fetchMetrics, 2000);
  window.addEventListener('resize', onResize);
});

onBeforeUnmount(() => {
  if (timer !== null) {
    window.clearInterval(timer);
    timer = null;
  }
  window.removeEventListener('resize', onResize);
  if (chart) {
    chart.dispose();
    chart = null;
  }
});
</script>

<style scoped>
.card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 12px;
}

.head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 12px;
  margin-bottom: 10px;
}

.title {
  font-weight: 650;
}

.sub {
  margin-top: 4px;
  font-size: 12px;
  color: #6b7280;
}

.status {
  font-size: 12px;
  color: #6b7280;
}

.status .error {
  color: #ef4444;
}

.stats-row {
  margin-bottom: 10px;
}

.stat-card {
  padding: 8px 10px;
}

.stat-label {
  font-size: 12px;
  color: #6b7280;
}

.stat-value {
  margin-top: 4px;
  font-size: 18px;
  font-weight: 600;
}

.chart-title {
  margin: 6px 0 10px;
  font-size: 13px;
  color: #374151;
}

.chart {
  width: 100%;
  height: 260px;
  margin-top: 4px;
}
</style>

