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
import uuid
import requests
import gevent
from locust import HttpUser, task, between, events
from flask import request

# 全局行情缓存，定期刷新
PRICE_CACHE = {}


class TradingUser(HttpUser):
    """模拟交易用户行为"""
    
    # 模拟用户在两次操作之间的等待时间（秒）
    wait_time = between(0.1, 0.5)
    
    # 预定义的股票池
    STOCK_POOL = [
        {"market": "XSHG", "securityId": "600030", "name": "中信证券"},  # 600030.SH
        {"market": "XSHG", "securityId": "600519", "name": "贵州茅台"},  # 600519.SH
        {"market": "XSHG", "securityId": "600036", "name": "招商银行"},  # 600036.SH
        {"market": "XSHE", "securityId": "000001", "name": "平安银行"},  # 000001.SZ
        {"market": "XSHE", "securityId": "000858", "name": "五粮液"},    # 000858.SZ
    ]
    
    # 预定义的股东号池（模拟不同的投资者）
    SHAREHOLDER_POOL = [
        "SH" + ''.join(random.choices(string.digits, k=8)) for _ in range(20)
    ]

    def on_start(self):
        """每个模拟用户启动时执行一次"""
        # 为每个用户分配一个固定的股东号
        self.shareholder_id = random.choice(self.SHAREHOLDER_POOL)
        # 存储已发送的订单信息，用于后续撤单
        self.pending_orders = []
        print(f"用户启动，股东号: {self.shareholder_id}")

    def generate_order_id(self):
        """生成全局唯一的 16 位订单编号（满足服务端 clOrderId 长度约束，高并发下不碰撞）"""
        return uuid.uuid4().hex[:16]

    def generate_shareholder_id(self):
        """生成10位股东号"""
        return ''.join(random.choices(string.ascii_uppercase + string.digits, k=10))

    def _get_market_price(self, security_id):
        """从全局缓存获取最新的股票行情，如果尚未缓存则返回兜底价格"""
        return PRICE_CACHE.get(security_id, 50.0)

    @task(10)
    def place_buy_order(self):
        """模拟买入订单（权重10）"""
        stock = random.choice(self.STOCK_POOL)
        current_price = self._get_market_price(stock["securityId"])
        
        # 正常订单：在市价 ±2% 范围内波动
        price_fluctuation = random.uniform(-0.02, 0.02)
        simulated_price = round(current_price * (1 + price_fluctuation), 2)
        
        order_data = {
            "clOrderId": self.generate_order_id(),
            "market": stock["market"],
            "securityId": stock["securityId"],
            "side": "B",  # 买入
            "qty": random.randint(1, 100) * 100,  # 100-10000股，以100为单位
            "price": simulated_price,
            "shareholderId": self.shareholder_id
        }
        
        self._send_order(order_data, "买入", stock)

    @task(10)
    def place_sell_order(self):
        """模拟卖出订单（权重10）"""
        stock = random.choice(self.STOCK_POOL)
        current_price = self._get_market_price(stock["securityId"])
        
        # 正常订单：在市价 ±2% 范围内波动
        price_fluctuation = random.uniform(-0.02, 0.02)
        simulated_price = round(current_price * (1 + price_fluctuation), 2)
        
        order_data = {
            "clOrderId": self.generate_order_id(),
            "market": stock["market"],
            "securityId": stock["securityId"],
            "side": "S",  # 卖出
            "qty": random.randint(1, 100) * 100,
            "price": simulated_price,
            "shareholderId": self.shareholder_id
        }
        
        self._send_order(order_data, "卖出", stock)

    @task(5)
    def place_high_price_order(self):
        """模拟高价订单（权重5，可能触发对敲）"""
        stock = random.choice(self.STOCK_POOL)
        current_price = self._get_market_price(stock["securityId"])
        side = random.choice(["B", "S"])
        
        # 高价订单：偏离市价 1-5%，可能触发对敲
        deviation = random.uniform(0.01, 0.05)
        if side == "B":
            # 买单出高价（高于市价1-5%），容易立刻和卖盘撮合
            simulated_price = round(current_price * (1 + deviation), 2)
        else:
            # 卖单出低价（低于市价1-5%），容易立刻和买盘撮合
            simulated_price = round(current_price * (1 - deviation), 2)
        
        order_data = {
            "clOrderId": self.generate_order_id(),
            "market": stock["market"],
            "securityId": stock["securityId"],
            "side": side,
            "qty": random.randint(10, 50) * 100,
            "price": simulated_price,
            "shareholderId": self.shareholder_id
        }
        
        self._send_order(order_data, "高价", stock)

    def _send_order(self, order_data, order_type, stock):
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
                            # 订单成功发送到交易所，保存订单信息用于后续撤单
                            self.pending_orders.append({
                                "clOrderId": order_data["clOrderId"],
                                "market": order_data["market"],
                                "securityId": order_data["securityId"],
                                "side": order_data["side"],
                                "shareholderId": order_data["shareholderId"]
                            })
                            # 限制列表大小，避免内存占用过大
                            if len(self.pending_orders) > 50:
                                self.pending_orders.pop(0)
                            
                            response.success()
                            print(f"✓ 订单已发送到交易所 - {order_type} "
                                  f"{order_data['securityId']} ({stock.get('name', '')}) "
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

    @task(3)
    def cancel_order(self):
        """模拟撤单请求（权重3）"""
        # 检查是否有可撤销的订单
        if not self.pending_orders:
            # 如果没有待撤订单，跳过本次撤单
            return
        
        # 随机选择一个待撤订单
        original_order = random.choice(self.pending_orders)
        
        # 构造撤单请求
        cancel_data = {
            "clOrderId": self.generate_order_id(),  # 撤单请求的唯一编号
            "origClOrderId": original_order["clOrderId"],  # 待撤原始订单编号
            "market": original_order["market"],
            "securityId": original_order["securityId"],
            "side": original_order["side"],
            "shareholderId": original_order["shareholderId"]
        }
        
        headers = {'Content-Type': 'application/json'}
        
        with self.client.post(
            "/api/v1/vclient/orders/cancel",
            data=json.dumps(cancel_data),
            headers=headers,
            catch_response=True,
            name="撤单"
        ) as response:
            
            if response.status_code == 200:
                try:
                    resp_json = response.json()
                    
                    if resp_json.get("success") and resp_json.get("code") == 0:
                        # 撤单请求成功，从待撤列表中移除
                        self.pending_orders.remove(original_order)
                        response.success()
                        print(f"✓ 撤单请求已发送 - 原订单: {original_order['clOrderId'][:8]}... "
                              f"{original_order['securityId']}")
                    else:
                        response.failure(f"业务错误 [{resp_json.get('code')}]: {resp_json.get('message')}")
                        print(f"✗ 撤单失败: {resp_json.get('message')}")
                        
                except json.JSONDecodeError:
                    response.failure("响应不是有效的 JSON 格式")
                    print(f"✗ JSON 解析失败: {response.text[:100]}")
            else:
                response.failure(f"HTTP {response.status_code}")
                print(f"✗ HTTP 错误 {response.status_code}: {response.text[:100]}")


@events.init.add_listener
def on_locust_init(environment, **_kwargs):
    """初始化时设置中文界面"""
    
    # 设置页面标题
    environment.web_ui.app.config['LOCUST_UI_TITLE'] = 'Vortex 交易系统压力测试'
    
    # 注入中文翻译脚本
    @environment.web_ui.app.after_request
    def inject_chinese_ui(response):
        """注入中文翻译脚本到页面"""
        if request.path == '/' or 'tab=' in request.url or '/stats/' in request.url:
            if response.content_type and response.content_type.startswith('text/html'):
                html = response.get_data(as_text=True)
                
                # JavaScript 翻译脚本
                inject_script = '''
<script>
// 中文翻译映射
const translations = {
    // 顶部导航 - 大写
    "STATISTICS": "统计数据",
    "CHARTS": "图表",
    "FAILURES": "失败记录",
    "EXCEPTIONS": "异常",
    "CURRENT RATIO": "当前比例",
    "DOWNLOAD DATA": "下载数据",
    "LOGS": "日志",
    
    // 顶部导航 - 首字母大写
    "Statistics": "统计数据",
    "Charts": "图表",
    "Failures": "失败记录",
    "Exceptions": "异常",
    "Current Ratio": "当前比例",
    "Download Data": "下载数据",
    "Logs": "日志",
    
    // 表头 - 完整匹配
    "Type": "类型",
    "Name": "名称",
    "# Requests": "请求数",
    "# Fails": "失败数",
    "Median": "中位数",
    "Median (ms)": "中位数(ms)",
    "95%ile": "95分位",
    "95%ile (ms)": "95分位(ms)",
    "99%ile": "99分位",
    "99%ile (ms)": "99分位(ms)",
    "Average": "平均值",
    "Average (ms)": "平均值(ms)",
    "Min": "最小值",
    "Min (ms)": "最小值(ms)",
    "Max": "最大值",
    "Max (ms)": "最大值(ms)",
    "Current RPS": "当前RPS",
    "Current Failures/s": "当前失败/秒",
    "Requests": "请求",
    "Fails": "失败",
    
    // 按钮
    "NEW": "开始",
    "RESET": "重置",
    "STOP": "停止",
    "EDIT": "编辑",
    "LOADING": "加载中",
    "Start": "开始",
    "Stop": "停止",
    "Reset": "重置",
    
    // 状态
    "STOPPED": "已停止",
    "RUNNING": "运行中",
    "SPAWNING": "启动中",
    "STOPPING": "停止中",
    "Stopped": "已停止",
    "Running": "运行中",
    "Spawning": "启动中",
    "Stopping": "停止中",
    
    // 其他
    "Host": "目标主机",
    "Status": "状态",
    "Users": "用户数",
    "RPS": "每秒请求",
    "Failures": "失败",
    "Aggregated": "汇总",
    "POST": "提交",
    "GET": "获取",
    "Total": "总计",
    "Method": "方法",
    "Error": "错误",
    "Occurrences": "出现次数",
    
    // Failures 页面
    "# failures": "失败次数",
    
    // Charts 页面
    "Total Requests per Second": "每秒总请求数",
    "Response Times (ms)": "响应时间(ms)",
    "Number of Users": "用户数量",
    
    // 启动表单
    "Number of users": "用户数量",
    "Spawn rate": "启动速率",
    "Start swarming": "开始测试",
    "users": "用户",
    "user": "用户",
    "Start new load test": "开始新的负载测试",
    "Edit running load test": "编辑运行中的测试",
};

// 隐藏"平均大小"列
function hideAverageSizeColumn() {
    try {
        // 查找包含 "Average size" 的表头
        const headers = document.querySelectorAll("th");
        let columnIndex = -1;
        
        headers.forEach((th, index) => {
            const text = th.textContent.trim();
            if (text.includes("Average size") || text.includes("平均大小") || text.includes("bytes")) {
                columnIndex = index;
                th.style.display = "none";
            }
        });
        
        // 隐藏对应列的所有单元格
        if (columnIndex >= 0) {
            const rows = document.querySelectorAll("tr");
            rows.forEach(row => {
                const cells = row.querySelectorAll("td");
                if (cells[columnIndex]) {
                    cells[columnIndex].style.display = "none";
                }
            });
        }
    } catch (e) {
        console.error("Hide column error:", e);
    }
}

// 翻译单个元素
function translateElement(element) {
    if (!element || !element.textContent) return;
    
    const text = element.textContent.trim();
    if (text && translations[text]) {
        element.textContent = translations[text];
        return true;
    }
    return false;
}

// 翻译包含特定文本的元素
function translateContains(element) {
    if (!element || !element.textContent) return;
    
    let text = element.textContent;
    let translated = false;
    
    Object.keys(translations).forEach(key => {
        if (text.includes(key)) {
            text = text.replace(new RegExp(key, 'g'), translations[key]);
            translated = true;
        }
    });
    
    if (translated) {
        element.textContent = text;
    }
}

// 主翻译函数
function translatePage() {
    try {
        // 翻译顶部导航栏
        document.querySelectorAll("nav a, .tabs a, .tabs-menu a").forEach(link => {
            translateElement(link);
        });
        
        // 翻译表头 th
        document.querySelectorAll("th").forEach(th => {
            if (!translateElement(th)) {
                translateContains(th);
            }
        });
        
        // 翻译表格单元格中的特殊文本
        document.querySelectorAll("td").forEach(td => {
            const text = td.textContent.trim();
            if (text === "POST" || text === "GET" || text === "Aggregated") {
                translateElement(td);
            }
        });
        
        // 翻译按钮
        document.querySelectorAll("button, a.btn, .button, input[type='submit']").forEach(btn => {
            translateElement(btn);
            // 翻译按钮的 value 属性
            if (btn.value && translations[btn.value]) {
                btn.value = translations[btn.value];
            }
        });
        
        // 翻译标签
        document.querySelectorAll("label").forEach(label => {
            translateElement(label);
        });
        
        // 翻译状态栏
        document.querySelectorAll(".status, .top-stats span, .box-title").forEach(el => {
            if (!translateElement(el)) {
                translateContains(el);
            }
        });
        
        // 翻译顶部统计信息
        document.querySelectorAll(".top-stats .value-label, .stats-label").forEach(el => {
            translateElement(el);
        });
        
        // 翻译页面标题
        document.querySelectorAll("h1, h2, h3").forEach(heading => {
            translateElement(heading);
        });
        
        // 隐藏平均大小列
        hideAverageSizeColumn();
        
    } catch (e) {
        console.error("Translation error:", e);
    }
}

// 页面加载完成后翻译
if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", function() {
        setTimeout(translatePage, 100);
    });
} else {
    setTimeout(translatePage, 100);
}

// 监听 DOM 变化，动态翻译新内容
const observer = new MutationObserver(function(mutations) {
    translatePage();
});

observer.observe(document.body, {
    childList: true,
    subtree: true,
    characterData: true
});

// 定期翻译（处理动态更新的内容）
setInterval(translatePage, 800);

// 立即执行一次
translatePage();
</script>
<style>
body, * {
    font-family: "Microsoft YaHei", "微软雅黑", Arial, sans-serif !important;
}
/* 隐藏平均大小列 */
th:has-text("Average size"),
th:contains("bytes"),
td:nth-child(11) {
    display: none !important;
}
</style>
</body>'''
                
                # 在 </body> 前注入脚本
                if '</body>' in html:
                    html = html.replace('</body>', inject_script)
                    response.set_data(html)
        
        return response


def refresh_market_prices(environment):
    """后台协程：每秒刷新一次所有股票的最新行情缓存"""
    while True:
        for stock in TradingUser.STOCK_POOL:
            security_id = stock["securityId"]
            try:
                url = f"{environment.host}/api/v1/vclient/quote/tick/{security_id}"
                response = requests.get(url, timeout=3)
                if response.status_code == 200:
                    resp_json = response.json()
                    if resp_json.get("success") and resp_json.get("code") == 0:
                        data = resp_json.get("data")
                        if data and "lastPrice" in data:
                            PRICE_CACHE[security_id] = float(data["lastPrice"])
            except Exception as e:
                # 忽略偶尔的网络异常，防止日志刷屏
                pass
        
        # 每秒刷新一次
        gevent.sleep(1.0)


@events.test_start.add_listener
def on_test_start(environment, **kwargs):
    """测试开始时的回调"""
    print("=" * 60)
    print("Vortex 交易系统负载测试开始")
    print(f"目标地址: {environment.host}")
    print("=" * 60)
    
    # 启动后台协程来定期获取行情信息
    if environment.host:
        gevent.spawn(refresh_market_prices, environment)


@events.test_stop.add_listener
def on_test_stop(environment, **kwargs):
    """测试结束时的回调"""
    print("=" * 60)
    print("负载测试完成")
    print("=" * 60)