# Vortex 系统接口规范 v2.1
## 1. 客户端相关接口（/api/v1/vclient）
### 1.1 下单
- 请求方式：POST
- 接口路径： /api/v1/vclient/orders
- 请求体（request body）：
``` json
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
- 响应体（response body）：
未通过基础合法校验时，返回标准错误 Result（success=false, code=VALIDATION_ERROR。
``` json
{
    "success":false,
    "code":1999,
    "message":"clOrderId invalid",
    "data":{},
    "timestamp":"uint64"
}
```

通过基础合法校验时，表示请求已入队，返回已提交（订单确认/拒绝由异步对敲检测决定，通过 1.3 订单回报流 SSE 推送）：
``` json
{
    "success": true,
    "code": 0,
    "message": "Order submitted; confirm/reject will be sent via stream/reports.",
    "data": {
        "clOrderId": "char[16]"   // 客户端订单号，用于关联后续 SSE 回报
    },
    "timestamp": "uint64"
}
```

### 1.2 撤单
- 请求方式：POST
- 接口路径： /api/v1/vclient/orders/cancel  
- 请求体（request body）：
``` json
{
    "clOrderId": "char[16]", // 撤单请求的唯一编号 (16字节字符串)
    "origClOrderId": "char[16]", // 待撤原始订单的唯一编号 (16字节字符串)
    "market": "char[4]", // 待撤订单交易的市场 (4字节字符串) XSHG: 上交所, XSHE: 深交所, BJSE: 北交所
    "securityId": "char[6]", // 待撤订单交易的股票代码 (6字节字符串)
    "side": "char[1]", // 待撤订单买卖方向 (1字节字符串) B: 买, S: 卖
    "shareholderId": "char[10]" // 待撤订单的股东号 (10字节字符串)
}
```
- 响应体（response body）： 
``` json    
{
    "success": "bool", // 是否成功 true: 成功, false: 失败
    "code": "int32", // 状态码:[0:成功 业务错误：1000-4999 系统错误：5000-5999]
    "message": "char[64]", // 消息描述
    "data": "json", // 业务数据，根据不同接口有不同格式
    "timestamp": "uint64" // 时间戳 (8字节无符号整数)
}
```
收到撤单请求后，返回请求已提交。data为空，此时异步线程去处理撤单，然后推送回报。

### 1.3 订单回报流（SSE）
- 请求方式：GET
- 接口路径：/api/v1/vclient/stream/reports?shareholderId=
- 查询参数：shareholderId（必填，股东号）
- 响应：`Content-Type: text/event-stream`，长连接。服务端向该股东推送异步回报（JSON 封装在 `OrderReportEnvelope`：`reportType` + `data`），包括：订单确认（ORDER_CONFIRM）、订单拒绝（ORDER_REJECT，如对敲不通过）、订单成交（ORDER_EXECUTION）、撤单确认（CANCEL_CONFIRM）、撤单拒绝（CANCEL_REJECT）。客户端需先建立此连接，再下单/撤单，才能实时收到确认/拒绝与成交回报。

### 1.4 查询订单簿
- 请求方式：GET（单次查询）
- 接口路径： /api/v1/vclient/orderbook/{securityId}?depth=10
- 查询参数：securityId（股票代码）depth（查询深度）
- 响应：
   ``` json
   {
    "securityId": "600030",
    "bids": [
        {
            "price": 25.85,
            "totalQty": 1791,
            "orderCount": 2
        },
        {
            "price": 25.0,
            "totalQty": 1000,
            "orderCount": 1
        }
    ],
    "asks": [],
    "timestamp": 1772023126873
    }
   ```

- 请求方式：GET（SSE推送）
- 接口路径： /api/v1/vclient/orderbook/{securityId}/stream?depth=
- 查询参数：securityId（股票代码）depth（查询深度）
- 响应：
   ``` json
   {
    "securityId": "600030",
    "bids": [
        {
            "price": 25.85,
            "totalQty": 1791,
            "orderCount": 2
        },
        {
            "price": 25.0,
            "totalQty": 1000,
            "orderCount": 1
        }
    ],
    "asks": [],
    "timestamp": 1772023126873
    }
   ```
---

## 2. 异步回报类型
2.1 订单确认回报    
``` json    
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

2.2 订单非法回报（reportType: ORDER_REJECT，如对敲检测不通过时通过 SSE 推送）

``` json        
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
2.3 订单成交回报

``` json        
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

2.4 撤单确认回报

``` json            
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
2.5 撤单非法回报

``` json            
{
    "clOrderId": "char[16]", // 撤单请求的唯一编号 (16字节字符串)
    "origClOrderId": "char[16]", // 待撤订单的唯一编号 (16字节字符串)
    "rejectCode": "int32", // 错误代码 (4字节整数)
    "rejectText": "char[64]" // 错误原因说明 (64字节字符串)
}
```

---

## 3. Analytics 接口

### 3.1 获取实时分析指标
- 请求方式：`GET`
- 接口路径：`/api/v1/analytics/metrics`
- 说明：返回当前缓存的实时指标快照（默认每 1 秒刷新一次）。

#### 响应示例
```json
{
  "washRejects": 767,
  "totalOrders": 12181,
  "washRatio": 0.062967,
  "latencyBuckets": [
    { "bucket": "0-1ms", "count": 1567 },
    { "bucket": "2-5ms", "count": 459 },
    { "bucket": "6-10ms", "count": 95 },
    { "bucket": "11-50ms", "count": 753 },
    { "bucket": "51-100ms", "count": 55 },
    { "bucket": "101-500ms", "count": 448 },
    { "bucket": ">500ms", "count": 1955 }
  ],
  "timestamp": 1772159000123
}
```

#### 字段说明
- `washRejects`：对敲拒绝订单数（`reject_code = 4001`）。
- `totalOrders`：订单总量（`orders` 通过订单 + `order_rejects` 拒绝订单，用于口径分母）。
- `washRatio`：对敲占比（`washRejects / totalOrders`）。
- `latencyBuckets`：成交延时分桶统计（按订单首笔成交时间计算，每个订单只计一次）。
- `bucket`：延时区间（毫秒）。
- `count`：该区间内订单数量。
- `timestamp`：指标快照更新时间（Unix 毫秒时间戳）。
