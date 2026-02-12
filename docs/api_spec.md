# Vortex 系统接口规范 v1.0

## 1. 下单接口 [POST] /api/v1/orders
模拟交易转发逻辑，接收客户端下单请求。

**请求 (Request Body):**
```json
{
    "clOrderId": "char[16]", // 客户端订单号 (16字节字符串)
    "market": "char[4]", // 订单交易的市场 (4字节字符串) XSHG: 上交所, XSHE: 深交所, BJSE: 北交所
    "securityId": "char[6]", // 订单交易的股票代码 (6字节字符串)
    "side": "char[1]", // 订单买卖方向 (1字节字符串) B: 买, S: 卖
    "qty": "uint32", // 订单数量 (4字节无符号整数)
    "price": "double", // 订单价格 (8字节浮点数)
    "shareholderId": "char[10]" // 股东号 (10字节字符串)
}
```

**响应 (Response Body):**
```json
{
    "status": "int32", // 状态码: 0-成功接收, 1-基础校验失败
    "message": "char[64]" // 状态描述
}
```

---

## 2. 撤单接口 [POST] /api/v1/orders/cancel
处理输入的撤单请求。

**请求 (Request Body):**
```json
{
    "clOrderId": "char[16]", // 撤单请求的唯一编号 (16字节字符串)
    "origClOrderId": "char[16]", // 待撤原始订单的唯一编号 (16字节字符串)
    "market": "char[4]", // 待撤订单交易的市场 (4字节字符串) XSHG: 上交所, XSHE: 深交所, BJSE: 北交所
    "securityId": "char[6]", // 待撤订单交易的股票代码 (6字节字符串)
    "side": "char[1]", // 待撤订单买卖方向 (1字节字符串) B: 买, S: 卖
    "shareholderId": "char[10]" // 待撤订单的股东号 (10字节字符串)
}
```

**响应 (Response Body):**
```json
{
    "status": "int32", // 状态码: 0-成功接收, 1-基础校验失败
    "message": "char[64]" // 状态描述
}
```

---

## 3. 行情信息接口 [POST] /api/v1/market-data
读取解析输入的行情信息。

**请求 (Request Body):**
```json
[
    {
        "market": "char[4]", // 行情对应的市场 (4字节字符串) XSHG: 上交所, XSHE: 深交所, BJSE: 北交所
        "securityId": "char[6]", // 行情对应的股票代码 (6字节字符串)
        "bidPrice": "double", // 行情对应的最新买价 (8字节浮点数)
        "askPrice": "double" // 行情对应的最新卖价 (8字节浮点数)
    }
]
```

**响应 (Response Body):**
```json
{
    "status": "int32", // 状态码: 0-成功接收
    "count": "uint32" // 接收到的行情条数
}
```

---

## 4. 交易所回报回调 (Execution Reports Callbacks)
系统异步推送给客户端的回报信息。客户端需在接收到推送后返回成功确认。

### 4.1 订单确认回报 [POST] /api/v1/callback/order-accept
**请求 (Request Body):**
```json
{
    "clOrderId": "char[16]", // 订单的唯一编号 (16字节字符串)
    "market": "char[4]", // 订单交易的市场 (4字节字符串) XSHG: 上交所, XSHE: 深交所, BJSE: 北交所
    "securityId": "char[6]", // 订单交易的股票代码 (6字节字符串)
    "side": "char[1]", // 订单买卖方向 (1字节字符串) B: 买, S: 卖
    "qty": "uint32", // 订单数量 (4字节无符号整数)
    "price": "double", // 订单价格 (8字节浮点数)
    "shareholderId": "char[10]" // 股东号 (10字节字符串)
}
```

**响应 (Response Body):**
```json
{
    "status": "int32" // 客户端返回: 0-成功处理推送
}
```

### 4.2 订单非法回报 [POST] /api/v1/callback/order-reject
**请求 (Request Body):**
```json
{
    "clOrderId": "char[16]", // 订单的唯一编号 (16字节字符串)
    "market": "char[4]", // 订单交易的市场 (4字节字符串) XSHG: 上交所, XSHE: 深交所, BJSE: 北交所
    "securityId": "char[6]", // 订单交易的股票代码 (6字节字符串)
    "side": "char[1]", // 订单买卖方向 (1字节字符串) B: 买, S: 卖
    "qty": "uint32", // 订单数量 (4字节无符号整数)
    "price": "double", // 订单价格 (8字节浮点数)
    "shareholderId": "char[10]", // 股东号 (10字节字符串)
    "rejectCode": "int32", // 错误代码 (4字节整数)
    "rejectText": "char[64]" // 错误原因说明 (64字节字符串)
}
```

**响应 (Response Body):**
```json
{
    "status": "int32" // 客户端返回: 0-成功处理推送
}
```

### 4.3 订单成交回报 [POST] /api/v1/callback/execution
**请求 (Request Body):**
```json
{
    "clOrderId": "char[16]", // 订单的唯一编号 (16字节字符串)
    "market": "char[4]", // 订单交易的市场 (4字节字符串) XSHG: 上交所, XSHE: 深交所, BJSE: 北交所
    "securityId": "char[6]", // 订单交易的股票代码 (6字节字符串)
    "side": "char[1]", // 订单买卖方向 (1字节字符串) B: 买, S: 卖
    "qty": "uint32", // 订单原始数量 (4字节无符号整数)
    "price": "double", // 订单原始价格 (8字节浮点数)
    "shareholderId": "char[10]", // 股东号 (10字节字符串)
    "execId": "char[12]", // 成交的唯一编号 (12字节字符串)
    "execQty": "uint32", // 本次成交数量 (4字节无符号整数)
    "execPrice": "double" // 本次成交价格 (8字节浮点数)
}
```

**响应 (Response Body):**
```json
{
    "status": "int32" // 客户端返回: 0-成功处理推送
}
```

### 4.4 撤单确认回报 [POST] /api/v1/callback/cancel-accept
**请求 (Request Body):**
```json
{
    "clOrderId": "char[16]", // 撤单请求的唯一编号 (16字节字符串)
    "origClOrderId": "char[16]", // 已撤原始订单的唯一编号 (16字节字符串)
    "market": "char[4]", // 已撤订单交易的市场 (4字节字符串) XSHG: 上交所, XSHE: 深交所, BJSE: 北交所
    "securityId": "char[6]", // 已撤订单交易的股票代码 (6字节字符串)
    "side": "char[1]", // 已撤订单买卖方向 (1字节字符串) B: 买, S: 卖
    "shareholderId": "char[10]", // 已撤订单的股东号 (10字节字符串)
    "qty": "uint32", // 原始订单数量 (4字节无符号整数)
    "price": "double", // 原始订单价格 (8字节浮点数)
    "cumQty": "uint32", // 累计成交数量 (4字节无符号整数)
    "canceledQty": "uint32" // 本次撤单成功的数量 (4字节无符号整数)
}
```

**响应 (Response Body):**
```json
{
    "status": "int32" // 客户端返回: 0-成功处理推送
}
```

### 4.5 撤单非法回报 [POST] /api/v1/callback/cancel-reject
**请求 (Request Body):**
```json
{
    "clOrderId": "char[16]", // 撤单请求的唯一编号 (16字节字符串)
    "origClOrderId": "char[16]", // 待撤订单的唯一编号 (16字节字符串)
    "rejectCode": "int32", // 错误代码 (4字节整数)
    "rejectText": "char[64]" // 错误原因说明 (64字节字符串)
}
```

**响应 (Response Body):**
```json
{
    "status": "int32" // 客户端返回: 0-成功处理推送
}
```
