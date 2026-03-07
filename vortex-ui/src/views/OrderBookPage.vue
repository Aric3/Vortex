<template>
  <div class="page">
    <header class="page-header">
      <h1 class="page-title">订单簿对比图</h1>
    </header>
    <div class="content">
      <OrderBookCompareChart :security-id="filter.securityId" :depth="filter.depth" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { useFilterStore } from '../stores/filter';
import OrderBookCompareChart from '../components/charts/OrderBookCompareChart.vue';

const filter = useFilterStore();
</script>

<style scoped>
.page {
  max-width: 960px;
  margin: 0 auto;
}
.page-header {
  margin-bottom: 16px;
}
.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #111827;
}
.page-desc {
  margin: 6px 0 0;
  font-size: 13px;
  color: #6b7280;
}
.content {
  background: #fff;
  border-radius: 10px;
  border: 1px solid #e5e7eb;
  padding: 12px;
}
</style>
