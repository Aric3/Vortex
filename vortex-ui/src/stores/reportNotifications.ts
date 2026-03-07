import { defineStore } from 'pinia';
import { ref } from 'vue';

const REPORT_TYPES = ['ORDER_CONFIRM', 'ORDER_REJECT', 'ORDER_EXECUTION', 'CANCEL_CONFIRM', 'CANCEL_REJECT', 'HEARTBEAT'] as const;
const MAX_NOTIFICATIONS = 50;

export type NotificationItem = {
  id: string;
  title: string;
  subtitle: string;
  color: 'success' | 'warning' | 'danger' | 'info';
  timeText: string;
  shareholderId: string;
  env: any;
};

export const useReportNotificationsStore = defineStore('reportNotifications', () => {
  const notifications = ref<NotificationItem[]>([]);

  function addReport(env: any) {
    const reportType = typeof env.reportType === 'string'
      ? env.reportType
      : REPORT_TYPES[env.reportType as number] ?? String(env.reportType);
    if (reportType === 'HEARTBEAT') return;

    const now = new Date();
    const timeText = now.toLocaleTimeString('zh-CN', { hour12: false });
    const d: any = env.data || {};
    const shareholderId = (d.shareholderId || '').trim();

    let title = reportType;
    let subtitle = '';
    let color: NotificationItem['color'] = 'info';

    switch (reportType) {
      case 'ORDER_CONFIRM':
        title = '订单确认';
        subtitle = `${d.side === 'B' ? '买' : '卖'} ${d.securityId} 数量 ${d.qty} 价格 ${d.price}`;
        color = 'success';
        break;
      case 'ORDER_REJECT': {
        const isWashTrade = Number(d.rejectCode) === 4001;
        title = '订单拒绝';
        subtitle = isWashTrade ? (d.rejectText || '对敲拒绝') : `原因：${d.rejectText || d.rejectCode}`;
        color = 'danger';
        break;
      }
      case 'ORDER_EXECUTION':
        title = '成交';
        subtitle = `数量 ${d.execQty} 价格 ${d.execPrice}`;
        color = 'success';
        break;
      case 'CANCEL_CONFIRM':
        title = '撤单确认';
        subtitle = `撤单数量 ${d.canceledQty}，累计成交 ${d.cumQty}`;
        color = 'warning';
        break;
      case 'CANCEL_REJECT':
        title = '撤单拒绝';
        subtitle = d.rejectText || String(d.rejectCode || '');
        color = 'danger';
        break;
      default:
        subtitle = typeof d === 'object' ? JSON.stringify(d) : String(d);
    }

    const id = `report-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
    notifications.value.unshift({
      id,
      title,
      subtitle,
      color,
      timeText,
      shareholderId,
      env,
    });
    if (notifications.value.length > MAX_NOTIFICATIONS) {
      notifications.value = notifications.value.slice(0, MAX_NOTIFICATIONS);
    }
  }

  function remove(id: string) {
    notifications.value = notifications.value.filter((n) => n.id !== id);
  }

  function clear() {
    notifications.value = [];
  }

  return { notifications, addReport, remove, clear };
});
