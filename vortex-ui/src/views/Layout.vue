<template>
  <div class="layout">
    <aside class="sidebar">
      <div class="sidebar-title">Vortex 交易</div>
      <el-menu
        :default-active="activeMenu"
        class="sidebar-menu"
        router
      >
        <el-menu-item index="/orderbook">
          <span>订单簿</span>
        </el-menu-item>
        <el-menu-item index="/trades">
          <span>成交分布</span>
        </el-menu-item>
        <el-menu-item index="/console">
          <span>下单撤单</span>
        </el-menu-item>
        <el-menu-item index="/analytics">
          <span>风控指标</span>
        </el-menu-item>
      </el-menu>

      <div class="filter-section">
        <div class="filter-title">全局筛选</div>
        <el-form label-position="top" size="small" class="filter-form">
          <el-form-item label="股东号">
            <el-input v-model.trim="filter.editShareholderId" placeholder="10位，如 A001000000" maxlength="10" show-word-limit />
          </el-form-item>
          <el-form-item label="股票代码">
            <el-select
              v-model="filter.editSecurityId"
              placeholder="请选择股票"
              filterable
              clearable
              style="width: 100%"
            >
              <el-option
                v-for="code in symbolOptions"
                :key="code"
                :label="code"
                :value="code"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="深度">
            <el-input-number v-model="filter.depth" :min="1" :max="50" :step="1" style="width: 100%" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" plain size="small" class="switch-btn" @click="filter.applyEdit">
              切换
            </el-button>
          </el-form-item>
          <el-form-item v-if="filter.securityId" class="realtime-price-row">
            <span class="realtime-label">实时价</span>
            <span class="realtime-value">{{ lastPrice != null ? lastPrice : '--' }}</span>
          </el-form-item>
        </el-form>
      </div>

    </aside>
    <main class="main">
      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <keep-alive :max="10">
            <component :is="Component" :key="route.fullPath" />
          </keep-alive>
        </transition>
      </router-view>
    </main>

    <!-- 右下角回报通知：订单状态变化时动态弹出 -->
    <div class="report-notifications" v-if="reportNotifications.notifications.length">
      <div
        v-for="item in reportNotifications.notifications"
        :key="item.id"
        class="report-toast"
        :class="item.color"
      >
        <div class="report-toast-header">
          <span class="report-toast-title">{{ item.title }}</span>
          <span class="report-toast-time">{{ item.timeText }}</span>
          <button type="button" class="report-toast-close" @click="reportNotifications.remove(item.id)" aria-label="关闭">×</button>
        </div>
        <div class="report-toast-sub">{{ item.subtitle }}</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, watch, onBeforeUnmount } from 'vue';
import { useRoute } from 'vue-router';
import { useFilterStore } from '../stores/filter';
import { useReportNotificationsStore } from '../stores/reportNotifications';
import { http, type ApiResult } from '../services/http';
import { createSSE, safeJsonParse } from '../services/sse';

const route = useRoute();
const filter = useFilterStore();
const reportNotifications = useReportNotificationsStore();

const activeMenu = computed(() => route.path || '/orderbook');

const symbolOptions = ref<string[]>([]);
const lastPrice = ref<number | null>(null);
let quoteTickEs: EventSource | null = null;
let reportEs: EventSource | null = null;

async function fetchSymbols() {
  try {
    const res = await http.get<ApiResult<string[]>>('/v1/vclient/quote/symbols');
    if (res.data?.success && Array.isArray(res.data.data)) {
      symbolOptions.value = res.data.data;
    }
  } catch {
    symbolOptions.value = [];
  }
}

function connectQuoteTickStream() {
  if (quoteTickEs) {
    try {
      quoteTickEs.close();
    } catch {}
    quoteTickEs = null;
  }
  const sid = filter.securityId;
  if (!sid) {
    lastPrice.value = null;
    return;
  }
  const path = `/v1/vclient/quote/stream/tick/${encodeURIComponent(sid)}`;
  quoteTickEs = createSSE(path);
  quoteTickEs.onmessage = (evt) => {
    const raw = typeof evt.data === 'string' ? evt.data : '';
    const data = safeJsonParse<{ lastPrice?: number }>(raw);
    if (data != null && typeof data.lastPrice === 'number') {
      lastPrice.value = data.lastPrice;
    }
  };
}

function disconnectQuoteTickStream() {
  if (quoteTickEs) {
    try {
      quoteTickEs.close();
    } catch {}
    quoteTickEs = null;
  }
  lastPrice.value = null;
}

function connectReportStream() {
  if (reportEs) {
    try { reportEs.close(); } catch {}
    reportEs = null;
  }
  const sh = (filter.shareholderId || '').trim();
  if (!sh || sh.length !== 10) return;
  const path = `/v1/vclient/stream/reports?shareholderId=${encodeURIComponent(sh)}`;
  reportEs = createSSE(path);
  reportEs.onmessage = (evt) => {
    const raw = typeof evt.data === 'string' ? evt.data : '';
    const lines = raw.split(/\r?\n/).map((s) => s.replace(/^\s*data:\s*/, '').trim()).filter(Boolean);
    for (const line of lines) {
      const env = safeJsonParse<any>(line);
      if (env && env.reportType != null) reportNotifications.addReport(env);
    }
  };
}

onMounted(() => {
  fetchSymbols();
  connectQuoteTickStream();
  connectReportStream();
});

watch(() => filter.shareholderId, () => {
  connectReportStream();
});

watch(() => filter.securityId, () => {
  connectQuoteTickStream();
});

onBeforeUnmount(() => {
  disconnectQuoteTickStream();
  if (reportEs) {
    try { reportEs.close(); } catch {}
    reportEs = null;
  }
});
</script>

<style scoped>
.layout {
  display: flex;
  min-height: 100vh;
  background: #f3f4f6;
}

.sidebar {
  width: 240px;
  min-width: 240px;
  background: #fff;
  border-right: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
}

.sidebar-title {
  padding: 16px;
  font-size: 16px;
  font-weight: 700;
  color: #111827;
  border-bottom: 1px solid #e5e7eb;
}

.sidebar-menu {
  border-right: none;
  flex: 0 0 auto;
}

.sidebar-menu .el-menu-item {
  height: 48px;
  line-height: 48px;
}

.filter-section {
  padding: 12px;
  border-top: 1px solid #e5e7eb;
  margin-top: auto;
}

.filter-title {
  font-size: 12px;
  font-weight: 600;
  color: #6b7280;
  margin-bottom: 8px;
}

.filter-form :deep(.el-form-item) {
  margin-bottom: 10px;
}

.filter-form :deep(.el-form-item__label) {
  font-size: 12px;
  color: #374151;
}

.switch-btn {
  width: 100%;
}

.realtime-price-row {
  margin-bottom: 0;
}

.realtime-price-row :deep(.el-form-item__content) {
  display: flex;
  align-items: center;
  gap: 8px;
}

.realtime-label {
  font-size: 12px;
  color: #6b7280;
}

.realtime-value {
  font-size: 14px;
  font-weight: 600;
  color: #111827;
}

.main {
  flex: 1;
  overflow: auto;
  padding: 16px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

/* 右下角回报通知 */
.report-notifications {
  position: fixed;
  right: 16px;
  bottom: 16px;
  z-index: 9999;
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-width: 360px;
  max-height: 70vh;
  overflow-y: auto;
  pointer-events: none;
}
.report-notifications .report-toast {
  pointer-events: auto;
  padding: 10px 12px;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
  background: #fff;
  border-left: 4px solid #909399;
}
.report-notifications .report-toast.success { border-left-color: #67c23a; }
.report-notifications .report-toast.warning { border-left-color: #e6a23c; }
.report-notifications .report-toast.danger { border-left-color: #f56c6c; }
.report-notifications .report-toast.info { border-left-color: #409eff; }
.report-toast-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.report-toast-title {
  font-weight: 600;
  font-size: 13px;
  color: #111827;
}
.report-toast-time {
  font-size: 11px;
  color: #9ca3af;
  margin-left: auto;
}
.report-toast-close {
  margin-left: 4px;
  padding: 0 4px;
  border: none;
  background: none;
  font-size: 16px;
  line-height: 1;
  color: #9ca3af;
  cursor: pointer;
}
.report-toast-close:hover {
  color: #374151;
}
.report-toast-sub {
  font-size: 12px;
  color: #6b7280;
  line-height: 1.4;
}
</style>
