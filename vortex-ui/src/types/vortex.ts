export type PriceLevel = {
  price: number;
  // backend uses totalQty; we also accept qty for compatibility
  totalQty?: number;
  qty?: number;
  [k: string]: any;
};

export type OrderBookSnapshot = {
  securityId?: string;
  market?: string;
  bids?: PriceLevel[];
  asks?: PriceLevel[];

  // tolerate alternative naming
  bidLevels?: PriceLevel[];
  askLevels?: PriceLevel[];
  buy?: PriceLevel[];
  sell?: PriceLevel[];

  [k: string]: any;
};

export type OrderReportEnvelope = {
  reportType: string;
  data: any;
  [k: string]: any;
};

export type ExecutionReport = {
  execId?: string;
  execQty: number;
  execPrice: number;
  securityId?: string;
  market?: string;
  clOrderId?: string;
  shareholderId?: string;
  ts?: number | string;
  [k: string]: any;
};

export type OrderRequest = {
  clOrderId: string;
  market: string;
  securityId: string;
  side: 'B' | 'S';
  qty: number;
  price: number;
  shareholderId: string;
};

export type CancelOrderRequest = {
  clOrderId: string;
  origClOrderId: string;
  market: string;
  securityId: string;
  side: 'B' | 'S';
  shareholderId: string;
};

export type OrderReportType =
  | 'ORDER_CONFIRM'
  | 'ORDER_REJECT'
  | 'ORDER_EXECUTION'
  | 'CANCEL_CONFIRM'
  | 'CANCEL_REJECT';

export type OrderReportEnvelopeTyped<T = any> = {
  reportType: OrderReportType;
  data: T;
  [k: string]: any;
};

export type AnalyticsLatencyBucket = {
  bucket: string;
  count: number;
};

export type AnalyticsMetrics = {
  washRejects: number;
  totalOrders: number;
  washRatio: number;
  latencyBuckets: AnalyticsLatencyBucket[];
  timestamp: number;
};

