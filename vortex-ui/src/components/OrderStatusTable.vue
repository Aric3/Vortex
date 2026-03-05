<template>
  <div class="card">
    <div class="head">
      <div>
        <div class="title">订单状态</div>
        <div class="sub">
          股东号 {{ shareholderId || '（未填写）' }} · 股票 {{ securityId || '（未填写）' }}
        </div>
      </div>
      <div class="actions">
        <el-button size="small" @click="refresh" :loading="loading">刷新</el-button>
      </div>
    </div>

    <el-alert
      v-if="!shareholderId || shareholderId.trim().length !== 10"
      title="提示：股东号必须为 10 位字符串（例如 A000000001），否则无法保证下单/查询符合 API 标准。"
      type="warning"
      show-icon
      class="mb"
    />

    <el-table
      :data="rows"
      size="small"
      stripe
      border
      height="320"
      v-loading="loading"
      element-loading-text="加载订单中..."
      empty-text="暂无订单（确认下单成功且未被拒绝后，会在这里出现）"
    >
      <el-table-column prop="updatedAtText" label="更新时间" width="100" />
      <el-table-column prop="clOrderId" label="clOrderId" width="170" />
      <el-table-column prop="sideText" label="方向" width="70" />
      <el-table-column prop="qty" label="数量" width="90" />
      <el-table-column prop="price" label="价格" width="90" />
      <el-table-column prop="statusText" label="状态" min-width="160" />
    </el-table>

    <div class="foot">
      <span class="hint">说明：后端禁止改动时，前端通过接口拉取订单并做过滤；同时会把刚提交的订单先以本地“SUBMITTED”状态展示，等回报到达/落库后自动刷新。</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { ElMessage } from "element-plus";
import { http, type ApiResult } from "../services/http";

type AnyOrder = Record<string, any>;

const props = defineProps<{
  shareholderId: string;
  securityId: string;
  refreshKey: number;
  localOrders: AnyOrder[];
}>();

const loading = ref(false);
const dbOrders = ref<AnyOrder[]>([]);

function isMatch(o: AnyOrder): boolean {
  const sh = (props.shareholderId || "").trim();
  const sec = (props.securityId || "").trim();
  if (sh && (o.shareholderId || "").trim() !== sh) return false;
  if (sec && (o.securityId || "").trim() !== sec) return false;
  return true;
}

function normalizeStatus(raw: string | undefined): string {
  if (!raw) return "UNKNOWN";
  return String(raw).toUpperCase();
}

function statusToText(s: string): string {
  switch (s) {
    case "SUBMITTED":
      return "已提交（等待确认/落库）";
    case "CONFIRMED":
      return "已确认（已入簿）";
    case "REJECTED":
      return "已拒绝（非法回报）";
    case "EXECUTED":
      return "已成交";
    case "CANCELED":
      return "已撤单";
    case "CANCEL_REJECTED":
      return "撤单被拒绝";
    case "IN_BOOK":
      return "在簿";
    default:
      return s;
  }
}

function sideToText(side: string | undefined): string {
  return side === "B" ? "买" : side === "S" ? "卖" : "";
}

function timeText(ts: number | undefined): string {
  if (!ts) return "";
  try {
    return new Date(ts).toLocaleTimeString("zh-CN", { hour12: false });
  } catch {
    return "";
  }
}

async function refresh() {
  const sh = (props.shareholderId || "").trim();
  if (!sh) return; // 没填就不拉取
  loading.value = true;
  try {
    const res = await http.get<ApiResult<any>>("/v1/vclient/orders");
    if (!res.data?.success) {
      ElMessage.error(`查询订单失败：${res.data?.message || res.data?.code}`);
      return;
    }
    const arr = Array.isArray(res.data?.data) ? res.data.data : (res.data?.data?.items || []);
    dbOrders.value = (Array.isArray(arr) ? arr : []).filter(isMatch);
  } catch (e: any) {
    ElMessage.error(`查询订单异常：${e?.message || "网络错误"}`);
  } finally {
    loading.value = false;
  }
}

const rows = computed(() => {
  const locals = (props.localOrders || []).filter(isMatch).map((o) => ({
    ...o,
    _source: "local",
    _status: normalizeStatus(o.status),
    _updatedAt: o.updatedAt || Date.now(),
  }));

  const dbs = (dbOrders.value || []).filter(isMatch).map((o) => ({
    ...o,
    _source: "db",
    _status: normalizeStatus(o.status || "IN_BOOK"),
    _updatedAt: o.updatedAt || o.createTime || o.createdAt || Date.now(),
  }));

  // merge by clOrderId (db wins)
  const map = new Map<string, AnyOrder>();
  for (const o of locals) {
    if (o.clOrderId) map.set(String(o.clOrderId), o);
  }
  for (const o of dbs) {
    if (o.clOrderId) map.set(String(o.clOrderId), o);
  }

  const merged = Array.from(map.values())
    .sort((a, b) => (b._updatedAt || 0) - (a._updatedAt || 0))
    .map((o) => ({
      ...o,
      sideText: sideToText(o.side),
      statusText: statusToText(o._status),
      updatedAtText: timeText(o._updatedAt),
    }));

  return merged;
});

watch(
  () => [props.shareholderId, props.securityId, props.refreshKey],
  () => {
    refresh();
  },
  { immediate: true }
);

onMounted(() => {
  refresh();
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
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 8px;
}
.title {
  font-weight: 700;
}
.sub {
  color: #6b7280;
  margin-top: 4px;
  font-size: 12px;
}
.actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.mb { margin-bottom: 10px; }
.foot { margin-top: 8px; }
.hint { color: #6b7280; font-size: 12px; }
</style>
