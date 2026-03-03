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
            <el-input v-model="orderForm.shareholderId" placeholder="从上方面板自动带入" />
          </el-form-item>

          <el-form-item label="股票代码">
            <el-input v-model="orderForm.securityId" placeholder="6 位股票代码，如 600030" />
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
            <span class="hint">提交后，确认/拒绝与成交通过下方“回报流”实时推送。</span>
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
            <el-input v-model="cancelForm.shareholderId" />
          </el-form-item>

          <el-form-item label="股票代码">
            <el-input v-model="cancelForm.securityId" />
          </el-form-item>

          <el-form-item label="买卖方向">
            <el-radio-group v-model="cancelForm.side">
              <el-radio-button label="B">买</el-radio-button>
              <el-radio-button label="S">卖</el-radio-button>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="原订单号">
            <el-input v-model="cancelForm.origClOrderId" placeholder="需要撤销的原 clOrderId" />
          </el-form-item>

          <el-form-item>
            <el-button type="warning" :loading="submittingCancel" @click="submitCancel">
              提交撤单
            </el-button>
            <span class="hint">撤单结果也会通过回报流 SSE 推送。</span>
          </el-form-item>
        </el-form>
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
import { http, type ApiResult } from '../services/http';
import { createSSE, safeJsonParse } from '../services/sse';
import type {
  AnalyticsMetrics,
  CancelOrderRequest,
  ExecutionReport,
  OrderReportEnvelopeTyped,
  OrderRequest,
} from '../types/vortex';

const props = defineProps<{
  shareholderId: string;
  securityId: string;
}>();

const activeTab = ref<'order' | 'cancel' | 'reports'>('order');

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

type DisplayReport = {
  id: string;
  title: string;
  subtitle: string;
  color: 'success' | 'warning' | 'danger' | 'info';
  timeText: string;
};

const reports = ref<DisplayReport[]>([]);
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

function genClOrderId(prefix: string): string {
  const ts = Date.now().toString(36);
  const rnd = Math.random().toString(36).slice(2, 8);
  return (prefix + ts + rnd).slice(-16).toUpperCase();
}

async function submitOrder() {
  if (!orderForm.shareholderId || !orderForm.securityId) {
    ElMessage.error('股东号与股票代码不能为空（可从顶部面板填写）。');
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
      clOrderId: genClOrderId('O'),
    };
    const res = await http.post<ApiResult<{ clOrderId: string }>>('/v1/vclient/orders', payload);
    if (!res.data.success) {
      ElMessage.error(`下单失败：${res.data.message || res.data.code}`);
      return;
    }
    ElMessage.success(`下单已提交，clOrderId = ${res.data.data?.clOrderId || payload.clOrderId}`);
    reconnectReports();
  } catch (e: any) {
    ElMessage.error(`下单异常：${e?.message || '网络错误'}`);
  } finally {
    submittingOrder.value = false;
  }
}

async function submitCancel() {
  if (!cancelForm.shareholderId || !cancelForm.securityId || !cancelForm.origClOrderId) {
    ElMessage.error('股东号、股票代码与原订单号不能为空。');
    return;
  }

  submittingCancel.value = true;
  try {
    const payload: CancelOrderRequest = {
      ...cancelForm,
      clOrderId: genClOrderId('C'),
    };
    const res = await http.post<ApiResult>('/v1/vclient/orders/cancel', payload);
    if (!res.data.success) {
      ElMessage.error(`撤单失败：${res.data.message || res.data.code}`);
      return;
    }
    ElMessage.success('撤单请求已提交，结果通过回报流推送。');
    reconnectReports();
  } catch (e: any) {
    ElMessage.error(`撤单异常：${e?.message || '网络错误'}`);
  } finally {
    submittingCancel.value = false;
  }
}

function clearReports() {
  reports.value = [];
}

function pushReport(env: OrderReportEnvelopeTyped<any>) {
  const now = new Date();
  const timeText = now.toLocaleTimeString('zh-CN', { hour12: false });

  let title = env.reportType;
  let subtitle = '';
  let color: DisplayReport['color'] = 'info';

  const d: any = env.data || {};

  switch (env.reportType) {
    case 'ORDER_CONFIRM':
      title = '订单确认';
      subtitle = `clOrderId=${d.clOrderId}, ${d.side === 'B' ? '买' : '卖'} ${d.securityId} 数量 ${d.qty} 价格 ${d.price}`;
      color = 'success';
      break;
    case 'ORDER_REJECT':
      title = '订单拒绝';
      subtitle = `clOrderId=${d.clOrderId}, 原因：${d.rejectText || d.rejectCode}`;
      color = 'danger';
      break;
    case 'ORDER_EXECUTION':
      title = '成交';
      subtitle = `clOrderId=${d.clOrderId}, execId=${d.execId}, 数量 ${d.execQty} 价格 ${d.execPrice}`;
      color = 'success';
      break;
    case 'CANCEL_CONFIRM':
      title = '撤单确认';
      subtitle = `origClOrderId=${d.origClOrderId}, 撤单数量 ${d.canceledQty}, 累计成交 ${d.cumQty}`;
      color = 'warning';
      break;
    case 'CANCEL_REJECT':
      title = '撤单拒绝';
      subtitle = `origClOrderId=${d.origClOrderId}, 原因：${d.rejectText || d.rejectCode}`;
      color = 'danger';
      break;
    default:
      subtitle = JSON.stringify(d);
      color = 'info';
  }

  const item: DisplayReport = {
    id: `${env.reportType}-${d.clOrderId || d.execId || now.getTime()}`,
    title,
    subtitle,
    color,
    timeText,
  };

  reports.value.unshift(item);
  if (reports.value.length > 200) {
    reports.value.splice(200);
  }
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
    const env = safeJsonParse<OrderReportEnvelopeTyped<any>>(evt.data);
    if (!env) return;
    pushReport(env);
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
</style>

