import { defineStore } from 'pinia';
import { computed, ref } from 'vue';

export type DisplayReport = {
  id: string;
  title: string;
  subtitle: string;
  color: 'success' | 'warning' | 'danger' | 'info';
  timeText: string;
};

export type OrderRow = {
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

const MAX_REPORTS = 200;

export const useOrderReportsStore = defineStore('orderReports', () => {
  const reports = ref<DisplayReport[]>([]);
  const orderMap = ref<Record<string, OrderRow>>({});

  const orderList = computed(() =>
    Object.values(orderMap.value).sort((a, b) =>
      b.lastUpdate > a.lastUpdate ? 1 : -1
    )
  );

  function addReport(item: DisplayReport) {
    reports.value.unshift(item);
    if (reports.value.length > MAX_REPORTS) {
      reports.value.splice(MAX_REPORTS);
    }
  }

  function upsertOrder(clOrderId: string, patch: Partial<OrderRow>) {
    const timeText = new Date().toLocaleTimeString('zh-CN', { hour12: false });
    const map = orderMap.value;
    let row = map[clOrderId];
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
      map[clOrderId] = row;
    }
    Object.assign(row, patch, { lastUpdate: timeText });
    orderMap.value = { ...map };
  }

  function clearReports() {
    reports.value = [];
  }

  function clearOrders() {
    orderMap.value = {};
  }

  function getOrder(clOrderId: string): OrderRow | undefined {
    return orderMap.value[clOrderId];
  }

  return {
    reports,
    orderMap,
    orderList,
    addReport,
    upsertOrder,
    clearReports,
    clearOrders,
    getOrder,
  };
});
