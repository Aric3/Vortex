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


def to_minute_bucket(ms):
    if ms is None:
        return None
    return datetime.fromtimestamp(ms / 1000).strftime("%Y-%m-%d %H:%M")


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

    # cancellations (must be before status distribution)
    cancel_total = 0
    cancel_success = 0
    cancel_reject = 0
    cancel_rate = 0.0
    if has_cancellations:
        cancel_total = int(fetch_value(cur, "SELECT COUNT(*) FROM cancellations"))
        cancel_success = int(fetch_value(cur, "SELECT COUNT(*) FROM cancellations WHERE success = 1"))
        cancel_reject = int(fetch_value(cur, "SELECT COUNT(*) FROM cancellations WHERE success = 0"))
        cancel_rate = (cancel_total / accepted_orders) if accepted_orders else 0.0

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

    # status distribution (merge orders + order_rejects + cancellations)
    cur.execute(
        "SELECT COALESCE(status,'UNKNOWN') AS status, COUNT(*) AS cnt FROM orders GROUP BY COALESCE(status,'UNKNOWN') ORDER BY cnt DESC"
    )
    status_rows_raw = cur.fetchall()
    status_map = {r[0]: int(r[1]) for r in status_rows_raw}
    if has_order_rejects and rejected_orders > 0:
        status_map["Rejected"] = rejected_orders
    if has_cancellations and cancel_success > 0:
        status_map["Canceled"] = cancel_success
    status_rows = sorted(status_map.items(), key=lambda x: x[1], reverse=True)
    traded_orders = status_map.get("Filled", 0) + status_map.get("PartiallyFilled", 0)
    filled_rate = (status_map.get("Filled", 0) / accepted_orders) if accepted_orders else 0.0
    partial_rate = (status_map.get("PartiallyFilled", 0) / accepted_orders) if accepted_orders else 0.0
    canceled_rate = (status_map.get("Canceled", 0) / accepted_orders) if accepted_orders else 0.0

    # latency (updated_time_epoch_ms - create_time_epoch_ms for Filled/PartiallyFilled orders)
    latencies = []
    latency_bucket_rows = []
    cur.execute(
        """
        SELECT COALESCE(updated_time_epoch_ms, create_time_epoch_ms) - create_time_epoch_ms AS latency_ms
        FROM orders
        WHERE status IN ('Filled', 'PartiallyFilled')
        """
    )
    latencies = [max(0, int(r[0])) if r[0] is not None else 0 for r in cur.fetchall()]

    if latencies:
        cur.execute(
            """
            WITH latency AS (
                SELECT COALESCE(updated_time_epoch_ms, create_time_epoch_ms) - create_time_epoch_ms AS latency_ms
                FROM orders
                WHERE status IN ('Filled', 'PartiallyFilled')
                  AND create_time_epoch_ms IS NOT NULL AND updated_time_epoch_ms IS NOT NULL
            )
            SELECT
                CASE
                    WHEN latency_ms <= 1000 THEN '0-1s'
                    WHEN latency_ms <= 5000 THEN '1-5s'
                    WHEN latency_ms <= 10000 THEN '5-10s'
                    WHEN latency_ms <= 30000 THEN '10-30s'
                    WHEN latency_ms <= 60000 THEN '30-60s'
                    WHEN latency_ms <= 300000 THEN '1-5min'
                    WHEN latency_ms <= 900000 THEN '5-15min'
                    ELSE '>15min'
                END AS bucket,
                COUNT(*) AS cnt
            FROM latency
            GROUP BY bucket
            ORDER BY
                CASE bucket
                    WHEN '0-1s' THEN 1
                    WHEN '1-5s' THEN 2
                    WHEN '5-10s' THEN 3
                    WHEN '10-30s' THEN 4
                    WHEN '30-60s' THEN 5
                    WHEN '1-5min' THEN 6
                    WHEN '5-15min' THEN 7
                    ELSE 8
                END
            """
        )
        latency_bucket_rows = cur.fetchall()

    latency_stats = {
        "count": len(latencies),
        "avg": round(sum(latencies) / len(latencies) / 1000, 3) if latencies else None,
        "p50": round(percentile(sorted(latencies), 0.50) / 1000, 3) if latencies else None,
        "p90": round(percentile(sorted(latencies), 0.90) / 1000, 3) if latencies else None,
        "p95": round(percentile(sorted(latencies), 0.95) / 1000, 3) if latencies else None,
        "p99": round(percentile(sorted(latencies), 0.99) / 1000, 3) if latencies else None,
        "max": round(max(latencies) / 1000, 3) if latencies else None,
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
        h = to_minute_bucket(int(ms))
        orders_hours[h] = orders_hours.get(h, 0) + 1

    reject_hours = {}
    if has_order_rejects:
        cur.execute("SELECT CAST(create_time AS INTEGER) FROM order_rejects WHERE create_time IS NOT NULL")
        for (ms,) in cur.fetchall():
            h = to_minute_bucket(int(ms))
            reject_hours[h] = reject_hours.get(h, 0) + 1

    traded_hours = {}
    cur.execute("SELECT CAST(create_time AS INTEGER) FROM orders WHERE create_time IS NOT NULL AND status IN ('Filled', 'PartiallyFilled')")
    for (ms,) in cur.fetchall():
        h = to_minute_bucket(int(ms))
        traded_hours[h] = traded_hours.get(h, 0) + 1

    hourly_rows = []
    for h in sorted(set(list(orders_hours.keys()) + list(reject_hours.keys()) + list(traded_hours.keys()))):
        accepted = orders_hours.get(h, 0)
        rejected = reject_hours.get(h, 0)
        hourly_rows.append((h, accepted + rejected, rejected, traded_hours.get(h, 0)))

    conn.close()

    summary = {
        "accepted_orders": accepted_orders,
        "rejected_orders": rejected_orders,
        "total_orders": total_orders,
        "wash_rejects": wash_rejects,
        "wash_ratio": round(wash_ratio, 6),
        "trade_count": trade_count,
        "traded_orders": traded_orders,
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


# --------------- chart helpers (pure CSS / inline SVG, zero JS dependency) ---------------

_PALETTE = ["#2f80ed", "#f5a623", "#e74c3c", "#27ae60", "#8e44ad",
            "#1abc9c", "#e67e22", "#3498db", "#e91e63", "#00bcd4"]


def _css_donut(items, size=150):
    """items: [(label, value, color), ...]  -> HTML donut via conic-gradient"""
    total = sum(v for _, v, _ in items) or 1
    stops, acc = [], 0.0
    for _, val, color in items:
        s = acc / total * 360
        acc += val
        e = acc / total * 360
        stops.append(f"{color} {s:.1f}deg {e:.1f}deg")
    ring = ",".join(stops)
    inner = int(size * 0.55)
    legend = "".join(
        f'<div style="display:flex;align-items:center;gap:4px;margin:2px 0;font-size:11px">'
        f'<span style="display:inline-block;width:10px;height:10px;border-radius:2px;'
        f'background:{c};flex-shrink:0"></span>{lb}: {v:,} ({v/total*100:.1f}%)</div>'
        for lb, v, c in items
    )
    return (
        f'<div style="display:flex;align-items:center;gap:14px;flex-wrap:wrap">'
        f'<div style="width:{size}px;height:{size}px;border-radius:50%;'
        f'background:conic-gradient({ring});flex-shrink:0;'
        f'display:flex;align-items:center;justify-content:center">'
        f'<div style="width:{inner}px;height:{inner}px;border-radius:50%;background:#fff"></div>'
        f'</div><div>{legend}</div></div>'
    )


def _css_pie(items, size=150):
    """items: [(label, value, color), ...]  -> HTML pie via conic-gradient"""
    total = sum(v for _, v, _ in items) or 1
    stops, acc = [], 0.0
    for _, val, color in items:
        s = acc / total * 360
        acc += val
        e = acc / total * 360
        stops.append(f"{color} {s:.1f}deg {e:.1f}deg")
    ring = ",".join(stops)
    legend = "".join(
        f'<div style="display:flex;align-items:center;gap:4px;margin:2px 0;font-size:11px">'
        f'<span style="display:inline-block;width:10px;height:10px;border-radius:2px;'
        f'background:{c};flex-shrink:0"></span>{lb}: {v:,} ({v/total*100:.1f}%)</div>'
        for lb, v, c in items
    )
    return (
        f'<div style="display:flex;align-items:center;gap:14px;flex-wrap:wrap">'
        f'<div style="width:{size}px;height:{size}px;border-radius:50%;'
        f'background:conic-gradient({ring});flex-shrink:0"></div>'
        f'<div>{legend}</div></div>'
    )


def _hbar(items, bar_color="#2f80ed", show_pct=False):
    """Horizontal bar chart.  items: [(label, value), ...]"""
    mx = max((v for _, v in items), default=1) or 1
    total = sum(v for _, v in items) or 1
    rows = ""
    for lb, val in items:
        w = val / mx * 100
        pct = f" ({val/total*100:.1f}%)" if show_pct else ""
        rows += (
            f'<div style="display:grid;grid-template-columns:80px 1fr 100px;gap:6px;'
            f'align-items:center;margin:3px 0">'
            f'<div style="font-size:11px;color:#555;text-align:right;overflow:hidden;'
            f'text-overflow:ellipsis;white-space:nowrap">{lb}</div>'
            f'<div style="background:#eee;border-radius:3px;height:16px">'
            f'<div style="width:{w:.1f}%;background:{bar_color};height:16px;'
            f'border-radius:3px;min-width:2px"></div></div>'
            f'<div style="font-size:11px;color:#333">{val:,}{pct}</div></div>'
        )
    return rows


def _svg_line(datasets, labels, width=720, height=220):
    """Inline SVG multi-line chart with optional dual Y-axes.

    datasets: [(name, values, color), ...] or [(name, values, color, axis), ...]
    axis: "left" (default) or "right"
    """
    if not labels or not datasets:
        return ""
    # normalise to 4-tuples
    ds = []
    for item in datasets:
        if len(item) == 3:
            ds.append((*item, "left"))
        else:
            ds.append(item)

    has_right = any(a == "right" for _, _, _, a in ds)
    pl, pr, pt, pb = 55, (55 if has_right else 20), 24, 36
    cw = width - pl - pr
    ch = height - pt - pb
    n = len(labels)

    left_vals = [v for _, vs, _, a in ds if a == "left" for v in vs]
    right_vals = [v for _, vs, _, a in ds if a == "right" for v in vs]
    ym_l = max(left_vals) if left_vals else 1
    ym_l = ym_l or 1
    ym_r = max(right_vals) if right_vals else 1
    ym_r = ym_r or 1

    def sx(i):
        return pl + (i / max(n - 1, 1)) * cw

    def sy_l(v):
        return pt + ch - (v / ym_l) * ch

    def sy_r(v):
        return pt + ch - (v / ym_r) * ch

    parts = [f'<svg viewBox="0 0 {width} {height}" style="width:100%;max-height:{height}px" xmlns="http://www.w3.org/2000/svg">']
    # grid lines (based on left axis)
    for i in range(5):
        y = pt + ch * i / 4
        val_l = ym_l * (4 - i) / 4
        parts.append(f'<line x1="{pl}" y1="{y:.0f}" x2="{width-pr}" y2="{y:.0f}" stroke="#eee"/>')
        parts.append(f'<text x="{pl-4}" y="{y:.0f}" text-anchor="end" font-size="9" fill="#999" dominant-baseline="middle">{val_l:,.0f}</text>')
    # right axis labels
    if has_right:
        for i in range(5):
            y = pt + ch * i / 4
            val_r = ym_r * (4 - i) / 4
            parts.append(f'<text x="{width-pr+4}" y="{y:.0f}" text-anchor="start" font-size="9" fill="#999" dominant-baseline="middle">{val_r:,.0f}</text>')
    # x labels
    step = max(1, n // 8)
    for i in range(0, n, step):
        x = sx(i)
        lbl = labels[i][-5:] if len(labels[i]) > 5 else labels[i]
        parts.append(f'<text x="{x:.0f}" y="{height-6}" text-anchor="middle" font-size="9" fill="#999">{lbl}</text>')
    # lines + area fills
    for name, vals, color, axis in ds:
        sy = sy_r if axis == "right" else sy_l
        pts = " ".join(f"{sx(i):.1f},{sy(v):.1f}" for i, v in enumerate(vals))
        fill_pts = f"{sx(0):.1f},{pt+ch:.1f} " + pts + f" {sx(len(vals)-1):.1f},{pt+ch:.1f}"
        parts.append(f'<polygon points="{fill_pts}" fill="{color}" fill-opacity="0.08"/>')
        parts.append(f'<polyline points="{pts}" fill="none" stroke="{color}" stroke-width="2"/>')
    # legend
    lx = pl + 4
    for idx, (name, _, color, _) in enumerate(ds):
        x = lx + idx * 90
        parts.append(f'<rect x="{x}" y="4" width="12" height="8" rx="2" fill="{color}"/>')
        parts.append(f'<text x="{x+16}" y="12" font-size="10" fill="#555">{name}</text>')
    parts.append("</svg>")
    return "".join(parts)


def _pct_bar_td(value, total, color="#2f80ed"):
    """Table cell with inline percentage bar."""
    w = round(value / total * 100, 1) if total else 0
    return (
        f'<td style="padding:2px 6px"><div style="display:flex;align-items:center;gap:4px">'
        f'<div style="flex:1;background:#eee;border-radius:3px;height:12px;min-width:50px">'
        f'<div style="width:{w}%;background:{color};height:12px;border-radius:3px;min-width:1px"></div></div>'
        f'<span style="font-size:11px;white-space:nowrap">{w}%</span></div></td>'
    )


# --------------- markdown helpers ---------------

def _md_bar(ratio, width=20):
    filled = round(ratio * width)
    return "\u2588" * filled + "\u2591" * (width - filled)


def _safe_pct(part, total):
    return (part / total * 100) if total else 0.0


# --------------- render functions ---------------

def render_markdown(result: dict) -> str:
    s = result["summary"]
    total_orders = s["total_orders"] or 1
    lines = []
    lines.append("# 离线分析报告")
    lines.append("")
    lines.append("## 1. 总览指标")
    lines.append("")
    lines.append(f"- 通过订单数: {s['accepted_orders']}  ({_safe_pct(s['accepted_orders'], total_orders):.2f}%)")
    lines.append(f"- 拒绝订单数: {s['rejected_orders']}  ({_safe_pct(s['rejected_orders'], total_orders):.2f}%)")
    lines.append(f"- 订单总数: {s['total_orders']}")
    lines.append(f"- 对敲拒绝数: {s['wash_rejects']}  ({_safe_pct(s['wash_rejects'], total_orders):.2f}%)")
    lines.append(f"- 对敲占比: {s['wash_ratio']:.6f}")
    lines.append(f"- 成交订单数: {s['traded_orders']}")
    lines.append(f"- 成交总量: {s['trade_qty']}")
    lines.append(f"- 成交额(估算): {s['trade_turnover']:.3f}")
    lines.append(f"- 完全成交率(Filled/accepted): {s['filled_rate']:.6f}")
    lines.append(f"- 部分成交率(PartiallyFilled/accepted): {s['partially_filled_rate']:.6f}")
    lines.append(f"- 撤单率(cancel_requests/accepted): {s['cancel_request_rate']:.6f}")
    lines.append(f"- 撤单成功: {s['cancel_success']}  撤单拒绝: {s['cancel_reject']}")
    lines.append("")
    lines.append("## 2. 成交延时统计")
    lines.append("")
    lat = s["latency"]
    lines.append(f"- 样本数: {lat['count']}")
    lines.append(f"- 平均延时: {lat['avg']}s")
    lines.append(f"- P50: {lat['p50']}s")
    lines.append(f"- P90: {lat['p90']}s")
    lines.append(f"- P95: {lat['p95']}s")
    lines.append(f"- P99: {lat['p99']}s")
    lines.append(f"- 最大延时: {lat['max']}s")
    lines.append("")
    lines.append("### 2.1 延时分桶")
    lines.append("")
    latency_total = sum(r[1] for r in result["latency_bucket_rows"]) or 1
    max_lat = max((r[1] for r in result["latency_bucket_rows"]), default=1) or 1
    lines.append("| bucket | count | 占比 | 分布 |")
    lines.append("|---|---:|---:|---|")
    for bucket, cnt in result["latency_bucket_rows"]:
        pct = _safe_pct(cnt, latency_total)
        bar = _md_bar(cnt / max_lat)
        lines.append(f"| {bucket} | {cnt} | {pct:.1f}% | `{bar}` |")
    lines.append("")
    lines.append("## 3. 订单状态分布")
    lines.append("")
    st_total = sum(r[1] for r in result["status_rows"]) or 1
    lines.append("| status | count | 占比 | 分布 |")
    lines.append("|---|---:|---:|---|")
    for status, cnt in result["status_rows"]:
        pct = _safe_pct(cnt, st_total)
        bar = _md_bar(cnt / st_total)
        lines.append(f"| {status} | {cnt} | {pct:.1f}% | `{bar}` |")
    lines.append("")
    lines.append("## 4. 拒单原因分布")
    lines.append("")
    rej_total = sum(r[2] for r in result["reject_rows"]) or 1
    lines.append("| reject_code | reject_text | count | 占比 | 分布 |")
    lines.append("|---:|---|---:|---:|---|")
    for code, text, cnt in result["reject_rows"]:
        safe_text = str(text).replace("|", "\\|")
        pct = _safe_pct(cnt, rej_total)
        bar = _md_bar(cnt / rej_total)
        lines.append(f"| {code} | {safe_text} | {cnt} | {pct:.1f}% | `{bar}` |")
    lines.append("")
    lines.append("## 5. 市场汇总")
    lines.append("")
    mkt_rows = result["market_summary_rows"]
    mo_total = sum(r[1] for r in mkt_rows) or 1
    mt_total = sum(r[5] for r in mkt_rows) or 1.0
    lines.append("| 市场 | 订单数 | 订单占比 | 订单量 | 成交笔数 | 成交量 | 成交额 | 成交额占比 |")
    lines.append("|---|---:|---:|---:|---:|---:|---:|---:|")
    for market, ocnt, oqty, tcnt, tqty, turnover in mkt_rows:
        lines.append(f"| {market} | {ocnt} | {_safe_pct(ocnt,mo_total):.1f}% | {oqty} | {tcnt} | {tqty} | {turnover} | {_safe_pct(turnover,mt_total):.1f}% |")
    lines.append("")
    lines.append("## 6. 证券 Top20")
    lines.append("")
    lines.append("| 证券代码 | 订单数 | 订单量 | 成交笔数 | 成交量 | 成交额 |")
    lines.append("|---|---:|---:|---:|---:|---:|")
    for sec, ocnt, oqty, tcnt, tqty, turnover in result["top_security_rows"]:
        lines.append(f"| {sec} | {ocnt} | {oqty} | {tcnt} | {tqty} | {turnover} |")
    lines.append("")
    lines.append("## 7. 股东 Top20")
    lines.append("")
    lines.append("| 股东号 | 订单数 | 成交行数 | 成交量 |")
    lines.append("|---|---:|---:|---:|")
    for h, ocnt, trows, tqty in result["top_holder_rows"]:
        lines.append(f"| {h} | {ocnt} | {trows} | {tqty} |")
    lines.append("")
    lines.append("## 8. 每分钟趋势")
    lines.append("")
    lines.append("| 时间 | 订单数 | 拒绝数 | 成交订单 |")
    lines.append("|---|---:|---:|---:|")
    for hour, ocnt, rcnt, tqty in result["hourly_rows"]:
        lines.append(f"| {hour} | {ocnt} | {rcnt} | {tqty} |")
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
    hourly_rows = result["hourly_rows"]

    C = _PALETTE
    total_orders = s["total_orders"] or 1
    status_total = sum(r[1] for r in status_rows) or 1
    reject_total = sum(r[2] for r in reject_rows) or 1
    mkt_o_total = sum(r[1] for r in market_rows) or 1
    mkt_t_total = sum(r[5] for r in market_rows) or 1.0
    sec_o_total = sum(r[1] for r in sec_rows) or 1
    holder_o_total = sum(r[1] for r in holder_rows) or 1

    # --- build charts ---
    sc = {"New": C[0], "Filled": C[3], "PartiallyFilled": C[1], "Canceled": C[2], "Rejected": C[4]}
    donut_ar = _css_donut([("通过", s["accepted_orders"], C[0]), ("拒绝", s["rejected_orders"], C[2])])
    donut_cancel = _css_donut([("成功", s["cancel_success"], C[3]), ("拒绝", s["cancel_reject"], C[2])])
    pie_status = _css_pie([(r[0], r[1], sc.get(r[0], C[4])) for r in status_rows])
    pie_reject = _css_pie([(f"{r[0]}: {str(r[1])[:20]}", r[2], C[i % len(C)]) for i, r in enumerate(reject_rows)])
    pie_mkt_o = _css_pie([(r[0], r[1], C[i % len(C)]) for i, r in enumerate(market_rows)])
    pie_mkt_t = _css_pie([(r[0], r[5], C[i % len(C)]) for i, r in enumerate(market_rows)])
    bar_lat = _hbar([(r[0], r[1]) for r in latency_rows], show_pct=True)
    bar_sec = _hbar([(r[0], r[1]) for r in sec_rows])
    line_h = _svg_line(
        [("订单数", [r[1] for r in hourly_rows], C[0]),
         ("拒绝数", [r[2] for r in hourly_rows], C[2]),
         ("成交订单", [r[3] for r in hourly_rows], C[3])],
        [r[0] for r in hourly_rows],
    )

    # --- build table bodies with inline bars ---
    st_body = ""
    for st, cnt in status_rows:
        st_body += f'<tr><td>{st}</td><td style="text-align:right">{cnt:,}</td>{_pct_bar_td(cnt, status_total, sc.get(st,"#999"))}</tr>'
    rej_body = ""
    for code, text, cnt in reject_rows:
        rej_body += f'<tr><td>{code}</td><td>{text}</td><td style="text-align:right">{cnt:,}</td>{_pct_bar_td(cnt, reject_total, "#e74c3c")}</tr>'
    mkt_body = ""
    for mkt, ocnt, oqty, tcnt, tqty, to in market_rows:
        mkt_body += (f'<tr><td>{mkt}</td><td style="text-align:right">{ocnt:,}</td>{_pct_bar_td(ocnt, mkt_o_total)}'
                     f'<td style="text-align:right">{oqty:,}</td><td style="text-align:right">{tcnt:,}</td>'
                     f'<td style="text-align:right">{tqty:,}</td><td style="text-align:right">{to:,.0f}</td>'
                     f'{_pct_bar_td(to, mkt_t_total, "#27ae60")}</tr>')
    sec_body = ""
    for sid, ocnt, oqty, tcnt, tqty, to in sec_rows:
        sec_body += (f'<tr><td>{sid}</td><td style="text-align:right">{ocnt:,}</td>{_pct_bar_td(ocnt, sec_o_total)}'
                     f'<td style="text-align:right">{oqty:,}</td><td style="text-align:right">{tcnt:,}</td>'
                     f'<td style="text-align:right">{tqty:,}</td><td style="text-align:right">{to:,.0f}</td></tr>')
    hld_body = ""
    for hid, ocnt, trows, tqty in holder_rows:
        hld_body += (f'<tr><td>{hid}</td><td style="text-align:right">{ocnt:,}</td>{_pct_bar_td(ocnt, holder_o_total, "#8e44ad")}'
                     f'<td style="text-align:right">{trows:,}</td><td style="text-align:right">{tqty:,}</td></tr>')

    ts = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    lat = s["latency"]

    # NOTE: no f-string here to avoid brace escaping nightmares; use str.format / concatenation
    parts = []
    parts.append('<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"/>')
    parts.append('<meta name="viewport" content="width=device-width,initial-scale=1"/>')
    parts.append('<title>Vortex 离线分析报告</title><style>')
    parts.append('*{box-sizing:border-box}')
    parts.append('body{font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;margin:0;padding:16px 20px;color:#222;background:#eef0f4;font-size:13px}')
    parts.append('h1{font-size:22px;margin:0 0 2px} h2{font-size:14px;margin:10px 0 6px;color:#333}')
    parts.append('.sub{color:#888;font-size:12px;margin-bottom:14px}')
    parts.append('.section{background:#fff;border:1px solid #dde0e6;border-radius:8px;padding:14px 16px;margin-bottom:14px}')
    parts.append('.section-title{font-size:15px;font-weight:700;color:#1a1a2e;margin:0 0 10px;padding-bottom:8px;border-bottom:2px solid #2f80ed}')
    parts.append('.cards{display:grid;grid-template-columns:repeat(auto-fill,minmax(140px,1fr));gap:6px;margin:4px 0 0}')
    parts.append('.c{background:#f8f9fb;border:1px solid #e8eaef;border-radius:6px;padding:6px 8px}')
    parts.append('.c .k{font-size:10px;color:#888} .c .v{font-size:17px;font-weight:700;margin:1px 0} .c .s{font-size:10px;color:#aaa}')
    parts.append('.row{display:flex;gap:12px;margin:8px 0 0;flex-wrap:wrap} .col{flex:1;min-width:220px}')
    parts.append('.box{background:#f8f9fb;border:1px solid #e8eaef;border-radius:6px;padding:10px 12px;margin-bottom:8px}')
    parts.append('.box:last-child{margin-bottom:0}')
    parts.append('table{width:100%;border-collapse:collapse;font-size:11px;margin:4px 0 0}')
    parts.append('th,td{border:1px solid #eaeaea;padding:3px 5px} th{background:#f0f1f4;font-weight:600;white-space:nowrap}')
    parts.append('td{white-space:nowrap} tr:nth-child(even){background:#fafbfc}')
    parts.append('</style></head><body>')
    parts.append(f'<h1>Vortex 离线分析报告</h1><div class="sub">生成时间：{ts}</div>')

    # ── Section 1: 总览 ──
    parts.append('<div class="section">')
    parts.append('<div class="section-title">总览指标</div>')
    parts.append('<div class="cards">')
    for k, v, sub in [
        ("订单总数", f"{s['total_orders']:,}", ""),
        ("通过订单", f"{s['accepted_orders']:,}", f"占总订单 {s['accepted_orders']/total_orders*100:.1f}%"),
        ("拒绝订单", f"{s['rejected_orders']:,}", f"占总订单 {s['rejected_orders']/total_orders*100:.1f}%"),
        ("成交订单", f"{s['traded_orders']:,}", f"占总订单 {s['traded_orders']/total_orders*100:.1f}%"),
        ("成交总量", f"{s['trade_qty']:,}", ""),
        ("成交额", f"{s['trade_turnover']:,.0f}", ""),
        ("撤单请求", f"{s['cancel_total']:,}", f"成功{s['cancel_success']:,} 拒绝{s['cancel_reject']:,}"),
    ]:
        sub_html = f'<div class="s">{sub}</div>' if sub else ""
        parts.append(f'<div class="c"><div class="k">{k}</div><div class="v">{v}</div>{sub_html}</div>')
    parts.append('</div>')
    parts.append('<div class="row">')
    parts.append(f'<div class="col"><div class="box"><h2>通过 / 拒绝</h2>{donut_ar}</div></div>')
    parts.append(f'<div class="col"><div class="box"><h2>撤单结果</h2>{donut_cancel}</div></div>')
    parts.append('</div>')
    parts.append('</div>')

    # ── Section 2: 订单状态 & 拒单分析 ──
    parts.append('<div class="section">')
    parts.append('<div class="section-title">订单状态 & 拒单分析</div>')
    parts.append('<div class="cards">')
    for k, v, sub in [
        ("完全成交率", f"{s['filled_rate']*100:.2f}%", "Filled / 通过订单"),
        ("部分成交率", f"{s['partially_filled_rate']*100:.2f}%", "PartiallyFilled / 通过订单"),
        ("撤单率", f"{s['canceled_rate']*100:.2f}%", "Canceled / 通过订单"),
        ("对敲拒绝", f"{s['wash_rejects']:,}", f"占总订单 {s['wash_ratio']*100:.2f}%"),
    ]:
        sub_html = f'<div class="s">{sub}</div>' if sub else ""
        parts.append(f'<div class="c"><div class="k">{k}</div><div class="v">{v}</div>{sub_html}</div>')
    parts.append('</div>')
    parts.append('<div class="row">')
    parts.append(f'<div class="col"><div class="box"><h2>订单状态</h2>{pie_status}</div></div>')
    parts.append(f'<div class="col"><div class="box"><h2>订单状态明细</h2><table><thead><tr><th>状态</th><th>数量</th><th>占比</th></tr></thead><tbody>{st_body}</tbody></table></div></div>')
    parts.append('</div>')
    parts.append('<div class="row">')
    parts.append(f'<div class="col"><div class="box"><h2>拒单原因分布</h2>{pie_reject}</div></div>')
    parts.append(f'<div class="col"><div class="box"><h2>拒单原因明细</h2><table><thead><tr><th>拒绝码</th><th>拒绝原因</th><th>数量</th><th>占比</th></tr></thead><tbody>{rej_body}</tbody></table></div></div>')
    parts.append('</div>')
    parts.append('</div>')

    # ── Section 3: 成交延时 ──
    parts.append('<div class="section">')
    parts.append('<div class="section-title">成交延时</div>')
    parts.append(f'<div class="box"><h2>延时分布</h2>{bar_lat}')
    parts.append(f'<div style="font-size:11px;color:#888;margin-top:4px">样本={lat["count"]} | 平均={lat["avg"]}s | P50={lat["p50"]}s | P90={lat["p90"]}s | P95={lat["p95"]}s | P99={lat["p99"]}s | Max={lat["max"]}s</div></div>')
    parts.append('</div>')

    # ── Section 4: 市场分析 ──
    parts.append('<div class="section">')
    parts.append('<div class="section-title">市场分析</div>')
    parts.append('<div class="row">')
    parts.append(f'<div class="col"><div class="box"><h2>市场订单占比</h2>{pie_mkt_o}</div></div>')
    parts.append(f'<div class="col"><div class="box"><h2>市场成交额占比</h2>{pie_mkt_t}</div></div>')
    parts.append('</div>')
    parts.append(f'<div class="box"><h2>市场汇总</h2><table><thead><tr><th>市场</th><th>订单数</th><th>订单占比</th><th>订单量</th><th>成交笔数</th><th>成交量</th><th>成交额</th><th>成交额占比</th></tr></thead><tbody>{mkt_body}</tbody></table></div>')
    parts.append('</div>')

    # ── Section 5: 证券 & 股东 ──
    parts.append('<div class="section">')
    parts.append(f'<div class="section-title">证券 & 股东</div>')
    parts.append(f'<div class="box"><h2>证券 Top{len(sec_rows)}</h2>{bar_sec}</div>')
    parts.append(f'<div class="box"><table><thead><tr><th>证券代码</th><th>订单数</th><th>占比</th><th>订单量</th><th>成交笔数</th><th>成交量</th><th>成交额</th></tr></thead><tbody>{sec_body}</tbody></table></div>')
    parts.append(f'<div class="box"><h2>股东 Top{len(holder_rows)}</h2><table><thead><tr><th>股东号</th><th>订单数</th><th>占比</th><th>成交行数</th><th>成交量</th></tr></thead><tbody>{hld_body}</tbody></table></div>')
    parts.append('</div>')

    # ── Section 6: 时间趋势 ──
    parts.append('<div class="section">')
    parts.append('<div class="section-title">时间趋势</div>')
    parts.append(f'<div class="box"><h2>每分钟趋势</h2>{line_h}</div>')
    parts.append('</div>')

    parts.append('</body></html>')
    return "".join(parts)


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
        ["hour", "orders_count", "rejects_count", "traded_orders"],
    )

    print(f"Offline analytics finished. Output: {run_dir}")
    print(f"Report file: {run_dir / 'report.md'}")
    print(f"HTML report: {run_dir / 'report.html'}")


if __name__ == "__main__":
    main()
