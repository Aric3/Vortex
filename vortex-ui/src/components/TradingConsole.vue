<template>
  <div class="card">
    <div class="head">
      <div>
        <div class="title">下单与撤单控制台</div>
        <div class="sub">
          股东号 {{ shareholderId || '（请在上方输入）' }} · 股票 {{ securityId || '（请在上方输入）' }}
        </div>
      </div>
      <div class="status" v-if="reportStatus">
        {{ reportStatus }}
      </div>
    </div>

    <el-tabs v-model="activeTab" class="tabs" type="card">
      <el-tab-pane label="下单" name="order">
        <el-form :model="orderForm" label-width="90px" size="small" class="form">
          <el-form-item label="市场">
            <el-select v-model="orderForm.market" placeholder="选择市场" style="width: 140px">
              <el-option label="上交所 XSHG" value="XSHG" />
              <el-option label="深交所 XSHE" value="XSHE" />
              <el-option label="北交所 BJSE" value="BJSE" />
            </el-select>
          </el-form-item>

          <el-form-item label="买卖方向">
            <el-radio-group v-model="orderForm.side">
              <el-radio-button label="B">买入</el-radio-button>
              <el-radio-button label="S">卖出</el-radio-button>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="股东号">
            <el-input v-model="orderForm.shareholderId" placeholder="10位，如 A001000000" maxlength="10" show-word-limit />
          </el-form-item>

          <el-form-item label="股票代码">
            <el-input v-model="orderForm.securityId" placeholder="6位，如 600030" maxlength="6" show-word-limit />
          </el-form-item>

          <el-form-item label="数量">
            <el-input-number v-model="orderForm.qty" :min="1" :step="100" />
          </el-form-item>

          <el-form-item label="价格">
            <el-input-number v-model="orderForm.price" :min="0" :step="0.01" :precision="2" />
          </el-form-item>

          <el-form-item>
            <el-button type="primary" :loading="submittingOrder" @click="submitOrder">
              提交订单
            </el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="撤单" name="cancel">
        <el-form :model="cancelForm" label-width="90px" size="small" class="form">
          <el-form-item label="市场">
            <el-select v-model="cancelForm.market" placeholder="选择市场" style="width: 140px">
              <el-option label="上交所 XSHG" value="XSHG" />
              <el-option label="深交所 XSHE" value="XSHE" />
              <el-option label="北交所 BJSE" value="BJSE" />
            </el-select>
          </el-form-item>

          <el-form-item label="股东号">
            <el-input v-model="cancelForm.shareholderId" placeholder="10位" maxlength="10" show-word-limit />
          </el-form-item>

          <el-form-item label="股票代码">
            <el-input v-model="cancelForm.securityId" placeholder="6位" maxlength="6" show-word-limit />
          </el-form-item>

          <el-form-item label="买卖方向">
            <el-radio-group v-model="cancelForm.side">
              <el-radio-button label="B">买</el-radio-button>
              <el-radio-button label="S">卖</el-radio-button>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="原订单号">
            <el-input v-model="cancelForm.origClOrderId" placeholder="16位，从订单状态表复制" maxlength="16" show-word-limit />
          </el-form-item>

          <el-form-item>
            <el-button type="warning" :loading="submittingCancel" @click="submitCancel">
              提交撤单
            </el-button>
            <span class="hint">撤单结果也会通过回报流 SSE 推送。</span>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="订单状态" name="orders">
        <div class="orders-section">
          <div class="reports-head">
            <span>订单状态（由回报流实时更新，共 {{ orderList.length }} 笔）</span>
            <el-button link size="small" @click="clearOrders">清空</el-button>
          </div>
          <el-table :data="orderList" stripe size="small" max-height="320" class="order-table">
            <el-table-column prop="clOrderId" label="订单号" width="180">
              <template #default="{ row }">
                <span class="mono">{{ row.clOrderId }}</span>
                <el-button link type="primary" size="small" @click="copyToCancel(row.clOrderId)">复制撤单</el-button>
              </template>
            </el-table-column>
            <el-table-column prop="market" label="市场" width="72" />
            <el-table-column prop="securityId" label="股票" width="80" />
            <el-table-column prop="side" label="方向" width="56">
              <template #default="{ row }">{{ row.side === 'B' ? '买' : '卖' }}</template>
            </el-table-column>
            <el-table-column prop="qty" label="数量" width="72" align="right" />
            <el-table-column prop="price" label="价格" width="72" align="right" />
            <el-table-column prop="status" label="状态" width="88">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="cumQty" label="已成交" width="72" align="right" />
            <el-table-column prop="lastUpdate" label="更新时间" width="88" />
          </el-table>
          <el-empty v-if="!orderList.length" description="暂无订单，下单后回报流会更新此处。" class="order-empty" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="回报流" name="reports">
        <div class="reports">
          <div class="reports-head">
            <span>实时订单回报（最近 {{ reports.length }} 条）</span>
            <el-button link size="small" @click="clearReports">清空</el-button>
          </div>
          <el-scrollbar height="260px">
            <el-empty v-if="!reports.length" description="暂无回报，先建立 SSE 连接并下单试试。" />
            <el-timeline v-else class="timeline">
              <el-timeline-item
                v-for="item in reports"
                :key="item.id"
                :timestamp="item.timeText"
                :type="item.color"
                :hollow="true"
              >
                <div class="report-title">{{ item.title }}</div>
                <div class="report-sub">{{ item.subtitle }}</div>
              </el-timeline-item>
            </el-timeline>
          </el-scrollbar>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { useFilterStore } from '../stores/filter';
import { http, type ApiResult } from '../services/http';
import { createSSE, safeJsonParse } from '../services/sse';
import type {
  CancelOrderRequest,
  OrderReportEnvelopeTyped,
  OrderRequest,
} from '../types/vortex';

const props = defineProps<{
  shareholderId: string;
  securityId: string;
}>();

const filterStore = useFilterStore();

type DisplayReport = {
  id: string;
  title: string;
  subtitle: string;
  color: 'success' | 'warning' | 'danger' | 'info';
  timeText: string;
};

type OrderRow = {
  clOrderId: string;
  market: string;
  securityId: string;
  side: string;
  qty: number;
  price: number;
  status: string;
  cumQty: number;
  lastUpdate: string;
};

const reports = ref<DisplayReport[]>([]);
const orderList = ref<OrderRow[]>([]);
const orderMap = new Map<string, OrderRow>();

const activeTab = ref<'order' | 'cancel' | 'orders' | 'reports'>('order');

const orderForm = reactive<OrderRequest>({
  clOrderId: '',
  market: 'XSHG',
  securityId: '',
  side: 'B',
  qty: 100,
  price: 0,
  shareholderId: '',
});

const cancelForm = reactive<CancelOrderRequest>({
  clOrderId: '',
  origClOrderId: '',
  market: 'XSHG',
  securityId: '',
  side: 'B',
  shareholderId: '',
});

const reportStatus = ref<string>('');

let es: EventSource | null = null;

const submittingOrder = ref(false);
const submittingCancel = ref(false);

const boundShareholderId = computed(() => props.shareholderId?.trim() || orderForm.shareholderId.trim());

function syncFromProps() {
  if (props.shareholderId) {
    orderForm.shareholderId = props.shareholderId;
    cancelForm.shareholderId = props.shareholderId;
  }
  if (props.securityId) {
    orderForm.securityId = props.securityId;
    cancelForm.securityId = props.securityId;
  }
}

syncFromProps();

watch(
  () => [props.shareholderId, props.securityId],
  () => {
    syncFromProps();
    reconnectReports();
  }
);

<<<<<<< HEAD
// 下单/撤单表单中的股东号、股票代码同步回全局筛选 store，保证 SSE 订阅与下单使用同一股东号，并参与持久化
watch(
  () => [orderForm.shareholderId, orderForm.securityId],
  ([sh, sid]) => {
    const s = (sh as string)?.trim();
    const s2 = (sid as string)?.trim();
    if (s) filterStore.shareholderId = s;
    if (s2) filterStore.securityId = s2;
  },
  { deep: true }
);

// API: clOrderId 为 16 字节字符串，仅使用 0-9、A-Z 保证合法
function genClOrderId(): string {
  const chars = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ';
  let s = '';
  for (let i = 0; i < 16; i++) {
    s += chars[Math.floor(Math.random() * chars.length)];
  }
  return s;
}

async function submitOrder() {
  const sh = orderForm.shareholderId?.trim() ?? '';
  const sid = orderForm.securityId?.trim() ?? '';
  if (!sh || !sid) {
    ElMessage.error('股东号与股票代码不能为空（可从左侧全局筛选填写）。');
    return;
  }
  if (sh.length !== 10) {
    ElMessage.error('股东号须为 10 位字符串。');
    return;
  }
  if (sid.length !== 6) {
    ElMessage.error('股票代码须为 6 位字符串。');
=======
function genClOrderId(prefix: string): string {
  // API 标准：clOrderId 为 16 位字符串（建议仅使用大写字母与数字）
  const chars = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ';
  const p = (prefix || 'O').slice(0, 1).toUpperCase();
  let body = '';
  for (let i = 0; i < 15; i++) {
    body += chars[Math.floor(Math.random() * chars.length)];
  }
  return (p + body).slice(0, 16);
}

function isValidShareholderId(v: string): boolean {
  return (v || '').trim().length === 10;
}

function isValidSecurityId(v: string): boolean {
  const s = (v || '').trim();
  return /^[0-9]{6}$/.test(s);
}

async function submitOrder() {
  const sh = orderForm.shareholderId?.trim();
  const sec = orderForm.securityId?.trim();
  if (!sh || !sec) {
    ElMessage.error('股东号与股票代码不能为空（可从顶部面板填写）。');
>>>>>>> 3663ffd12426c5c9df17863ab6d5e0b72b44216f
    return;
  }
  if (!isValidShareholderId(sh)) {
    ElMessage.error('股东号不合法：必须为 10 位字符串（例如 A000000001）。');
    return;
  }
  if (!isValidSecurityId(sec)) {
    ElMessage.error('股票代码不合法：必须为 6 位数字（例如 600030）。');
    return;
  }
  if (!orderForm.qty || orderForm.qty <= 0 || !orderForm.price || orderForm.price <= 0) {
    ElMessage.error('请填写有效的数量与价格。');
    return;
  }

  submittingOrder.value = true;
  try {
    const payload: OrderRequest = {
      ...orderForm,
      clOrderId: genClOrderId(),
    };
    const res = await http.post<ApiResult<{ clOrderId: string }>>('/v1/vclient/orders', payload);
    if (!res.data.success) {
      ElMessage.error(`下单失败：${res.data.message || res.data.code}`);
      return;
    }
    ElMessage.success(`下单已提交，clOrderId = ${res.data.data?.clOrderId || payload.clOrderId}`);
<<<<<<< HEAD
=======
    emit('order-submitted', payload);

    reconnectReports();
>>>>>>> 3663ffd12426c5c9df17863ab6d5e0b72b44216f
  } catch (e: any) {
    ElMessage.error(`下单异常：${e?.message || '网络错误'}`);
  } finally {
    submittingOrder.value = false;
  }
}

async function submitCancel() {
<<<<<<< HEAD
  const sh = cancelForm.shareholderId?.trim() ?? '';
  const sid = cancelForm.securityId?.trim() ?? '';
  const orig = cancelForm.origClOrderId?.trim() ?? '';
  if (!sh || !sid || !orig) {
    ElMessage.error('股东号、股票代码与原订单号不能为空。');
    return;
  }
  if (sh.length !== 10) {
    ElMessage.error('股东号须为 10 位字符串。');
    return;
  }
  if (sid.length !== 6) {
    ElMessage.error('股票代码须为 6 位字符串。');
    return;
  }
  if (orig.length !== 16) {
    ElMessage.error('原订单号须为 16 位字符串（可从订单状态表复制）。');
=======
  const sh = cancelForm.shareholderId?.trim();
  const sec = cancelForm.securityId?.trim();
  const orig = cancelForm.origClOrderId?.trim();
  if (!sh || !sec || !orig) {
    ElMessage.error('股东号、股票代码与原订单号不能为空。');
    return;
  }
  if (!isValidShareholderId(sh)) {
    ElMessage.error('股东号不合法：必须为 10 位字符串（例如 A000000001）。');
    return;
  }
  if (!isValidSecurityId(sec)) {
    ElMessage.error('股票代码不合法：必须为 6 位数字（例如 600030）。');
    return;
  }
  if (orig.length !== 16) {
    ElMessage.error('原订单号 origClOrderId 不合法：必须为 16 位字符串。');
>>>>>>> 3663ffd12426c5c9df17863ab6d5e0b72b44216f
    return;
  }

  submittingCancel.value = true;
  try {
    const payload: CancelOrderRequest = {
      ...cancelForm,
      clOrderId: genClOrderId(),
    };
    const res = await http.post<ApiResult>('/v1/vclient/orders/cancel', payload);
    if (!res.data.success) {
      ElMessage.error(`撤单失败：${res.data.message || res.data.code}`);
      return;
    }
    ElMessage.success('撤单请求已提交，结果通过回报流推送。');
  } catch (e: any) {
    ElMessage.error(`撤单异常：${e?.message || '网络错误'}`);
  } finally {
    submittingCancel.value = false;
  }
}

function flushOrderList() {
  orderList.value = Array.from(orderMap.values()).sort(
    (a, b) => (b.lastUpdate > a.lastUpdate ? 1 : -1)
  );
}

function upsertOrder(clOrderId: string, patch: Partial<OrderRow>) {
  const timeText = new Date().toLocaleTimeString('zh-CN', { hour12: false });
  let row = orderMap.get(clOrderId);
  if (!row) {
    row = {
      clOrderId,
      market: patch.market ?? '',
      securityId: patch.securityId ?? '',
      side: patch.side ?? '',
      qty: patch.qty ?? 0,
      price: patch.price ?? 0,
      status: patch.status ?? '已报',
      cumQty: patch.cumQty ?? 0,
      lastUpdate: timeText,
    };
    orderMap.set(clOrderId, row);
  }
  Object.assign(row, patch, { lastUpdate: timeText });
  flushOrderList();
}

function clearReports() {
  reports.value = [];
}

function clearOrders() {
  orderMap.clear();
  flushOrderList();
}

function copyToCancel(clOrderId: string) {
  cancelForm.origClOrderId = clOrderId;
  if (navigator.clipboard?.writeText) {
    navigator.clipboard.writeText(clOrderId).then(() => {
      ElMessage.success('已复制订单号到撤单框');
    });
  } else {
    ElMessage.success('已填入撤单框，请手动复制订单号');
  }
}

function statusTagType(status: string): 'success' | 'warning' | 'danger' | 'info' {
  if (status === '已确认' || status === '已成') return 'success';
  if (status === '部分成交' || status === '已撤') return 'warning';
  if (status === '已拒绝' || status === '撤单拒绝') return 'danger';
  return 'info';
}

const REPORT_TYPES = ['ORDER_CONFIRM', 'ORDER_REJECT', 'ORDER_EXECUTION', 'CANCEL_CONFIRM', 'CANCEL_REJECT', 'HEARTBEAT'] as const;

function pushReport(env: OrderReportEnvelopeTyped<any>) {
<<<<<<< HEAD
  const reportType = typeof env.reportType === 'string'
    ? env.reportType
    : REPORT_TYPES[env.reportType as number] ?? String(env.reportType);
  if (reportType === 'HEARTBEAT') return;

=======
  if (env?.reportType === 'HEARTBEAT') return;
>>>>>>> 3663ffd12426c5c9df17863ab6d5e0b72b44216f
  const now = new Date();
  const timeText = now.toLocaleTimeString('zh-CN', { hour12: false });

  let title = reportType;
  let subtitle = '';
  let color: DisplayReport['color'] = 'info';

  const d: any = env.data || {};

  switch (reportType) {
    case 'ORDER_CONFIRM':
      title = '订单确认';
      subtitle = `clOrderId=${d.clOrderId}, ${d.side === 'B' ? '买' : '卖'} ${d.securityId} 数量 ${d.qty} 价格 ${d.price}`;
      color = 'success';
      if (d.clOrderId) {
        upsertOrder(d.clOrderId, {
          market: d.market,
          securityId: d.securityId,
          side: d.side,
          qty: Number(d.qty),
          price: Number(d.price),
          status: '已确认',
          cumQty: 0,
        });
      }
      break;
    case 'ORDER_REJECT':
      title = '订单拒绝';
      subtitle = `clOrderId=${d.clOrderId}, 原因：${d.rejectText || d.rejectCode}`;
      color = 'danger';
      if (d.clOrderId) {
        upsertOrder(d.clOrderId, {
          market: d.market,
          securityId: d.securityId,
          side: d.side,
          qty: Number(d.qty),
          price: Number(d.price),
          status: '已拒绝',
        });
      }
      break;
    case 'ORDER_EXECUTION':
      title = '成交';
      subtitle = `clOrderId=${d.clOrderId}, execId=${d.execId}, 数量 ${d.execQty} 价格 ${d.execPrice}`;
      color = 'success';
      if (d.clOrderId) {
        const row = orderMap.get(d.clOrderId);
        const add = Number(d.execQty) || 0;
        const newCum = (row?.cumQty ?? 0) + add;
        const qty = row?.qty ?? Number(d.qty);
        upsertOrder(d.clOrderId, {
          market: row?.market ?? d.market,
          securityId: row?.securityId ?? d.securityId,
          side: row?.side ?? d.side,
          qty,
          price: row?.price ?? Number(d.price),
          status: newCum >= qty ? '已成' : '部分成交',
          cumQty: newCum,
        });
      }
      break;
    case 'CANCEL_CONFIRM':
      title = '撤单确认';
      subtitle = `origClOrderId=${d.origClOrderId}, 撤单数量 ${d.canceledQty}, 累计成交 ${d.cumQty}`;
      color = 'warning';
      if (d.origClOrderId) {
        upsertOrder(d.origClOrderId, { status: '已撤' });
      }
      break;
    case 'CANCEL_REJECT':
      title = '撤单拒绝';
      subtitle = `origClOrderId=${d.origClOrderId}, 原因：${d.rejectText || d.rejectCode}`;
      color = 'danger';
      if (d.origClOrderId) {
        upsertOrder(d.origClOrderId, { status: '撤单拒绝' });
      }
      break;
    default:
      subtitle = JSON.stringify(d);
      color = 'info';
  }

  const item: DisplayReport = {
    id: `${reportType}-${d.clOrderId || d.execId || now.getTime()}`,
    title,
    subtitle,
    color,
    timeText,
  };

  reports.value.unshift(item);
  if (reports.value.length > 200) reports.value.splice(200);
}

function cleanupReportsStream() {
  if (es) {
    try {
      es.close();
    } catch {
      // ignore
    }
    es = null;
  }
}

function reconnectReports() {
  cleanupReportsStream();
  const sh = boundShareholderId.value;
  if (!sh) {
    reportStatus.value = '未连接：请先填写股东号。';
    return;
  }
  reportStatus.value = '回报流连接中...';
  const path = `/v1/vclient/stream/reports?shareholderId=${encodeURIComponent(sh)}`;
  es = createSSE(path);
  es.onopen = () => {
    reportStatus.value = '回报流已连接';
  };
  es.onerror = () => {
    reportStatus.value = '回报流连接异常，准备重试...';
  };
  es.onmessage = (evt) => {
<<<<<<< HEAD
    const raw = typeof evt.data === 'string' ? evt.data : '';
    const lines = raw.split(/\r?\n/).map((s) => s.replace(/^\s*data:\s*/, '').trim()).filter(Boolean);
    for (const line of lines) {
      const env = safeJsonParse<OrderReportEnvelopeTyped<any>>(line);
      if (env && env.reportType != null) pushReport(env);
    }
=======
    const env = safeJsonParse<OrderReportEnvelopeTyped<any>>(evt.data);
    if (!env) return;
    if (env.reportType === 'HEARTBEAT') return;
    emit('report', env);
    pushReport(env);
>>>>>>> 3663ffd12426c5c9df17863ab6d5e0b72b44216f
  };
}

onMounted(() => {
  reconnectReports();
});

onBeforeUnmount(() => {
  cleanupReportsStream();
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
  margin-bottom: 8px;
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

.tabs {
  --el-tabs-header-height: 34px;
}

.form {
  max-width: 620px;
  padding-top: 8px;
}

.hint {
  margin-left: 12px;
  font-size: 12px;
  color: #6b7280;
}

.reports {
  padding-top: 8px;
}

.reports-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  margin-bottom: 6px;
}

.timeline {
  padding-left: 4px;
}

.report-title {
  font-size: 13px;
  font-weight: 600;
}

.report-sub {
  margin-top: 2px;
  font-size: 12px;
  color: #6b7280;
}

.orders-section {
  padding-top: 8px;
}

.order-table {
  font-size: 12px;
}

.order-table .mono {
  font-family: ui-monospace, monospace;
  font-size: 11px;
}

.order-empty {
  margin-top: 12px;
}
</style>

