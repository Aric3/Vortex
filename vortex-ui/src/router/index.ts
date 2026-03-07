import { createRouter, createWebHistory } from 'vue-router';

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      component: () => import('../views/Layout.vue'),
      redirect: '/orderbook',
      children: [
        {
          path: 'orderbook',
          name: 'OrderBook',
          component: () => import('../views/OrderBookPage.vue'),
          meta: { title: '订单簿' },
        },
        {
          path: 'trades',
          name: 'TradeDistribution',
          component: () => import('../views/TradeDistributionPage.vue'),
          meta: { title: '成交分布' },
        },
        {
          path: 'console',
          name: 'TradingConsole',
          component: () => import('../views/TradingConsolePage.vue'),
          meta: { title: '下单撤单' },
        },
        {
          path: 'analytics',
          name: 'Analytics',
          component: () => import('../views/AnalyticsPage.vue'),
          meta: { title: '风控指标' },
        },
      ],
    },
  ],
});

export default router;
