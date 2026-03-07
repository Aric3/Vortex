<template>
  <div class="card">
    <div class="head">
      <div>
        <div class="title">下单与撤单控制台</div>
        <div class="sub">
          股东号 {{ shareholderId || '（请在上方输入）' }} · 股票 {{ securityId || '（请在上方输入）' }}
        </div>
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
            <span class="hint">撤单结果会以右下角通知推送。</span>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="订单状态" name="orders">
        <div class="orders-section">
          <div class="reports-head">
            <span>订单状态（共 {{ orderList.length }} 笔）</span>
            <el-button link size="small" @click="clearOrders">清空</el-button>
          </div>
          <el-table
            :data="orderList"
            stripe
            size="small"
            max-height="320"
            class="order-table"
            v-loading="orderHistoryLoading"
            element-loading-text="加载订单历史..."
          >
            <el-table-column prop="clOrderId" label="订单号" width="180">
              <template #default="{ row }">
                <span class="mono">{{ row.status === '已拒绝' ? '—' : row.clOrderId }}</span>
                <el-button v-if="row.status !== '已拒绝'" link type="primary" size="small" @click="copyToCancel(row.clOrderId)">复制撤单</el-button>
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
          <el-empty v-if="!orderList.length" description="暂无订单，下单后订单状态会更新，回报以右下角通知显示。" class="order-empty" />
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
import { useReportNotificationsStore } from '../stores/reportNotifications';
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
  lastUpdateMs?: number;
};

/** 后端订单历史分页响应 */
type OrderHistoryPage = {
  content: Array<{
    clOrderId?: string;
    market?: string;
    securityId?: string;
    side?: string;
    qty?: number;
    price?: number;
    orderQty?: number;
    cumQty?: number;
    status?: string;
    createTime?: string;
    updatedTime?: string;
  }>;
  page?: number;
  size?: number;
  totalElements?: number;
  totalPages?: number;
  last?: boolean;
};

const orderList = ref<OrderRow[]>([]);
const orderMap = new Map<string, OrderRow>();
const orderHistoryLoading = ref(false);
const reportNotifications = useReportNotificationsStore();

const activeTab = ref<'order' | 'cancel' | 'orders'>('order');

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

const lastBoundShareholderId = ref<string>('');

watch(
  () => [props.shareholderId, props.securityId],
  async () => {
    syncFromProps();
    const sh = boundShareholderId.value;
    if (sh !== lastBoundShareholderId.value) {
      lastBoundShareholderId.value = sh;
      orderMap.clear();
      flushOrderList();
      await loadOrderHistory(sh);
    }
  },
  { immediate: true }
);

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
  } catch (e: any) {
    ElMessage.error(`下单异常：${e?.message || '网络错误'}`);
  } finally {
    submittingOrder.value = false;
  }
}

async function submitCancel() {
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
    (a, b) => (b.lastUpdateMs ?? 0) - (a.lastUpdateMs ?? 0)
  );
}

function upsertOrder(clOrderId: string, patch: Partial<OrderRow>) {
  const now = Date.now();
  const timeText = new Date(now).toLocaleTimeString('zh-CN', { hour12: false });
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
      lastUpdateMs: now,
    };
    orderMap.set(clOrderId, row);
  }
  Object.assign(row, patch, { lastUpdate: timeText, lastUpdateMs: now });
  flushOrderList();
}

function clearOrders() {
  orderMap.clear();
  flushOrderList();
}
/** 后端状态枚举转展示文案 */
function orderStatusToDisplay(s: string | undefined): string {
  if (!s) return '--';
  const u = String(s);
  if (u === 'New') return '在簿';
  if (u === 'PartiallyFilled') return '部分成交';
  if (u === 'Filled') return '已成';
  if (u === 'Canceled') return '已撤';
  if (u === 'Rejected') return '已拒绝';
  return u;
}
/** 从接口时间字符串解析毫秒时间戳（支持 ISO 或 "yyyy-MM-dd HH:mm:ss"） */
function parseTimeMs(t: string | undefined): number {
  if (!t) return 0;
  const d = new Date(t);
  return isNaN(d.getTime()) ? 0 : d.getTime();
}
/** 按股东号加载订单历史并写入 orderMap，刷新后/切换股东号后订单状态可保留 */
async function loadOrderHistory(shareholderId: string) {
  const sh = (shareholderId || '').trim();
  if (!sh || sh.length !== 10) return;
  orderHistoryLoading.value = true;
  try {
    const res = await http.get<ApiResult<OrderHistoryPage>>(
      `/v1/vclient/orders/history?shareholderId=${encodeURIComponent(sh)}&page=0&size=100`
    );
    const page = res.data?.data;
    const list = page?.content ?? [];
    for (const o of list) {
      const cid = o.clOrderId;
      if (!cid) continue;
      const updatedTime = o.updatedTime ?? o.createTime;
      const lastUpdateMs = parseTimeMs(updatedTime);
      const timeText = lastUpdateMs
        ? new Date(lastUpdateMs).toLocaleTimeString('zh-CN', { hour12: false })
        : '';
      orderMap.set(cid, {
        clOrderId: cid,
        market: o.market ?? '',
        securityId: o.securityId ?? '',
        side: o.side ?? '',
        qty: Number(o.qty) ?? 0,
        price: Number(o.price) ?? 0,
        status: orderStatusToDisplay(o.status),
        cumQty: Number(o.cumQty) ?? 0,
        lastUpdate: timeText,
        lastUpdateMs,
      });
    }
    flushOrderList();
  } catch (e: any) {
    ElMessage.error(`加载订单历史失败：${e?.message || '网络错误'}`);
  } finally {
    orderHistoryLoading.value = false;
  }
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

/** 将回报应用到订单状态列表（与 Layout 右下角通知同源，由 store 推送后在此同步） */
function applyReportToOrderMap(env: OrderReportEnvelopeTyped<any>) {
  const reportType = typeof env.reportType === 'string'
    ? env.reportType
    : REPORT_TYPES[env.reportType as number] ?? String(env.reportType);
  if (reportType === 'HEARTBEAT') return;
  const d: any = env.data || {};

  switch (reportType) {
    case 'ORDER_CONFIRM':
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
      if (d.clOrderId && Number(d.rejectCode) !== 4001) {
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
      if (d.origClOrderId) upsertOrder(d.origClOrderId, { status: '已撤' });
      break;
    case 'CANCEL_REJECT':
      if (d.origClOrderId) upsertOrder(d.origClOrderId, { status: '撤单拒绝' });
      break;
    default:
      break;
  }
}

const processedReportIds = new Set<string>();
watch(
  () => reportNotifications.notifications,
  (list) => {
    const sh = boundShareholderId.value;
    if (!sh) return;
    for (const item of list) {
      if (item.shareholderId !== sh || processedReportIds.has(item.id)) continue;
      processedReportIds.add(item.id);
      applyReportToOrderMap(item.env);
    }
  },
  { deep: true }
);
watch(boundShareholderId, () => {
  processedReportIds.clear();
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

.reports-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  margin-bottom: 6px;
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

