## 安装依赖

```bash
cd vortex-simulators
pip install -r requirements.txt
```

## 运行测试

### 方式一：Web 界面模式（推荐）

启动 Locust Web 界面：

```bash
locust -f load_test.py --host=http://localhost:8080
```

然后在浏览器中访问 `http://localhost:8089`，可以通过界面配置：
- 并发用户数（Number of users）
- 用户启动速率（Spawn rate）
- 运行时长

### 方式二：无界面模式（自动化测试）

直接运行压力测试：

```bash
locust -f load_test.py --host=http://localhost:8080 --users=100 --spawn-rate=10 --run-time=60s --headless
```

参数说明：
- `--host`: 目标服务器地址（根据实际情况修改）
- `--users`: 模拟的并发用户数
- `--spawn-rate`: 每秒启动的用户数
- `--run-time`: 测试运行时长（如 60s, 5m, 1h）
- `--headless`: 无界面模式


## 测试场景

脚本模拟了四种交易场景：

1. **买入订单**（权重 10）
   - 随机选择股票
   - 买入方向（side: B）
   - 价格范围：10-100 元
   - 数量范围：100-10000 股

2. **卖出订单**（权重 10）
   - 随机选择股票
   - 卖出方向（side: S）
   - 价格范围：10-100 元
   - 数量范围：100-10000 股

3. **高价订单**（权重 5）
   - 可能触发对敲撮合
   - 价格范围：50-200 元
   - 数量范围：1000-5000 股

## 订单数据格式

### 下单请求

```json
{
  "clOrderId": "1234567890ABCDEF",
  "market": "XSHG",
  "securityId": "600030",
  "side": "B",
  "qty": 1000,
  "price": 25.50,
  "shareholderId": "SH12345678"
}
```



### 撤单请求

```json
{
  "clOrderId": "ABCDEF1234567890",
  "origClOrderId": "1234567890ABCDEF",
  "market": "XSHG",
  "securityId": "600030",
  "side": "B",
  "shareholderId": "SH12345678"
}
```


```

字段说明：
- `clOrderId`: 16位订单唯一编号
- `market`: 交易市场（XSHG/XSHE/BJSE）
- `securityId`: 6位股票代码
- `side`: 买卖方向（B=买入, S=卖出）
- `qty`: 订单数量（无符号整数）
- `price`: 订单价格（浮点数）
- `shareholderId`: 10位股东号

## 预定义股票池

- **上交所（XSHG）**
  - 600030 - 中信证券
  - 600519 - 贵州茅台
  - 600036 - 招商银行

- **深交所（XSHE）**
  - 000001 - 平安银行
  - 000858 - 五粮液
  - 000002 - 万科A

- **北交所（BJSE）**
  - 872925 - 锦好医疗
  - 430047 - 诺思兰德

## 测试结果分析

测试完成后，Locust 会提供以下指标：

- **请求统计**：成功率、失败率、响应时间分布
- **RPS**：每秒请求数
- **响应时间**：平均值、中位数、95%、99% 分位数
- **并发用户数**：实际运行的虚拟用户数
```
## 常见测试场景

### 1. 基准测试（10 用户，1 分钟）
```bash
locust -f load_test.py --host=http://localhost:8080 --users=10 --spawn-rate=2 --run-time=1m --headless
```

### 2. 中等压力（100 用户，5 分钟）
```bash
locust -f load_test.py --host=http://localhost:8080 --users=100 --spawn-rate=10 --run-time=5m --headless
```

### 3. 高压力测试（500 用户，10 分钟）
```bash
locust -f load_test.py --host=http://localhost:8080 --users=500 --spawn-rate=20 --run-time=10m --headless
```

### 4. 极限压力（1000 用户，持续运行）
```bash
locust -f load_test.py --host=http://localhost:8080 --users=1000 --spawn-rate=50 --headless
```

## 注意事项

1. 确保目标服务器已启动并可访问
2. 根据服务器性能调整并发用户数
3. 监控服务器资源使用情况（CPU、内存、网络）
4. 建议从小规模测试开始，逐步增加压力
5. 测试环境应与生产环境隔离
