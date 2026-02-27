import argparse
import json
import random
import string
import threading
import time
import urllib.error
import urllib.request
from collections import deque


STOCK_POOL = [
    ("XSHG", "600030"),
    ("XSHG", "600519"),
    ("XSHG", "600036"),
    ("XSHE", "000001"),
    ("XSHE", "000858"),
    ("XSHE", "000002"),
    ("BJSE", "872925"),
    ("BJSE", "430047"),
]


def rand_id(n: int) -> str:
    return "".join(random.choices(string.ascii_uppercase + string.digits, k=n))


def order_id() -> str:
    # 16 chars
    t = str(int(time.time() * 1000))[-10:]
    return (t + rand_id(6))[:16]


def post_json(url: str, payload: dict, timeout: float = 3.0):
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        url=url,
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            body = resp.read().decode("utf-8", errors="ignore")
            return resp.status, body
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", errors="ignore")
    except Exception as e:
        return 0, str(e)


class TrafficRunner:
    def __init__(self, host: str, qps: int, cancel_ratio: float, workers: int):
        self.host = host.rstrip("/")
        self.qps = qps
        self.cancel_ratio = cancel_ratio
        self.workers = workers
        self.running = True
        self.lock = threading.Lock()
        self.sent = 0
        self.ok = 0
        self.fail = 0
        self.cancel_ok = 0
        self.cancel_fail = 0
        self.pending_orders = deque(maxlen=5000)
        self.shareholders = [f"SH{rand_id(8)}" for _ in range(100)]

    def _build_order(self, market, security_id, side, price, shareholder):
        return {
            "clOrderId": order_id(),
            "market": market,
            "securityId": security_id,
            "side": side,
            "qty": random.randint(1, 100) * 100,
            "price": round(price, 2),
            "shareholderId": shareholder,
        }

    def _submit_order(self, payload):
        url = f"{self.host}/api/v1/vclient/orders"
        status, _ = post_json(url, payload)
        with self.lock:
            self.sent += 1
            if status == 200:
                self.ok += 1
                self.pending_orders.append(
                    {
                        "clOrderId": payload["clOrderId"],
                        "market": payload["market"],
                        "securityId": payload["securityId"],
                        "side": payload["side"],
                        "shareholderId": payload["shareholderId"],
                    }
                )
            else:
                self.fail += 1

    def _submit_cancel(self):
        with self.lock:
            if not self.pending_orders:
                return
            target = random.choice(list(self.pending_orders))

        payload = {
            "clOrderId": order_id(),
            "origClOrderId": target["clOrderId"],
            "market": target["market"],
            "securityId": target["securityId"],
            "side": target["side"],
            "shareholderId": target["shareholderId"],
        }
        url = f"{self.host}/api/v1/vclient/orders/cancel"
        status, _ = post_json(url, payload)
        with self.lock:
            self.sent += 1
            if status == 200:
                self.cancel_ok += 1
            else:
                self.cancel_fail += 1

    def _send_match_pair(self):
        market, security_id = random.choice(STOCK_POOL)
        mid = random.uniform(20.0, 120.0)
        buy_price = mid + random.uniform(0.01, 0.5)
        sell_price = mid - random.uniform(0.01, 0.5)
        buyer = random.choice(self.shareholders)
        seller = random.choice(self.shareholders)
        if seller == buyer:
            seller = random.choice([s for s in self.shareholders if s != buyer])

        buy = self._build_order(market, security_id, "B", buy_price, buyer)
        sell = self._build_order(market, security_id, "S", sell_price, seller)

        # Randomize order to vary taker/maker roles.
        if random.random() < 0.5:
            self._submit_order(buy)
            self._submit_order(sell)
        else:
            self._submit_order(sell)
            self._submit_order(buy)

    def _send_regular_order(self):
        market, security_id = random.choice(STOCK_POOL)
        side = random.choice(["B", "S"])
        price = random.uniform(10.0, 200.0)
        sh = random.choice(self.shareholders)
        self._submit_order(self._build_order(market, security_id, side, price, sh))

    def worker_loop(self):
        base_interval = 1.0 / max(1, self.qps / max(1, self.workers))
        while self.running:
            if random.random() < self.cancel_ratio:
                self._submit_cancel()
            elif random.random() < 0.45:
                self._send_match_pair()
            else:
                self._send_regular_order()
            time.sleep(base_interval)

    def print_stats_loop(self):
        while self.running:
            time.sleep(5)
            with self.lock:
                print(
                    f"[stats] sent={self.sent} ok={self.ok} fail={self.fail} "
                    f"cancel_ok={self.cancel_ok} cancel_fail={self.cancel_fail} "
                    f"pending={len(self.pending_orders)}"
                )

    def run(self, duration_sec: int):
        threads = []
        for _ in range(self.workers):
            t = threading.Thread(target=self.worker_loop, daemon=True)
            t.start()
            threads.append(t)

        stats_thread = threading.Thread(target=self.print_stats_loop, daemon=True)
        stats_thread.start()

        deadline = time.time() + duration_sec if duration_sec > 0 else None
        try:
            while True:
                if deadline and time.time() >= deadline:
                    break
                time.sleep(0.5)
        except KeyboardInterrupt:
            pass
        finally:
            self.running = False
            for t in threads:
                t.join(timeout=1.0)
            with self.lock:
                print(
                    f"[done] sent={self.sent} ok={self.ok} fail={self.fail} "
                    f"cancel_ok={self.cancel_ok} cancel_fail={self.cancel_fail}"
                )


def main():
    parser = argparse.ArgumentParser(
        description="Keep sending mixed trading requests for continuous data generation."
    )
    parser.add_argument("--host", default="http://localhost:8080", help="Core service host")
    parser.add_argument("--qps", type=int, default=30, help="Approx total requests per second")
    parser.add_argument("--workers", type=int, default=4, help="Sender worker threads")
    parser.add_argument("--cancel-ratio", type=float, default=0.12, help="Cancel request ratio [0,1]")
    parser.add_argument(
        "--duration-sec",
        type=int,
        default=0,
        help="Run duration in seconds, 0 means run forever until Ctrl+C",
    )
    parser.add_argument("--seed", type=int, default=42, help="Random seed")
    args = parser.parse_args()

    random.seed(args.seed)
    runner = TrafficRunner(
        host=args.host,
        qps=args.qps,
        cancel_ratio=max(0.0, min(1.0, args.cancel_ratio)),
        workers=max(1, args.workers),
    )
    runner.run(duration_sec=max(0, args.duration_sec))


if __name__ == "__main__":
    main()
