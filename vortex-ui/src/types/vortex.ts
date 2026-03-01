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
