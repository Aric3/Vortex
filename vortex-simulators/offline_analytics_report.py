import argparse
import csv
import json
import math
import os
import sqlite3
from datetime import datetime
from pathlib import Path


def table_exists(cur: sqlite3.Cursor, table: str) -> bool:
    cur.execute("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", (table,))
    return cur.fetchone() is not None


def fetch_value(cur: sqlite3.Cursor, sql: str, params=()):
    cur.execute(sql, params)
    row = cur.fetchone()
    if not row:
        return 0
    return row[0] if row[0] is not None else 0


def percentile(values, p: float):
    if not values:
        return None
    if len(values) == 1:
        return values[0]
    idx = (len(values) - 1) * p
    lower = math.floor(idx)
    upper = math.ceil(idx)
    if lower == upper:
        return values[lower]
    weight = idx - lower
    return values[lower] * (1 - weight) + values[upper] * weight


def write_csv(path: Path, rows, headers):
    with path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(headers)
        for r in rows:
            writer.writerow(r)


def to_hour_bucket(ms):
    if ms is None:
        return None
    return datetime.fromtimestamp(ms / 1000).strftime("%Y-%m-%d %H:00")


def run_analysis(db_path: Path):
    conn = sqlite3.connect(str(db_path))
    cur = conn.cursor()

    has_orders = table_exists(cur, "orders")
    has_order_rejects = table_exists(cur, "order_rejects")
    has_trades = table_exists(cur, "trades")
    has_cancellations = table_exists(cur, "cancellations")

    if not has_orders:
        raise RuntimeError("Missing required table: orders")

    accepted_orders = int(fetch_value(cur, "SELECT COUNT(*) FROM orders"))
    rejected_orders = int(fetch_value(cur, "SELECT COUNT(*) FROM order_rejects")) if has_order_rejects else 0
    total_orders = accepted_orders + rejected_orders

    wash_rejects = 0
    if has_order_rejects:
        wash_rejects = int(fetch_value(cur, "SELECT COUNT(*) FROM order_rejects WHERE reject_code = 4001"))
    wash_ratio = (wash_rejects / total_orders) if total_orders else 0.0

    trade_count = 0
    trade_qty = 0
    trade_turnover = 0.0
    if has_trades:
        cur.execute("SELECT COUNT(*), COALESCE(SUM(qty),0), COALESCE(SUM(qty * price),0) FROM trades")
        row = cur.fetchone()
        trade_count, trade_qty, trade_turnover = int(row[0]), int(row[1]), float(row[2])

    # status distribution
    cur.execute(
        "SELECT COALESCE(status,'UNKNOWN') AS status, COUNT(*) AS cnt FROM orders GROUP BY COALESCE(status,'UNKNOWN') ORDER BY cnt DESC"
    )
    status_rows = cur.fetchall()
    status_map = {r[0]: int(r[1]) for r in status_rows}
    filled_rate = (status_map.get("Filled", 0) / accepted_orders) if accepted_orders else 0.0
    partial_rate = (status_map.get("PartiallyFilled", 0) / accepted_orders) if accepted_orders else 0.0
    canceled_rate = (status_map.get("Canceled", 0) / accepted_orders) if accepted_orders else 0.0

    # reject breakdown
    reject_rows = []
    if has_order_rejects:
        cur.execute(
            """
            SELECT COALESCE(reject_code, -1) AS reject_code,
                   COALESCE(reject_text, '') AS reject_text,
                   COUNT(*) AS cnt
            FROM order_rejects
            GROUP BY COALESCE(reject_code, -1), COALESCE(reject_text, '')
            ORDER BY cnt DESC
            """
        )
        reject_rows = cur.fetchall()

    # cancellations
    cancel_total = 0
    cancel_success = 0
    cancel_reject = 0
    cancel_rate = 0.0
    if has_cancellations:
        cancel_total = int(fetch_value(cur, "SELECT COUNT(*) FROM cancellations"))
        cancel_success = int(fetch_value(cur, "SELECT COUNT(*) FROM cancellations WHERE success = 1"))
        cancel_reject = int(fetch_value(cur, "SELECT COUNT(*) FROM cancellations WHERE success = 0"))
        cancel_rate = (cancel_total / accepted_orders) if accepted_orders else 0.0

    # latency (first trade time - order create time)
    latencies = []
    latency_bucket_rows = []
    if has_trades:
        cur.execute(
            """
            WITH order_trade AS (
                SELECT taker_cl_order_id AS cl_order_id, CAST(trade_time AS INTEGER) AS trade_time
                FROM trades
                WHERE taker_cl_order_id IS NOT NULL AND taker_cl_order_id <> ''
                UNION ALL
                SELECT maker_cl_order_id AS cl_order_id, CAST(trade_time AS INTEGER) AS trade_time
                FROM trades
                WHERE maker_cl_order_id IS NOT NULL AND maker_cl_order_id <> ''
            ),
            first_trade AS (
                SELECT cl_order_id, MIN(trade_time) AS first_trade_time
                FROM order_trade
                GROUP BY cl_order_id
            )
            SELECT CAST(ft.first_trade_time AS INTEGER) - CAST(o.create_time AS INTEGER) AS latency_ms
            FROM first_trade ft
            JOIN orders o ON o.cl_order_id = ft.cl_order_id
            WHERE o.create_time IS NOT NULL
            """
        )
        latencies = [int(r[0]) for r in cur.fetchall() if r[0] is not None and int(r[0]) >= 0]

    if latencies:
        cur.execute(
            """
            WITH order_trade AS (
                SELECT taker_cl_order_id AS cl_order_id, CAST(trade_time AS INTEGER) AS trade_time
                FROM trades
                WHERE taker_cl_order_id IS NOT NULL AND taker_cl_order_id <> ''
                UNION ALL
                SELECT maker_cl_order_id AS cl_order_id, CAST(trade_time AS INTEGER) AS trade_time
                FROM trades
                WHERE maker_cl_order_id IS NOT NULL AND maker_cl_order_id <> ''
            ),
            first_trade AS (
                SELECT cl_order_id, MIN(trade_time) AS first_trade_time
                FROM order_trade
                GROUP BY cl_order_id
            ),
            latency AS (
                SELECT CAST(ft.first_trade_time AS INTEGER) - CAST(o.create_time AS INTEGER) AS latency_ms
                FROM first_trade ft
                JOIN orders o ON o.cl_order_id = ft.cl_order_id
                WHERE o.create_time IS NOT NULL
            )
            SELECT
                CASE
                    WHEN latency_ms <= 1 THEN '0-1ms'
                    WHEN latency_ms <= 5 THEN '2-5ms'
                    WHEN latency_ms <= 10 THEN '6-10ms'
                    WHEN latency_ms <= 50 THEN '11-50ms'
                    WHEN latency_ms <= 100 THEN '51-100ms'
                    WHEN latency_ms <= 500 THEN '101-500ms'
                    ELSE '>500ms'
                END AS bucket,
                COUNT(*) AS cnt
            FROM latency
            WHERE latency_ms >= 0
            GROUP BY bucket
            ORDER BY
                CASE bucket
                    WHEN '0-1ms' THEN 1
                    WHEN '2-5ms' THEN 2
                    WHEN '6-10ms' THEN 3
                    WHEN '11-50ms' THEN 4
                    WHEN '51-100ms' THEN 5
                    WHEN '101-500ms' THEN 6
                    ELSE 7
                END
            """
        )
        latency_bucket_rows = cur.fetchall()

    latency_stats = {
        "count": len(latencies),
        "avg_ms": round(sum(latencies) / len(latencies), 3) if latencies else None,
        "p50_ms": round(percentile(sorted(latencies), 0.50), 3) if latencies else None,
        "p90_ms": round(percentile(sorted(latencies), 0.90), 3) if latencies else None,
        "p95_ms": round(percentile(sorted(latencies), 0.95), 3) if latencies else None,
        "p99_ms": round(percentile(sorted(latencies), 0.99), 3) if latencies else None,
        "max_ms": max(latencies) if latencies else None,
    }

    # market summary
    cur.execute(
        """
        SELECT market, COUNT(*) AS orders_cnt, COALESCE(SUM(qty),0) AS order_qty
        FROM orders
        GROUP BY market
        ORDER BY orders_cnt DESC
        """
    )
    market_order_rows = {r[0]: {"orders_cnt": int(r[1]), "order_qty": int(r[2])} for r in cur.fetchall()}

    market_trade_rows = {}
    if has_trades:
        cur.execute(
            """
            SELECT market, COUNT(*) AS trades_cnt, COALESCE(SUM(qty),0) AS trade_qty, COALESCE(SUM(qty * price),0) AS turnover
            FROM trades
            GROUP BY market
            ORDER BY trades_cnt DESC
            """
        )
        market_trade_rows = {
            r[0]: {"trades_cnt": int(r[1]), "trade_qty": int(r[2]), "turnover": float(r[3])}
            for r in cur.fetchall()
        }

    market_summary_rows = []
    for market in sorted(set(list(market_order_rows.keys()) + list(market_trade_rows.keys()))):
        o = market_order_rows.get(market, {"orders_cnt": 0, "order_qty": 0})
        t = market_trade_rows.get(market, {"trades_cnt": 0, "trade_qty": 0, "turnover": 0.0})
        market_summary_rows.append(
            (
                market,
                o["orders_cnt"],
                o["order_qty"],
                t["trades_cnt"],
                t["trade_qty"],
                round(t["turnover"], 3),
            )
        )

    # top securities
    cur.execute(
        """
        SELECT security_id, COUNT(*) AS orders_cnt, COALESCE(SUM(qty),0) AS order_qty
        FROM orders
        GROUP BY security_id
        ORDER BY orders_cnt DESC
        LIMIT 20
        """
    )
    top_sec_orders = {r[0]: {"orders_cnt": int(r[1]), "order_qty": int(r[2])} for r in cur.fetchall()}

    top_sec_trades = {}
    if has_trades:
        cur.execute(
            """
            SELECT security_id, COUNT(*) AS trades_cnt, COALESCE(SUM(qty),0) AS trade_qty, COALESCE(SUM(qty * price),0) AS turnover
            FROM trades
            GROUP BY security_id
            ORDER BY trades_cnt DESC
            LIMIT 20
            """
        )
        top_sec_trades = {
            r[0]: {"trades_cnt": int(r[1]), "trade_qty": int(r[2]), "turnover": float(r[3])}
            for r in cur.fetchall()
        }

    top_security_rows = []
    for sec in sorted(set(list(top_sec_orders.keys()) + list(top_sec_trades.keys()))):
        o = top_sec_orders.get(sec, {"orders_cnt": 0, "order_qty": 0})
        t = top_sec_trades.get(sec, {"trades_cnt": 0, "trade_qty": 0, "turnover": 0.0})
        top_security_rows.append(
            (
                sec,
                o["orders_cnt"],
                o["order_qty"],
                t["trades_cnt"],
                t["trade_qty"],
                round(t["turnover"], 3),
            )
        )
    top_security_rows.sort(key=lambda x: x[1], reverse=True)
    top_security_rows = top_security_rows[:20]

    # top shareholders
    cur.execute(
        """
        SELECT shareholder_id, COUNT(*) AS orders_cnt
        FROM orders
        GROUP BY shareholder_id
        ORDER BY orders_cnt DESC
        LIMIT 20
        """
    )
    top_holder_orders = {r[0]: {"orders_cnt": int(r[1])} for r in cur.fetchall()}

    top_holder_trades = {}
    if has_trades:
        cur.execute(
            """
            SELECT shareholder_id, COUNT(*) AS trade_rows, COALESCE(SUM(trade_qty),0) AS trade_qty
            FROM (
                SELECT taker_shareholder_id AS shareholder_id, qty AS trade_qty FROM trades
                UNION ALL
                SELECT maker_shareholder_id AS shareholder_id, qty AS trade_qty FROM trades
            )
            WHERE shareholder_id IS NOT NULL AND shareholder_id <> ''
            GROUP BY shareholder_id
            ORDER BY trade_rows DESC
            LIMIT 20
            """
        )
        top_holder_trades = {r[0]: {"trade_rows": int(r[1]), "trade_qty": int(r[2])} for r in cur.fetchall()}

    top_holder_rows = []
    for h in sorted(set(list(top_holder_orders.keys()) + list(top_holder_trades.keys()))):
        o = top_holder_orders.get(h, {"orders_cnt": 0})
        t = top_holder_trades.get(h, {"trade_rows": 0, "trade_qty": 0})
        top_holder_rows.append((h, o["orders_cnt"], t["trade_rows"], t["trade_qty"]))
    top_holder_rows.sort(key=lambda x: x[1], reverse=True)
    top_holder_rows = top_holder_rows[:20]

    # hourly trend
    cur.execute("SELECT CAST(create_time AS INTEGER) FROM orders WHERE create_time IS NOT NULL")
    orders_hours = {}
    for (ms,) in cur.fetchall():
        h = to_hour_bucket(int(ms))
        orders_hours[h] = orders_hours.get(h, 0) + 1

    reject_hours = {}
    if has_order_rejects:
        cur.execute("SELECT CAST(create_time AS INTEGER) FROM order_rejects WHERE create_time IS NOT NULL")
        for (ms,) in cur.fetchall():
            h = to_hour_bucket(int(ms))
            reject_hours[h] = reject_hours.get(h, 0) + 1

    trade_hours = {}
    if has_trades:
        cur.execute("SELECT CAST(trade_time AS INTEGER), qty FROM trades WHERE trade_time IS NOT NULL")
        for ms, qty in cur.fetchall():
            h = to_hour_bucket(int(ms))
            trade_hours[h] = trade_hours.get(h, 0) + int(qty)

    hourly_rows = []
    for h in sorted(set(list(orders_hours.keys()) + list(reject_hours.keys()) + list(trade_hours.keys()))):
        hourly_rows.append((h, orders_hours.get(h, 0), reject_hours.get(h, 0), trade_hours.get(h, 0)))

    conn.close()

    summary = {
        "accepted_orders": accepted_orders,
        "rejected_orders": rejected_orders,
        "total_orders": total_orders,
        "wash_rejects": wash_rejects,
        "wash_ratio": round(wash_ratio, 6),
        "trade_count": trade_count,
        "trade_qty": trade_qty,
        "trade_turnover": round(trade_turnover, 3),
        "filled_rate": round(filled_rate, 6),
        "partially_filled_rate": round(partial_rate, 6),
        "canceled_rate": round(canceled_rate, 6),
        "cancel_total": cancel_total,
        "cancel_success": cancel_success,
        "cancel_reject": cancel_reject,
        "cancel_request_rate": round(cancel_rate, 6),
        "latency": latency_stats,
    }

    return {
        "summary": summary,
        "status_rows": status_rows,
        "reject_rows": reject_rows,
        "latency_bucket_rows": latency_bucket_rows,
        "market_summary_rows": market_summary_rows,
        "top_security_rows": top_security_rows,
        "top_holder_rows": top_holder_rows,
        "hourly_rows": hourly_rows,
    }


def render_markdown(result: dict) -> str:
    s = result["summary"]
    lines = []
    lines.append("# 离线分析报告")
    lines.append("")
    lines.append("## 1. 总览指标")
    lines.append("")
    lines.append(f"- 通过订单数: {s['accepted_orders']}")
    lines.append(f"- 拒绝订单数: {s['rejected_orders']}")
    lines.append(f"- 订单总数: {s['total_orders']}")
    lines.append(f"- 对敲拒绝数: {s['wash_rejects']}")
    lines.append(f"- 对敲占比: {s['wash_ratio']:.6f}")
    lines.append(f"- 成交笔数: {s['trade_count']}")
    lines.append(f"- 成交总量: {s['trade_qty']}")
    lines.append(f"- 成交额(估算): {s['trade_turnover']:.3f}")
    lines.append(f"- 完全成交率(Filled/accepted): {s['filled_rate']:.6f}")
    lines.append(f"- 部分成交率(PartiallyFilled/accepted): {s['partially_filled_rate']:.6f}")
    lines.append(f"- 撤单率(cancel_requests/accepted): {s['cancel_request_rate']:.6f}")
    lines.append("")
    lines.append("## 2. 成交延时统计")
    lines.append("")
    lat = s["latency"]
    lines.append(f"- 样本数: {lat['count']}")
    lines.append(f"- 平均延时(ms): {lat['avg_ms']}")
    lines.append(f"- P50(ms): {lat['p50_ms']}")
    lines.append(f"- P90(ms): {lat['p90_ms']}")
    lines.append(f"- P95(ms): {lat['p95_ms']}")
    lines.append(f"- P99(ms): {lat['p99_ms']}")
    lines.append(f"- 最大延时(ms): {lat['max_ms']}")
    lines.append("")
    lines.append("### 2.1 延时分桶")
    lines.append("")
    lines.append("| bucket | count |")
    lines.append("|---|---:|")
    for bucket, cnt in result["latency_bucket_rows"]:
        lines.append(f"| {bucket} | {cnt} |")
    lines.append("")
    lines.append("## 3. 订单状态分布")
    lines.append("")
    lines.append("| status | count |")
    lines.append("|---|---:|")
    for status, cnt in result["status_rows"]:
        lines.append(f"| {status} | {cnt} |")
    lines.append("")
    lines.append("## 4. 拒单原因分布")
    lines.append("")
    lines.append("| reject_code | reject_text | count |")
    lines.append("|---:|---|---:|")
    for code, text, cnt in result["reject_rows"]:
        safe_text = str(text).replace("|", "\\|")
        lines.append(f"| {code} | {safe_text} | {cnt} |")
    lines.append("")
    return "\n".join(lines)


def render_html(result: dict) -> str:
    s = result["summary"]
    latency_rows = result["latency_bucket_rows"]
    status_rows = result["status_rows"]
    reject_rows = result["reject_rows"][:20]
    market_rows = result["market_summary_rows"][:20]
    sec_rows = result["top_security_rows"][:20]
    holder_rows = result["top_holder_rows"][:20]

    max_latency_cnt = max([r[1] for r in latency_rows], default=1)

    def table(headers, rows):
        thead = "".join([f"<th>{h}</th>" for h in headers])
        body = []
        for r in rows:
            tds = "".join([f"<td>{x}</td>" for x in r])
            body.append(f"<tr>{tds}</tr>")
        return f"<table><thead><tr>{thead}</tr></thead><tbody>{''.join(body)}</tbody></table>"

    bars = []
    for bucket, cnt in latency_rows:
        width = int((cnt / max_latency_cnt) * 100) if max_latency_cnt > 0 else 0
        bars.append(
            f"""
            <div class="bar-row">
              <div class="bar-label">{bucket}</div>
              <div class="bar-wrap"><div class="bar" style="width:{width}%"></div></div>
              <div class="bar-val">{cnt}</div>
            </div>
            """
        )

    html = f"""
<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Vortex 离线分析报告</title>
  <style>
    body {{ font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; margin: 24px; color: #111; }}
    h1, h2, h3 {{ margin: 0 0 12px 0; }}
    .muted {{ color: #666; margin-bottom: 16px; }}
    .grid {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 12px; margin: 12px 0 24px; }}
    .card {{ border: 1px solid #ddd; border-radius: 8px; padding: 12px; background: #fafafa; }}
    .card .k {{ font-size: 12px; color: #666; }}
    .card .v {{ font-size: 22px; font-weight: 600; margin-top: 4px; }}
    table {{ width: 100%; border-collapse: collapse; margin: 8px 0 20px; font-size: 13px; }}
    th, td {{ border: 1px solid #e5e5e5; padding: 6px 8px; text-align: left; }}
    th {{ background: #f4f4f4; }}
    .section {{ margin: 20px 0; }}
    .bar-row {{ display: grid; grid-template-columns: 90px 1fr 70px; gap: 8px; align-items: center; margin: 6px 0; }}
    .bar-wrap {{ background: #eee; border-radius: 4px; height: 14px; }}
    .bar {{ background: #2f80ed; height: 14px; border-radius: 4px; }}
    .bar-label {{ font-size: 12px; color: #333; }}
    .bar-val {{ text-align: right; font-size: 12px; color: #333; }}
  </style>
</head>
<body>
  <h1>Vortex 离线分析报告</h1>
  <div class="muted">生成时间：{datetime.now().strftime("%Y-%m-%d %H:%M:%S")}</div>

  <div class="grid">
    <div class="card"><div class="k">通过订单数</div><div class="v">{s['accepted_orders']}</div></div>
    <div class="card"><div class="k">拒绝订单数</div><div class="v">{s['rejected_orders']}</div></div>
    <div class="card"><div class="k">订单总数</div><div class="v">{s['total_orders']}</div></div>
    <div class="card"><div class="k">对敲拒绝数</div><div class="v">{s['wash_rejects']}</div></div>
    <div class="card"><div class="k">对敲占比</div><div class="v">{s['wash_ratio']:.6f}</div></div>
    <div class="card"><div class="k">成交笔数</div><div class="v">{s['trade_count']}</div></div>
    <div class="card"><div class="k">成交总量</div><div class="v">{s['trade_qty']}</div></div>
    <div class="card"><div class="k">撤单请求数</div><div class="v">{s['cancel_total']}</div></div>
  </div>

  <div class="section">
    <h2>延时分布</h2>
    {''.join(bars)}
    <div class="muted">P50={s['latency']['p50_ms']}ms, P95={s['latency']['p95_ms']}ms, P99={s['latency']['p99_ms']}ms, Max={s['latency']['max_ms']}ms</div>
  </div>

  <div class="section">
    <h2>订单状态分布</h2>
    {table(["状态", "数量"], status_rows)}
  </div>

  <div class="section">
    <h2>拒单原因 Top20</h2>
    {table(["拒绝码", "拒绝原因", "数量"], reject_rows)}
  </div>

  <div class="section">
    <h2>市场汇总</h2>
    {table(["市场", "订单数", "订单量", "成交笔数", "成交量", "成交额"], market_rows)}
  </div>

  <div class="section">
    <h2>证券 Top20</h2>
    {table(["证券代码", "订单数", "订单量", "成交笔数", "成交量", "成交额"], sec_rows)}
  </div>

  <div class="section">
    <h2>股东 Top20</h2>
    {table(["股东号", "订单数", "成交行数", "成交量"], holder_rows)}
  </div>
</body>
</html>
"""
    return html


def main():
    parser = argparse.ArgumentParser(description="Offline analytics for Vortex SQLite trading data.")
    parser.add_argument("--db", default=None, help="SQLite db path")
    parser.add_argument("--out-dir", default=None, help="Output directory")
    args = parser.parse_args()

    script_dir = Path(__file__).resolve().parent
    project_root = script_dir.parent
    db_path = Path(args.db) if args.db else (project_root / "vortex-core" / "vortex.db")
    if not db_path.is_absolute():
        db_path = (Path.cwd() / db_path).resolve()
    out_dir = Path(args.out_dir) if args.out_dir else (script_dir / "reports")
    if not out_dir.is_absolute():
        out_dir = (Path.cwd() / out_dir).resolve()
    out_dir.mkdir(parents=True, exist_ok=True)

    result = run_analysis(db_path)
    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    run_dir = out_dir / f"offline_analytics_{ts}"
    run_dir.mkdir(parents=True, exist_ok=True)

    # Summary JSON
    (run_dir / "summary.json").write_text(
        json.dumps(result["summary"], ensure_ascii=False, indent=2), encoding="utf-8"
    )

    # Markdown report
    (run_dir / "report.md").write_text(render_markdown(result), encoding="utf-8")
    (run_dir / "report.html").write_text(render_html(result), encoding="utf-8")

    # CSV exports
    write_csv(run_dir / "latency_buckets.csv", result["latency_bucket_rows"], ["bucket", "count"])
    write_csv(run_dir / "status_distribution.csv", result["status_rows"], ["status", "count"])
    write_csv(run_dir / "reject_breakdown.csv", result["reject_rows"], ["reject_code", "reject_text", "count"])
    write_csv(
        run_dir / "market_summary.csv",
        result["market_summary_rows"],
        ["market", "orders_count", "order_qty", "trades_count", "trade_qty", "turnover"],
    )
    write_csv(
        run_dir / "top_securities.csv",
        result["top_security_rows"],
        ["security_id", "orders_count", "order_qty", "trades_count", "trade_qty", "turnover"],
    )
    write_csv(
        run_dir / "top_shareholders.csv",
        result["top_holder_rows"],
        ["shareholder_id", "orders_count", "trade_rows", "trade_qty"],
    )
    write_csv(
        run_dir / "hourly_trend.csv",
        result["hourly_rows"],
        ["hour", "orders_count", "rejects_count", "trade_qty"],
    )

    print(f"Offline analytics finished. Output: {run_dir}")
    print(f"Report file: {run_dir / 'report.md'}")
    print(f"HTML report: {run_dir / 'report.html'}")


if __name__ == "__main__":
    main()
