# Vortex 系统接口规范 v1.0

## 1. 下单接口 [POST] /api/v1/orders
**输入 (JSON):**
```json
{
    "clOrderId": "char[16]", # 客户端订单号 (16字节字符串)
    "market": "char[4]", # 订单交易的市场 (4字节字符串) XSHG: 上交所 XSHE: 深交所 BJSE: 北交所
    "securityId": "char[6]", # 订单交易的股票代码 (6字节字符串)
    "side": "char[1]", # 订单买卖方向 (1字节字符串) B: 买 S: 卖
    "qty": "uint32", # 订单数量 (4字节无符号整数)
    "price": "double", # 订单价格 (8字节浮点数)
    "shareholderId": "char[10]", # 股东号 (10字节字符串)    
}
```