# Vortex 系统接口规范 v1.1
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
``` json
{
"success": "bool", // 是否成功 true: 成功, false: 失败
"code": "int32", // 状态码:[0:成功 业务错误：1000-4999 系统错误：5000-5999]
"message": "char[64]", // 消息描述
"data": "json", // 业务数据，根据不同接口有不同格式
"timestamp": "uint64" // 时间戳 (8字节无符号整数)
}
```
业务数据（data）：
- 未触发对敲撮合且通过基础合法检验，订单会被发送到交易所，等待交易所返回确认回报。此时data为空.
success: true
code: 0
message: "The order is valid and has been forwarded to the exchange."
data: {}
timestamp: current timestamp in milliseconds

- 触发对敲撮合时，返回撮合结果。且系统自动向交易所发起撤单请求，将待撤单请求的信息返回给客户端。
``` json
{
    "buyOrderId": "char[16]", // 买订单号 (16字节字符串)
    "sellOrderId": "char[16]", // 卖订单号 (16字节字符串)
    "matchPrice": "double", // 撮合价格 (8字节浮点数)
    "matchQty": "uint32", // 撮合成交数量 (4字节无符号整数)
    "cancellationOrderId": "char[16]", // 撤单请求的唯一编号 (16字节字符串)
    "origClOrderId": "char[16]", // 待撤原始订单的唯一编号 (16字节字符串)(买订单号或卖订单号)
    "market": "char[4]", // 待撤订单交易的市场 (4字节字符串) XSHG: 上交所, XSHE: 深交所, BJSE: 北交所
    "securityId": "char[6]", // 待撤订单交易的股票代码 (6字节字符串)
    "side": "char[1]", // 待撤订单买卖方向 (1字节字符串) B: 买, S: 卖
    "shareholderId": "char[10]", // 待撤订单的股东号 (10字节字符串) 与下单时的股东号一致
    "cancellationQty": "uint32" // 待撤回数量 (4字节无符号整数) 等于下单时的数量减去已撮合数量
}
```

- 下单失败时，返回错误码和错误消息。data为空。

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
收到撤单请求后，只返回处理这个撤单请求的情况。data为空，因为此时还未真正将撤单提交到交易所，未收到交易所的确认。
---

## 2. 交易所相关接口（/api/v1/vexchange）
### 2.1 订单确认回报    
- 请求方式：POST
- 接口路径：/api/v1/vexchange/ack/order-accept
- 请求体（request body）：
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

- 响应 (Response Body):
``` json    
{
    "success": "bool", // 是否成功 true: 成功, false: 失败
    "code": "int32", // 状态码:[0:成功 业务错误：1000-4999 系统错误：5000-5999]
    "message": "char[64]", // 消息描述
    "data": "json", // 业务数据，根据不同接口有不同格式
    "timestamp": "uint64" // 时间戳 (8字节无符号整数)
}
```
### 2.2 订单非法回报
- 请求方式：POST
- 接口路径：/api/v1/vexchange/ack/order-reject
- 请求体（request body）：
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
- 响应 (Response Body):
``` json    
{
    "success": "bool", // 是否成功 true: 成功, false: 失败
    "code": "int32", // 状态码:[0:成功 业务错误：1000-4999 系统错误：5000-5999]
    "message": "char[64]", // 消息描述
    "data": "json", // 业务数据，根据不同接口有不同格式
    "timestamp": "uint64" // 时间戳 (8字节无符号整数)
}
```
### 2.3 订单成交回报
- 请求方式：POST
- 接口路径：/api/v1/vexchange/ack/order-deal
- 请求体（request body）：
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
- 响应 (Response Body):
``` json    
{
    "success": "bool", // 是否成功 true: 成功, false: 失败
    "code": "int32", // 状态码:[0:成功 业务错误：1000-4999 系统错误：5000-5999]
    "message": "char[64]", // 消息描述
    "data": "json", // 业务数据，根据不同接口有不同格式
    "timestamp": "uint64" // 时间戳 (8字节无符号整数)
}
```
### 2.4 撤单确认回报
- 请求方式：POST
- 接口路径：/api/v1/vexchange/ack/cancellation-accept
- 请求体（request body）：
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
- 响应 (Response Body):
``` json    
{
    "success": "bool", // 是否成功 true: 成功, false: 失败
    "code": "int32", // 状态码:[0:成功 业务错误：1000-4999 系统错误：5000-5999]
    "message": "char[64]", // 消息描述
    "data": "json", // 业务数据，根据不同接口有不同格式
    "timestamp": "uint64" // 时间戳 (8字节无符号整数)
}
```
### 2.5 撤单非法回报
- 请求方式：POST
- 接口路径：/api/v1/vexchange/ack/cancellation-reject
- 请求体（request body）：
``` json            
{
    "clOrderId": "char[16]", // 撤单请求的唯一编号 (16字节字符串)
    "origClOrderId": "char[16]", // 待撤订单的唯一编号 (16字节字符串)
    "rejectCode": "int32", // 错误代码 (4字节整数)
    "rejectText": "char[64]" // 错误原因说明 (64字节字符串)
}
```
- 响应 (Response Body):
``` json    
{
    "success": "bool", // 是否成功 true: 成功, false: 失败
    "code": "int32", // 状态码:[0:成功 业务错误：1000-4999 系统错误：5000-5999]
    "message": "char[64]", // 消息描述
    "data": "json", // 业务数据，根据不同接口有不同格式
    "timestamp": "uint64" // 时间戳 (8字节无符号整数)
}
