"""
Vortex 交易系统负载测试脚本
使用 Locust 模拟高并发下单流量，验证系统稳定性
参数说明
--host: 目标服务器地址
--users: 模拟的并发用户数
--spawn-rate: 每秒启动的用户数
--run-time: 测试运行时长
"""

import json
import random
import string
import time
from locust import HttpUser, task, between, events


class TradingUser(HttpUser):
    """模拟交易用户行为"""
    
    # 模拟用户在两次操作之间的等待时间（秒）
    wait_time = between(0.1, 0.5)
    
    # 预定义的股票池
    STOCK_POOL = [
        {"market": "XSHG", "securityId": "600030"},  # 中信证券
        {"market": "XSHG", "securityId": "600519"},  # 贵州茅台
        {"market": "XSHG", "securityId": "600036"},  # 招商银行
        {"market": "XSHE", "securityId": "000001"},  # 平安银行
        {"market": "XSHE", "securityId": "000858"},  # 五粮液
        {"market": "XSHE", "securityId": "000002"},  # 万科A
        {"market": "BJSE", "securityId": "872925"},  # 锦好医疗
        {"market": "BJSE", "securityId": "430047"},  # 诺思兰德
    ]
    
    # 预定义的股东号池（模拟不同的投资者）
    SHAREHOLDER_POOL = [
        "SH" + ''.join(random.choices(string.digits, k=8)) for _ in range(20)
    ]

    def on_start(self):
        """每个模拟用户启动时执行一次"""
        # 为每个用户分配一个固定的股东号
        self.shareholder_id = random.choice(self.SHAREHOLDER_POOL)
        print(f"用户启动，股东号: {self.shareholder_id}")

    def generate_order_id(self):
        """生成16位订单编号"""
        timestamp = str(int(time.time() * 1000))[-10:]  # 取时间戳后10位
        random_part = ''.join(random.choices(string.ascii_uppercase + string.digits, k=6))
        return timestamp + random_part

    def generate_shareholder_id(self):
        """生成10位股东号"""
        return ''.join(random.choices(string.ascii_uppercase + string.digits, k=10))

    @task(10)
    def place_buy_order(self):
        """模拟买入订单（权重10）"""
        stock = random.choice(self.STOCK_POOL)
        
        order_data = {
            "clOrderId": self.generate_order_id(),
            "market": stock["market"],
            "securityId": stock["securityId"],
            "side": "B",  # 买入
            "qty": random.randint(1, 100) * 100,  # 100-10000股，以100为单位
            "price": round(random.uniform(10.0, 100.0), 2),
            "shareholderId": self.shareholder_id
        }
        
        self._send_order(order_data, "买入")

    @task(10)
    def place_sell_order(self):
        """模拟卖出订单（权重10）"""
        stock = random.choice(self.STOCK_POOL)
        
        order_data = {
            "clOrderId": self.generate_order_id(),
            "market": stock["market"],
            "securityId": stock["securityId"],
            "side": "S",  # 卖出
            "qty": random.randint(1, 100) * 100,
            "price": round(random.uniform(10.0, 100.0), 2),
            "shareholderId": self.shareholder_id
        }
        
        self._send_order(order_data, "卖出")

    @task(5)
    def place_high_price_order(self):
        """模拟高价订单（权重5，可能触发对敲）"""
        stock = random.choice(self.STOCK_POOL)
        
        order_data = {
            "clOrderId": self.generate_order_id(),
            "market": stock["market"],
            "securityId": stock["securityId"],
            "side": random.choice(["B", "S"]),
            "qty": random.randint(10, 50) * 100,
            "price": round(random.uniform(50.0, 200.0), 2),
            "shareholderId": self.shareholder_id
        }
        
        self._send_order(order_data, "高价")

    def _send_order(self, order_data, order_type):
        """发送订单的通用方法"""
        headers = {'Content-Type': 'application/json'}
        
        with self.client.post(
            "/api/v1/vclient/orders",
            data=json.dumps(order_data),
            headers=headers,
            catch_response=True,
            name=f"下单-{order_type}"
        ) as response:
            
            if response.status_code == 200:
                try:
                    resp_json = response.json()
                    
                    if resp_json.get("success") and resp_json.get("code") == 0:
                        # 检查是否触发对敲撮合
                        if resp_json.get("data") and "matchPrice" in resp_json.get("data", {}):
                            match_info = resp_json["data"]
                            response.success()
                            print(f"✓ 触发对敲撮合 - 价格: {match_info.get('matchPrice')}, "
                                  f"数量: {match_info.get('matchQty')}")
                        else:
                            response.success()
                            print(f"✓ 订单已发送到交易所 - {order_type} "
                                  f"{order_data['securityId']} "
                                  f"@{order_data['price']} x{order_data['qty']}")
                    else:
                        response.failure(f"业务错误 [{resp_json.get('code')}]: {resp_json.get('message')}")
                        print(f"✗ 下单失败: {resp_json.get('message')}")
                        
                except json.JSONDecodeError:
                    response.failure("响应不是有效的 JSON 格式")
                    print(f"✗ JSON 解析失败: {response.text[:100]}")
            else:
                response.failure(f"HTTP {response.status_code}")
                print(f"✗ HTTP 错误 {response.status_code}: {response.text[:100]}")


@events.test_start.add_listener
def on_test_start(environment, **kwargs):
    """测试开始时的回调"""
    print("=" * 60)
    print("Vortex 交易系统负载测试开始")
    print(f"目标地址: {environment.host}")
    print("=" * 60)


@events.test_stop.add_listener
def on_test_stop(environment, **kwargs):
    """测试结束时的回调"""
    print("=" * 60)
    print("负载测试完成")
    print("=" * 60)